package dev.kage.server.database.guild;

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
import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarOutcome;
import dev.kage.guild.domain.GuildWarPort;
import dev.kage.guild.domain.GuildWarRules;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-102 trên PostgreSQL THẬT (V88) — tuyên chiến, đếm lần hạ, chốt đúng một lần, hàng đợi thư. */
@Testcontainers
class GuildWarIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

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
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "guildwartester"));
        accountId = holder[0].id();
    }

    private static long character(String name, long gold) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, holder[0].id());
        return holder[0].id();
    }

    private static long guild(String name, long leaderId) {
        long guildId = JdbcOperations.insertReturningId(
                dataSource,
                "INSERT INTO guild (name, leader_character_id, created_at) VALUES (?, ?, now()) RETURNING id",
                name,
                leaderId);
        join(guildId, leaderId, "LEADER");
        return guildId;
    }

    private static void join(long guildId, long characterId, String role) {
        JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, ?, now())",
                guildId,
                characterId,
                role);
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }

    @Test
    void shouldDeclareCountKillsAndSettleExactlyOnce() {
        PostgresGuildWarAdapter adapter = new PostgresGuildWarAdapter(dataSource);
        long leaderA = character("WarLeadA", 50_000L);
        long memberA = character("WarMembA", 0L);
        long leaderB = character("WarLeadB", 0L);
        long guildA = guild("WarGuildA", leaderA);
        join(guildA, memberA, "MEMBER");
        guild("WarGuildB", leaderB);

        GuildWarPort.Declared declared = adapter.declare(new GuildWarPort.Declaration(leaderA, "WarGuildB", NOW));
        assertThat(declared.outcome()).isEqualTo(GuildWarOutcome.OK);
        assertThat(gold(leaderA)).isEqualTo(50_000L - GuildWarRules.DECLARE_COST);
        GuildWar war = declared.war().orElseThrow();
        assertThat(war.startsAt()).isEqualTo(NOW.plus(GuildWarRules.PREPARATION));
        assertThat(adapter.declare(new GuildWarPort.Declaration(leaderB, "WarGuildA", NOW))
                        .outcome())
                .isEqualTo(GuildWarOutcome.ALREADY_AT_WAR);

        Instant during = war.startsAt().plusSeconds(60);
        assertThat(adapter.recordKill(memberA, leaderB, NOW)).isFalse(); // chưa tới giờ đánh
        assertThat(adapter.recordKill(memberA, leaderB, during)).isTrue();
        assertThat(adapter.recordKill(leaderA, leaderB, during)).isTrue();
        assertThat(adapter.recordKill(leaderB, memberA, during)).isTrue();
        assertThat(adapter.recordKill(leaderA, memberA, during)).isFalse(); // cùng bang không tính
        assertThat(adapter.combatants(during))
                .extracting(GuildWarPort.Combatant::characterId)
                .containsExactlyInAnyOrder(leaderA, memberA, leaderB);
        assertThat(adapter.combatants(NOW)).isEmpty();

        assertThat(adapter.settle(war.id(), during)).isEmpty(); // chưa hết giờ
        GuildWar settled = adapter.settle(war.id(), war.endsAt()).orElseThrow();
        assertThat(settled.attacker().kills()).isEqualTo(2);
        assertThat(settled.defender().kills()).isEqualTo(1);
        assertThat(adapter.settle(war.id(), war.endsAt().plusSeconds(1))).isEmpty();
        List<GuildWarPort.Payout> payouts = adapter.pendingPayouts();
        assertThat(payouts).extracting(GuildWarPort.Payout::characterId).containsExactlyInAnyOrder(leaderA, memberA);
        payouts.forEach(adapter::markPaid);
        assertThat(adapter.pendingPayouts()).isEmpty();

        // Cặp vừa đánh xong: chờ 24 giờ mới tuyên lại.
        assertThat(adapter.declare(new GuildWarPort.Declaration(leaderA, "WarGuildB", war.endsAt()))
                        .outcome())
                .isEqualTo(GuildWarOutcome.COOLDOWN);
    }

    @Test
    void shouldRefuseNonLeadersUnknownGuildsAndOwnGuild() {
        PostgresGuildWarAdapter adapter = new PostgresGuildWarAdapter(dataSource);
        long leaderA = character("RuleLeadA", 50_000L);
        long memberA = character("RuleMembA", 0L);
        long leaderB = character("RuleLeadB", 0L);
        long guildA = guild("RuleGuildA", leaderA);
        join(guildA, memberA, "MEMBER");
        guild("RuleGuildB", leaderB);

        assertThat(adapter.declare(new GuildWarPort.Declaration(memberA, "RuleGuildB", NOW))
                        .outcome())
                .isEqualTo(GuildWarOutcome.NOT_LEADER);
        assertThat(adapter.declare(new GuildWarPort.Declaration(leaderA, "NoSuchGuild", NOW))
                        .outcome())
                .isEqualTo(GuildWarOutcome.UNKNOWN_GUILD);
        assertThat(adapter.declare(new GuildWarPort.Declaration(leaderA, "RuleGuildA", NOW))
                        .outcome())
                .isEqualTo(GuildWarOutcome.SAME_GUILD);
        assertThat(gold(leaderA)).isEqualTo(50_000L);
    }

    @Test
    void shouldRefuseWithoutEnoughGold_andChargeNothing() {
        PostgresGuildWarAdapter adapter = new PostgresGuildWarAdapter(dataSource);
        long poorLeader = character("PoorLead", 100L);
        long otherLeader = character("OtherLead", 0L);
        guild("PoorGuild", poorLeader);
        guild("OtherGuild", otherLeader);

        assertThat(adapter.declare(new GuildWarPort.Declaration(poorLeader, "OtherGuild", NOW))
                        .outcome())
                .isEqualTo(GuildWarOutcome.INSUFFICIENT_GOLD);
        assertThat(gold(poorLeader)).isEqualTo(100L);
        assertThat(adapter.unsettled())
                .noneMatch(war -> war.attacker().guildName().equals("PoorGuild"));
    }
}
