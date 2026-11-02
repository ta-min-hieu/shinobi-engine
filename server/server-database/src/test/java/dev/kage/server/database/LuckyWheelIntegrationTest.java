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
import dev.kage.checkin.domain.LuckyWheelPort;
import dev.kage.checkin.domain.LuckyWheelRules;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.checkin.PostgresLuckyWheelAdapter;

/**
 * ADR-151 — vòng quay trên PostgreSQL THẬT (V122): lượt miễn phí trước, rồi lượt mua trừ đúng giá, trần lượt mua/ngày,
 * thiếu tiền hay túi đầy thì không mất lượt, không mất tiền; sang ngày mới có lại lượt miễn phí.
 */
@Testcontainers
class LuckyWheelIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    /** Trọng số V122 — mỗi số chỉ thuộc về MỘT ô. */
    private static final int SMALL_GOLD = 30;

    private static final int PILLS = 20;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static PostgresLuckyWheelAdapter wheel;

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
        wheel = new PostgresLuckyWheelAdapter(dataSource);
    }

    @Test
    void freeSpinFirst_thenPaidSpinsChargeTheCost_untilTheDailyCap() {
        long hero = character("VongQuay01");
        setGold(hero, 0L);
        LuckyWheelPort.Wheel fresh = wheel.wheel(hero, TODAY);
        assertThat(fresh.prizes()).hasSize(8);
        assertThat(fresh.spins()).isEqualTo(new LuckyWheelPort.Spins(1, LuckyWheelRules.PAID_SPINS_PER_DAY));

        LuckyWheelPort.Spin free = wheel.spin(hero, TODAY, NOW, w -> indexOf(w, SMALL_GOLD));
        assertThat(free.outcome()).isEqualTo(LuckyWheelPort.Outcome.OK);
        assertThat(free.paid()).isFalse();
        assertThat(free.prize().orElseThrow().gold()).isEqualTo(200L);
        assertThat(gold(hero)).isEqualTo(200L);

        assertThat(wheel.spin(hero, TODAY, NOW, w -> indexOf(w, SMALL_GOLD)).outcome())
                .as("hết lượt miễn phí, 200 < giá một lượt")
                .isEqualTo(LuckyWheelPort.Outcome.INSUFFICIENT_GOLD);
        assertThat(gold(hero)).isEqualTo(200L);

        setGold(hero, 1_000_000L);
        for (int i = 0; i < LuckyWheelRules.PAID_SPINS_PER_DAY; i++) {
            LuckyWheelPort.Spin paid = wheel.spin(hero, TODAY, NOW, w -> indexOf(w, SMALL_GOLD));
            assertThat(paid.outcome()).isEqualTo(LuckyWheelPort.Outcome.OK);
            assertThat(paid.paid()).isTrue();
        }
        assertThat(gold(hero))
                .isEqualTo(1_000_000L - LuckyWheelRules.PAID_SPINS_PER_DAY * (LuckyWheelRules.SPIN_COST - 200L));
        assertThat(wheel.spin(hero, TODAY, NOW, w -> 0).outcome()).isEqualTo(LuckyWheelPort.Outcome.NO_SPINS_LEFT);
        assertThat(wheel.wheel(hero, TODAY.plusDays(1)).spins().freeLeft())
                .as("sang ngày mới")
                .isEqualTo(1);
        assertThat(scalar(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'LUCKY_WHEEL_SPIN'", hero))
                .isEqualTo(1L + LuckyWheelRules.PAID_SPINS_PER_DAY);
    }

    @Test
    void fullBagShouldKeepTheSpinAndTheGold() {
        long hero = character("VongQuay02");
        setGold(hero, 5_000L);
        for (int slot = 0; slot < 20; slot++) {
            JdbcOperations.update(dataSource, """
                    INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                    SELECT ?, id, 1, ?, 0 FROM item_template WHERE code = 'kunai'
                    """, hero, slot);
        }

        LuckyWheelPort.Spin full = wheel.spin(hero, TODAY, NOW, w -> indexOf(w, PILLS));

        assertThat(full.outcome()).isEqualTo(LuckyWheelPort.Outcome.BAG_FULL);
        assertThat(full.spins().freeLeft()).isEqualTo(1);
        assertThat(gold(hero)).isEqualTo(5_000L);
    }

    private static int indexOf(int[] weights, int weight) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == weight) {
                return i;
            }
        }
        throw new AssertionError("không có ô trọng số " + weight);
    }

    private static void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    private static long gold(long characterId) {
        return scalar("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
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
