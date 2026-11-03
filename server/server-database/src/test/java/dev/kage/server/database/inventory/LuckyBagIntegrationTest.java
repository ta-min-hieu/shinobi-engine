package dev.kage.server.database.inventory;

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
import dev.kage.inventory.domain.LuckyBagPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-163 — túi phúc trên Postgres thật: mở ra đúng phần quà được rút (đồ vào túi hoặc vàng), túi bị trừ; không có
 * túi / không phải túi thì từ chối và không đổi gì.
 */
@Testcontainers
final class LuckyBagIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    /** Thứ tự V131: 0 = 3 đan hồi máu, 1 = 800 vàng. */
    private static final int PILLS = 0;

    private static final int GOLD = 1;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long bag;
    static long pill;
    static PostgresLuckyBagAdapter adapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "luckybagtester"));
        accountId = accountHolder[0].id();
        bag = queryLong("SELECT id FROM item_template WHERE code = ?", "lucky_bag");
        pill = queryLong("SELECT id FROM item_template WHERE code = ?", "minor_healing_pill");
        adapter = new PostgresLuckyBagAdapter(dataSource);
    }

    @Test
    void openingShouldGrantThePickedRewardAndConsumeOneBag() {
        long owner = createFreshCharacter("BagA");
        insert(owner, bag, 2);
        long goldBefore = queryLong("SELECT gold FROM character_stat WHERE character_id = ?", owner);

        LuckyBagPort.Opened pills = adapter.open(owner, bag, weights -> PILLS);
        LuckyBagPort.Opened gold = adapter.open(owner, bag, weights -> GOLD);

        assertThat(pills.outcome()).isEqualTo(LuckyBagPort.Outcome.OK);
        assertThat(pills.reward().itemTemplateId()).isEqualTo(pill);
        assertThat(count(owner, pill)).isEqualTo(3L);
        assertThat(gold.reward().gold()).isEqualTo(800L);
        assertThat(queryLong("SELECT gold FROM character_stat WHERE character_id = ?", owner))
                .isEqualTo(goldBefore + 800L);
        assertThat(count(owner, bag)).isZero();
        assertThat(adapter.open(owner, bag, weights -> PILLS).outcome()).isEqualTo(LuckyBagPort.Outcome.NO_BAG);
    }

    @Test
    void bagListShouldShowOddsAndOwnedCount_andNonBagsAreRefused() {
        long owner = createFreshCharacter("BagB");
        insert(owner, bag, 4);

        LuckyBagPort.Bag listed = adapter.bags(owner).stream()
                .filter(b -> b.bagItemTemplateId() == bag)
                .findFirst()
                .orElseThrow();

        assertThat(listed.owned()).isEqualTo(4);
        assertThat(listed.rewards()).hasSize(10);
        assertThat(listed.rewards().stream()
                        .mapToInt(LuckyBagPort.Reward::weight)
                        .sum())
                .isEqualTo(100);
        assertThat(adapter.open(owner, pill, weights -> 0).outcome()).isEqualTo(LuckyBagPort.Outcome.NOT_A_BAG);
    }

    private static void insert(long characterId, long templateId, int quantity) {
        queryLong("""
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
