package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V11 (quest_template/
 * character_quest_progress) + toàn bộ vòng đời quest THẬT bằng script sản phẩm
 * (kill_training_dummies.js, seed V11): nhận → giết 3 lần → hoàn thành → nhận thưởng, atomically.
 */
@Testcontainers
class QuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrate() {
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        var accountAdapter = new PostgresAccountAdapter();
        var accountTx = new JdbcAccountTransactionRunner(dataSource);
        var accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "questtester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        var characterAdapter = new PostgresCharacterAdapter(dataSource);
        var characterTx = new JdbcCharacterTransactionRunner(dataSource);
        var newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        var holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static long readExp(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT exp FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp: characterId=" + characterId, e);
        }
    }

    private static int countInventoryRows(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm inventory_item: characterId=" + characterId, e);
        }
    }

    private static AcceptQuestUseCase newAcceptUseCase() {
        var progressPort = new PostgresQuestProgressAdapter(dataSource);
        var templatePort = new PostgresQuestTemplateAdapter(dataSource);
        var tx = new JdbcQuestTransactionRunner(dataSource);
        return new AcceptQuestUseCase(progressPort, templatePort, tx);
    }

    private static ProcessMonsterKilledUseCase newProcessUseCase() {
        var progressPort = new PostgresQuestProgressAdapter(dataSource);
        var templatePort = new PostgresQuestTemplateAdapter(dataSource);
        var tx = new JdbcQuestTransactionRunner(dataSource);
        return new ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
    }

    private static ClaimQuestRewardUseCase newClaimUseCase() {
        var progressPort = new PostgresQuestProgressAdapter(dataSource);
        var templatePort = new PostgresQuestTemplateAdapter(dataSource);
        var rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(), new PostgresQuestInventoryGrantAdapter(dataSource),
                new PostgresQuestAuditAdapter());
        var tx = new JdbcQuestTransactionRunner(dataSource);
        return new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
    }

    @Test
    void shouldPlayFullQuestLifecycle_withRealScript() {
        long characterId = createFreshCharacter("QuestPlayer1");

        var acceptResult = newAcceptUseCase().execute(characterId, QUEST_CODE);
        assertThat(acceptResult).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Kịch bản thật (kill_training_dummies.js) cần giết 3 lần mới hoàn thành.
        var afterFirst = newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterFirst).isEmpty();
        var afterSecond = newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterSecond).isEmpty();
        var afterThird = newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterThird).isPresent();
        assertThat(afterThird.get().questCode()).isEqualTo(QUEST_CODE);
        assertThat(afterThird.get().status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int itemsBefore = countInventoryRows(characterId);

        var claimResult = newClaimUseCase().execute(characterId, QUEST_CODE);
        assertThat(claimResult.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claimResult.expGranted()).isEqualTo(500L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 500L);
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsBefore + 1); // 5 kunai trong 1 dòng mới

        // Không nhận thưởng lặp lại được (status đã CLAIMED).
        var secondClaim = newClaimUseCase().execute(characterId, QUEST_CODE);
        assertThat(secondClaim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
    }

    @Test
    void shouldRejectDoubleAccept() {
        long characterId = createFreshCharacter("QuestPlayer2");
        newAcceptUseCase().execute(characterId, QUEST_CODE);

        var result = newAcceptUseCase().execute(characterId, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
    }

    @Test
    void shouldRejectClaim_beforeAccepting() {
        long characterId = createFreshCharacter("QuestPlayer3");

        var result = newClaimUseCase().execute(characterId, QUEST_CODE);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_ACCEPTED);
    }
}
