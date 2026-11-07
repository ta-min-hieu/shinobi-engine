package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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
import dev.kage.guild.domain.GuildWarHistoryPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-208 — lịch sử bang chiến trên Postgres thật: chỉ trận đã chốt, mới nhất trước, nhìn từ phía bang người hỏi (thắng
 * / thua / hoà, điểm của mình trước).
 */
@Testcontainers
final class GuildWarHistoryIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "gwarhistory"));
        accountId = accountHolder[0].id();
    }

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void showsSettledWarsFromOurSide_newestFirst() {
        long leaderA = createFreshCharacter("WhLeaderA");
        long leaderB = createFreshCharacter("WhLeaderB");
        long loner = createFreshCharacter("WhLoner");
        PostgresGuildAdapter guilds = new PostgresGuildAdapter(dataSource);
        long a = guilds.createGuild("WhBangA", leaderA).id();
        long b = guilds.createGuild("WhBangB", leaderB).id();
        insertWar(new War(a, b, 0, 5, 2, a));
        insertWar(new War(b, a, 1, 3, 3, null));
        insertWar(new War(b, a, 2, 4, 1, b));
        insertUnsettledWar(a, b, 3);
        PostgresGuildWarHistoryAdapter adapter = new PostgresGuildWarHistoryAdapter(dataSource);

        List<GuildWarHistoryPort.PastWar> fromA = adapter.recentOf(leaderA, 20);

        assertThat(fromA)
                .extracting(GuildWarHistoryPort.PastWar::result)
                .containsExactly(
                        GuildWarHistoryPort.Result.LOSS,
                        GuildWarHistoryPort.Result.DRAW,
                        GuildWarHistoryPort.Result.WIN);
        assertThat(fromA.get(0).opponentName()).isEqualTo("WhBangB");
        assertThat(fromA.get(0).declaredByUs()).isFalse();
        assertThat(fromA.get(0).ourKills()).isEqualTo(1);
        assertThat(fromA.get(0).theirKills()).isEqualTo(4);
        assertThat(fromA.get(2).declaredByUs()).isTrue();
        assertThat(adapter.recentOf(leaderB, 20).get(2).result()).isEqualTo(GuildWarHistoryPort.Result.LOSS);
        assertThat(adapter.recentOf(leaderA, 1)).hasSize(1);
        assertThat(adapter.recentOf(loner, 20)).isEmpty();
    }

    /** Một trận đã chốt: {@code a} tuyên {@code b}, kết thúc {@code dayOffset} ngày sau {@link #T0}. */
    private record War(long a, long b, int dayOffset, int killsA, int killsB, Long winner) {}

    private static void insertWar(War war) {
        Instant ends = T0.plus(Duration.ofDays(war.dayOffset()));
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_war (guild_a, guild_b, declared_at, starts_at, ends_at, kills_a, kills_b,"
                        + " settled_at, winner_guild_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                war.a(),
                war.b(),
                Timestamp.from(ends.minus(Duration.ofHours(2))),
                Timestamp.from(ends.minus(Duration.ofHours(1))),
                Timestamp.from(ends),
                war.killsA(),
                war.killsB(),
                Timestamp.from(ends),
                war.winner());
    }

    private static void insertUnsettledWar(long a, long b, int dayOffset) {
        Instant ends = T0.plus(Duration.ofDays(dayOffset));
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_war (guild_a, guild_b, declared_at, starts_at, ends_at) VALUES (?, ?, ?, ?, ?)",
                a,
                b,
                Timestamp.from(ends.minus(Duration.ofHours(2))),
                Timestamp.from(ends.minus(Duration.ofHours(1))),
                Timestamp.from(ends));
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
