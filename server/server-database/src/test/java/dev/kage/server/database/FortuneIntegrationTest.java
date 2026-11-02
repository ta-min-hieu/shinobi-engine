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
import dev.kage.checkin.api.FortuneFacade;
import dev.kage.checkin.domain.FortunePort;
import dev.kage.checkin.domain.FortuneRules;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.checkin.PostgresFortuneAdapter;

/**
 * ADR-149 — quẻ bói trên PostgreSQL THẬT (V121): một quẻ mỗi ngày giờ Việt Nam, quà cấp đúng một lần, túi đầy thì chưa
 * tính là đã gieo, phần trăm EXP đọc lại được sau khi gieo.
 */
@Testcontainers
class FortuneIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z"); // 10:00 giờ VN

    /** Trọng số V121 — mỗi số chỉ thuộc về MỘT quẻ. */
    private static final int DAI_CAT = 8;

    private static final int TRUNG_CAT = 20;
    private static final int HUNG = 15;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static PostgresFortuneAdapter fortunes;

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
        fortunes = new PostgresFortuneAdapter(dataSource);
    }

    @Test
    void shouldDrawOncePerVietnamDay_andPayTheGiftOnce() {
        long hero = character("XinQue01");
        LocalDate today = FortuneRules.day(NOW);

        FortunePort.Draw first = fortunes.draw(hero, today, NOW, weights -> indexOf(weights, TRUNG_CAT));
        assertThat(first.outcome()).isEqualTo(FortunePort.Outcome.OK);
        assertThat(first.fortune().orElseThrow().code()).isEqualTo("trung_cat");
        assertThat(gold(hero)).isEqualTo(300L);

        FortunePort.Draw again = fortunes.draw(hero, today, NOW.plusSeconds(60), weights -> indexOf(weights, DAI_CAT));
        assertThat(again.outcome()).isEqualTo(FortunePort.Outcome.ALREADY_DRAWN);
        assertThat(again.fortune()).isEqualTo(first.fortune());
        assertThat(gold(hero)).as("không quà lần hai").isEqualTo(300L);
        assertThat(fortunes.today(hero, today)).isEqualTo(first.fortune());

        Instant tomorrow = NOW.plus(Duration.ofDays(1));
        assertThat(fortunes.draw(hero, FortuneRules.day(tomorrow), tomorrow, weights -> indexOf(weights, HUNG))
                        .outcome())
                .isEqualTo(FortunePort.Outcome.OK);
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'FORTUNE_DRAW'", hero))
                .isEqualTo(2L);
    }

    @Test
    void fullBagShouldNotCountAsDrawn() {
        long hero = character("XinQue02");
        LocalDate today = FortuneRules.day(NOW);
        for (int slot = 0; slot < 20; slot++) {
            JdbcOperations.update(dataSource, """
                    INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                    SELECT ?, id, 1, ?, 0 FROM item_template WHERE code = 'kunai'
                    """, hero, slot);
        }

        assertThat(fortunes.draw(hero, today, NOW, weights -> indexOf(weights, DAI_CAT))
                        .outcome())
                .isEqualTo(FortunePort.Outcome.BAG_FULL);
        assertThat(fortunes.today(hero, today)).isEmpty();
    }

    @Test
    void facadeShouldApplyTodaysPercentToMonsterExp() {
        long hero = character("XinQue03");
        FortuneFacade facade = new FortuneFacade(fortunes);
        assertThat(facade.boostedExp(hero, 100L, NOW)).as("chưa gieo").isEqualTo(100L);

        fortunes.draw(hero, FortuneRules.day(NOW), NOW, weights -> indexOf(weights, HUNG));

        assertThat(new FortuneFacade(fortunes).boostedExp(hero, 100L, NOW)).isEqualTo(90L);
    }

    private static int indexOf(int[] weights, int weight) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == weight) {
                return i;
            }
        }
        throw new AssertionError("không có quẻ trọng số " + weight);
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
