package dev.kage.server.database.guild;

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
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildItemDonationPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-207 — góp vật phẩm cho bang trên Postgres thật: món rời túi, cống hiến + kinh nghiệm bang = giá thu × số lượng,
 * nhật ký bang; món khoá / không ở bang / quá số lượng không ghi gì.
 */
@Testcontainers
final class GuildItemDonationIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "gitemdonor"));
        accountId = accountHolder[0].id();
    }

    private static final long KUNAI = 1L;
    private static final long KUNAI_SELL_PRICE = 2L;

    @Test
    void donatesPartOfAStack_thenTheRest_andRefusesWhatCannotLeaveTheBag() {
        long leader = createFreshCharacter("GdLeader");
        long outsider = createFreshCharacter("GdOutsider");
        Guild guild = new PostgresGuildAdapter(dataSource).createGuild("GdBang", leader);
        long stack = giveItem(leader, KUNAI, 10, 20);
        long locked = giveItem(leader, KUNAI, 3, 21);
        dev.kage.server.database.JdbcOperations.update(
                dataSource, "UPDATE inventory_item SET locked = TRUE WHERE id = ?", locked);
        PostgresGuildItemDonationAdapter adapter = new PostgresGuildItemDonationAdapter(dataSource);

        assertThat(adapter.donate(outsider, stack, 1).outcome()).isEqualTo(GuildItemDonationPort.Outcome.NOT_IN_GUILD);
        assertThat(adapter.donate(leader, locked, 1).outcome()).isEqualTo(GuildItemDonationPort.Outcome.NOT_DONATABLE);
        assertThat(adapter.donate(leader, stack, 11).outcome())
                .isEqualTo(GuildItemDonationPort.Outcome.INSUFFICIENT_QUANTITY);

        GuildItemDonationPort.Donation first = adapter.donate(leader, stack, 4);
        assertThat(first.outcome()).isEqualTo(GuildItemDonationPort.Outcome.OK);
        assertThat(first.contribution()).isEqualTo(4 * KUNAI_SELL_PRICE);
        assertThat(quantity(stack)).isEqualTo(6L);
        assertThat(adapter.donate(leader, stack, 6).contribution()).isEqualTo(6 * KUNAI_SELL_PRICE);
        assertThat(quantity(stack)).isZero();
        assertThat(quantity(locked)).isEqualTo(3L);

        assertThat(single("SELECT contribution FROM guild_member WHERE character_id = ?", leader))
                .isEqualTo(10 * KUNAI_SELL_PRICE);
        assertThat(single("SELECT exp FROM guild WHERE id = ?", guild.id())).isEqualTo(10 * KUNAI_SELL_PRICE);
        assertThat(single(
                        "SELECT COUNT(*) FROM guild_log WHERE guild_id = ? AND kind = 'ITEM_DONATED'"
                                + " AND detail = 'kunai'",
                        guild.id()))
                .isEqualTo(2L);
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

    private static long quantity(long inventoryItemId) {
        return single("SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE id = ?", inventoryItemId);
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
