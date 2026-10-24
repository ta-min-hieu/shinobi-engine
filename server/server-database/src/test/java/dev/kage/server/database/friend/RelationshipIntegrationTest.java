package dev.kage.server.database.friend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import dev.kage.friend.domain.BlockOutcome;
import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** Lô 21 phần 1 trên PostgreSQL THẬT (V85) — chặn (ADR-091), sư đồ (ADR-093), đạo lữ (ADR-094). */
@Testcontainers
class RelationshipIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int MIN_INTIMACY = 600;
    private static final long COST = 5_000L;

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
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "relationtester"));
        accountId = holder[0].id();
    }

    private static long character(String name) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }

    private static void befriend(long first, long second, int intimacy) {
        JdbcOperations.update(dataSource, """
                INSERT INTO friendship (requester_id, addressee_id, status, created_at, responded_at, intimacy)
                VALUES (?, ?, 'ACCEPTED', now(), now(), ?)
                """, first, second, intimacy);
    }

    private static void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }

    private static long friendshipRows(long first, long second) {
        return JdbcOperations.queryOne(dataSource, """
                        SELECT COUNT(*) FROM friendship
                         WHERE (requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?)
                        """, rs -> rs.getLong(1), first, second, second, first)
                .orElseThrow();
    }

    @Test
    void blockShouldCutFriendshipAndDaoLu_andBlockBothWays() {
        long a = character("BlockA1");
        long b = character("BlockB1");
        befriend(a, b, MIN_INTIMACY);
        setGold(a, COST);
        PostgresDaoLuAdapter daoLu = new PostgresDaoLuAdapter(dataSource);
        assertThat(daoLu.bind(a, b, MIN_INTIMACY, COST)).isEqualTo(DaoLuOutcome.OK);

        PostgresBlockAdapter blocks = new PostgresBlockAdapter(dataSource);
        assertThat(blocks.block(b, a, 50)).isEqualTo(BlockOutcome.OK);
        assertThat(blocks.block(b, a, 50)).isEqualTo(BlockOutcome.ALREADY_BLOCKED);
        assertThat(blocks.eitherBlocks(a, b)).isTrue();
        assertThat(blocks.eitherBlocks(b, a)).isTrue();
        assertThat(friendshipRows(a, b)).isZero();
        assertThat(daoLu.partnerOf(a)).isEmpty();
        assertThat(blocks.blockedBy(b)).containsExactly(a);

        assertThat(blocks.unblock(b, a)).isEqualTo(BlockOutcome.OK);
        assertThat(blocks.unblock(b, a)).isEqualTo(BlockOutcome.NOT_BLOCKED);
        assertThat(blocks.eitherBlocks(a, b)).isFalse();
    }

    @Test
    void blockShouldStopAtTheCap() {
        long blocker = character("BlockCap0");
        PostgresBlockAdapter blocks = new PostgresBlockAdapter(dataSource);
        assertThat(blocks.block(blocker, character("BlockCap1"), 2)).isEqualTo(BlockOutcome.OK);
        assertThat(blocks.block(blocker, character("BlockCap2"), 2)).isEqualTo(BlockOutcome.OK);
        assertThat(blocks.block(blocker, character("BlockCap3"), 2)).isEqualTo(BlockOutcome.LIST_FULL);
    }

    @Test
    void mentorshipShouldCapDisciplesAndPayEachMilestoneOnce() {
        long mentor = character("MentorM1");
        long first = character("MentorD1");
        long second = character("MentorD2");
        PostgresMentorshipAdapter mentorship = new PostgresMentorshipAdapter(dataSource);

        assertThat(mentorship.bind(mentor, first, 1)).isEqualTo(MentorshipOutcome.OK);
        assertThat(mentorship.bind(mentor, second, 1)).isEqualTo(MentorshipOutcome.MENTOR_FULL);
        assertThat(mentorship.bind(character("MentorM2"), first, 1)).isEqualTo(MentorshipOutcome.ALREADY_HAS_MENTOR);
        assertThat(mentorship.mentorOf(first)).contains(mentor);
        assertThat(mentorship.disciplesOf(mentor)).containsExactly(first);

        mentorship.markMilestonesPassed(first, mentor, List.of(10));
        assertThat(mentorship.claimMilestone(first, mentor, 10)).isFalse();
        assertThat(mentorship.claimMilestone(first, mentor, 20)).isTrue();
        assertThat(mentorship.claimMilestone(first, mentor, 20)).isFalse();

        mentorship.graduate(first);
        assertThat(mentorship.mentorOf(first)).isEmpty();
        assertThat(mentorship.unbind(mentor, first)).isFalse();
    }

    @Test
    void daoLuShouldChargeTheProposerAndRefuseSecondBondsAndPoorProposers() {
        long a = character("DaoLuA1");
        long b = character("DaoLuB1");
        long c = character("DaoLuC1");
        befriend(a, b, MIN_INTIMACY);
        befriend(c, b, MIN_INTIMACY);
        befriend(a, c, MIN_INTIMACY - 1);
        setGold(a, COST + 7);
        setGold(c, COST);
        PostgresDaoLuAdapter daoLu = new PostgresDaoLuAdapter(dataSource);

        assertThat(daoLu.bind(a, c, MIN_INTIMACY, COST)).isEqualTo(DaoLuOutcome.INTIMACY_TOO_LOW);
        assertThat(daoLu.bind(a, b, MIN_INTIMACY, COST)).isEqualTo(DaoLuOutcome.OK);
        assertThat(gold(a)).isEqualTo(7L);
        assertThat(daoLu.partnerOf(b)).contains(a);
        assertThat(daoLu.bind(c, b, MIN_INTIMACY, COST)).isEqualTo(DaoLuOutcome.ALREADY_BOUND);
        assertThat(gold(c)).isEqualTo(COST); // rollback — không mất vàng

        assertThat(daoLu.dissolve(b)).contains(a);
        assertThat(daoLu.partnerOf(a)).isEmpty();
        setGold(c, COST - 1);
        assertThat(daoLu.bind(c, b, MIN_INTIMACY, COST)).isEqualTo(DaoLuOutcome.INSUFFICIENT_GOLD);
        assertThat(daoLu.partnerOf(b)).isEmpty();
    }
}
