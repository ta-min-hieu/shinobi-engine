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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
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
 * Test bằng PostgreSQL THẬT — mục tiêu quest loại THỨ BA: "mang vật X tới NPC Y" (2026-09-11).
 * Quest mẫu nằm ở test resources ({@code it_fixture_delivery.js}), dòng {@code quest_template} do
 * chính test INSERT: nội dung arc 2 thật đang chờ chủ dự án, và cơ chế thì không nên phải chờ theo.
 *
 * <p><b>Vì sao là lớp RIÊNG.</b> Khối này sinh ra trong {@link QuestIntegrationTest} và góp phần
 * đẩy lớp đó chạm trần {@code CyclomaticComplexity} của PMD (tính cho CẢ LỚP, trần 80). Luật tách
 * theo VÙNG NGHIỆP VỤ đã ghi sẵn trong javadoc lớp kia: {@link OuterSectQuestIntegrationTest} là
 * lần thứ nhất, {@link FoundationTrialQuestIntegrationTest} lần thứ hai, đây là lần thứ ba.
 *
 * <p>Thứ lớp này gác là CƠ CHẾ, không phải nội dung: một transaction lấy đủ đồ hoặc không lấy gì,
 * túi phân mảnh vẫn gom đúng, đồ đang mặc / đã cường hoá không bao giờ bị ăn mất, và bốn lần mở lời
 * cùng lúc chỉ giao được MỘT lần ({@code UPDATE ... WHERE progress_json = ?}).
 */
@Testcontainers
class QuestDeliveryIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    private static final String DELIVERY_CODE = "it_deliver_to_senior_sister";
    private static final String DELIVERY_SCRIPT_REF = "/scripts/quest/it_fixture_delivery.js";
    private static final String ITEM_TAKEN_AUDIT_ACTION = "QUEST_ITEM_TAKEN";
    private static final String SENIOR_SISTER = "senior_sister";
    private static final String HEALING_PILL = "minor_healing_pill";
    private static final String KUNAI = "kunai";
    private static final String WOODEN_SWORD = "wooden_sword";
    private static final int CONCURRENT_TALKS = 4;
    private static final long CONCURRENT_TALK_TIMEOUT_SECONDS = 30L;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "deliverytester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void shouldHandOverEverything_andCompleteTheDelivery_inOneTransaction() {
        long characterId = acceptDeliveryQuest("Deliver1");
        insertInventoryRow(characterId, HEALING_PILL, 3, 0);
        insertInventoryRow(characterId, KUNAI, 1, 0);

        List<ProcessQuestEventUseCase.QuestStatusChange> afterTalk =
                newProcessUseCase().npcTalked(characterId, SENIOR_SISTER);

        assertThat(afterTalk)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(DELIVERY_CODE);
        assertThat(countItemQuantity(characterId, HEALING_PILL)).isEqualTo(1);
        assertThat(countItemQuantity(characterId, KUNAI)).isZero();
        assertThat(countInventoryRows(characterId))
                .isEqualTo(1); // dòng phi đao về 0 thì bị XOÁ, không để lại dòng rỗng
        assertThat(countAudit(characterId, ITEM_TAKEN_AUDIT_ACTION)).isEqualTo(2);

        // Mở lời lần nữa: quest đã rời IN_PROGRESS — không một viên đan nào bị lấy thêm.
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();
        assertThat(countItemQuantity(characterId, HEALING_PILL)).isEqualTo(1);
    }

    /**
     * Rollback trên DB THẬT: script lấy 2 viên đan TRƯỚC (câu {@code DELETE} đã chạy), rồi mới thiếu
     * phi đao. Hai viên đan phải quay về, cờ {@code delivered} không được ghi, không một dòng audit.
     */
    @Test
    void shouldRollBackTheFirstItem_whenTheSecondIsMissing() {
        long characterId = acceptDeliveryQuest("Deliver2");
        insertInventoryRow(characterId, HEALING_PILL, 2, 0);

        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).isEmpty();

        assertThat(countItemQuantity(characterId, HEALING_PILL)).isEqualTo(2);
        assertThat(readProgressJson(characterId, DELIVERY_CODE)).isEqualTo("{\"delivered\":false}");
        assertThat(countAudit(characterId, ITEM_TAKEN_AUDIT_ACTION)).isZero();

        // Và quay lại với đủ đồ thì vẫn giao được — lần thiếu đồ không để lại dấu vết nào chặn đường.
        insertInventoryRow(characterId, KUNAI, 1, 0);
        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).hasSize(1);
        assertThat(countItemQuantity(characterId, HEALING_PILL)).isZero();
    }

    /** Túi cũ phân mảnh: hai viên đan ở hai dòng vẫn là hai viên đan. Dòng id nhỏ lấy trước. */
    @Test
    void shouldGatherTheDelivery_fromAFragmentedBag() {
        long characterId = acceptDeliveryQuest("Deliver3");
        long firstPill = insertInventoryRow(characterId, HEALING_PILL, 1, 0);
        long secondPill = insertInventoryRow(characterId, HEALING_PILL, 1, 0);
        insertInventoryRow(characterId, HEALING_PILL, 5, 0);
        insertInventoryRow(characterId, KUNAI, 1, 0);

        assertThat(newProcessUseCase().npcTalked(characterId, SENIOR_SISTER)).hasSize(1);

        assertThat(inventoryRowExists(firstPill)).isFalse();
        assertThat(inventoryRowExists(secondPill)).isFalse();
        assertThat(countItemQuantity(characterId, HEALING_PILL)).isEqualTo(5);
        assertThat(countInventoryRows(characterId)).isEqualTo(1);
    }

    /**
     * Cùng mã vật phẩm KHÔNG có nghĩa là lấy được: thanh kiếm đang cầm trên tay và thanh đã cường
     * hoá không bao giờ bị một quest giao đồ ăn mất. Gọi thẳng adapter — quest mẫu không giao kiếm.
     */
    @Test
    void shouldNeverTakeAnEquippedOrEnhancedItem() {
        long characterId = createFreshCharacter("Deliver4");
        long equipped = insertInventoryRow(characterId, WOODEN_SWORD, 1, 0);
        execute(
                "INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, 'WEAPON', ?)",
                characterId,
                equipped);
        long enhanced = insertInventoryRow(characterId, WOODEN_SWORD, 1, 3);

        assertThat(takeInOwnTransaction(characterId, WOODEN_SWORD, 1)).isFalse();
        assertThat(countInventoryRows(characterId)).isEqualTo(2);

        long spare = insertInventoryRow(characterId, WOODEN_SWORD, 1, 0);
        assertThat(takeInOwnTransaction(characterId, WOODEN_SWORD, 1)).isTrue();
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
        insertInventoryRow(characterId, HEALING_PILL, 10, 0);
        insertInventoryRow(characterId, KUNAI, 5, 0);
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
                    return sharedUseCase.npcTalked(characterId, SENIOR_SISTER);
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
        assertThat(countItemQuantity(characterId, HEALING_PILL)).isEqualTo(8);
        assertThat(countItemQuantity(characterId, KUNAI)).isEqualTo(4);
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

    private static ProcessQuestEventUseCase newProcessUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ProcessQuestEventUseCase(
                progressPort, new PostgresQuestItemTakeAdapter(), new PostgresQuestAuditAdapter(), tx);
    }

    private static String readProgressJson(long characterId, String questCode) {
        String sql = "SELECT p.progress_json FROM character_quest_progress p"
                + " JOIN quest_template q ON q.id = p.quest_template_id"
                + " WHERE p.character_id = ? AND q.code = ?";
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

    /**
     * Tổng số lượng của MỘT mã vật phẩm (không phải số dòng): đồ xếp chồng được có thể gộp vào dòng
     * sẵn có thay vì sinh dòng mới — {@link #countInventoryRows} sẽ không thấy gì nếu chỉ đếm dòng.
     */
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
}
