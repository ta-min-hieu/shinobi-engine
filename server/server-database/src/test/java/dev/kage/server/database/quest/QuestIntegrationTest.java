package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.ExperienceCurve;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.TransactionRunner;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V11 (quest_template/
 * character_quest_progress) + toàn bộ vòng đời quest THẬT bằng script sản phẩm
 * (kill_training_dummies.js, seed V11): nhận → giết 3 lần → hoàn thành → nhận thưởng, atomically.
 *
 * <p>Từ 2026-09-05 lớp này phủ CẢ quest thứ hai ({@code night_watch_bandit}, seed V28,
 * night_watch_bandit.js). Hai quest cùng một lớp test vì {@code @Container} là static — tách lớp
 * riêng sẽ dựng thêm một Postgres nữa cho toàn bộ suite, đắt hơn hẳn phần đọc dễ thu được.
 *
 * <p>⚠️ Lập luận đó có TRẦN, và quest thứ sáu đã chạm: thêm khối của nó vào đây làm lớp vượt
 * ngưỡng {@code CyclomaticComplexity} của PMD (tính cho cả lớp). Vì vậy quest 6 sống ở
 * {@link OuterSectTallyQuestIntegrationTest} — quest thứ bảy trở đi cũng nên tách theo vùng
 * nghiệp vụ như vậy, đừng cố nhét tiếp vào đây.
 */
@Testcontainers
class QuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";
    private static final String NIGHT_WATCH_CODE = "night_watch_bandit";
    private static final String OLD_ARCHIVE_CODE = "clear_old_archive";
    private static final String AWAKENING_TRIAL_CODE = "awakening_trial";
    private static final String REPORT_TO_OUTER_SECT_CODE = "report_to_outer_sect";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: @BeforeAll ghi ở luồng JUnit, còn các test đồng thời đọc nó từ luồng khác —
    // ghi một biến 64-bit không volatile không bảo đảm nguyên tử (SpotBugs AT).
    static volatile long accountId;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "questtester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static long readExp(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT exp FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp: characterId=" + characterId, e);
        }
    }

    private static int countInventoryRows(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm inventory_item: characterId=" + characterId, e);
        }
    }

    /**
     * Tổng số lượng của MỘT mã vật phẩm (không phải số dòng): {@code enhance_protection_charm} là
     * đồ xếp chồng được, nên phần thưởng có thể gộp vào dòng sẵn có thay vì sinh dòng mới —
     * {@link #countInventoryRows} sẽ không thấy gì cả nếu chỉ đếm dòng.
     */
    private static int countItemQuantity(long characterId, String itemCode) {
        String sql = "SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii"
                + " JOIN item_template it ON it.id = ii.item_template_id"
                + " WHERE ii.character_id = ? AND it.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, itemCode);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm vật phẩm: " + itemCode, e);
        }
    }

    /**
     * {@code monster_template.exp_reward} đọc THẲNG từ seed — không chép số vào test.
     *
     * <p>Đây là điểm khác biệt của {@link #shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain()}
     * so với một phép tính tay: hạ chỉ số EXP của một loài quái trong migration sau sẽ làm test đó
     * đỏ, đúng như nó phải thế — quest 4 hứa một mốc CẤP ĐỘ, và mốc đó phụ thuộc vào cả EXP quái.
     */
    private static long expRewardOf(String monsterCode) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT exp_reward FROM monster_template WHERE code = ?")) {
            statement.setString(1, monsterCode);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp_reward: " + monsterCode, e);
        }
    }

    private static int readLevel(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT level FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc level: characterId=" + characterId, e);
        }
    }

    /** {@code quest_template.code} của quest tiên quyết, hoặc {@code null} nếu quest không có. */
    private static String prerequisiteCodeOf(String questCode) {
        String sql = "SELECT pre.code FROM quest_template q"
                + " LEFT JOIN quest_template pre ON pre.id = q.prerequisite_quest_id"
                + " WHERE q.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, questCode);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc prerequisite: " + questCode, e);
        }
    }

    private static AcceptQuestUseCase newAcceptUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new AcceptQuestUseCase(progressPort, templatePort, tx);
    }

    private static ProcessQuestEventUseCase newProcessUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        // Không cần QuestTemplatePort nữa: use case đọc scriptRef ngay từ listInProgress (2026-09-05).
        return new ProcessQuestEventUseCase(
                progressPort, new PostgresQuestItemTakeAdapter(), new PostgresQuestAuditAdapter(), tx);
    }

    private static ClaimQuestRewardUseCase newClaimUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter());
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
    }

    /**
     * ADR-044 — quest 2 nay đòi quest 1 đã CLAIMED. Mọi test cần quest 2 đều phải đi qua đây.
     */
    private static void claimTrainingDummies(long characterId) {
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldPlayFullQuestLifecycle_withRealScript() {
        long characterId = createFreshCharacter("QuestPlayer1");

        AcceptQuestUseCase.AcceptResult acceptResult = newAcceptUseCase().execute(characterId, QUEST_CODE);
        assertThat(acceptResult).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Kịch bản thật (kill_training_dummies.js) cần giết 3 lần mới hoàn thành.
        assertThat(newProcessUseCase().monsterKilled(characterId, "training_dummy"))
                .isEmpty();
        assertThat(newProcessUseCase().monsterKilled(characterId, "training_dummy"))
                .isEmpty();
        List<ProcessQuestEventUseCase.QuestStatusChange> afterThird =
                newProcessUseCase().monsterKilled(characterId, "training_dummy");
        assertThat(afterThird).hasSize(1);
        assertThat(afterThird.get(0).questCode()).isEqualTo(QUEST_CODE);
        assertThat(afterThird.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int itemsBefore = countInventoryRows(characterId);

        ClaimQuestRewardUseCase.ClaimResult claimResult =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());
        assertThat(claimResult.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claimResult.expGranted()).isEqualTo(500L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 500L);
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsBefore + 1); // 5 kunai trong 1 dòng mới

        // Không nhận thưởng lặp lại được (status đã CLAIMED).
        ClaimQuestRewardUseCase.ClaimResult secondClaim =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());
        assertThat(secondClaim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
    }

    @Test
    void shouldListScriptRef_forStartupValidation() {
        // SCRIPTING.md §8 — validate lúc khởi động cần liệt kê ĐÚNG script_ref đang tham chiếu
        // thật trong DB (V11 seed), không phải danh sách tự khai trong code.
        List<String> scriptRefs = new PostgresQuestTemplateAdapter(dataSource).listAllScriptRefs();

        assertThat(scriptRefs)
                .contains(
                        "/scripts/quest/kill_training_dummies.js",
                        "/scripts/quest/night_watch_bandit.js",
                        "/scripts/quest/clear_old_archive.js",
                        "/scripts/quest/awakening_trial.js");
    }

    @Test
    void shouldRejectDoubleAccept() {
        long characterId = createFreshCharacter("QuestPlayer2");
        newAcceptUseCase().execute(characterId, QUEST_CODE);

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
    }

    @Test
    void shouldRejectClaim_beforeAccepting() {
        long characterId = createFreshCharacter("QuestPlayer3");

        ClaimQuestRewardUseCase.ClaimResult result =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_ACCEPTED);
    }

    @Test
    void shouldRejectDuplicateClaim_whenIdempotencyKeyReused() {
        long characterId = createFreshCharacter("QuestPlayer4");
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        String key = newIdempotencyKey();
        newClaimUseCase().execute(characterId, QUEST_CODE, key); // lần đầu — nhận thưởng thật

        long expAfterFirstClaim = readExp(characterId);
        int itemsAfterFirstClaim = countInventoryRows(characterId);
        ClaimQuestRewardUseCase.ClaimResult result = newClaimUseCase().execute(characterId, QUEST_CODE, key);

        // Trạng thái CLAIMED sau lần đầu đã tự chặn NOT_COMPLETED_YET rồi (business guard) — request
        // song song THẬT SỰ (2 request cùng đọc status COMPLETED trước khi request đầu commit) mới
        // chạm nhánh DUPLICATE_REQUEST của idempotencyKey; ở đây verify KHÔNG có phản ứng phụ nào
        // lặp lại dù nhánh nào được trả về (EXP/item không tăng lần 2 SO VỚI SAU lần nhận đầu tiên).
        assertThat(result.outcome())
                .isIn(
                        ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET,
                        ClaimQuestRewardUseCase.ClaimResult.Outcome.DUPLICATE_REQUEST);
        assertThat(readExp(characterId)).isEqualTo(expAfterFirstClaim); // KHÔNG cộng EXP lần 2
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsAfterFirstClaim); // KHÔNG cấp item lần 2
    }

    /**
     * Vòng đời TRỌN VẸN của quest thứ hai qua script sản phẩm thật (night_watch_bandit.js, seed
     * V28). Đây là quest THỨ HAI theo dõi giết quái của dự án — trước 2026-09-05 nó KHÔNG THỂ chạy:
     * {@code ProcessQuestEventUseCase} hardcode đúng một mã quest nên script này không bao giờ
     * nhận được {@code on_event}. Test này là bằng chứng phần tổng quát hoá đó chạy thật, xuyên
     * Postgres, chứ không chỉ xanh trên Fake Port.
     */
    @Test
    void shouldPlayNightWatchLifecycle_withRealScript() {
        long characterId = createFreshCharacter("NightWatch1");
        claimTrainingDummies(characterId); // ADR-044 — quest 2 khoá sau quest 1

        assertThat(newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Giết nhầm loài: CHÍNH SCRIPT lọc theo event.monster_code, Java không lọc (Điều 27).
        assertThat(newProcessUseCase().monsterKilled(characterId, "training_dummy"))
                .isEmpty();

        List<ProcessQuestEventUseCase.QuestStatusChange> afterBandit =
                newProcessUseCase().monsterKilled(characterId, "wandering_bandit");
        assertThat(afterBandit).hasSize(1);
        assertThat(afterBandit.get(0).questCode()).isEqualTo(NIGHT_WATCH_CODE);
        assertThat(afterBandit.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int charmsBefore = countItemQuantity(characterId, "enhance_protection_charm");

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, NIGHT_WATCH_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(800L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 800L);
        assertThat(countItemQuantity(characterId, "enhance_protection_charm")).isEqualTo(charmsBefore + 1);
    }

    /**
     * Cú giết ĐI TỚI ĐÚNG quest quan tâm nó, khi người chơi đang làm NHIỀU quest cùng lúc — đây là
     * chỗ bản hardcode cũ sai: nó chỉ tra một mã quest, nên quest kia không bao giờ nhúc nhích.
     */
    @Test
    void shouldRouteKillToTheQuestThatCaresAboutIt_whenTwoQuestsAreInProgress() {
        long characterId = createFreshCharacter("NightWatch2");
        // ADR-044 — phải xong quest 1 mới mở được quest 2; rồi nhận LẠI quest 1 thì không được nữa
        // (ALREADY_ACCEPTED), nên test này dùng quest 1 của một nhân vật khác làm quest thứ hai
        // đang chạy song song là không được. Thay bằng: quest 2 đang làm, quest 1 làm LẠI từ đầu
        // không khả thi ⇒ chỉ cần HAI quest cùng IN_PROGRESS, và cách duy nhất là nhận quest 2
        // TRƯỚC khi lĩnh thưởng quest 1... cũng bị ADR-044 chặn. Nên: nhận quest 1, xong, lĩnh
        // thưởng, nhận quest 2 — rồi verify cú giết đi tới ĐÚNG quest đang làm và KHÔNG đụng quest
        // đã CLAIMED.
        claimTrainingDummies(characterId);
        newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);

        List<ProcessQuestEventUseCase.QuestStatusChange> afterBandit =
                newProcessUseCase().monsterKilled(characterId, "wandering_bandit");

        // Chỉ quest "trực đêm" xong; quest đập mộc nhân KHÔNG bị cú giết này chạm tới.
        assertThat(afterBandit)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(NIGHT_WATCH_CODE);

        // Và quest ĐÃ LĨNH THƯỞNG không bị cú giết nào chạm tới nữa: listInProgress lọc theo
        // status='IN_PROGRESS' ngay trong SQL, nên quest CLAIMED — thứ chỉ tăng theo đời nhân vật —
        // không bao giờ được nạp lại.
        assertThat(newProcessUseCase().monsterKilled(characterId, "training_dummy"))
                .isEmpty();
    }

    /**
     * V28 phải trỏ {@code prerequisite_quest_id} về {@code kill_training_dummies} bằng cách TRA
     * THEO {@code code}, không hardcode số 1 (id do {@code GENERATED ALWAYS AS IDENTITY} cấp).
     *
     * <p>⚠️ Test này chỉ verify DỮ LIỆU. Cột này CHƯA được đọc ở bất kỳ đâu trong code — xem
     * {@link #shouldStillAcceptNightWatch_becausePrerequisiteIsNotEnforcedYet}.
     */
    @Test
    void shouldSeedNightWatch_withPrerequisiteResolvedByCode() {
        assertThat(prerequisiteCodeOf(NIGHT_WATCH_CODE)).isEqualTo(QUEST_CODE);
        assertThat(prerequisiteCodeOf(QUEST_CODE)).isNull();
    }

    /**
     * ADR-044 (Accepted 2026-09-05) — ĐẢO NGƯỢC khẳng định cũ. Bản trước của test này ghi nhận
     * trung thực rằng {@code prerequisite_quest_id} KHÔNG được thi hành, và nói rõ nó "sẽ ĐỎ khi ai
     * đó thi hành — đó là dấu hiệu ĐÚNG". Việc đó vừa xảy ra.
     *
     * <p>Nhân vật mới tinh chưa đập mộc nhân nào ⇒ không nhận được quest "trực đêm".
     */
    @Test
    void shouldRejectNightWatch_whenTrainingDummiesNotClaimedYet() {
        long characterId = createFreshCharacter("NightWatch3");

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /**
     * Mốc là CLAIMED chứ KHÔNG phải COMPLETED (chủ dự án chốt 2026-09-05): đánh xong 3 mộc nhân mà
     * chưa quay lại lĩnh thưởng thì chuỗi kể chuyện chưa đóng, và Chu Hoành chưa có cớ giao việc mới.
     */
    @Test
    void shouldStillRejectNightWatch_whenPrerequisiteIsCompletedButNotClaimed() {
        long characterId = createFreshCharacter("NightWatch4");
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy"); // COMPLETED, chưa CLAIMED

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /** Lĩnh thưởng xong quest 1 thì quest 2 mở khoá — đúng thứ tự STORY.md §6. */
    @Test
    void shouldAcceptNightWatch_onceTrainingDummiesIsClaimed() {
        long characterId = createFreshCharacter("NightWatch5");
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newProcessUseCase().monsterKilled(characterId, "training_dummy");
        newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
    }

    /** Quest KHÔNG có tiên quyết giữ nguyên hành vi cũ — ADR-044 D6, không được chặn nhầm. */
    @Test
    void shouldStillAcceptQuestWithoutPrerequisite_forABrandNewCharacter() {
        long characterId = createFreshCharacter("NightWatch6");

        assertThat(newAcceptUseCase().execute(characterId, QUEST_CODE)).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
    }

    // ---------------------------------------------------------------------------------------
    // Quest THỨ BA — `clear_old_archive` (seed V30, STORY.md §6 arc 1 nhịp "gieo mầm").
    //
    // Giá trị thật của khối test này KHÔNG phải là "quest 3 chạy". Nó là bằng chứng rằng thêm một
    // quest theo dõi giết quái nay là việc của DỮ LIỆU: migration + script + i18n, không một dòng
    // Java sản phẩm nào bị sửa. Quest 2 mới chỉ chứng minh được điều đó MỘT lần, ngay sau đợt gỡ
    // hardcode; quest 3 là lần thứ hai, trên một loài quái cũng hoàn toàn mới.
    // ---------------------------------------------------------------------------------------

    /** Đưa nhân vật qua trọn quest 1 + quest 2 — điều kiện ADR-044 để mở quest 3. */
    private static void claimNightWatch(long characterId) {
        claimTrainingDummies(characterId);
        newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);
        newProcessUseCase().monsterKilled(characterId, "wandering_bandit");
        newClaimUseCase().execute(characterId, NIGHT_WATCH_CODE, newIdempotencyKey());
    }

    @Test
    void shouldPlayOldArchiveLifecycle_withRealScript() {
        long characterId = createFreshCharacter("OldArchive1");
        claimNightWatch(characterId); // ADR-044 — quest 3 khoá sau quest 2

        assertThat(newAcceptUseCase().execute(characterId, OLD_ARCHIVE_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Giết nhầm loài — kể cả loài của ĐÚNG quest liền trước — không nhúc nhích: CHÍNH SCRIPT
        // lọc theo event.monster_code, Java không lọc (Điều 27).
        assertThat(newProcessUseCase().monsterKilled(characterId, "wandering_bandit"))
                .isEmpty();

        // Script đòi HAI cỗ, nên con thứ nhất chưa đóng quest.
        assertThat(newProcessUseCase().monsterKilled(characterId, "archive_warden"))
                .isEmpty();

        List<ProcessQuestEventUseCase.QuestStatusChange> afterSecond =
                newProcessUseCase().monsterKilled(characterId, "archive_warden");
        assertThat(afterSecond).hasSize(1);
        assertThat(afterSecond.get(0).questCode()).isEqualTo(OLD_ARCHIVE_CODE);
        assertThat(afterSecond.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int shardsBefore = countItemQuantity(characterId, "ancient_jade_shard");

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, OLD_ARCHIVE_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(1200L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 1200L);
        // Mảnh ngọc cổ là McGuffin của cả truyện (STORY.md §6 arc 1) — nó phải NẰM THẬT trong túi,
        // không phải chỉ là một câu trong script.
        assertThat(countItemQuantity(characterId, "ancient_jade_shard")).isEqualTo(shardsBefore + 1);
    }

    /** V30 phải tra `night_watch_bandit` theo `code`, không hardcode id (khuôn V28). */
    @Test
    void shouldSeedOldArchive_withPrerequisitePointingAtTheNightWatch() {
        assertThat(prerequisiteCodeOf(OLD_ARCHIVE_CODE)).isEqualTo(NIGHT_WATCH_CODE);
    }

    @Test
    void shouldRejectOldArchive_whenNightWatchNotClaimedYet() {
        long characterId = createFreshCharacter("OldArchive2");
        claimTrainingDummies(characterId); // xong quest 1, nhưng chưa đụng quest 2

        assertThat(newAcceptUseCase().execute(characterId, OLD_ARCHIVE_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /**
     * ADR-044 Consequences ghi rõ chuỗi quest KHÔNG tự kiểm bắc cầu: nhận quest 3 chỉ kiểm quest 2.
     * Ở đây điều đó không tạo lỗ hổng nào — muốn CLAIMED quest 2 thì đã phải qua quest 1 — nhưng ô
     * test này ghim đúng phạm vi ấy lại, để không ai đọc nhầm thành "kiểm cả chuỗi".
     */
    @Test
    void shouldOnlyCheckTheImmediatePrerequisite_notTheWholeChain() {
        long characterId = createFreshCharacter("OldArchive3");
        claimNightWatch(characterId);

        assertThat(prerequisiteCodeOf(OLD_ARCHIVE_CODE)).isEqualTo(NIGHT_WATCH_CODE);
        assertThat(prerequisiteCodeOf(NIGHT_WATCH_CODE)).isEqualTo(QUEST_CODE);
        assertThat(newAcceptUseCase().execute(characterId, OLD_ARCHIVE_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
    }

    // ---------------------------------------------------------------------------------------
    // Quest THỨ TƯ — `awakening_trial` (seed V33, STORY.md §6 arc 1 nhịp ĐÓNG).
    //
    // Khác ba quest trước ở đúng một điểm, và điểm đó là lý do khối test này tồn tại: quest 4 có
    // một mốc CƠ CHẾ nằm ngay sau nó. Lời thoại của Chu Hoành hứa "thức tỉnh linh căn", mà thứ mở
    // khoá chọn hệ phái là `SelectFactionUseCase.MIN_LEVEL = 10`. Nếu người chơi lĩnh thưởng xong
    // mà vẫn Cấp 9 thì quest đã nói dối — và không một test nào của ba quest trước bắt được điều
    // đó, vì trước nay chưa quest nào hứa hẹn gì ngoài EXP.
    // ---------------------------------------------------------------------------------------

    /** Đưa nhân vật qua trọn quest 1 + 2 + 3 — điều kiện ADR-044 để mở quest 4. */
    private static void claimOldArchive(long characterId) {
        claimNightWatch(characterId);
        newAcceptUseCase().execute(characterId, OLD_ARCHIVE_CODE);
        newProcessUseCase().monsterKilled(characterId, "archive_warden");
        newProcessUseCase().monsterKilled(characterId, "archive_warden");
        newClaimUseCase().execute(characterId, OLD_ARCHIVE_CODE, newIdempotencyKey());
    }

    @Test
    void shouldPlayAwakeningTrialLifecycle_withRealScript() {
        long characterId = createFreshCharacter("Awakening1");
        claimOldArchive(characterId); // ADR-044 — quest 4 khoá sau quest 3

        assertThat(newAcceptUseCase().execute(characterId, AWAKENING_TRIAL_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Hạ nhầm loài — kể cả loài của đúng quest liền trước, thứ người chơi vừa đi ngang qua trên
        // đường tới sân khảo hạch — không nhúc nhích: CHÍNH SCRIPT lọc theo event.monster_code.
        assertThat(newProcessUseCase().monsterKilled(characterId, "archive_warden"))
                .isEmpty();

        // MỘT trận, không phải hai — xem awakening_trial.js về vì sao N = 1 là quyết định cơ chế
        // chứ không chỉ là tường thuật.
        List<ProcessQuestEventUseCase.QuestStatusChange> afterTrial =
                newProcessUseCase().monsterKilled(characterId, "trial_construct");
        assertThat(afterTrial).hasSize(1);
        assertThat(afterTrial.get(0).questCode()).isEqualTo(AWAKENING_TRIAL_CODE);
        assertThat(afterTrial.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int itemsBefore = countInventoryRows(characterId);

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, AWAKENING_TRIAL_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(1800L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 1800L);
        // Quest DUY NHẤT của arc 1 không thưởng vật phẩm: phần thưởng là chính cái mốc Cấp 10 mà
        // nó đưa người chơi tới (V33 §4). Túi đồ phải KHÔNG đổi.
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsBefore);
    }

    /** V33 phải tra `clear_old_archive` theo `code`, không hardcode id (khuôn V28/V30). */
    @Test
    void shouldSeedAwakeningTrial_withPrerequisitePointingAtTheOldArchive() {
        assertThat(prerequisiteCodeOf(AWAKENING_TRIAL_CODE)).isEqualTo(OLD_ARCHIVE_CODE);
    }

    @Test
    void shouldRejectAwakeningTrial_whenOldArchiveNotClaimedYet() {
        long characterId = createFreshCharacter("Awakening2");
        claimNightWatch(characterId); // xong quest 1+2, nhưng chưa đụng quest 3

        assertThat(newAcceptUseCase().execute(characterId, AWAKENING_TRIAL_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /**
     * LỜI HỨA CỦA ARC 1, kiểm bằng đường chơi TỐI THIỂU.
     *
     * <p>Chu Hoành hứa kỳ khảo hạch sẽ thức tỉnh linh căn; cơ chế thực hiện lời hứa đó là
     * {@code SelectFactionUseCase.MIN_LEVEL = 10} và {@code FactionController.MIN_LEVEL = 10} (cửa
     * sổ chọn hệ ở client chỉ mở khi đủ cấp). Test này đi ĐÚNG con đường ít EXP nhất mà một người
     * chơi có thể đi qua arc 1 — chỉ EXP thưởng quest + đúng số quái mà chính các quest bắt hạ,
     * KHÔNG cày thêm con nào — rồi hỏi: lĩnh thưởng xong, họ có Cấp 10 không?
     *
     * <p>Ba thứ đều là THẬT chứ không phải bản sao chép trong test: EXP thưởng đến từ script sản
     * phẩm (qua {@link ClaimQuestRewardUseCase}), EXP quái đọc từ {@code monster_template} của
     * migration, và cấp độ suy ra bằng chính {@link ExperienceCurve} mà server dùng. Đổi bất kỳ cái
     * nào trong ba thứ đó — hạ EXP một loài quái, sửa {@code give_exp}, đổi đường cong — đều làm ô
     * này đỏ, và đó chính là việc nó sinh ra để làm.
     *
     * <p>⚠️ Đường đi ở đây mô phỏng ĐÚNG thứ tự production: mỗi cú hạ quái vừa đẩy tiến độ quest
     * ({@code ProcessQuestEventUseCase}) vừa cộng EXP ({@code GrantExperienceUseCase}) — hai
     * việc mà {@code ConnectedCharacterRegistry.onMonsterKilled} bắn song song; còn sau mỗi lần
     * lĩnh thưởng thì gateway gọi {@code grantExperience(id, 0)} để hoà giải cột {@code level}
     * (EXP thưởng quest đi thẳng vào {@code character.exp}, không qua use case — xem javadoc
     * {@code GrantExperienceUseCase.reconcileStoredLevel}).
     */
    @Test
    void shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain() {
        long characterId = createFreshCharacter("ArcOneMinimal");

        playQuest(characterId, QUEST_CODE, "training_dummy", 3);
        playQuest(characterId, NIGHT_WATCH_CODE, "wandering_bandit", 1);
        playQuest(characterId, OLD_ARCHIVE_CODE, "archive_warden", 2);
        playQuest(characterId, AWAKENING_TRIAL_CODE, "trial_construct", 1);

        long expected = 500L
                + 3 * expRewardOf("training_dummy")
                + 800L
                + expRewardOf("wandering_bandit")
                + 1200L
                + 2 * expRewardOf("archive_warden")
                + 1800L
                + expRewardOf("trial_construct");
        assertThat(readExp(characterId)).isEqualTo(expected);

        // Cấp 10 cần 4.500 EXP tích luỹ, Cấp 11 cần 5.500 (ExperienceCurve tuyến tính 100 × cấp).
        // Đường tối thiểu về đích ở 4.585 — qua mốc, chưa tới mốc sau.
        assertThat(ExperienceCurve.progressOf(readExp(characterId)).level()).isEqualTo(10);
        assertThat(readLevel(characterId)).isEqualTo(10); // cột level THẬT, thứ SelectFactionUseCase đọc
    }

    // ---------------------------------------------------------------------------------------
    // Quest THỨ NĂM — `report_to_outer_sect` (seed V37, STORY.md §6 arc 2 nhịp MỞ).
    //
    // Quest ĐẦU TIÊN của dự án không đếm quái. Nó chạy trên loại sự kiện thứ hai (`npc_talked`),
    // nên khối test này gác đúng thứ mà bốn quest trước chưa bao giờ phải gác: hai loại sự kiện
    // không được lẫn vào nhau, và một sự kiện LẶP LẠI không được làm tiến độ trôi.
    // ---------------------------------------------------------------------------------------

    /** Đưa nhân vật qua trọn arc 1 (quest 1→4) — điều kiện ADR-044 để mở quest 5. */
    private static void claimAwakeningTrial(long characterId) {
        claimOldArchive(characterId);
        newAcceptUseCase().execute(characterId, AWAKENING_TRIAL_CODE);
        newProcessUseCase().monsterKilled(characterId, "trial_construct");
        newClaimUseCase().execute(characterId, AWAKENING_TRIAL_CODE, newIdempotencyKey());
    }

    @Test
    void shouldPlayReportToOuterSectLifecycle_withRealScript() {
        long characterId = createFreshCharacter("OuterSect1");
        claimAwakeningTrial(characterId); // ADR-044 — arc 2 chỉ mở khi arc 1 đã ĐÓNG

        assertThat(newAcceptUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Giết cả thế giới cũng không xong quest này — script lọc theo event.type TRƯỚC.
        assertThat(newProcessUseCase().monsterKilled(characterId, "trial_construct"))
                .isEmpty();
        // Nói chuyện với ĐÚNG người mở quest cũng không xong — nó hỏi Tô Vân Nhi, không hỏi ông lão.
        assertThat(newProcessUseCase().npcTalked(characterId, "town_elder")).isEmpty();

        List<ProcessQuestEventUseCase.QuestStatusChange> afterTalk =
                newProcessUseCase().npcTalked(characterId, "senior_sister");
        assertThat(afterTalk).hasSize(1);
        assertThat(afterTalk.get(0).questCode()).isEqualTo(REPORT_TO_OUTER_SECT_CODE);
        assertThat(afterTalk.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int itemsBefore = countInventoryRows(characterId);

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(200L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 200L);
        // 2 viên Tiểu Hồi Nguyên Đan — món quà sư tỷ cho đàn em, và cũng là lần đầu game TỰ đưa
        // đan dược vào tay người chơi mà không bắt mua (V36 bán 80 vàng/viên).
        // ⚠️ Đếm SỐ LƯỢNG chứ không chỉ đếm dòng: `minor_healing_pill` có `max_stack = 20` nên hai
        // viên nằm gọn trong MỘT dòng — đúng cái bẫy mà `countItemQuantity` được viết ra để tránh.
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsBefore + 1);
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(2);
    }

    /**
     * Sự kiện {@code npc_talked} LẶP LẠI mỗi lần người chơi mở lời — mốc đặt ở lúc MỞ LỜI, không ở
     * một lựa chọn thoại (xem {@code QuestFacade.onNpcTalked}). Tiến độ vì thế là một CỜ; nếu ai đó
     * sửa script thành bộ đếm thì ô này bắt được ngay, vì lần nói chuyện thứ hai sẽ ghi ra một
     * {@code progress_json} khác.
     */
    @Test
    void shouldStayIdempotent_whenTalkingToTheSameNpcTwice() {
        long characterId = createFreshCharacter("OuterSect2");
        claimAwakeningTrial(characterId);
        newAcceptUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE);

        newProcessUseCase().npcTalked(characterId, "senior_sister");
        String progressAfterFirst = readProgressJson(characterId, REPORT_TO_OUTER_SECT_CODE);
        newProcessUseCase().npcTalked(characterId, "senior_sister");

        assertThat(readProgressJson(characterId, REPORT_TO_OUTER_SECT_CODE)).isEqualTo(progressAfterFirst);
    }

    /** V37 phải tra `awakening_trial` theo `code`, không hardcode id (khuôn V28/V30/V33). */
    @Test
    void shouldSeedReportToOuterSect_withPrerequisitePointingAtTheAwakeningTrial() {
        assertThat(prerequisiteCodeOf(REPORT_TO_OUTER_SECT_CODE)).isEqualTo(AWAKENING_TRIAL_CODE);
    }

    @Test
    void shouldRejectReportToOuterSect_whenAwakeningTrialNotClaimedYet() {
        long characterId = createFreshCharacter("OuterSect3");
        claimOldArchive(characterId); // xong quest 1+2+3, nhưng chưa đụng khảo hạch

        assertThat(newAcceptUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    private static String readProgressJson(long characterId, String questCode) {
        String sql = """
                SELECT p.progress_json
                  FROM character_quest_progress p
                  JOIN quest_template q ON q.id = p.quest_template_id
                 WHERE p.character_id = ? AND q.code = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, questCode);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next())
                        .as("không có dòng tiến độ cho quest %s", questCode)
                        .isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("đọc progress_json thất bại", e);
        }
    }

    // =============================================================================================
    // Mục tiêu loại THỨ BA — "mang vật X tới NPC Y" (2026-09-11). Quest mẫu nằm ở test resources
    // (it_fixture_delivery.js), dòng quest_template do chính test INSERT: nội dung arc 2 thật đang
    // chờ chủ dự án, và cơ chế thì không nên phải chờ theo.
    // =============================================================================================

    // ---------------------------------------------------------------------------------------
    private static final String DELIVERY_CODE = "it_deliver_to_senior_sister";
    private static final String DELIVERY_SCRIPT_REF = "/scripts/quest/it_fixture_delivery.js";
    private static final String ITEM_TAKEN_AUDIT_ACTION = "QUEST_ITEM_TAKEN";
    private static final int CONCURRENT_TALKS = 4;
    private static final long CONCURRENT_TALK_TIMEOUT_SECONDS = 30L;

    /** Nhận quest giao đồ mẫu cho một nhân vật mới tinh (quest mẫu không có tiên quyết). */
    private static long acceptDeliveryQuest(String characterName) {
        execute("""
                INSERT INTO quest_template (code, type, script_ref)
                VALUES (?, 'MAIN', ?) ON CONFLICT (code) DO NOTHING
                """, DELIVERY_CODE, DELIVERY_SCRIPT_REF);
        long characterId = createFreshCharacter(characterName);
        assertThat(newAcceptUseCase().execute(characterId, DELIVERY_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        return characterId;
    }

    @Test
    void shouldHandOverEverything_andCompleteTheDelivery_inOneTransaction() {
        long characterId = acceptDeliveryQuest("Deliver1");
        insertInventoryRow(characterId, "minor_healing_pill", 3, 0);
        insertInventoryRow(characterId, "kunai", 1, 0);

        List<ProcessQuestEventUseCase.QuestStatusChange> afterTalk =
                newProcessUseCase().npcTalked(characterId, "senior_sister");

        assertThat(afterTalk)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(DELIVERY_CODE);
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(1);
        assertThat(countItemQuantity(characterId, "kunai")).isZero();
        assertThat(countInventoryRows(characterId))
                .isEqualTo(1); // dòng phi đao về 0 thì bị XOÁ, không để lại dòng rỗng
        assertThat(countAudit(characterId, ITEM_TAKEN_AUDIT_ACTION)).isEqualTo(2);

        // Mở lời lần nữa: quest đã rời IN_PROGRESS — không một viên đan nào bị lấy thêm.
        assertThat(newProcessUseCase().npcTalked(characterId, "senior_sister")).isEmpty();
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(1);
    }

    /**
     * Rollback trên DB THẬT: script lấy 2 viên đan TRƯỚC (câu {@code DELETE} đã chạy), rồi mới thiếu
     * phi đao. Hai viên đan phải quay về, cờ {@code delivered} không được ghi, không một dòng audit.
     */
    @Test
    void shouldRollBackTheFirstItem_whenTheSecondIsMissing() {
        long characterId = acceptDeliveryQuest("Deliver2");
        insertInventoryRow(characterId, "minor_healing_pill", 2, 0);

        assertThat(newProcessUseCase().npcTalked(characterId, "senior_sister")).isEmpty();

        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(2);
        assertThat(readProgressJson(characterId, DELIVERY_CODE)).isEqualTo("{\"delivered\":false}");
        assertThat(countAudit(characterId, ITEM_TAKEN_AUDIT_ACTION)).isZero();

        // Và quay lại với đủ đồ thì vẫn giao được — lần thiếu đồ không để lại dấu vết nào chặn đường.
        insertInventoryRow(characterId, "kunai", 1, 0);
        assertThat(newProcessUseCase().npcTalked(characterId, "senior_sister")).hasSize(1);
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isZero();
    }

    /** Túi cũ phân mảnh: hai viên đan ở hai dòng vẫn là hai viên đan. Dòng id nhỏ lấy trước. */
    @Test
    void shouldGatherTheDelivery_fromAFragmentedBag() {
        long characterId = acceptDeliveryQuest("Deliver3");
        long firstPill = insertInventoryRow(characterId, "minor_healing_pill", 1, 0);
        long secondPill = insertInventoryRow(characterId, "minor_healing_pill", 1, 0);
        insertInventoryRow(characterId, "minor_healing_pill", 5, 0);
        insertInventoryRow(characterId, "kunai", 1, 0);

        assertThat(newProcessUseCase().npcTalked(characterId, "senior_sister")).hasSize(1);

        assertThat(inventoryRowExists(firstPill)).isFalse();
        assertThat(inventoryRowExists(secondPill)).isFalse();
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(5);
        assertThat(countInventoryRows(characterId)).isEqualTo(1);
    }

    /**
     * Cùng mã vật phẩm KHÔNG có nghĩa là lấy được: thanh kiếm đang cầm trên tay và thanh đã cường
     * hoá không bao giờ bị một quest giao đồ ăn mất. Gọi thẳng adapter — quest mẫu không giao kiếm.
     */
    @Test
    void shouldNeverTakeAnEquippedOrEnhancedItem() {
        long characterId = createFreshCharacter("Deliver4");
        long equipped = insertInventoryRow(characterId, "wooden_sword", 1, 0);
        execute(
                "INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, 'WEAPON', ?)",
                characterId,
                equipped);
        long enhanced = insertInventoryRow(characterId, "wooden_sword", 1, 3);

        assertThat(takeInOwnTransaction(characterId, "wooden_sword", 1)).isFalse();
        assertThat(countInventoryRows(characterId)).isEqualTo(2);

        long spare = insertInventoryRow(characterId, "wooden_sword", 1, 0);
        assertThat(takeInOwnTransaction(characterId, "wooden_sword", 1)).isTrue();
        assertThat(inventoryRowExists(spare)).isFalse();
        assertThat(inventoryRowExists(equipped)).isTrue();
        assertThat(inventoryRowExists(enhanced)).isTrue();
    }

    /**
     * Bốn lần mở lời CÙNG LÚC (bấm liên tục, client tự gửi lại) trên MỘT use case dùng chung như
     * server thật. Bất kể thứ tự xen kẽ nào, bất biến chỉ có một: giao ĐÚNG MỘT lần — 2 viên đan và
     * 1 phi đao, không hơn. Thứ đứng giữa là câu {@code UPDATE ... WHERE progress_json = ?}.
     */
    @Test
    void shouldHandOverOnlyOnce_whenTheSameTalkArrivesConcurrently()
            throws InterruptedException, ExecutionException, TimeoutException {
        long characterId = acceptDeliveryQuest("Deliver5");
        insertInventoryRow(characterId, "minor_healing_pill", 10, 0);
        insertInventoryRow(characterId, "kunai", 5, 0);
        ProcessQuestEventUseCase sharedUseCase = newProcessUseCase();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_TALKS);
        int completions = 0;
        try {
            List<Future<List<ProcessQuestEventUseCase.QuestStatusChange>>> talks =
                    new ArrayList<Future<List<ProcessQuestEventUseCase.QuestStatusChange>>>();
            for (int i = 0; i < CONCURRENT_TALKS; i++) {
                talks.add(pool.submit(() -> {
                    startGate.await();
                    return sharedUseCase.npcTalked(characterId, "senior_sister");
                }));
            }
            startGate.countDown();
            for (Future<List<ProcessQuestEventUseCase.QuestStatusChange>> talk : talks) {
                completions += talk.get(CONCURRENT_TALK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .size();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(completions).isEqualTo(1);
        assertThat(countItemQuantity(characterId, "minor_healing_pill")).isEqualTo(8);
        assertThat(countItemQuantity(characterId, "kunai")).isEqualTo(4);
        assertThat(countAudit(characterId, ITEM_TAKEN_AUDIT_ACTION)).isEqualTo(2);
    }

    /** Hợp đồng compare-and-set của adapter thật: sai kỳ vọng hoặc không còn IN_PROGRESS ⇒ không ghi. */
    @Test
    void shouldRefuseCompareAndSet_onceTheProgressHasMovedOn() {
        long characterId = acceptDeliveryQuest("Deliver6");
        long templateId = queryLong("SELECT id FROM quest_template WHERE code = ?", DELIVERY_CODE);
        QuestProgress next = new QuestProgress(characterId, templateId, QuestStatus.COMPLETED, "{\"delivered\":true}");

        assertThat(compareAndSetInOwnTransaction(next, "{\"delivered\":\"stale\"}"))
                .isFalse();
        assertThat(readProgressJson(characterId, DELIVERY_CODE)).isEqualTo("{\"delivered\":false}");

        assertThat(compareAndSetInOwnTransaction(next, "{\"delivered\":false}")).isTrue();
        // Đã COMPLETED: kỳ vọng khớp đúng chuỗi hiện tại cũng không ghi được lần hai.
        assertThat(compareAndSetInOwnTransaction(next, "{\"delivered\":true}")).isFalse();
    }

    private static boolean takeInOwnTransaction(long characterId, String itemCode, int quantity) {
        AtomicBoolean taken = new AtomicBoolean(false);
        new JdbcQuestTransactionRunner(dataSource)
                .inTransaction(t -> taken.set(
                        new PostgresQuestItemTakeAdapter().takeItemByCode(t, characterId, itemCode, quantity)));
        return taken.get();
    }

    private static boolean compareAndSetInOwnTransaction(QuestProgress next, String expectedProgressJson) {
        AtomicBoolean written = new AtomicBoolean(false);
        new JdbcQuestTransactionRunner(dataSource)
                .inTransaction(t -> written.set(
                        new PostgresQuestProgressAdapter(dataSource).saveIfUnchanged(t, next, expectedProgressJson)));
        return written.get();
    }

    private static long insertInventoryRow(long characterId, String itemCode, int quantity, int enhanceLevel) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, ?, ?, 0 FROM item_template WHERE code = ?
                RETURNING id
                """, characterId, quantity, enhanceLevel, itemCode);
    }

    private static boolean inventoryRowExists(long inventoryItemId) {
        return queryLong("SELECT COUNT(*) FROM inventory_item WHERE id = ?", inventoryItemId) > 0;
    }

    private static long countAudit(long characterId, String action) {
        return queryLong("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = ?", characterId, action);
    }

    private static void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }

    /** Một giá trị số duy nhất — {@code COUNT(*)}, {@code id}, hoặc {@code RETURNING id}. */
    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("không có dòng nào: %s", sql).isTrue();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    /** Nhận → hạ đủ số quái (mỗi cú vừa đẩy quest vừa cộng EXP) → lĩnh thưởng → hoà giải cột level. */
    private static void playQuest(long characterId, String questCode, String monsterCode, int kills) {
        GrantExperienceUseCase experience = new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource));
        assertThat(newAcceptUseCase().execute(characterId, questCode)).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        for (int i = 0; i < kills; i++) {
            newProcessUseCase().monsterKilled(characterId, monsterCode);
            experience.execute(characterId, expRewardOf(monsterCode));
        }
        assertThat(newClaimUseCase()
                        .execute(characterId, questCode, newIdempotencyKey())
                        .outcome())
                .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        experience.execute(characterId, 0L);
    }
}
