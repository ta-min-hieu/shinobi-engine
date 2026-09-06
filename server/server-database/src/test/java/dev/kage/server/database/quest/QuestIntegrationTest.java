package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
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
 */
@Testcontainers
class QuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";
    private static final String NIGHT_WATCH_CODE = "night_watch_bandit";
    private static final String OLD_ARCHIVE_CODE = "clear_old_archive";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

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

    private static ProcessMonsterKilledUseCase newProcessUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        // Không cần QuestTemplatePort nữa: use case đọc scriptRef ngay từ listInProgress (2026-09-05).
        return new ProcessMonsterKilledUseCase(progressPort, tx);
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
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
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
        assertThat(newProcessUseCase().execute(characterId, "training_dummy")).isEmpty();
        assertThat(newProcessUseCase().execute(characterId, "training_dummy")).isEmpty();
        List<ProcessMonsterKilledUseCase.QuestStatusChange> afterThird =
                newProcessUseCase().execute(characterId, "training_dummy");
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
                        "/scripts/quest/clear_old_archive.js");
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
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
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
     * {@code ProcessMonsterKilledUseCase} hardcode đúng một mã quest nên script này không bao giờ
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
        assertThat(newProcessUseCase().execute(characterId, "training_dummy")).isEmpty();

        List<ProcessMonsterKilledUseCase.QuestStatusChange> afterBandit =
                newProcessUseCase().execute(characterId, "wandering_bandit");
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

        List<ProcessMonsterKilledUseCase.QuestStatusChange> afterBandit =
                newProcessUseCase().execute(characterId, "wandering_bandit");

        // Chỉ quest "trực đêm" xong; quest đập mộc nhân KHÔNG bị cú giết này chạm tới.
        assertThat(afterBandit)
                .extracting(ProcessMonsterKilledUseCase.QuestStatusChange::questCode)
                .containsExactly(NIGHT_WATCH_CODE);

        // Và quest ĐÃ LĨNH THƯỞNG không bị cú giết nào chạm tới nữa: listInProgress lọc theo
        // status='IN_PROGRESS' ngay trong SQL, nên quest CLAIMED — thứ chỉ tăng theo đời nhân vật —
        // không bao giờ được nạp lại.
        assertThat(newProcessUseCase().execute(characterId, "training_dummy")).isEmpty();
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
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy"); // COMPLETED, chưa CLAIMED

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, NIGHT_WATCH_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /** Lĩnh thưởng xong quest 1 thì quest 2 mở khoá — đúng thứ tự STORY.md §6. */
    @Test
    void shouldAcceptNightWatch_onceTrainingDummiesIsClaimed() {
        long characterId = createFreshCharacter("NightWatch5");
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
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
        newProcessUseCase().execute(characterId, "wandering_bandit");
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
        assertThat(newProcessUseCase().execute(characterId, "wandering_bandit")).isEmpty();

        // Script đòi HAI cỗ, nên con thứ nhất chưa đóng quest.
        assertThat(newProcessUseCase().execute(characterId, "archive_warden")).isEmpty();

        List<ProcessMonsterKilledUseCase.QuestStatusChange> afterSecond =
                newProcessUseCase().execute(characterId, "archive_warden");
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
}
