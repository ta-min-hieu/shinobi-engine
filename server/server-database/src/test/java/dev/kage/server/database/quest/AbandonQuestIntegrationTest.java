package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Bỏ nhiệm vụ (ADR-054) trên PostgreSQL THẬT, qua đúng {@link QuestFacade} gateway gọi: chỉ quest
 * {@code IN_PROGRESS} bỏ được, bỏ xong nhận lại được từ đầu, và có dòng audit {@code QUEST_ABANDON}.
 */
@Testcontainers
class AbandonQuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";
    private static final String QUEST_MONSTER = "training_dummy";
    private static final int KILLS_TO_COMPLETE = 3;
    private static final String PROGRESS_JSON = "SELECT p.progress_json FROM character_quest_progress p"
            + " JOIN quest_template t ON t.id = p.quest_template_id WHERE p.character_id = ? AND t.code = ?";
    private static final String STATUS = "SELECT p.status FROM character_quest_progress p"
            + " JOIN quest_template t ON t.id = p.quest_template_id WHERE p.character_id = ? AND t.code = ?";
    private static final String AUDIT_DETAIL =
            "SELECT detail_json FROM audit_log WHERE character_id = ? AND action = ?";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static volatile long accountId;
    static final AtomicInteger NAME_COUNTER = new AtomicInteger();

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        AtomicReference<Account> holder = new AtomicReference<>();
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder.set(new PostgresAccountAdapter().findOrCreate(tx, "questabandoner")));
        accountId = holder.get().id();
    }

    @Test
    void shouldForgetTheProgressAndAllowReaccepting_afterAbandoning() {
        QuestFacade facade = wireQuestFacade();
        long characterId = newCharacter();
        assertThat(facade.acceptQuest(characterId, QUEST_CODE)).isEqualTo(QuestFacade.AcceptResult.OK);
        facade.onMonsterKilled(characterId, QUEST_MONSTER);
        assertThat(progressJson(characterId)).contains("{\"killed\":1}");

        assertThat(facade.abandonQuest(characterId, QUEST_CODE)).isEqualTo(QuestFacade.AbandonResult.OK);

        assertThat(progressJson(characterId)).as("bỏ = xoá dòng tiến độ").isEmpty();
        assertThat(auditDetail(characterId, "QUEST_ABANDON")).hasValueSatisfying(detail -> assertThat(detail)
                .contains("\"questCode\":\"kill_training_dummies\"")
                .contains("\\\"killed\\\":1"));
        assertThat(facade.acceptQuest(characterId, QUEST_CODE))
                .as("nhận lại được ngay")
                .isEqualTo(QuestFacade.AcceptResult.OK);
        assertThat(progressJson(characterId)).contains("{\"killed\":0}");
    }

    @Test
    void shouldRefuseToAbandon_aCompletedQuestWaitingForItsReward() {
        QuestFacade facade = wireQuestFacade();
        long characterId = newCharacter();
        facade.acceptQuest(characterId, QUEST_CODE);
        for (int i = 0; i < KILLS_TO_COMPLETE; i++) {
            facade.onMonsterKilled(characterId, QUEST_MONSTER);
        }

        assertThat(facade.abandonQuest(characterId, QUEST_CODE)).isEqualTo(QuestFacade.AbandonResult.NOT_IN_PROGRESS);
        assertThat(status(characterId)).contains("COMPLETED");
    }

    @Test
    void shouldReportWhyNothingWasAbandoned() {
        QuestFacade facade = wireQuestFacade();
        long characterId = newCharacter();

        assertThat(facade.abandonQuest(characterId, QUEST_CODE)).isEqualTo(QuestFacade.AbandonResult.NOT_IN_PROGRESS);
        assertThat(facade.abandonQuest(characterId, "no_such_quest"))
                .isEqualTo(QuestFacade.AbandonResult.QUEST_NOT_FOUND);
        assertThat(auditDetail(characterId, "QUEST_ABANDON")).isEmpty();
    }

    /** Lắp y hệt {@code ServerDatabaseBootstrap#wireQuestFacade}. */
    private static QuestFacade wireQuestFacade() {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        PostgresQuestAuditAdapter auditPort = new PostgresQuestAuditAdapter();
        JdbcQuestTransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                auditPort,
                new PostgresQuestIdempotencyAdapter());
        return new QuestFacade(
                new AcceptQuestUseCase(progressPort, templatePort, tx),
                new ProcessQuestEventUseCase(progressPort, new PostgresQuestItemTakeAdapter(), auditPort, tx),
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx),
                new dev.kage.quest.api.QuestLogUseCases(
                        new ListQuestsUseCase(progressPort),
                        new AbandonQuestUseCase(progressPort, templatePort, auditPort, tx)));
    }

    private static long newCharacter() {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "Abandon" + NAME_COUNTER.incrementAndGet(),
                Faction.HOA,
                UUID.randomUUID().toString());
        AtomicReference<Character> holder = new AtomicReference<>();
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder.set(characterAdapter.create(tx, newCharacter)));
        return holder.get().id();
    }

    private static Optional<String> progressJson(long characterId) {
        return queryString(PROGRESS_JSON, characterId, QUEST_CODE);
    }

    private static Optional<String> status(long characterId) {
        return queryString(STATUS, characterId, QUEST_CODE);
    }

    private static Optional<String> auditDetail(long characterId, String action) {
        return queryString(AUDIT_DETAIL, characterId, action);
    }

    private static Optional<String> queryString(String sql, long characterId, String text) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, text);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
