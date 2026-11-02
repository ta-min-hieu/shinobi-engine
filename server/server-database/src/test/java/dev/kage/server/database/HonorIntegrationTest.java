package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
import dev.kage.pvp.domain.HonorPort;
import dev.kage.pvp.domain.HonorRules;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.pvp.PostgresHonorAdapter;

/**
 * ADR-155 — điểm vinh dự + cửa hàng trên PostgreSQL THẬT (V126): trần 100 điểm/ngày, đổi trừ đúng giá, trần lượt tuần,
 * thiếu điểm hay túi đầy thì không trừ.
 */
@Testcontainers
class HonorIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static PostgresHonorAdapter honor;

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
        honor = new PostgresHonorAdapter(dataSource);
    }

    @Test
    void dailyCapShouldStopFarming_andTheShopShouldChargeAndLimit() {
        long hero = character("VinhDu01");
        for (int i = 0; i < 12; i++) {
            honor.award(hero, HonorRules.WIN_POINTS, DAY);
        }
        assertThat(honor.shop(hero, DAY).available()).as("trần 100/ngày").isEqualTo(100L);
        honor.award(hero, HonorRules.WIN_POINTS, DAY.plusDays(1));
        assertThat(honor.shop(hero, DAY).available()).isEqualTo(110L);

        long charm = templateOf("enhance_protection_charm");
        assertThat(honor.buy(hero, charm, DAY, NOW)).isEqualTo(HonorPort.Outcome.OK);
        assertThat(honor.buy(hero, charm, DAY, NOW))
                .as("110 - 60 = 50 < 60")
                .isEqualTo(HonorPort.Outcome.NOT_ENOUGH_HONOR);
        assertThat(honor.buy(hero, 999_999L, DAY, NOW)).isEqualTo(HonorPort.Outcome.UNKNOWN_ITEM);

        long teleport = templateOf("teleport_talisman");
        for (int i = 0; i < 2; i++) {
            assertThat(honor.buy(hero, teleport, DAY, NOW)).isEqualTo(HonorPort.Outcome.OK);
        }
        HonorPort.Shop shop = honor.shop(hero, DAY);
        assertThat(shop.available()).isEqualTo(110L - 60L - 2 * 20L);
        assertThat(shop.items())
                .filteredOn(i -> i.itemTemplateId() == teleport)
                .singleElement()
                .satisfies(i -> assertThat(i.boughtThisWeek()).isEqualTo(2));
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'HONOR_SHOP_BUY'", hero))
                .isEqualTo(3L);
    }

    @Test
    void weeklyLimitShouldResetNextWeek() {
        long hero = character("VinhDu02");
        for (int d = 0; d < 4; d++) {
            for (int i = 0; i < 10; i++) {
                honor.award(hero, HonorRules.WIN_POINTS, DAY.plusDays(d));
            }
        }
        long map = templateOf("broadcast_talisman");
        assertThat(honor.buy(hero, map, DAY, NOW)).isEqualTo(HonorPort.Outcome.OK);
        assertThat(honor.buy(hero, map, DAY, NOW)).isEqualTo(HonorPort.Outcome.OK);
        assertThat(honor.buy(hero, map, DAY, NOW)).isEqualTo(HonorPort.Outcome.WEEKLY_LIMIT);
        assertThat(honor.buy(hero, map, DAY.plusWeeks(1), NOW)).isEqualTo(HonorPort.Outcome.OK);
    }

    private static long templateOf(String code) {
        return scalar("SELECT id FROM item_template WHERE code = ?", code);
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static long character(String name) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] account = new Account[1];
        accountTx.inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, name.toLowerCase(Locale.ROOT)));
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(
                        account[0].id(), name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }
}
