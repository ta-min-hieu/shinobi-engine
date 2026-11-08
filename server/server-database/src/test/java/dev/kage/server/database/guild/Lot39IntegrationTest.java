package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
import dev.kage.character.domain.PersonalRecordPort;
import dev.kage.guild.domain.GuildBuffPort;
import dev.kage.quest.application.PreviewQuestRewardUseCase;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Lô 39 trên Postgres thật: buff bang (ADR-239), kỷ lục cá nhân (ADR-240), bảng giàu có (ADR-241), xem trước thưởng
 * nhiệm vụ (ADR-242).
 */
@Testcontainers
final class Lot39IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot39tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void guildBuffChargesGuildExpOnceAndNeverDropsALevel() {
        long leader = createFreshCharacter("BfLeader");
        long member = createFreshCharacter("BfMember");
        long guildId = insertGuild("BuffBang", leader, 10_500L, 2);
        joinGuild(guildId, leader, "LEADER");
        joinGuild(guildId, member, "MEMBER");
        PostgresGuildBuffAdapter adapter = new PostgresGuildBuffAdapter(dataSource);
        Instant now = Instant.now();

        assertThat(adapter.activate(member, 100L, now.plusSeconds(3600), now).outcome())
                .isEqualTo(GuildBuffPort.Outcome.NOT_LEADER);
        assertThat(adapter.activate(leader, 1_000L, now.plusSeconds(3600), now).outcome())
                .isEqualTo(GuildBuffPort.Outcome.NOT_ENOUGH_EXP);
        GuildBuffPort.Activation ok = adapter.activate(leader, 400L, now.plusSeconds(3600), now);
        assertThat(ok.outcome()).isEqualTo(GuildBuffPort.Outcome.OK);
        assertThat(ok.guildExpLeft()).isEqualTo(10_100L);
        assertThat(adapter.activate(leader, 50L, now.plusSeconds(7200), now).outcome())
                .isEqualTo(GuildBuffPort.Outcome.ALREADY_ACTIVE);
        assertThat(adapter.buffUntilOf(member)).isPresent();
        assertThat(adapter.buffUntilOf(member + 999)).isEmpty();
        assertThat(single("SELECT exp FROM guild WHERE id = ?", guildId)).isEqualTo(10_100L);
    }

    @Test
    void personalRecordsOnlyGoUp() {
        long owner = createFreshCharacter("RcOwner");
        dev.kage.server.database.character.PostgresPersonalRecordAdapter adapter =
                new dev.kage.server.database.character.PostgresPersonalRecordAdapter(dataSource);
        Instant now = Instant.now();

        assertThat(adapter.raise(owner, PersonalRecordPort.Kind.HIGHEST_HIT, 50L, now))
                .isTrue();
        assertThat(adapter.raise(owner, PersonalRecordPort.Kind.HIGHEST_HIT, 40L, now))
                .isFalse();
        assertThat(adapter.raise(owner, PersonalRecordPort.Kind.HIGHEST_HIT, 70L, now))
                .isTrue();
        assertThat(adapter.raise(owner, PersonalRecordPort.Kind.LONGEST_KILL_STREAK, 3L, now))
                .isTrue();
        assertThat(adapter.recordsOf(owner))
                .containsEntry(PersonalRecordPort.Kind.HIGHEST_HIT, 70L)
                .containsEntry(PersonalRecordPort.Kind.LONGEST_KILL_STREAK, 3L);
    }

    @Test
    void ranksRichestFirst() {
        long poor = createFreshCharacter("WlPoor");
        long rich = createFreshCharacter("WlRich");
        dev.kage.server.database.JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = 9999999 WHERE character_id = ?", rich);
        dev.kage.server.database.JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = 0 WHERE character_id = ?", poor);
        java.util.Map<Long, Long> richest =
                new dev.kage.server.database.character.PostgresWealthRankingAdapter(dataSource).richest(3);

        assertThat(richest.keySet().iterator().next()).isEqualTo(rich);
        assertThat(richest).doesNotContainKey(poor);
    }

    @Test
    void previewsSeededQuestRewardWithoutGrantingAnything() {
        long owner = createFreshCharacter("QpOwner");
        PreviewQuestRewardUseCase useCase = new PreviewQuestRewardUseCase(
                new dev.kage.server.database.quest.PostgresQuestTemplateAdapter(dataSource),
                new dev.kage.server.database.quest.PostgresQuestProgressAdapter(dataSource));

        Optional<PreviewQuestRewardUseCase.Preview> preview = useCase.preview(owner, "kill_training_dummies");
        assertThat(preview).isPresent();
        assertThat(preview.get().available()).isTrue();
        assertThat(preview.get().exp()).isEqualTo(500L);
        assertThat(preview.get().items()).containsExactly(new PreviewQuestRewardUseCase.Preview.Item("kunai", 5));
        assertThat(useCase.preview(owner, "no_such_quest")).isEmpty();
        assertThat(single("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?", owner))
                .isZero();
    }

    private static long insertGuild(String name, long leader, long exp, int level) {
        return dev.kage.server.database.JdbcOperations.queryOne(
                        dataSource,
                        "INSERT INTO guild (name, leader_character_id, created_at, exp, level)"
                                + " VALUES (?, ?, now(), ?, ?) RETURNING id",
                        rs -> rs.getLong(1),
                        name,
                        leader,
                        exp,
                        level)
                .orElseThrow();
    }

    private static void joinGuild(long guildId, long characterId, String role) {
        dev.kage.server.database.JdbcOperations.update(
                dataSource,
                "INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, ?, now())",
                guildId,
                characterId,
                role);
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
