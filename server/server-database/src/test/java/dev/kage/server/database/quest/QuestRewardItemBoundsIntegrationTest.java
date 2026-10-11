package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.kage.inventory.domain.Inventory;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase.ClaimResult.Outcome;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestInventoryGrantPort.ItemGrantRequest;
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
import dev.kage.server.database.inventory.PostgresInventoryAdapter;

/**
 * Test bằng PostgreSQL THẬT — {@code ctx.give_item} ở biên của miền số lượng và mã (rà 2026-10-10).
 *
 * <p><b>Bug gác ở đây</b> (đo trước khi vá, cùng ngày đã vá {@code give_exp} âm):
 *
 * <ul>
 *   <li>{@code give_item("kunai", -5)} khi túi có sẵn 3 phi đao: câu gộp chồng
 *       {@code quantity = quantity + ?} chạy với {@code -5} ⇒ dòng {@code inventory_item} mang
 *       {@code quantity = -2} (cột chưa có CHECK tới V52). {@code ItemStack} từ chối số lượng ≤ 0 ngay trong
 *       constructor, nên từ đó {@code PostgresInventoryAdapter.findByCharacter} NÉM cho nhân vật đó
 *       — túi đồ không liệt kê được nữa. Lượt lĩnh vẫn trả {@code OK} và quest thành {@code CLAIMED}.
 *   <li>Cùng lời gọi khi túi KHÔNG có phi đao, hoặc với số lượng {@code 0}: không dòng nào được ghi,
 *       nhưng lượt lĩnh vẫn {@code OK} + {@code CLAIMED} ⇒ phần thưởng mất vĩnh viễn trong im lặng.
 *   <li>{@code hasRoomFor} tính số ô bằng {@code int}: {@code Integer.MAX_VALUE} phi đao tràn thành
 *       số ô ÂM ⇒ trả lời "đủ chỗ". Lúc trao {@code insertRespectingMaxStack} (tính bằng
 *       {@code long}) mới từ chối, nên kết quả cuối vẫn là rollback — nhưng guard thì nói sai.
 * </ul>
 *
 * <p>Quest mẫu nằm ở test resources ({@code it_fixture_reward_bounds.js}), dòng
 * {@code quest_template} do chính test INSERT — không thêm nội dung thật. Test chọn nhánh của script
 * bằng cách ghi thẳng {@code progress_json}.
 */
@Testcontainers
class QuestRewardItemBoundsIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    private static final String QUEST_CODE = "it_reward_bounds";
    private static final String SCRIPT_REF = "/scripts/quest/it_fixture_reward_bounds.js";
    private static final String KUNAI = "kunai";
    private static final String WOODEN_SWORD = "wooden_sword";
    private static final int KUNAI_IN_BAG = 3;
    private static final int NEGATIVE_QUANTITY = -5;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "rewardboundstester"));
        accountId = accountHolder[0].id();
        execute("""
                INSERT INTO quest_template (code, type, script_ref)
                VALUES (?, 'MAIN', ?) ON CONFLICT (code) DO NOTHING
                """, QUEST_CODE, SCRIPT_REF);
    }

    /** Ca nặng nhất: số âm gộp vào chồng sẵn có ⇒ trước bản vá là dòng quantity −2 và túi đồ chết. */
    @Test
    void shouldRefuseANegativeGrant_andLeaveTheExistingStackUntouched() {
        long characterId = completedQuestCharacter("BoundsNegStack", "negative");
        insertInventoryRow(characterId, KUNAI, KUNAI_IN_BAG);
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

        // Giá trị cuối trong DB trước, outcome sau: thứ làm hỏng nhân vật là DÒNG, không phải câu trả lời.
        assertThat(countItemQuantity(characterId, KUNAI))
                .as("thưởng quest không bao giờ được TRỪ đồ của người chơi")
                .isEqualTo(KUNAI_IN_BAG);
        assertBagIsStillReadable(characterId, 1);
        assertThat(result.outcome()).isEqualTo(Outcome.SCRIPT_ERROR);
        assertNothingWasGranted(characterId, expBefore);
    }

    /** Không có chồng nào để trừ: trước bản vá lượt lĩnh "thành công" mà không trao gì. */
    @Test
    void shouldRefuseANegativeGrant_whenTheBagHoldsNoSuchItem() {
        long characterId = completedQuestCharacter("BoundsNegEmpty", "negative");
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

        assertThat(result.outcome()).isEqualTo(Outcome.SCRIPT_ERROR);
        assertNothingWasGranted(characterId, expBefore);
        assertBagIsStillReadable(characterId, 0);
    }

    @Test
    void shouldRefuseAZeroGrant_insteadOfClaimingTheQuestForNothing() {
        long characterId = completedQuestCharacter("BoundsZero", "zero");
        insertInventoryRow(characterId, KUNAI, KUNAI_IN_BAG);
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

        assertThat(result.outcome()).isEqualTo(Outcome.SCRIPT_ERROR);
        assertNothingWasGranted(characterId, expBefore);
        assertThat(countItemQuantity(characterId, KUNAI)).isEqualTo(KUNAI_IN_BAG);
    }

    /** Mã rỗng không thể là một dòng {@code item_template} — lỗi của script, không phải "túi đầy". */
    @Test
    void shouldRefuseAnEmptyItemCode_asAScriptError() {
        long characterId = completedQuestCharacter("BoundsEmptyCode", "empty_code");
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

        assertThat(result.outcome()).isEqualTo(Outcome.SCRIPT_ERROR);
        assertNothingWasGranted(characterId, expBefore);
    }

    /**
     * {@code Integer.MAX_VALUE} hợp lệ ở tầng script; túi 20 ô không bao giờ chứa nổi ⇒ rollback trọn.
     * Ba nhánh: gộp chồng được, cùng mã hai lần (tổng vượt {@code int}), và không xếp chồng.
     */
    @Test
    void shouldRollBackTheWholeClaim_whenTheGrantIsFarBeyondTheBag() {
        List<String> scriptCases = List.of("max_int_stackable", "max_int_stackable_twice", "max_int_unstackable");
        for (String scriptCase : scriptCases) {
            long characterId = completedQuestCharacter("BoundsMax" + scriptCases.indexOf(scriptCase), scriptCase);
            insertInventoryRow(characterId, KUNAI, KUNAI_IN_BAG);
            long expBefore = readExp(characterId);

            ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

            assertThat(result.outcome()).as("nhánh %s", scriptCase).isEqualTo(Outcome.INVENTORY_FULL);
            assertNothingWasGranted(characterId, expBefore);
            assertThat(countItemQuantity(characterId, KUNAI)).isEqualTo(KUNAI_IN_BAG);
            assertThat(countItemQuantity(characterId, WOODEN_SWORD)).isZero();
            assertBagIsStillReadable(characterId, 1);
        }
    }

    /**
     * Mã không tồn tại: hành vi HIỆN CÓ (từ 2026-10-02) là rollback + {@code INVENTORY_FULL}. Ghim lại
     * phần quan trọng — không ghi gì, lĩnh lại được — chứ không khẳng định tên outcome là đúng.
     */
    @Test
    void shouldRollBackTheWholeClaim_whenTheItemCodeDoesNotExist() {
        long characterId = completedQuestCharacter("BoundsUnknown", "unknown_code");
        long expBefore = readExp(characterId);

        ClaimQuestRewardUseCase.ClaimResult result = claim(characterId);

        assertThat(result.outcome()).isEqualTo(Outcome.INVENTORY_FULL);
        assertNothingWasGranted(characterId, expBefore);
    }

    /**
     * Lớp gác thứ hai, ngay ở adapter: dù tầng script có hở lại, một số lượng ≤ 0 không bao giờ được
     * tới câu {@code quantity = quantity + ?}.
     */
    @Test
    void adapterShouldRejectANonPositiveQuantity_beforeTouchingAnyRow() {
        long characterId = createFreshCharacter("BoundsAdapterNeg");
        insertInventoryRow(characterId, KUNAI, KUNAI_IN_BAG);
        PostgresQuestInventoryGrantAdapter adapter = new PostgresQuestInventoryGrantAdapter();
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);

        for (int quantity : new int[] {NEGATIVE_QUANTITY, 0, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> tx.inTransaction(t -> adapter.grantItemByCode(t, characterId, KUNAI, quantity)))
                    .as("grantItemByCode(%d)", quantity)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> tx.inTransaction(
                            t -> adapter.hasRoomFor(t, characterId, List.of(new ItemGrantRequest(KUNAI, quantity)))))
                    .as("hasRoomFor(%d)", quantity)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(countItemQuantity(characterId, KUNAI)).isEqualTo(KUNAI_IN_BAG);
        assertBagIsStillReadable(characterId, 1);
    }

    /** Phép đếm ô từng tràn {@code int} thành số ÂM và trả lời "đủ chỗ" cho hai tỉ món. */
    @Test
    void adapterShouldSayNoRoom_forAGrantWhoseSlotCountOverflowsInt() {
        long characterId = createFreshCharacter("BoundsAdapterMax");
        PostgresQuestInventoryGrantAdapter adapter = new PostgresQuestInventoryGrantAdapter();
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);

        for (String itemCode : List.of(KUNAI, WOODEN_SWORD)) {
            AtomicBoolean hasRoom = new AtomicBoolean(true);
            tx.inTransaction(t -> hasRoom.set(adapter.hasRoomFor(
                    t,
                    characterId,
                    List.of(
                            new ItemGrantRequest(itemCode, Integer.MAX_VALUE),
                            new ItemGrantRequest(itemCode, Integer.MAX_VALUE)))));

            assertThat(hasRoom.get())
                    .as("hasRoomFor(%s × MAX_VALUE × 2)", itemCode)
                    .isFalse();
        }
    }

    private static void assertNothingWasGranted(long characterId, long expBefore) {
        assertThat(readExp(characterId)).as("EXP không được trao").isEqualTo(expBefore);
        assertThat(readStatus(characterId)).as("quest vẫn lĩnh lại được").isEqualTo("COMPLETED");
        assertThat(countNonPositiveRows(characterId))
                .as("không dòng inventory_item nào có quantity <= 0")
                .isZero();
    }

    /** Đọc túi qua đúng adapter mà đường liệt kê túi đồ dùng — dòng quantity ≤ 0 làm nó NÉM. */
    private static void assertBagIsStillReadable(long characterId, int expectedRows) {
        Inventory inventory = new PostgresInventoryAdapter(dataSource)
                .findByCharacter(characterId)
                .orElseThrow();
        assertThat(inventory.items()).hasSize(expectedRows);
    }

    private static ClaimQuestRewardUseCase.ClaimResult claim(long characterId) {
        return newClaimUseCase()
                .execute(characterId, QUEST_CODE, UUID.randomUUID().toString());
    }

    /** Nhân vật mới tinh đã nhận quest mẫu, rồi đặt thẳng COMPLETED kèm nhánh script cần chạy. */
    private static long completedQuestCharacter(String characterName, String scriptCase) {
        long characterId = createFreshCharacter(characterName);
        assertThat(newAcceptUseCase().execute(characterId, QUEST_CODE)).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        execute("""
                UPDATE character_quest_progress SET status = 'COMPLETED', progress_json = ?
                 WHERE character_id = ?
                   AND quest_template_id = (SELECT id FROM quest_template WHERE code = ?)
                """, "{\"case\":\"" + scriptCase + "\"}", characterId, QUEST_CODE);
        return characterId;
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
                new PostgresQuestIdempotencyAdapter());
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
    private static long countItemQuantity(long characterId, String itemCode) {
        return queryLong(
                "SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii"
                        + " JOIN item_template it ON it.id = ii.item_template_id"
                        + " WHERE ii.character_id = ? AND it.code = ?",
                characterId,
                itemCode);
    }

    private static long countNonPositiveRows(long characterId) {
        return queryLong("SELECT COUNT(*) FROM inventory_item WHERE character_id = ? AND quantity <= 0", characterId);
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
