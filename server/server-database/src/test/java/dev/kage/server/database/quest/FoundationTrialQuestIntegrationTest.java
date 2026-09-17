package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

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
import dev.kage.quest.domain.QuestProgressPort;
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
 * Test bằng PostgreSQL THẬT — ràng buộc ĐÓNG của v1: đi hết CẢ HAI ARC theo đường ít EXP nhất thì
 * người chơi dừng ở ĐÚNG Cấp 36, tức Trúc Cơ tầng 9.
 *
 * <p><b>Vì sao là lớp RIÊNG.</b> Khối này sinh ra trong {@link QuestIntegrationTest} và đẩy lớp đó
 * vượt trần {@code CyclomaticComplexity} của PMD (tính cho CẢ LỚP: 87). Chính javadoc của lớp kia
 * đã ghi luật "quest thứ bảy trở đi tách theo vùng nghiệp vụ" —
 * {@link OuterSectQuestIntegrationTest} là tiền lệ thứ nhất, lớp này là thứ hai.
 *
 * <p>⚠️ Bài học kèm theo: bản đầu của khối này có một helper NĂM tham số, đúng bằng
 * {@code ExcessiveParameterList} minimum=5 của ruleset. Ở đây bốn thứ mô tả một bước quest gói vào
 * {@link TalkQuest}, nên lời gọi đọc ra thành câu thay vì một hàng bốn chuỗi không tên.
 */
@Testcontainers
class FoundationTrialQuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    private static final String TOWN_ELDER = "town_elder";
    private static final String SENIOR_SISTER = "senior_sister";
    private static final String TRAINING_DUMMY = "training_dummy";
    private static final String WANDERING_BANDIT = "wandering_bandit";
    private static final String VEIN_RAIDER = "vein_raider";
    private static final String BLOOD_SAND_ACOLYTE = "blood_sand_acolyte";
    private static final String BLOOD_SAND_RINGLEADER = "blood_sand_ringleader";
    private static final String WASTELAND_BEAST = "wasteland_beast";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác — ghi một biến 64-bit không
    // volatile thì không bảo đảm nguyên tử (SpotBugs AT).
    static volatile long accountId;

    /** Một bước quest "giết N con rồi mở lời với ai đó" — gói lại để lời gọi không thành hàng chuỗi. */
    private record TalkQuest(String questCode, String monsterCode, int kills, String npcCode) {}

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "foundationtester"));
        accountId = accountHolder[0].id();
    }

    /**
     * Nhịp ĐÓNG arc 2 — ràng buộc cuối cùng của v1: lĩnh thưởng quest thứ HAI MƯƠI xong thì người
     * chơi phải đứng ở ĐÚNG <b>Cấp 36</b>.
     *
     * <p><b>Vì sao đúng 36.</b> {@code CultivationRealm.of(level)} chia năm cảnh giới đầu theo 18
     * cấp một cảnh giới: {@code realmIndex = (level − 1) / 18}. Ở Cấp 36 ⇒ {@code realmIndex = 1}
     * (<b>Trúc Cơ</b>), {@code levelInRealm = 18} ⇒ <b>tầng 9</b>, tầng CUỐI. Cấp 37 đã sang Tử Phủ,
     * tức arc 3. Mốc "Trúc Cơ thành công" của STORY §6 vì thế là có thật về CƠ CHẾ, và v1 kết thúc ở
     * ranh giới cảnh giới chứ không phải ở chỗ hết việc.
     *
     * <p>Đi ĐÚNG đường ít EXP nhất qua cả hai arc: chỉ EXP thưởng quest + đúng số quái mà chính các
     * quest bắt hạ, KHÔNG cày thêm con nào.
     *
     * <p>⚠️ <b>Biên tới Cấp 37 chỉ còn ~1.665 EXP</b> (mốc 36 là 63.000, mốc 37 là 66.600, đường tối
     * thiểu về đích ở ~64.935). Người chơi cày thêm vài con là vượt sang Tử Phủ trước khi arc 3 tồn
     * tại. Đó là thứ CẦN BIẾT khi viết arc 3, không phải lỗi của ô này — ô này khoá đường TỐI THIỂU.
     *
     * <p>⚠️ Ô này khoá SỐ HỌC, không khoá trải nghiệm: playtest sống 2026-09-17 cho thấy cùng bộ số
     * này vẫn thua trận vì hai nhát mở màn và một lần né. Xanh ở đây KHÔNG có nghĩa là đi được.
     */
    @Test
    void shouldLandExactlyAtLevelThirtySix_afterTheWholeArcTwoChain() {
        long characterId = createFreshCharacter("ArcTwoMinimal");

        playWholeArcOne(characterId);
        playArcTwoLower(characterId);
        playArcTwoUpper(characterId);

        // 63.000 = 100 × (1+…+35) là mốc Cấp 36; 66.600 là mốc Cấp 37.
        assertThat(ExperienceCurve.progressOf(readExp(characterId)).level()).isEqualTo(36);
        assertThat(readLevel(characterId)).isEqualTo(36);
    }

    /**
     * Arc 1 + nhịp mở arc 2 — MƯỜI HAI quest, theo ĐÚNG chuỗi tiên quyết trong DB.
     *
     * <p>⚠️ Thứ tự ở đây KHÔNG phải thứ tự người ta đoán từ tên quest. V45 trỏ lại tiên quyết của
     * {@code report_to_outer_sect} từ {@code awakening_trial} sang {@code break_the_raider_band},
     * nên arc 1 phải đi HẾT tới Cấp 18 rồi mới được sang arc 2. Bản đầu của hàm này xếp quest 5–9
     * lên trước và ô khoá Cấp 36 đỏ với {@code PREREQUISITE_NOT_MET} — đọc chuỗi từ DB, đừng xếp
     * theo trí nhớ.
     */
    private static void playWholeArcOne(long characterId) {
        playQuest(characterId, "kill_training_dummies", TRAINING_DUMMY, 3);
        playQuest(characterId, "night_watch_bandit", WANDERING_BANDIT, 3);
        playQuest(characterId, "clear_old_archive", "archive_warden", 2);
        playQuest(characterId, "awakening_trial", "trial_construct", 1);

        // Lv10 -> Lv18: ba quest của V45, và `break_the_raider_band` mới là thứ MỞ arc 2.
        playQuest(characterId, "spirit_vein_raiders", VEIN_RAIDER, 3);
        playQuestWithTalk(characterId, new TalkQuest("first_cultivation_manual", VEIN_RAIDER, 2, TOWN_ELDER));
        playQuestWithTalk(characterId, new TalkQuest("break_the_raider_band", "raider_chieftain", 1, TOWN_ELDER));

        // Arc 2 nhịp mở — không đếm quái, chỉ mở lời với Tô Vân Nhi.
        playQuestWithTalk(characterId, new TalkQuest("report_to_outer_sect", TRAINING_DUMMY, 0, SENIOR_SISTER));

        // Giao đồ: thẻ bài phải có SẴN trong túi thì `take_item` mới lấy được.
        insertInventoryRow(characterId, "outer_sect_tally", 3);
        playQuestWithTalk(characterId, new TalkQuest("recover_outer_sect_tallies", TRAINING_DUMMY, 0, SENIOR_SISTER));

        playMissingDiscipleTrail(characterId);

        // Hạ thám tử rồi đưa lệnh phù tận tay Tô Vân Nhi.
        insertInventoryRow(characterId, "blood_sand_talisman", 1);
        playQuestWithTalk(characterId, new TalkQuest("abandoned_cave", "blood_sand_scout", 1, SENIOR_SISTER));

        playQuestWithTalk(characterId, new TalkQuest("confront_the_steward", "blood_sand_enforcer", 1, SENIOR_SISTER));
    }

    /**
     * Quest ĐIỀU TRA — không đi qua {@link #playQuestWithTalk} được vì nó hỏi Chu Hoành TRƯỚC rồi
     * mới dọn hai tán tu, và ⚠️ bước 3 đòi TÔ VÂN NHI chứ không phải Chu Hoành: quest nhận từ ông
     * nhưng báo cho cô. Thứ tự sống trong script, không trong Java.
     */
    private static void playMissingDiscipleTrail(long characterId) {
        GrantExperienceUseCase experience = new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource));
        assertThat(newAcceptUseCase().execute(characterId, "missing_disciple_trail"))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        newProcessUseCase().npcTalked(characterId, TOWN_ELDER);
        for (int kill = 0; kill < 2; kill++) {
            newProcessUseCase().monsterKilled(characterId, WANDERING_BANDIT);
            experience.execute(characterId, expRewardOf(WANDERING_BANDIT));
        }
        newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);
        assertThat(newClaimUseCase()
                        .execute(characterId, "missing_disciple_trail", newIdempotencyKey())
                        .outcome())
                .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        experience.execute(characterId, 0L);
    }

    /** Quãng Lv22–27 (V47) — ba quest của vùng mạch chết. */
    private static void playArcTwoLower(long characterId) {
        playQuest(characterId, "dead_vein_intruders", BLOOD_SAND_ACOLYTE, 3);
        playQuestWithTalk(characterId, new TalkQuest("blood_sand_trail", BLOOD_SAND_ACOLYTE, 2, SENIOR_SISTER));
        playQuestWithTalk(
                characterId, new TalkQuest("scatter_the_blood_sand_band", BLOOD_SAND_RINGLEADER, 1, TOWN_ELDER));
    }

    /** Quãng Lv28–36 (V48) — năm quest, nhịp đóng arc 2 và nội dung cuối của v1. */
    private static void playArcTwoUpper(long characterId) {
        playQuestWithTalk(characterId, new TalkQuest("wasteland_edge_patrol", BLOOD_SAND_RINGLEADER, 1, TOWN_ELDER));
        playQuestWithTalk(characterId, new TalkQuest("rank_board_dispute", WASTELAND_BEAST, 2, SENIOR_SISTER));

        // Đan dược phải có sẵn trong túi — `foundation_pill_run` lấy 3 viên lúc giao.
        insertInventoryRow(characterId, "minor_healing_pill", 3);
        playQuestWithTalk(characterId, new TalkQuest("foundation_pill_run", WASTELAND_BEAST, 2, TOWN_ELDER));

        playQuestWithTalk(characterId, new TalkQuest("outer_sect_proving", WASTELAND_BEAST, 3, TOWN_ELDER));
        playQuestWithTalk(characterId, new TalkQuest("foundation_breakthrough", "foundation_warden", 1, TOWN_ELDER));
    }

    /** Nhận → hạ đủ số quái (mỗi cú vừa đẩy quest vừa cộng EXP) → lĩnh thưởng → hoà giải cột level. */
    private static void playQuest(long characterId, String questCode, String monsterCode, int kills) {
        GrantExperienceUseCase experience = new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource));
        assertThat(newAcceptUseCase().execute(characterId, questCode)).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        for (int kill = 0; kill < kills; kill++) {
            newProcessUseCase().monsterKilled(characterId, monsterCode);
            experience.execute(characterId, expRewardOf(monsterCode));
        }
        assertThat(newClaimUseCase()
                        .execute(characterId, questCode, newIdempotencyKey())
                        .outcome())
                .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        experience.execute(characterId, 0L);
    }

    /**
     * Quest hỗn hợp — {@link #playQuest} chỉ biết giết quái, nên bước {@code npc_talked} phải lái
     * tay. Vẫn cộng EXP quái và vẫn hoà giải cột {@code level} y hệt {@code playQuest}, nếu không
     * phép cộng của ô khoá sẽ đếm thiếu.
     *
     * <p>⚠️ ASSERT chứ không nuốt: bản đầu bỏ qua kết quả nhận quest, nên bốn quest bị từ chối
     * ({@code PREREQUISITE_NOT_MET}) trôi qua im lặng và lỗi chỉ nổ ở {@code playQuest} mãi sau.
     */
    private static void playQuestWithTalk(long characterId, TalkQuest step) {
        GrantExperienceUseCase experience = new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource));
        assertThat(newAcceptUseCase().execute(characterId, step.questCode()))
                .isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        for (int kill = 0; kill < step.kills(); kill++) {
            newProcessUseCase().monsterKilled(characterId, step.monsterCode());
            experience.execute(characterId, expRewardOf(step.monsterCode()));
        }
        newProcessUseCase().npcTalked(characterId, step.npcCode());
        assertThat(newClaimUseCase()
                        .execute(characterId, step.questCode(), newIdempotencyKey())
                        .outcome())
                .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        experience.execute(characterId, 0L);
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

    private static void insertInventoryRow(long characterId, String itemCode, int quantity) {
        execute("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
                """, characterId, quantity, itemCode);
    }

    private static long readExp(long characterId) {
        return queryLong("SELECT exp FROM character WHERE id = ?", characterId);
    }

    private static int readLevel(long characterId) {
        return (int) queryLong("SELECT level FROM character WHERE id = ?", characterId);
    }

    /** {@code monster_template.exp_reward} đọc THẲNG từ seed — không chép số vào test. */
    private static long expRewardOf(String monsterCode) {
        return queryLong("SELECT exp_reward FROM monster_template WHERE code = ?", monsterCode);
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

    private static void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
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

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
