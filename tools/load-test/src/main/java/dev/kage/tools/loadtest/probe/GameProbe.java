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
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SetTargetCommand;
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

    private GameProbe() {}

    /**
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
            if (config.moveToX().isPresent()) {
                walkTo(connection, observation, session, config.moveToX().getAsDouble());
            }
            observe(connection, config, session, observation);
        }
        session.print(observation.renderReport());
    }

    private static void enterWorld(
            GameConnection connection, ProbeConfig config, ProbeSession session, WorldObservation observation)
            throws IOException {
        connection.connect(config.username());
        CharacterSummary chosen = chooseCharacter(connection.listCharacters(), config);
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
     * Chọn nhân vật theo id, rồi theo tên, cuối cùng là nhân vật đầu tiên. KHÔNG tự tạo nhân vật
     * mới: probe dùng để soi thế giới hiện có, tạo thêm nhân vật rác chỉ làm bẩn DB dev.
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
        OptionalLong target = resolveAttackTarget(config, observation);
        if (target.isPresent()) {
            connection.send(
                    Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                    SetTargetCommand.newBuilder()
                            .setTargetEntityId(target.getAsLong())
                            .build()
                            .toByteArray());
            session.note("nhắm mục tiêu entityId=" + target.getAsLong());
        }

        connection.setReadTimeoutMillis(OBSERVE_READ_TIMEOUT_MILLIS);
        long endAt = System.currentTimeMillis() + (long) config.observeSeconds() * MILLIS_PER_SECOND;
        long nextAttackAt = System.currentTimeMillis();
        while (System.currentTimeMillis() < endAt) {
            if (target.isPresent() && System.currentTimeMillis() >= nextAttackAt) {
                connection.send(
                        Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                        AttackCommand.getDefaultInstance().toByteArray());
                nextAttackAt = System.currentTimeMillis() + config.attackIntervalMillis();
            }
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

    private static OptionalLong resolveAttackTarget(ProbeConfig config, WorldObservation observation) {
        if (config.attacksNearest()) {
            return observation.nearestMonsterId();
        }
        return config.explicitAttackTargetId();
    }
}
