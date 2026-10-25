package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
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
import dev.kage.guild.domain.GuildAuctionPort;
import dev.kage.guild.domain.GuildAuctionRules;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-112 trên PostgreSQL THẬT (V92) — mở lô, giữ vàng, hoàn tiền người bị vượt, chốt đúng một lần, chia tiền. */
@Testcontainers
class GuildAuctionIntegrationTest {

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
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "auctiontester"));
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
    void shouldHoldGoldRefundOutbidAndSettleOnceWithShares() {
        PostgresGuildAuctionAdapter adapter = new PostgresGuildAuctionAdapter(dataSource, new SplittableRandom(1));
        long killer = character("AucKiller", 0L);
        long rich = character("AucRich", 10_000L);
        long rival = character("AucRival", 10_000L);
        long outsider = character("AucOut", 10_000L);
        long guildId = guild("AucGuild", killer);
        join(guildId, rich, "MEMBER");
        join(guildId, rival, "MEMBER");
        guild("AucOther", outsider);

        GuildAuctionPort.Lot lot = adapter.createLot(
                        new GuildAuctionPort.NewLot(guildId, killer, List.of(killer, rich, rival), NOW))
                .orElseThrow();
        assertThat(lot.participants()).isEqualTo(3);
        assertThat(adapter.openLotsOf(rich, NOW))
                .extracting(GuildAuctionPort.Lot::id)
                .containsExactly(lot.id());
        assertThat(adapter.openLotsOf(outsider, NOW)).isEmpty();

        assertThat(adapter.bid(outsider, lot.id(), 500L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.NOT_FOUND);
        assertThat(adapter.bid(rich, lot.id(), 99L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.TOO_LOW);
        assertThat(adapter.bid(rich, lot.id(), 100L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.OK);
        assertThat(adapter.bid(rich, lot.id(), 500L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.ALREADY_LEADING);
        assertThat(adapter.bid(rival, lot.id(), 109L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.TOO_LOW);
        assertThat(adapter.bid(rival, lot.id(), 301L, NOW)).isEqualTo(GuildAuctionPort.BidOutcome.OK);
        assertThat(gold(rich)).isEqualTo(9_900L);
        assertThat(gold(rival)).isEqualTo(9_699L);
        List<Long> mine = List.of(killer, rich, rival);
        assertThat(adapter.pendingPayouts())
                .filteredOn(p -> mine.contains(p.characterId()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.characterId()).isEqualTo(rich);
                    assertThat(p.kind()).isEqualTo(GuildAuctionPort.PayoutKind.REFUND);
                    assertThat(p.gold()).isEqualTo(100L);
                });

        assertSettledOnceWithShares(adapter, lot, mine, rival);
    }

    private static void assertSettledOnceWithShares(
            PostgresGuildAuctionAdapter adapter, GuildAuctionPort.Lot lot, List<Long> mine, long rival) {
        long rich = mine.get(1);
        Instant late = NOW.plus(GuildAuctionRules.DURATION);
        assertThat(adapter.bid(rich, lot.id(), 1_000L, late)).isEqualTo(GuildAuctionPort.BidOutcome.CLOSED);
        assertThat(adapter.settleDue(late)).isPositive();
        assertThat(adapter.settleDue(late)).isZero();
        List<GuildAuctionPort.Payout> payouts = adapter.pendingPayouts().stream()
                .filter(p -> mine.contains(p.characterId()))
                .toList();
        assertThat(payouts)
                .filteredOn(p -> p.kind() == GuildAuctionPort.PayoutKind.ITEM)
                .singleElement()
                .satisfies(p -> assertThat(p.characterId()).isEqualTo(rival));
        assertThat(payouts)
                .filteredOn(p -> p.kind() == GuildAuctionPort.PayoutKind.SHARE)
                .extracting(GuildAuctionPort.Payout::gold)
                .containsExactly(100L, 100L, 100L);
        payouts.forEach(p -> adapter.markPaid(p.id()));
        assertThat(adapter.pendingPayouts()).noneMatch(p -> mine.contains(p.characterId()));
    }

    @Test
    void unsoldLotShouldGoToTheKiller() {
        PostgresGuildAuctionAdapter adapter = new PostgresGuildAuctionAdapter(dataSource, new SplittableRandom(2));
        long killer = character("AucSolo", 0L);
        long guildId = guild("AucSoloG", killer);
        assertThat(adapter.createLot(new GuildAuctionPort.NewLot(guildId, killer, List.of(killer), NOW)))
                .isPresent();

        adapter.settleDue(NOW.plus(GuildAuctionRules.DURATION));
        assertThat(adapter.pendingPayouts())
                .filteredOn(p -> p.characterId() == killer)
                .singleElement()
                .satisfies(p -> assertThat(p.kind()).isEqualTo(GuildAuctionPort.PayoutKind.ITEM));
    }
}
