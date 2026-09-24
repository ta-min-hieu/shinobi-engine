package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.trade.JdbcTradeTransactionRunner;
import dev.kage.server.database.trade.PostgresTradeAuditAdapter;
import dev.kage.server.database.trade.PostgresTradeGoldAdapter;
import dev.kage.server.database.trade.PostgresTradeIdempotencyAdapter;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.application.TradePorts;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;

/**
 * Một món ĐANG MẶC ({@code equipment_slot.inventory_item_id} trỏ vào nó) rời túi qua đường của
 * feature KHÁC — lát cắt xuyên feature mà các lượt rà theo từng module (2026-09-19 → 09-24) chưa
 * nhìn tới. Mỗi ô đo GIÁ TRỊ CUỐI trong DB: dòng thuộc về ai, ô trang bị còn trỏ vào đâu.
 */
@Testcontainers
class EquippedItemLeavesBagIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long swordTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "equipcrosstester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
    }

    /**
     * feature-trade/CLAUDE.md quyết định 1 ghi: ai đã "trang bị mất item đã chào giữa chừng" thì
     * giao dịch thất bại SẠCH. Code chưa từng kiểm điều đó: {@code transferItem} chỉ xét chủ + số
     * lượng, nên món đang mặc bị đổi chủ nguyên dòng trong khi ô vũ khí của người đưa VẪN trỏ vào
     * nó — người đưa giữ nguyên chỉ số vũ khí trong World (chỉ tính lại khi mặc/gỡ/cường hoá) còn
     * người nhận mặc được ngay: một thanh kiếm, hai người cùng hưởng.
     */
    @Test
    void shouldRejectTrade_whenTheOfferedItemIsEquippedByTheGiver() {
        long giver = createFreshCharacter("EqTrA");
        long receiver = createFreshCharacter("EqTrB");
        long sword = insertSword(giver);
        equipWeapon(giver, sword);

        ConfirmTradeUseCase.Result result = executeOneSidedTrade(giver, receiver, sword);

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
        assertThat(queryLong("SELECT character_id FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(giver);
        assertThat(equippedWeaponOf(giver)).contains(sword);
    }

    /** Đối chứng: cùng món đó, đã GỠ ra, thì giao dịch vẫn chạy bình thường. */
    @Test
    void shouldExecuteTrade_whenTheSameItemIsNotEquipped() {
        long giver = createFreshCharacter("EqTrC");
        long receiver = createFreshCharacter("EqTrD");
        long sword = insertSword(giver);

        ConfirmTradeUseCase.Result result = executeOneSidedTrade(giver, receiver, sword);

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(queryLong("SELECT character_id FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(receiver);
    }

    /**
     * Gửi thư đính kèm "1 kiếm gỗ" trừ theo LOẠI chứ không theo dòng cụ thể, lấy dòng id nhỏ nhất
     * trước. Có hai thanh — một đang mặc (thường là thanh có trước, id nhỏ hơn) và một thanh dự
     * phòng — thì thanh ĐANG MẶC bị xoá, ô vũ khí rơi về NULL (FK {@code ON DELETE SET NULL}) dù
     * thanh dự phòng vẫn nằm đó. Hai thanh giống hệt nhau (cùng loại, cùng cấp 0), nên thư phải
     * lấy thanh KHÔNG mặc.
     */
    @Test
    void shouldConsumeTheSpareCopyForMail_notTheEquippedOne() {
        long sender = createFreshCharacter("EqMlA");
        long equipped = insertSword(sender);
        long spare = insertSword(sender);
        equipWeapon(sender, equipped);
        PostgresMailInventoryGatewayAdapter adapter = new PostgresMailInventoryGatewayAdapter();
        boolean[] consumed = new boolean[1];

        new JdbcMailTransactionRunner(dataSource)
                .inTransaction(tx -> consumed[0] = adapter.consumeItem(tx, sender, swordTemplateId, 1));

        assertThat(consumed[0]).isTrue();
        assertThat(equippedWeaponOf(sender)).contains(equipped);
        assertThat(rowExists(equipped)).isTrue();
        assertThat(rowExists(spare)).isFalse();
    }

    private static ConfirmTradeUseCase.Result executeOneSidedTrade(long giver, long receiver, long itemId) {
        PostgresTradeSessionAdapter sessionPort = new PostgresTradeSessionAdapter(dataSource);
        TradeSession session =
                new RequestTradeUseCase(sessionPort).execute(giver, receiver).session();
        new AcceptTradeRequestUseCase(sessionPort).execute(session.id(), receiver);
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), giver, 0, List.of(new OfferedItemInput(itemId, 1)));
        new SetTradeOfferUseCase(sessionPort).execute(session.id(), receiver, 0, List.of());
        TradePorts ports = new TradePorts(
                sessionPort,
                new PostgresTradeInventoryAdapter(dataSource),
                new PostgresTradeGoldAdapter(),
                new PostgresTradeAuditAdapter(),
                new PostgresTradeIdempotencyAdapter());
        ConfirmTradeUseCase confirm = new ConfirmTradeUseCase(ports, new JdbcTradeTransactionRunner(dataSource));
        confirm.execute(session.id(), giver, UUID.randomUUID().toString());
        return confirm.execute(session.id(), receiver, UUID.randomUUID().toString());
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

    private static long insertSword(long characterId) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, 1, 0, 0) RETURNING id
                """, characterId, swordTemplateId);
    }

    private static void equipWeapon(long characterId, long itemId) {
        execute(
                "INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, 'WEAPON', ?)",
                characterId,
                itemId);
    }

    private static Optional<Long> equippedWeaponOf(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT inventory_item_id FROM equipment_slot"
                        + " WHERE character_id = ? AND slot_type = 'WEAPON'")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long itemId = rs.getLong(1);
                return rs.wasNull() ? Optional.empty() : Optional.of(itemId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc ô vũ khí: characterId=" + characterId, e);
        }
    }

    private static boolean rowExists(long itemId) {
        return queryLong("SELECT COUNT(*) FROM inventory_item WHERE id = ?", itemId) > 0;
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }

    private static void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only ghi: " + sql, e);
        }
    }
}
