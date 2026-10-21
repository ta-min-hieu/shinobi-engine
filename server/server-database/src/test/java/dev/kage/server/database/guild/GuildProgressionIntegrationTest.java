package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.guild.domain.GuildRank;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.GuildWeeklyQuest;
import dev.kage.guild.domain.WeeklyCompletion;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-068/069 trên Postgres thật — góp linh thạch nguyên tử, lên cấp, mốc tuần chạm đúng một lần. */
@Testcontainers
class GuildProgressionIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);

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
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresAccountAdapter().findOrCreate(tx, "guildprogress"));
        accountId = holder[0].id();
    }

    private static long newCharacter(String name, long gold) {
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresCharacterAdapter(dataSource)
                        .create(
                                tx,
                                new NewCharacter(
                                        accountId,
                                        name,
                                        Faction.HOA,
                                        UUID.randomUUID().toString())));
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, holder[0].id());
        return holder[0].id();
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }

    private static Guild guildLedBy(long leader, String name) {
        return new PostgresGuildAdapter(dataSource).createGuild(name, leader);
    }

    private static PostgresGuildProgressionAdapter port() {
        return new PostgresGuildProgressionAdapter(dataSource);
    }

    @Test
    void donationShouldSpendGold_raiseGuildExpAndContribution_andLevelUpAtTheThreshold() {
        long leader = newCharacter("DonorLead", 50_000L);
        Guild guild = guildLedBy(leader, "Donors");

        GuildProgressionPort.Donation first =
                port().donate(leader, 9_000L, UUID.randomUUID().toString());
        GuildProgressionPort.Donation second =
                port().donate(leader, 1_000L, UUID.randomUUID().toString());

        assertThat(first.outcome()).isEqualTo(DonationOutcome.OK);
        assertThat(first.leveledUp()).isFalse();
        assertThat(second.leveledUp()).as("10.000 = mốc cấp 2").isTrue();
        assertThat(gold(leader)).isEqualTo(40_000L);
        GuildStanding standing = port().standingOf(leader, WEEK).orElseThrow();
        assertThat(standing.guildId()).isEqualTo(guild.id());
        assertThat(standing.level()).isEqualTo(2);
        assertThat(standing.exp()).isEqualTo(10_000L);
        assertThat(standing.myContribution()).isEqualTo(10_000L);
        assertThat(port().contributionsOf(guild.id())).containsEntry(leader, 10_000L);
    }

    @Test
    void donationShouldChangeNothing_whenGoldIsShort_orTheKeyIsReused() {
        long leader = newCharacter("PoorLead", 500L);
        Guild guild = guildLedBy(leader, "Poor");
        String key = UUID.randomUUID().toString();

        GuildProgressionPort.Donation tooPoor = port().donate(leader, 1_000L, key);
        JdbcOperations.update(dataSource, "UPDATE character_stat SET gold = 5000 WHERE character_id = ?", leader);
        GuildProgressionPort.Donation retried = port().donate(leader, 1_000L, key);
        GuildProgressionPort.Donation duplicate = port().donate(leader, 1_000L, key);

        assertThat(tooPoor.outcome()).isEqualTo(DonationOutcome.INSUFFICIENT_GOLD);
        assertThat(retried.outcome())
                .as("lần thiếu vàng đã rollback cả khoá chống trùng")
                .isEqualTo(DonationOutcome.OK);
        assertThat(duplicate.outcome()).isEqualTo(DonationOutcome.DUPLICATE_REQUEST);
        assertThat(gold(leader)).isEqualTo(4_000L);
        assertThat(port().standingOf(leader, WEEK).orElseThrow().exp()).isEqualTo(1_000L);
        assertThat(guild.id()).isPositive();
    }

    @Test
    void donationWithoutAGuildShouldSpendNothing() {
        long loner = newCharacter("Loner", 5_000L);

        assertThat(port().donate(loner, 1_000L, UUID.randomUUID().toString()).outcome())
                .isEqualTo(DonationOutcome.NOT_IN_GUILD);
        assertThat(gold(loner)).isEqualTo(5_000L);
    }

    @Test
    void weeklyTargetShouldCompleteExactlyOnce_underConcurrentKills() throws Exception {
        long leader = newCharacter("HuntLead", 0L);
        Guild guild = guildLedBy(leader, "Hunters");
        JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_weekly_progress (guild_id, week_start, kills) VALUES (?, ?, ?)",
                guild.id(),
                java.sql.Date.valueOf(WEEK),
                GuildWeeklyQuest.TARGET_KILLS - 5);

        List<Callable<Optional<WeeklyCompletion>>> kills = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            kills.add(() -> port().recordKill(leader, WEEK));
        }
        int completions = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<Optional<WeeklyCompletion>> result : pool.invokeAll(kills)) {
                if (result.get().isPresent()) {
                    completions++;
                    assertThat(result.get().get().memberIds()).containsExactly(leader);
                }
            }
        }

        assertThat(completions).isEqualTo(1);
        GuildStanding standing = port().standingOf(leader, WEEK).orElseThrow();
        assertThat(standing.weeklyCompleted()).isTrue();
        assertThat(standing.weeklyKills()).isEqualTo(GuildWeeklyQuest.TARGET_KILLS);
        assertThat(standing.exp()).isEqualTo(GuildWeeklyQuest.REWARD_GUILD_EXP);
    }

    @Test
    void rankingShouldOrderGuildsByExp_andListMembers() {
        long a = newCharacter("RankA", 20_000L);
        long b = newCharacter("RankB", 20_000L);
        Guild small = guildLedBy(a, "RankSmall");
        Guild big = guildLedBy(b, "RankBig");
        port().donate(a, 1_000L, UUID.randomUUID().toString());
        port().donate(b, 15_000L, UUID.randomUUID().toString());

        List<GuildRank> ranks = port().rankGuilds();

        List<Long> order = ranks.stream().map(GuildRank::guildId).toList();
        assertThat(order.indexOf(big.id())).isLessThan(order.indexOf(small.id()));
        GuildRank top = ranks.get(order.indexOf(big.id()));
        assertThat(top.memberIds()).containsExactly(b);
        assertThat(top.level()).isEqualTo(2);
    }
}
