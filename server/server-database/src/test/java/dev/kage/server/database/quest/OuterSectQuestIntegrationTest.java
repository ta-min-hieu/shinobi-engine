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
import dev.kage.quest.application.ProcessQuestEventUseCase;
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
 * Test bằng PostgreSQL THẬT — các quest NGOẠI MÔN của arc 2 (STORY.md §6): quest THỨ SÁU
 * {@code recover_outer_sect_tallies} (V38, nhịp VIỆC VẶT — mục tiêu "mang vật X tới NPC Y",
 * GAMEPLAY.md §9.5) và quest THỨ BẢY {@code missing_disciple_trail} (V39, nhịp ĐIỀU TRA — quest
 * NHIỀU BƯỚC CÓ THỨ TỰ đầu tiên, GAMEPLAY.md §9.6).
 *
 * <p><b>Vì sao là lớp RIÊNG</b>, trong khi {@link QuestIntegrationTest} cố ý gom năm quest trước
 * vào một chỗ để khỏi dựng thêm một Postgres: thêm khối này vào lớp kia làm nó chạm TRẦN độ phức
 * tạp của PMD ({@code CyclomaticComplexity} tính cho cả lớp). Tức là lý do "gộp cho rẻ" đã hết hiệu
 * lực, và cách sửa đúng là tách theo VÙNG NGHIỆP VỤ — đúng như mọi lớp test tích hợp khác của
 * module này (guild, friend, inventory, dialogue: mỗi vùng một lớp, mỗi lớp một container).
 *
 * <p>Lớp kia gác CƠ CHẾ giao đồ bằng một script fixture; lớp này gác NỘI DUNG THẬT — đúng vật
 * phẩm, đúng số lượng, đúng người nhận, đúng phần thưởng, và đúng thứ tự quest (ADR-044).
 */
@Testcontainers
class OuterSectQuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String TALLY_QUEST_CODE = "recover_outer_sect_tallies";
    private static final String TALLY_ITEM_CODE = "outer_sect_tally";
    private static final String REPORT_TO_OUTER_SECT_CODE = "report_to_outer_sect";
    private static final String AWAKENING_TRIAL_CODE = "awakening_trial";
    private static final String SENIOR_SISTER = "senior_sister";
    private static final long TALLY_QUEST_EXP = 1000L;
    private static final String TRAIL_QUEST_CODE = "missing_disciple_trail";
    private static final String TOWN_ELDER = "town_elder";
    private static final String BANDIT = "wandering_bandit";
    private static final long TRAIL_QUEST_EXP = 1300L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác — ghi một biến 64-bit không
    // volatile thì không bảo đảm nguyên tử (SpotBugs AT).
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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "tallytester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void shouldPlayRecoverTalliesLifecycle_withRealScript() {
        long characterId = createFreshCharacter("Tally1");
        claimReportToOuterSect(characterId);

        assertThat(newAcceptUseCase().execute(characterId, TALLY_QUEST_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Tay không tới gặp sư tỷ: quest KHÔNG xong, và cũng không mất gì. Bốn quest "giết N con"
        // không bao giờ phải gác điều này — mục tiêu loại này có thể THẤT BẠI giữa chừng.
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();

        insertTallies(characterId, 2);
        long expBefore = readExp(characterId);

        List<ProcessQuestEventUseCase.QuestStatusChange> afterDelivery =
                newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);

        assertThat(afterDelivery).hasSize(1);
        assertThat(afterDelivery.get(0).questCode()).isEqualTo(TALLY_QUEST_CODE);
        assertThat(afterDelivery.get(0).status()).isEqualTo(QuestStatus.COMPLETED);
        // Giao rồi thì thẻ bài RỜI KHỎI túi — nếu không, người chơi giữ luôn vật chứng đã nộp.
        assertThat(countTallies(characterId)).isZero();

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, TALLY_QUEST_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(TALLY_QUEST_EXP);
        assertThat(readExp(characterId)).isEqualTo(expBefore + TALLY_QUEST_EXP);
    }

    /**
     * Mang THIẾU một thẻ bài: lần mở lời đó không được để lại dấu vết nào — không trừ cái đang có,
     * không ghi cờ {@code delivered}. Cả hai vế đúng vì chúng nằm trong CÙNG một transaction.
     */
    @Test
    void shouldLeaveNoTrace_whenPlayerCarriesOnlyOneTally() {
        long characterId = createFreshCharacter("Tally2");
        claimReportToOuterSect(characterId);
        newAcceptUseCase().execute(characterId, TALLY_QUEST_CODE);
        insertTallies(characterId, 1);

        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();

        assertThat(countTallies(characterId)).isEqualTo(1);
        assertThat(readProgressJson(characterId)).isEqualTo("{\"delivered\":false}");
    }

    /** Mở lời lần nữa sau khi đã giao: KHÔNG đòi thêm hai thẻ bài lần thứ hai. */
    @Test
    void shouldNotTakeTalliesAgain_whenTalkingAfterDelivery() {
        long characterId = createFreshCharacter("Tally3");
        claimReportToOuterSect(characterId);
        newAcceptUseCase().execute(characterId, TALLY_QUEST_CODE);
        insertTallies(characterId, 2);
        newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);

        insertTallies(characterId, 2);
        newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);

        assertThat(countTallies(characterId)).isEqualTo(2);
    }

    /** V38 phải tra quest tiên quyết theo {@code code}, không hardcode id (khuôn V28/V30/V33/V37). */
    @Test
    void shouldSeedRecoverTallies_withPrerequisitePointingAtReportToOuterSect() {
        assertThat(prerequisiteCodeOf(TALLY_QUEST_CODE)).isEqualTo(REPORT_TO_OUTER_SECT_CODE);
    }

    @Test
    void shouldRejectRecoverTallies_whenReportToOuterSectNotClaimedYet() {
        long characterId = createFreshCharacter("Tally4");
        advanceThroughArcOne(characterId); // xong arc 1, nhưng chưa báo danh ngoại môn

        assertThat(newAcceptUseCase().execute(characterId, TALLY_QUEST_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /**
     * Thẻ bài phải rơi từ tán tu với tỉ lệ 100%. Hạ tỉ lệ xuống thì số trận người chơi phải đánh
     * thành một biến ngẫu nhiên, và phép cân bằng 1.000 EXP (tính theo quãng đường + số nhát chịu
     * đòn, khuôn V30 §2) hết nghĩa — xem V38 mục CÂN BẰNG.
     */
    @Test
    void shouldSeedTallyDrop_atHundredPercentFromWanderingBandit() {
        long chance = queryLong("""
                SELECT e.drop_chance_percent
                  FROM drop_table_entry e
                  JOIN drop_table dt ON dt.id = e.drop_table_id
                  JOIN item_template it ON it.id = e.item_template_id
                 WHERE dt.code = 'wandering_bandit' AND it.code = ?
                """, TALLY_ITEM_CODE);

        assertThat(chance).isEqualTo(100L);
    }

    // ------------------------------------------------------------------------------------------
    // Quest THỨ BẢY — `missing_disciple_trail` (V39): ba bước, và THỨ TỰ là thứ phải gác.
    // ------------------------------------------------------------------------------------------

    @Test
    void shouldPlayMissingDiscipleLifecycle_withRealScript() {
        long characterId = createFreshCharacter("Trail1");
        claimTallyQuest(characterId);

        assertThat(newAcceptUseCase().execute(characterId, TRAIL_QUEST_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Về báo trước khi đi hỏi: không xong — bước 3 đứng sau hai bước kia.
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();

        newProcessUseCase().npcTalked(characterId, TOWN_ELDER);
        newProcessUseCase().monsterKilled(characterId, BANDIT);
        newProcessUseCase().monsterKilled(characterId, BANDIT);
        long expBefore = readExp(characterId);

        List<ProcessQuestEventUseCase.QuestStatusChange> afterReport =
                newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);

        assertThat(afterReport).hasSize(1);
        assertThat(afterReport.get(0).questCode()).isEqualTo(TRAIL_QUEST_CODE);
        assertThat(afterReport.get(0).status()).isEqualTo(QuestStatus.COMPLETED);

        ClaimQuestRewardUseCase.ClaimResult claim =
                newClaimUseCase().execute(characterId, TRAIL_QUEST_CODE, newIdempotencyKey());

        assertThat(claim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claim.expGranted()).isEqualTo(TRAIL_QUEST_EXP);
        assertThat(readExp(characterId)).isEqualTo(expBefore + TRAIL_QUEST_EXP);
    }

    /**
     * Tán tu hạ TRƯỚC khi hỏi Chu Hoành KHÔNG được tính lùi. Thiếu điều kiện cộng dồn này thì ba
     * bước thành ba việc rời rạc, và người chơi "điều tra" xong trước khi biết mình điều tra gì.
     */
    @Test
    void shouldNotCountBandits_killedBeforeAskingTheElder() {
        long characterId = createFreshCharacter("Trail2");
        claimTallyQuest(characterId);
        newAcceptUseCase().execute(characterId, TRAIL_QUEST_CODE);

        newProcessUseCase().monsterKilled(characterId, BANDIT);
        newProcessUseCase().monsterKilled(characterId, BANDIT);
        newProcessUseCase().npcTalked(characterId, TOWN_ELDER);

        // Hỏi xong mới giết: vẫn phải giết đủ hai con nữa.
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();
        newProcessUseCase().monsterKilled(characterId, BANDIT);
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();
        newProcessUseCase().monsterKilled(characterId, BANDIT);

        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER))
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRAIL_QUEST_CODE);
    }

    /** V39 tra quest tiên quyết theo {@code code}, không hardcode id. */
    @Test
    void shouldSeedMissingDisciple_withPrerequisitePointingAtTheTallyQuest() {
        assertThat(prerequisiteCodeOf(TRAIL_QUEST_CODE)).isEqualTo(TALLY_QUEST_CODE);
    }

    @Test
    void shouldRejectMissingDisciple_whenTallyQuestNotClaimedYet() {
        long characterId = createFreshCharacter("Trail3");
        claimReportToOuterSect(characterId); // xong quest 5, nhưng chưa đòi thẻ bài về

        assertThat(newAcceptUseCase().execute(characterId, TRAIL_QUEST_CODE))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.PREREQUISITE_NOT_MET);
    }

    /** Quest 5 + quest 6 — điều kiện ADR-044 để mở quest 7. */
    private static void claimTallyQuest(long characterId) {
        claimReportToOuterSect(characterId);
        newAcceptUseCase().execute(characterId, TALLY_QUEST_CODE);
        insertTallies(characterId, 2);
        newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);
        newClaimUseCase().execute(characterId, TALLY_QUEST_CODE, newIdempotencyKey());
    }

    // ------------------------------------------------------------------------------------------
    // Hạ tầng test
    // ------------------------------------------------------------------------------------------

    /** Trọn arc 1 (quest 1→4) bằng chính script sản phẩm — ADR-044 khoá quest sau theo quest trước. */
    private static void advanceThroughArcOne(long characterId) {
        playQuest(characterId, "kill_training_dummies", "training_dummy", 3);
        playQuest(characterId, "night_watch_bandit", "wandering_bandit", 3);
        playQuest(characterId, "clear_old_archive", "archive_warden", 2);
        playQuest(characterId, AWAKENING_TRIAL_CODE, "trial_construct", 1);
    }

    /** Arc 1 + quest 5 — điều kiện ADR-044 để mở quest 6. */
    private static void claimReportToOuterSect(long characterId) {
        advanceThroughArcOne(characterId);
        newAcceptUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE);
        newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);
        newClaimUseCase().execute(characterId, REPORT_TO_OUTER_SECT_CODE, newIdempotencyKey());
    }

    private static void playQuest(long characterId, String questCode, String monsterCode, int kills) {
        newAcceptUseCase().execute(characterId, questCode);
        for (int kill = 0; kill < kills; kill++) {
            newProcessUseCase().monsterKilled(characterId, monsterCode);
        }
        newClaimUseCase().execute(characterId, questCode, newIdempotencyKey());
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

    private static void insertTallies(long characterId, int quantity) {
        queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
                RETURNING id
                """, characterId, quantity, TALLY_ITEM_CODE);
    }

    private static int countTallies(long characterId) {
        return (int) queryLong("""
                SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii
                  JOIN item_template it ON it.id = ii.item_template_id
                 WHERE ii.character_id = ? AND it.code = ?
                """, characterId, TALLY_ITEM_CODE);
    }

    private static long readExp(long characterId) {
        return queryLong("SELECT exp FROM character WHERE id = ?", characterId);
    }

    private static String readProgressJson(long characterId) {
        return queryString("""
                SELECT p.progress_json FROM character_quest_progress p
                  JOIN quest_template q ON q.id = p.quest_template_id
                 WHERE p.character_id = ? AND q.code = ?
                """, characterId, TALLY_QUEST_CODE);
    }

    private static String prerequisiteCodeOf(String questCode) {
        return queryString("""
                SELECT pre.code FROM quest_template q
                  LEFT JOIN quest_template pre ON pre.id = q.prerequisite_quest_id
                 WHERE q.code = ?
                """, questCode);
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

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

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

    private static String queryString(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("không có dòng nào: %s", sql).isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int index = 0; index < params.length; index++) {
            statement.setObject(index + 1, params[index]);
        }
    }
}
