package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.kage.inventory.domain.StackSplitPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Lô 36 trên Postgres thật: tách chồng (ADR-215), xoá hàng loạt thư đã xong + ghim thư (ADR-216/217), dòng giới thiệu
 * (ADR-218).
 */
@Testcontainers
final class Lot36IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot36tester"));
        accountId = accountHolder[0].id();
    }

    private static final long KUNAI = 1L;

    @Test
    void splitsAStackIntoANewSlot_andRefusesBadAmounts() {
        long owner = createFreshCharacter("SpOwner");
        long stack = giveItem(owner, KUNAI, 10, 20);
        PostgresStackSplitAdapter adapter = new PostgresStackSplitAdapter(dataSource);

        assertThat(adapter.split(owner, stack, 10)).isEqualTo(StackSplitPort.Outcome.INVALID_AMOUNT);
        assertThat(adapter.split(owner + 999, stack, 1)).isEqualTo(StackSplitPort.Outcome.NOT_FOUND);
        assertThat(adapter.split(owner, stack, 4)).isEqualTo(StackSplitPort.Outcome.OK);

        assertThat(single("SELECT quantity FROM inventory_item WHERE id = ?", stack))
                .isEqualTo(6L);
        assertThat(single(
                        "SELECT quantity FROM inventory_item WHERE character_id = ? AND id <> " + stack
                                + " AND item_template_id = 1",
                        owner))
                .isEqualTo(4L);
    }

    @Test
    void deletesOnlyFinishedUnpinnedMail() {
        long owner = createFreshCharacter("MlOwner");
        long notice = insertMail(owner, 0L, false);
        long claimed = insertMail(owner, 50L, true);
        long unclaimedGold = insertMail(owner, 50L, false);
        long pinnedNotice = insertMail(owner, 0L, false);
        dev.kage.server.database.mail.PostgresMailHousekeepingAdapter adapter =
                new dev.kage.server.database.mail.PostgresMailHousekeepingAdapter(dataSource);

        assertThat(adapter.setPinned(owner, pinnedNotice, true)).isTrue();
        assertThat(adapter.setPinned(owner + 999, notice, true)).isFalse();
        assertThat(adapter.deleteFinished(owner)).isEqualTo(2);

        assertThat(single(
                        "SELECT COUNT(*) FROM mail WHERE id IN (" + notice + "," + claimed + ") AND recipient_id = ?",
                        owner))
                .isZero();
        assertThat(adapter.pinned(owner)).containsExactly(pinnedNotice);
        assertThat(single("SELECT COUNT(*) FROM mail WHERE recipient_id = ?", owner))
                .isEqualTo(2L);
        assertThat(unclaimedGold).isPositive();
    }

    @Test
    void storesTheBio() {
        long owner = createFreshCharacter("BioOwner");
        dev.kage.server.database.character.PostgresInspectPrivacyAdapter adapter =
                new dev.kage.server.database.character.PostgresInspectPrivacyAdapter(dataSource);

        assertThat(adapter.bio(owner)).isEmpty();
        adapter.setBio(owner, "Tu tiên vạn năm");
        assertThat(adapter.bio(owner)).isEqualTo("Tu tiên vạn năm");
    }

    private static long insertMail(long recipient, long gold, boolean claimed) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource,
                        "INSERT INTO mail (recipient_id, subject, body, gold_amount, claimed_at, created_at)"
                                + " VALUES (?, 's', 'b', ?, " + (claimed ? "now()" : "NULL") + ", now()) RETURNING id",
                        rs -> rs.getLong(1),
                        recipient,
                        gold)
                .orElseThrow();
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
