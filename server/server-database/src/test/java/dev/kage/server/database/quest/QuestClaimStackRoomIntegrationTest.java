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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
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
 * Test bằng PostgreSQL THẬT — lĩnh thưởng quest khi script trao CÙNG một mã vật phẩm nhiều lần
 * (2026-10-02).
 *
 * <p><b>Bug gác ở đây.</b> {@code PostgresQuestInventoryGrantAdapter#hasRoomFor} soi từng lời
 * {@code give_item} trên trạng thái túi TRƯỚC khi trao: chồng phi đao 49/99 trả lời "gộp được" cho
 * CẢ HAI lần 50 cái, nên phép đếm ra 0 ô mới. Lúc trao thật, lần đầu lấp chồng tới 99 và lần sau
 * buộc phải mở ô mới — túi đầy thì {@code grantItemByCode} trả {@code false}, mà use case vứt giá
 * trị đó đi: quest vẫn CLAIMED, {@code idempotency_key} vẫn bị đốt ⇒ mất 50 phi đao vĩnh viễn.
 *
 * <p>Quest mẫu nằm ở test resources ({@code it_fixture_double_stack_reward.js}), dòng
 * {@code quest_template} do chính test INSERT — không thêm nội dung thật.
 */
@Testcontainers
class QuestClaimStackRoomIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int BAG_CAPACITY = 20; // trùng DEFAULT_CAPACITY của các adapter túi đồ

    private static final String QUEST_CODE = "it_double_stack_reward";
    private static final String SCRIPT_REF = "/scripts/quest/it_fixture_double_stack_reward.js";
    private static final String KUNAI = "kunai";
    private static final String WOODEN_SWORD = "wooden_sword";
    private static final int STACK_WITH_ROOM_FOR_ONE_GRANT = 49; // 49 + 50 <= 99, 99 + 50 > 99
    private static final int TOTAL_REWARD_KUNAI = 100;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "stackroomtester"));
        accountId = accountHolder[0].id();
        execute("""
                INSERT INTO quest_template (code, type, script_ref)
                VALUES (?, 'MAIN', ?) ON CONFLICT (code) DO NOTHING
                """, QUEST_CODE, SCRIPT_REF);
    }

    /**
     * Túi đầy 20/20, chồng phi đao 49 chỉ nhận được MỘT trong hai lần 50 cái. Lần còn lại cần một ô
     * mới mà túi không có ⇒ phải là INVENTORY_FULL, không ghi gì, quest vẫn lĩnh lại được.
     */
    @Test
    void shouldRefuseTheClaim_whenTheSecondGrantOfTheSameCodeNeedsASlotTheBagDoesNotHave() {
        long characterId = completedQuestCharacter("StackRoom1");
        insertInventoryRow(characterId, KUNAI, STACK_WITH_ROOM_FOR_ONE_GRANT);
        fillWithSwords(characterId, BAG_CAPACITY - 1);
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = newClaimUseCase()
                .execute(characterId, QUEST_CODE, UUID.randomUUID().toString());

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.INVENTORY_FULL);
        assertThat(countItemQuantity(characterId, KUNAI))
                .as("không một phi đao nào được trao — kể cả lần gộp đầu")
                .isEqualTo(STACK_WITH_ROOM_FOR_ONE_GRANT);
        assertThat(countInventoryRows(characterId)).isEqualTo(BAG_CAPACITY);
        assertThat(readExp(characterId)).as("EXP cũng không được trao").isEqualTo(expBefore);
        assertThat(readStatus(characterId)).isEqualTo("COMPLETED");
    }

    /** Dọn một ô là đủ: lĩnh lại được, nhận trọn 100 phi đao ở hai dòng 99 + 50 → đúng 99 + 50. */
    @Test
    void shouldGrantBothStacks_whenTheBagHasExactlyTheOneExtraSlotNeeded() {
        long characterId = completedQuestCharacter("StackRoom2");
        insertInventoryRow(characterId, KUNAI, STACK_WITH_ROOM_FOR_ONE_GRANT);
        fillWithSwords(characterId, BAG_CAPACITY - 2);

        ClaimQuestRewardUseCase.ClaimResult result = newClaimUseCase()
                .execute(characterId, QUEST_CODE, UUID.randomUUID().toString());

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(countItemQuantity(characterId, KUNAI)).isEqualTo(STACK_WITH_ROOM_FOR_ONE_GRANT + TOTAL_REWARD_KUNAI);
        assertThat(countInventoryRows(characterId)).isEqualTo(BAG_CAPACITY);
        assertThat(readStatus(characterId)).isEqualTo("CLAIMED");
    }

    /** Nhân vật mới tinh đã nhận quest mẫu, rồi đặt thẳng status COMPLETED (quest mẫu không có mục tiêu). */
    private static long completedQuestCharacter(String characterName) {
        long characterId = createFreshCharacter(characterName);
        assertThat(newAcceptUseCase().execute(characterId, QUEST_CODE)).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        execute("""
                UPDATE character_quest_progress SET status = 'COMPLETED'
                 WHERE character_id = ?
                   AND quest_template_id = (SELECT id FROM quest_template WHERE code = ?)
                """, characterId, QUEST_CODE);
        return characterId;
    }

    private static void fillWithSwords(long characterId, int rows) {
        for (int i = 0; i < rows; i++) {
            insertInventoryRow(characterId, WOODEN_SWORD, 1);
        }
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

    private static AcceptQuestUseCase newAcceptUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new AcceptQuestUseCase(progressPort, templatePort, tx);
    }

    private static ClaimQuestRewardUseCase newClaimUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter(),
                new PostgresQuestGoldAdapter());
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
    }

    private static String readStatus(long characterId) {
        String sql = "SELECT p.status FROM character_quest_progress p"
                + " JOIN quest_template q ON q.id = p.quest_template_id"
                + " WHERE p.character_id = ? AND q.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, QUEST_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("không có dòng tiến độ").isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("đọc status thất bại", e);
        }
    }

    private static long readExp(long characterId) {
        return queryLong("SELECT exp FROM character WHERE id = ?", characterId);
    }

    /** Tổng số lượng (không phải số dòng) — phi đao gộp chồng nên đếm dòng không thấy gì. */
    private static int countItemQuantity(long characterId, String itemCode) {
        return (int) queryLong(
                "SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii"
                        + " JOIN item_template it ON it.id = ii.item_template_id"
                        + " WHERE ii.character_id = ? AND it.code = ?",
                characterId,
                itemCode);
    }

    private static int countInventoryRows(long characterId) {
        return (int) queryLong("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?", characterId);
    }

    private static void insertInventoryRow(long characterId, String itemCode, int quantity) {
        queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
                RETURNING id
                """, characterId, quantity, itemCode);
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
