package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.character.api.RenameFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.RenamePort;
import dev.kage.character.domain.RenameRules;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresRenameAdapter;

/**
 * ADR-148 — Dịch Danh Phù trên PostgreSQL THẬT (V120): đổi tên tiêu đúng một phù, từ chối thì KHÔNG mất phù, trùng tên
 * (kể cả khác hoa-thường) bị chặn, hồi chiêu 7 ngày, lịch sử + audit.
 */
@Testcontainers
class RenameIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static RenameFacade renames;

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
        renames = new RenameFacade(new PostgresRenameAdapter(dataSource));
    }

    @Test
    void shouldRenameOnceConsumingOneTalisman_thenEnforceTheCooldown() {
        long hero = character("DoiTen01");
        give(hero, RenameRules.TALISMAN_CODE, 2);

        RenamePort.Result renamed = renames.rename(hero, "TanDanh01", NOW);

        assertThat(renamed.outcome()).isEqualTo(RenamePort.Outcome.OK);
        assertThat(renamed.name()).isEqualTo("TanDanh01");
        assertThat(nameOf(hero)).isEqualTo("TanDanh01");
        assertThat(talismans(hero)).isEqualTo(1L);
        assertThat(scalar(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'CHARACTER_RENAME'", hero))
                .isEqualTo(1L);

        RenamePort.Result tooSoon = renames.rename(hero, "TanDanh02", NOW.plus(Duration.ofDays(6)));
        assertThat(tooSoon.outcome()).isEqualTo(RenamePort.Outcome.ON_COOLDOWN);
        assertThat(tooSoon.nextAllowedAt()).contains(NOW.plus(RenameRules.COOLDOWN));
        assertThat(talismans(hero)).as("bị từ chối thì không mất phù").isEqualTo(1L);

        assertThat(renames.rename(hero, "TanDanh02", NOW.plus(RenameRules.COOLDOWN))
                        .outcome())
                .isEqualTo(RenamePort.Outcome.OK);
        assertThat(talismans(hero)).isZero();
        assertThat(scalar("SELECT COUNT(*) FROM character_name_history WHERE character_id = ?", hero))
                .isEqualTo(2L);
    }

    @Test
    void shouldRefuseTakenInvalidAndSameNames_withoutConsumingTheTalisman() {
        long hero = character("DoiTen03");
        character("DaCoNguoi");
        give(hero, RenameRules.TALISMAN_CODE, 1);

        assertThat(renames.rename(hero, "daconguoi", NOW).outcome())
                .as("trùng tên khác hoa-thường — UNIQUE lower(name) của V54")
                .isEqualTo(RenamePort.Outcome.NAME_TAKEN);
        assertThat(renames.rename(hero, "có dấu cách", NOW).outcome()).isEqualTo(RenamePort.Outcome.INVALID_NAME);
        assertThat(renames.rename(hero, "DoiTen03", NOW).outcome()).isEqualTo(RenamePort.Outcome.SAME_NAME);

        assertThat(talismans(hero)).isEqualTo(1L);
        assertThat(nameOf(hero)).isEqualTo("DoiTen03");
    }

    @Test
    void shouldRefuseWithoutATalisman() {
        long hero = character("DoiTen04");

        RenamePort.Result result = renames.rename(hero, "KhongPhu04", NOW);

        assertThat(result.outcome()).isEqualTo(RenamePort.Outcome.NO_TALISMAN);
        assertThat(result.name()).isEqualTo("DoiTen04");
        assertThat(nameOf(hero)).isEqualTo("DoiTen04");
    }

    private static String nameOf(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource, "SELECT name FROM character WHERE id = ?", rs -> rs.getString(1), characterId)
                .orElseThrow();
    }

    private static long talismans(long characterId) {
        return scalar(
                "SELECT COALESCE(SUM(i.quantity), 0) FROM inventory_item i JOIN item_template t"
                        + " ON t.id = i.item_template_id WHERE i.character_id = ? AND t.code = 'rename_talisman'",
                characterId);
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static void give(long characterId, String code, int quantity) {
        JdbcOperations.update(dataSource, """
                INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
                """, characterId, quantity, code);
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
