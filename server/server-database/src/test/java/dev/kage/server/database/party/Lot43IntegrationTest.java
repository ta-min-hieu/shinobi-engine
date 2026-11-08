package dev.kage.server.database.party;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import dev.kage.party.domain.PartyGoldSplitPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 43 trên Postgres thật: chia vàng cho nhóm (ADR-278) — bảo toàn tổng vàng, audit, không đủ thì không đổi gì. */
@Testcontainers
final class Lot43IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot43tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void splitMovesGoldConservingTotalAndAudits() {
        long giver = createFreshCharacter("Splitter");
        long a = createFreshCharacter("ShareA");
        long b = createFreshCharacter("ShareB");
        setGold(giver, 1_000L);
        long before = gold(giver) + gold(a) + gold(b);
        PostgresPartyGoldSplitAdapter adapter = new PostgresPartyGoldSplitAdapter(dataSource);

        PartyGoldSplitPort.Split split = adapter.split(giver, List.of(a, b), 150L, Instant.now());

        assertThat(split.outcome()).isEqualTo(PartyGoldSplitPort.Outcome.OK);
        assertThat(split.goldAfter()).isEqualTo(gold(giver));
        assertThat(gold(giver) + gold(a) + gold(b)).isEqualTo(before);
        assertThat(audits(giver, "PARTY_GOLD_SPLIT")).isEqualTo(1L);
        assertThat(audits(a, "PARTY_GOLD_RECEIVED")).isEqualTo(1L);
        assertThat(audits(b, "PARTY_GOLD_RECEIVED")).isEqualTo(1L);
    }

    @Test
    void notEnoughGoldChangesNothing() {
        long giver = createFreshCharacter("PoorSplit");
        long a = createFreshCharacter("PoorShare");
        setGold(giver, 10L);
        long receiverBefore = gold(a);

        PartyGoldSplitPort.Split split =
                new PostgresPartyGoldSplitAdapter(dataSource).split(giver, List.of(a), 11L, Instant.now());

        assertThat(split.outcome()).isEqualTo(PartyGoldSplitPort.Outcome.NOT_ENOUGH_GOLD);
        assertThat(gold(giver)).isEqualTo(10L);
        assertThat(gold(a)).isEqualTo(receiverBefore);
        assertThat(audits(giver, "PARTY_GOLD_SPLIT")).isZero();
    }

    private static void setGold(long characterId, long target) {
        JdbcTransactionRunner.run(dataSource, connection -> {
            long current = JdbcOperations.queryOne(
                            connection,
                            "SELECT gold FROM character_stat WHERE character_id = ?",
                            rs -> rs.getLong(1),
                            characterId)
                    .orElseThrow();
            if (target > current) {
                GoldLedger.credit(connection, characterId, target - current, "gold.reason.gm_compensation");
            } else if (target < current) {
                GoldLedger.deduct(connection, characterId, current - target, "gold.reason.gm_compensation");
            }
        });
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }

    private static long audits(long characterId, String action) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT count(*) FROM audit_log WHERE character_id = ? AND action = ?",
                        rs -> rs.getLong(1),
                        characterId,
                        action)
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
