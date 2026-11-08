package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import dev.kage.inventory.domain.ItemDestroyPort;
import dev.kage.inventory.domain.ItemHistoryPort;
import dev.kage.market.domain.MarketPriceHintPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Lô 38 trên Postgres thật: huỷ vật phẩm (ADR-231), ứng viên phân giải hàng loạt (ADR-232), lịch sử cường hoá đọc từ
 * audit (ADR-233), giá gợi ý phường thị (ADR-234).
 */
@Testcontainers
final class Lot38IntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot38tester"));
        accountId = accountHolder[0].id();
    }

    private static final long KUNAI = 1L;
    private static final long WOODEN_SWORD = 2L;

    @Test
    void destroysOnlyFreeItems() {
        long owner = createFreshCharacter("DsOwner");
        long free = giveItem(owner, KUNAI, 10, 20);
        long locked = giveItem(owner, KUNAI, 3, 21);
        long worn = giveItem(owner, WOODEN_SWORD, 1, 22);
        execute("UPDATE inventory_item SET locked = TRUE WHERE id = ?", locked);
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO equipment_slot (character_id, slot_type, inventory_item_id) VALUES (?, 'WEAPON', ?)",
                owner,
                worn);
        PostgresItemDestroyAdapter adapter = new PostgresItemDestroyAdapter(dataSource);

        assertThat(adapter.destroy(owner + 999, free)).isEqualTo(ItemDestroyPort.Outcome.NOT_FOUND);
        assertThat(adapter.destroy(owner, locked)).isEqualTo(ItemDestroyPort.Outcome.LOCKED);
        assertThat(adapter.destroy(owner, worn)).isEqualTo(ItemDestroyPort.Outcome.EQUIPPED);
        assertThat(adapter.destroy(owner, free)).isEqualTo(ItemDestroyPort.Outcome.OK);

        assertThat(single("SELECT COUNT(*) FROM inventory_item WHERE id = ?", free))
                .isZero();
        assertThat(single("SELECT COUNT(*) FROM audit_log WHERE action = 'ITEM_DESTROYED' AND character_id = ?", owner))
                .isEqualTo(1L);
    }

    @Test
    void listsDismantleCandidatesAndReadsEnhanceHistory() {
        long owner = createFreshCharacter("DmOwner");
        long spare = giveItem(owner, WOODEN_SWORD, 1, 20);
        long locked = giveItem(owner, WOODEN_SWORD, 1, 21);
        giveItem(owner, KUNAI, 5, 22);
        execute("UPDATE inventory_item SET locked = TRUE WHERE id = ?", locked);
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO audit_log (character_id, action, detail_json, created_at)"
                        + " VALUES (?, 'ENHANCE_ITEM', ?, now())",
                owner,
                "{\"itemId\":" + spare + ",\"fromLevel\":2,\"toLevel\":3,\"goldCost\":120,\"result\":\"SUCCESS\"}");
        PostgresItemHistoryAdapter adapter = new PostgresItemHistoryAdapter(dataSource);

        assertThat(adapter.dismantleCandidates(owner, 1)).containsExactly(spare);
        assertThat(adapter.dismantleCandidates(owner, 0)).isEmpty();
        List<ItemHistoryPort.EnhanceRecord> history = adapter.enhanceHistory(owner, 20);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().itemTemplateId()).isEqualTo(WOODEN_SWORD);
        assertThat(history.getFirst().toLevel()).isEqualTo(3);
        assertThat(history.getFirst().goldCost()).isEqualTo(120L);
        assertThat(history.getFirst().result()).isEqualTo("SUCCESS");
    }

    @Test
    void aggregatesMarketPricePerPiece() {
        long seller = createFreshCharacter("PhSeller");
        insertListing(seller, 10, 500, Optional.empty());
        insertListing(seller, 2, 80, Optional.empty());
        insertListing(seller, 4, 200, Optional.of(Duration.ZERO));
        insertListing(seller, 1, 70, Optional.of(Duration.ZERO));
        insertListing(seller, 1, 9000, Optional.of(Duration.ofDays(30)));
        dev.kage.server.database.market.PostgresMarketPriceHintAdapter adapter =
                new dev.kage.server.database.market.PostgresMarketPriceHintAdapter(dataSource);

        MarketPriceHintPort.Hint hint = adapter.hint(KUNAI, Instant.now().minus(Duration.ofDays(7)));
        assertThat(hint.lowestActive()).isEqualTo(40L);
        assertThat(hint.averageSold()).isEqualTo(60L);
        assertThat(hint.soldListings()).isEqualTo(2);
        assertThat(adapter.hint(WOODEN_SWORD, Instant.now()).lowestActive()).isZero();
    }

    /** {@code soldAgo} rỗng ⇒ dòng đang rao; có ⇒ dòng đã bán cách đây chừng đó. */
    private static void insertListing(long seller, int quantity, long price, Optional<Duration> soldAgo) {
        Instant now = Instant.now();
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO market_listing (seller_id, item_template_id, quantity, price, status, listed_at,"
                        + " expires_at, closed_at) VALUES (?, 1, ?, ?, ?, ?, ?, ?)",
                seller,
                quantity,
                price,
                soldAgo.isPresent() ? "SOLD" : "ACTIVE",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plus(Duration.ofDays(1))),
                soldAgo.map(ago -> java.sql.Timestamp.from(now.minus(ago))).orElse(null));
    }

    private static void execute(String sql, long arg) {
        dev.kage.server.database.JdbcOperations.update(dataSource, sql, arg);
    }

    private static long giveItem(long characterId, long templateId, int quantity, int slot) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource,
                        "INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index)"
                                + " VALUES (?, ?, ?, ?) RETURNING id",
                        rs -> rs.getLong(1),
                        characterId,
                        templateId,
                        quantity,
                        slot)
                .orElseThrow();
    }

    private static long single(String sql, Object arg) {
        return dev.kage.server.database.JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), arg)
                .orElseThrow();
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
}
