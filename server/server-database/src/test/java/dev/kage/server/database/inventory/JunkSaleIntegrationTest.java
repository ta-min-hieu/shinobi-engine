package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
import dev.kage.inventory.domain.JunkSalePort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-170 — bán nhanh đồ rác trên Postgres thật: chỉ loại {@code junk}, bỏ qua chồng đã khoá; xem trước khớp với cái bị
 * bán; vàng cộng đúng giá thu mua; bán lần hai không còn gì.
 */
@Testcontainers
final class JunkSaleIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant AT = Instant.parse("2026-11-04T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long kunai;
    static long ironScrap;
    static long sword;
    static PostgresJunkSaleAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "junktester"));
        accountId = accountHolder[0].id();
        kunai = queryLong("SELECT id FROM item_template WHERE code = ?", "kunai");
        ironScrap = queryLong("SELECT id FROM item_template WHERE code = ?", "iron_scrap");
        sword = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
        adapter = new PostgresJunkSaleAdapter(dataSource);
    }

    @Test
    void shouldSellOnlyUnlockedJunk_matchingThePreview() {
        long owner = createFreshCharacter("JunkA");
        insert(owner, kunai, 6);
        long lockedScrap = insert(owner, ironScrap, 3);
        new PostgresItemLockAdapter(dataSource).setLocked(owner, lockedScrap, true);
        insert(owner, sword, 1);
        long goldBefore = gold(owner);
        long kunaiPrice = queryLong("SELECT sell_price FROM shop_item WHERE item_template_id = ?", kunai);

        assertThat(adapter.preview(owner)).containsExactly(new JunkSalePort.Line(kunai, 6, kunaiPrice));

        JunkSalePort.Sold sold = adapter.sellAll(owner, AT);

        assertThat(sold.lines()).containsExactly(new JunkSalePort.Line(kunai, 6, kunaiPrice));
        assertThat(sold.gold()).isEqualTo(6 * kunaiPrice);
        assertThat(gold(owner)).isEqualTo(goldBefore + 6 * kunaiPrice);
        assertThat(count(owner, kunai)).isZero();
        assertThat(count(owner, ironScrap)).isEqualTo(3L);
        assertThat(count(owner, sword)).isEqualTo(1L);
        assertThat(adapter.sellAll(owner, AT).lines()).isEmpty();
    }

    private static long insert(long characterId, long templateId, int quantity) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, ?, 0, 0) RETURNING id
                """, characterId, templateId, quantity);
    }

    private static long count(long characterId, long templateId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static long gold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
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
}
