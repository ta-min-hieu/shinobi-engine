package dev.kage.tools.loadtest.probe;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Client HEADLESS QUAN SÁT — vào world bằng ĐÚNG giao thức dây thật ({@link GameConnection}: TLS +
 * protobuf, y hệt client thật), rồi giải mã mọi sự kiện server đẩy xuống thành DÒNG SỐ.
 *
 * <p><b>Vì sao tồn tại</b> (ROADMAP mục 58): trong ngày 2026-09-05, ba lần kết luận rút ra từ ảnh
 * chụp màn hình đã sai hoặc không phân giải được — trong đó có câu hỏi còn treo "đứng ở x=24 thì
 * thật sự có mấy con quái trong tầm, ở toạ độ nào". Ảnh chụp không phải công cụ đo cho lớp câu hỏi
 * thời gian thực; dòng byte trên dây thì có.
 *
 * <p><b>Chạy:</b>
 *
 * <pre>{@code
 * ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --characterId=3 --moveToX=24 --observeSeconds=15"
 * }</pre>
 *
 * <p><b>Kịch bản NHIỆM VỤ</b> (2026-09-06) — cùng công cụ, thêm {@link QuestScenario}: nói chuyện
 * NPC, đi theo cây hội thoại, nhận nhiệm vụ, lĩnh thưởng, rồi in chênh lệch nhật ký/túi đồ/EXP.
 *
 * <pre>{@code
 * ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --characterId=3 \
 *     --talkToNpc=town_elder --dialogueChoices=about_town,night_watch,accept_quest:night_watch_bandit \
 *     --observeSeconds=1"
 * }</pre>
 *
 * <p><b>Kịch bản HẦM NGỤC</b> (ADR-051) — lập nhóm 1 người, vào hầm ngục, rồi đứng quan sát; gục
 * trong đó thì dòng {@code WORLD_CHANGED_EVENT reason=WORLD_CHANGE_REASON_DEFEATED} là bằng chứng.
 *
 * <pre>{@code
 * ./gradlew :tools:load-test:gameProbe --args="--username=aloha --enterDungeon=sample_dungeon --observeSeconds=30"
 * }</pre>
 *
 * <p>Kết quả ra stdout: mỗi frame nhận được là 1 dòng TSV ({@link ProbeEventLine}), cuối cùng là
 * báo cáo tổng kết ({@link WorldObservation#renderReport()}). Chuyển hướng ra file bằng {@code >}
 * nếu cần đọc lại — probe cố ý KHÔNG tự ghi file, để không phải nhận đường dẫn từ tham số (cwd của
 * Gradle là thư mục subproject chứ không phải gốc repo, bẫy đã lặp lại nhiều lần trong dự án).
 */
public final class GameProbe {

    /** Chờ bắt tay/đi bộ: rộng rãi, đây không phải đường đo hiệu năng. */
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 5000;

    /**
     * Vòng quan sát phải nhả ra định kỳ để còn kịp gửi {@code AttackCommand} — hết thời gian chờ
     * KHÔNG phải lỗi, chỉ nghĩa là AoI đang im lặng.
     */
    private static final int OBSERVE_READ_TIMEOUT_MILLIS = 200;

    private static final int MOVE_POLL_INTERVAL_MILLIS = 50;
    private static final long WALK_TIMEOUT_MILLIS = 30_000L;

    /**
     * Ngưỡng coi như "đã tới". Server chạy 60Hz với {@code MOVE_SPEED_MPS = 5}, mỗi nhịp poll 50ms
     * đi được ~0,25m — nên ngưỡng phải rộng hơn một nhịp, nếu không probe sẽ dao động qua lại quanh
     * đích mãi mãi. Vị trí THẬT vẫn được báo cáo nguyên vẹn từ {@code MoveResponse}, không làm tròn.
     */
    private static final double ARRIVAL_TOLERANCE_METERS = 0.35;

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở SimulatedPlayer. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private static final int MILLIS_PER_SECOND = 1000;

    /** Đủ cho vài chục tick 60Hz — hàng đợi world xử lý lệnh hồi máu rồi phát 408 ngay tick sau. */
    private static final long HEAL_SETTLE_MILLIS = 1500;

    private GameProbe() {}

    /**
     * Chạy một lượt probe: nối server, vào world, quan sát rồi in số liệu.
     *
     * @param args tham số {@code --key=value}, xem {@link ProbeConfig}
     * @throws IOException nếu không nối được server hoặc server từ chối — probe không đoán, nó dừng
     * @throws InterruptedException nếu bị ngắt trong lúc chờ
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        ProbeConfig config = ProbeConfig.parse(args);
        // stdout của JVM dùng charset console (cp1258/cp437 trên Windows) — ép UTF-8 để báo cáo
        // tiếng Việt không thành rác khi chuyển hướng ra file.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        WorldObservation observation = new WorldObservation();
        ProbeSession session = new ProbeSession(out, observation, System.nanoTime());

        GameConnection.GatewayEndpoint endpoint =
                new GameConnection.GatewayEndpoint(config.host(), config.port(), HANDSHAKE_TIMEOUT_MILLIS);
        try (GameConnection connection = GameConnection.open(endpoint, session)) {
            enterWorld(connection, config, session, observation);
            ArenaScenario arena = prepareBeforeObserving(connection, config, session, observation);
            observe(connection, config, session, observation);
            arena.forfeitIfRequested(config.duel());
            new PartyScenario(connection, session).reportIfRequested(config.party());
            new TradeScenario(connection, session, HANDSHAKE_TIMEOUT_MILLIS).reportIfRequested(config.trade());
            // Uống thuốc SAU vòng quan sát: máu không được lưu vào DB (thoát ra vào lại là đầy),
            // nên muốn thấy đan dược làm gì thì phải đánh mất máu TRƯỚC, trong cùng phiên này.
            // Nhiều mã cách nhau dấu phẩy = uống LIÊN TIẾP — thứ duy nhất làm hồi chiêu vật phẩm (theo
            // kết nối) quan sát được.
            if (!config.useItemCodes().isEmpty()) {
                for (String itemCode : config.useItemCodes()) {
                    new UseItemScenario(connection, session).run(itemCode);
                }
                drainForMillis(connection, HEAL_SETTLE_MILLIS); // đợi HealthChangedEvent (408) tới
            }
            // Đi bộ LẦN HAI, sau khi đã mất máu — thứ duy nhất làm vùng an toàn (ADR-046 D3) quan
            // sát được: nó chỉ kích hoạt khi BƯỚC VÀO vùng, mà lần đi bộ đầu xảy ra lúc còn đầy máu.
            if (config.returnToX().isPresent()) {
                walkTo(connection, observation, session, config.returnToX().getAsDouble());
            }
            // Cửa sổ ĐỨNG YÊN KHÔNG ĐÁNH AI — thứ duy nhất làm hồi máu ngoài chiến đấu (ADR-046 D2)
            // quan sát được: nó đòi 8 giây không gây và không chịu damage, mà vòng observe ở trên
            // thì vừa đánh vừa bị đánh.
            if (config.settleSeconds() > 0) {
                session.note("đứng yên " + config.settleSeconds() + "s để đo hồi máu ngoài chiến đấu");
                drainForMillis(connection, (long) config.settleSeconds() * MILLIS_PER_SECOND);
            }
        }
        session.print(observation.renderReport());
    }

    /**
     * Mọi bước TRƯỚC vòng quan sát: mặc đồ, đi bộ, vào hầm ngục/đấu trường, nhiệm vụ. Trả về kịch bản
     * Arena để bước bỏ cuộc (sau vòng quan sát) dùng lại.
     */
    private static ArenaScenario prepareBeforeObserving(
            GameConnection connection, ProbeConfig config, ProbeSession session, WorldObservation observation)
            throws IOException, InterruptedException {
        // Mặc đồ TRƯỚC khi đi bộ: `bonus_hp` nâng MÁU TỐI ĐA thật (ADR-036) và server trả máu
        // mới ngay trong EquipItemResponse (ADR-043), nên mặc sau khi đi thì "máu lúc tới nơi"
        // đo ra sai. Ngược hẳn với UseItemScenario bên dưới — món tiêu hao phải dùng SAU khi
        // đã mất máu, trong cùng phiên.
        new PartyScenario(connection, session).join(config.party());
        new TradeScenario(connection, session, HANDSHAKE_TIMEOUT_MILLIS).open(config.trade());
        if (!config.sellItems().isEmpty() || !config.buyItems().isEmpty()) {
            new ShopScenario(connection, session).run(config.sellItems(), config.buyItems());
        }
        if (!config.equipItemCodes().isEmpty()) {
            new EquipScenario(connection, session).run(config.equipItemCodes());
        }
        SessionEdgeScenario edge = new SessionEdgeScenario(connection, session);
        edge.runBeforeWalking(config.edge());
        if (config.moveToX().isPresent()) {
            walkTo(connection, observation, session, config.moveToX().getAsDouble());
        }
        edge.sendChat(config.edge());
        if (!config.enterDungeon().isEmpty()) {
            new DungeonScenario(connection, session).run(config.enterDungeon());
        }
        ArenaScenario arena = new ArenaScenario(connection, session, HANDSHAKE_TIMEOUT_MILLIS);
        arena.enter(config.duel());
        if (config.quest().isRequested()) {
            new QuestScenario(connection, session, observation).run(config.quest());
        }
        return arena;
    }

    private static void enterWorld(
            GameConnection connection, ProbeConfig config, ProbeSession session, WorldObservation observation)
            throws IOException {
        connection.connect(config.username());
        ListCharactersResponse characters = connection.listCharacters();
        if (needsCreation(characters.getCharactersList(), config)) {
            CreateCharacterResponse created = connection.createCharacter(config.characterName());
            if (!created.getSuccess()) {
                throw new IOException(
                        "Server từ chối CreateCharacter '" + config.characterName() + "': " + created.getErrorKey());
            }
            session.note("tạo nhân vật MỚI characterId="
                    + created.getCharacter().getId() + " tên=" + config.characterName());
            characters = connection.listCharacters();
        }
        CharacterSummary chosen = chooseCharacter(characters, config);
        SelectCharacterResponse selected = connection.selectCharacter(chosen.getId());
        if (!selected.getSuccess()) {
            throw new IOException("Server từ chối SelectCharacter: " + selected.getErrorKey());
        }
        observation.enterWorld(chosen, selected);
        session.note(String.format(
                Locale.ROOT,
                "vào world characterId=%d tên=%s spawn=(%.2f, %.2f) máu=%d/%d",
                chosen.getId(),
                chosen.getName(),
                selected.getSpawnX(),
                selected.getSpawnY(),
                selected.getCurrentHp(),
                selected.getMaxHp()));
    }

    /**
     * {@code true} khi người dùng xin {@code --createCharacter=true} VÀ account chưa có nhân vật mang
     * đúng {@code characterName}. Gọi lại lần hai với cùng tên thì KHÔNG tạo thêm — lượt chạy sau của
     * cùng kịch bản dùng lại đúng nhân vật đó (kiểm sống chuỗi quest chạy hàng chục lượt probe).
     *
     * @throws IOException nếu xin tạo mà không cho tên — tạo nhân vật vô danh là đoán thay người dùng
     */
    static boolean needsCreation(List<CharacterSummary> existing, ProbeConfig config) throws IOException {
        if (!config.createCharacter()) {
            return false;
        }
        if (config.characterName().isEmpty()) {
            throw new IOException("--createCharacter=true cần --characterName=<tên nhân vật mới>");
        }
        return existing.stream().noneMatch(summary -> config.characterName().equals(summary.getName()));
    }

    /**
     * Chọn nhân vật theo id, rồi theo tên, cuối cùng là nhân vật đầu tiên. KHÔNG tự tạo nhân vật
     * mới trừ khi được xin rõ ({@link #needsCreation}): probe dùng để soi thế giới hiện có, tạo thêm
     * nhân vật rác chỉ làm bẩn DB dev.
     */
    private static CharacterSummary chooseCharacter(ListCharactersResponse characters, ProbeConfig config)
            throws IOException {
        List<CharacterSummary> all = characters.getCharactersList();
        if (all.isEmpty()) {
            throw new IOException("Account " + config.username() + " chưa có nhân vật nào — probe không tự tạo");
        }
        Optional<CharacterSummary> match =
                all.stream().filter(summary -> matches(summary, config)).findFirst();
        return match.orElseThrow(() -> new IOException("Không thấy nhân vật khớp (characterId=" + config.characterId()
                + ", characterName='" + config.characterName() + "') trong " + all.size() + " nhân vật của account"));
    }

    private static boolean matches(CharacterSummary summary, ProbeConfig config) {
        if (config.characterId() != 0L) {
            return summary.getId() == config.characterId();
        }
        return config.characterName().isEmpty() || config.characterName().equals(summary.getName());
    }

    /**
     * Đi bộ THẬT tới toạ độ x: gửi {@code MoveCommand} hướng ±1 rồi đọc {@code MoveResponse} để
     * biết server đã đưa mình tới đâu — KHÔNG tự tính vị trí phía client (Server Authority; và
     * chính việc client tự đoán vị trí là thứ ADR-041 vừa phải đi sửa).
     */
    private static void walkTo(
            GameConnection connection, WorldObservation observation, ProbeSession session, double targetX)
            throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + WALK_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline
                && Math.abs(targetX - observation.playerX()) > ARRIVAL_TOLERANCE_METERS) {
            float direction = targetX > observation.playerX() ? 1f : -1f;
            connection.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(direction).build().toByteArray());
            connection.readUntilOpcode(Opcode.OPCODE_MOVE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
            Thread.sleep(MOVE_POLL_INTERVAL_MILLIS);
        }
        connection.send(
                Opcode.OPCODE_MOVE_COMMAND_VALUE,
                MoveCommand.newBuilder().setDirX(0f).build().toByteArray());
        connection.readUntilOpcode(Opcode.OPCODE_MOVE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
        session.note(
                String.format(Locale.ROOT, "đi bộ xong: đích=%.2f vị trí thật=%.2f", targetX, observation.playerX()));
    }

    /**
     * Đứng đọc trong {@code observeSeconds} giây, xen kẽ gửi {@code AttackCommand} nếu người dùng
     * yêu cầu. Mọi frame nhận được đã tự động thành dòng + cập nhật ảnh chụp qua {@link ProbeSession}.
     */
    private static void observe(
            GameConnection connection, ProbeConfig config, ProbeSession session, WorldObservation observation)
            throws IOException, InterruptedException {
        // Mục tiêu chọn lại mỗi vòng khi cần — xem TargetSelector (bẫy "chưa thấy quái lúc bắt đầu"
        // và "mục tiêu chết là ngừng đánh").
        TargetSelector selector = new TargetSelector(config, observation);
        if (selector.current().isPresent()) {
            aimAt(connection, session, selector.current().getAsLong());
        }

        connection.setReadTimeoutMillis(OBSERVE_READ_TIMEOUT_MILLIS);
        long endAt = System.currentTimeMillis() + (long) config.observeSeconds() * MILLIS_PER_SECOND;
        // Nhịp gửi: thử lại dày trước nhát đầu, rồi đúng attackIntervalMillis — xem AttackCadence
        // (kiểm sống V50: lệnh mở màn ngoài tầm bị bỏ im lặng, sinh nhát "phủ đầu" thứ ba giả).
        AttackCadence cadence = new AttackCadence(config.attackIntervalMillis(), System.currentTimeMillis());
        // Đòn đã ra TRƯỚC mục tiêu hiện tại — AttackCadence đếm "nhát mở màn" theo TỪNG mục tiêu.
        int attacksBeforeCurrentTarget = observation.ownBasicAttacksResolved();
        long nextSelfBuffAt = System.currentTimeMillis();
        while (System.currentTimeMillis() < endAt) {
            nextSelfBuffAt = castSelfBuffIfDue(connection, config, session, nextSelfBuffAt);
            OptionalLong changed = selector.refresh(observation);
            if (changed.isPresent()) {
                aimAt(connection, session, changed.getAsLong());
                cadence = new AttackCadence(config.attackIntervalMillis(), System.currentTimeMillis());
                attacksBeforeCurrentTarget = observation.ownBasicAttacksResolved();
            }
            if (selector.current().isEmpty()) {
                readOneFrameOrWait(connection);
                continue;
            }
            long now = System.currentTimeMillis();
            // Chiêu không sinh AttackResultEvent nên không có "nhát đầu đã ra" để chờ — coi như đã xác
            // nhận ngay, AttackCadence giữ đúng nhịp attackIntervalMillis từ đầu.
            int strikesResolved = config.attackSkillCode().isEmpty()
                    ? observation.ownBasicAttacksResolved() - attacksBeforeCurrentTarget
                    : 1;
            if (cadence.isDue(now, strikesResolved)) {
                sendStrike(connection, config, selector.current().getAsLong());
                cadence.onSent(now);
            }
            connection.setReadTimeoutMillis(
                    cadence.readTimeoutMillis(System.currentTimeMillis(), OBSERVE_READ_TIMEOUT_MILLIS));
            readOneFrameOrWait(connection);
        }
        // Trả lại cửa sổ đọc như trước vòng lặp: các bước SAU (uống thuốc, bỏ cuộc Arena) chờ phản
        // hồi bằng cửa sổ này, và 20ms của AttackCadence là quá hẹp cho họ.
        connection.setReadTimeoutMillis(OBSERVE_READ_TIMEOUT_MILLIS);
    }

    /**
     * Tung chiêu tự buff ({@code --selfBuffSkillCode}) nếu đã tới hạn, ghi mốc vào báo cáo để tách các
     * đòn trong/ngoài thời gian buff.
     *
     * @return hạn tung kế tiếp
     */
    private static long castSelfBuffIfDue(
            GameConnection connection, ProbeConfig config, ProbeSession session, long dueAtMillis) throws IOException {
        String skillCode = config.edge().selfBuffSkillCode();
        long now = System.currentTimeMillis();
        if (skillCode.isEmpty() || now < dueAtMillis) {
            return dueAtMillis;
        }
        connection.send(
                Opcode.OPCODE_USE_SKILL_COMMAND_VALUE,
                UseSkillCommand.newBuilder().setSkillCode(skillCode).build().toByteArray());
        session.note("tự buff: " + skillCode);
        return now + config.edge().selfBuffIntervalMillis();
    }

    /** Một nhịp ra đòn: đánh thường, hoặc {@code UseSkillCommand} nếu có {@code --attackSkillCode}. */
    private static void sendStrike(GameConnection connection, ProbeConfig config, long targetEntityId)
            throws IOException {
        if (config.attackSkillCode().isEmpty()) {
            connection.send(
                    Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                    AttackCommand.getDefaultInstance().toByteArray());
            return;
        }
        connection.send(
                Opcode.OPCODE_USE_SKILL_COMMAND_VALUE,
                UseSkillCommand.newBuilder()
                        .setSkillCode(config.attackSkillCode())
                        .setTargetEntityId(targetEntityId)
                        .build()
                        .toByteArray());
    }

    /**
     * Đọc tiếp trong {@code millis} mili-giây rồi thôi — dùng sau một lệnh có hiệu ứng ĐI QUA
     * HÀNG ĐỢI WORLD ({@code enqueueHealCharacter}), nơi kết quả tới bằng một event RIÊNG chứ không
     * nằm trong response (ADR-046 D5). Không có bước này thì probe đóng socket trước khi 408 kịp tới
     * và báo cáo sẽ nói "máu không đổi" — sai, và sai theo hướng khó nghi ngờ nhất.
     */
    private static void drainForMillis(GameConnection connection, long millis) throws IOException {
        long endAt = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < endAt) {
            readOneFrameOrWait(connection);
        }
    }

    /**
     * Hết thời gian chờ mà không frame nào tới là chuyện BÌNH THƯỜNG với một client chỉ quan sát:
     * AoI im lặng khi không ai di chuyển hay đánh nhau. Đây là tín hiệu điều khiển vòng lặp, không
     * phải lỗi bị nuốt — mọi {@link IOException} khác vẫn nổi lên nguyên vẹn.
     */
    private static void readOneFrameOrWait(GameConnection connection) throws IOException {
        try {
            connection.readFrame();
        } catch (SocketTimeoutException expected) {
            // Không có frame nào trong cửa sổ chờ — xem javadoc ngay trên. Vòng lặp đi tiếp.
        }
    }

    private static void aimAt(GameConnection connection, ProbeSession session, long targetEntityId) throws IOException {
        connection.send(
                Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                SetTargetCommand.newBuilder()
                        .setTargetEntityId(targetEntityId)
                        .build()
                        .toByteArray());
        session.note("nhắm mục tiêu entityId=" + targetEntityId);
    }
}
