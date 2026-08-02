package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.TransactionRunner;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "questtester"));
        accountId = accountHolder[0].id();
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

    private static long readExp(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT exp FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp: characterId=" + characterId, e);
        }
    }

    private static int countInventoryRows(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm inventory_item: characterId=" + characterId, e);
        }
    }

    private static AcceptQuestUseCase newAcceptUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new AcceptQuestUseCase(progressPort, templatePort, tx);
    }

    private static ProcessMonsterKilledUseCase newProcessUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ProcessMonsterKilledUseCase(progressPort, templatePort, tx);
    }

    private static ClaimQuestRewardUseCase newClaimUseCase() {
        QuestProgressPort progressPort = new PostgresQuestProgressAdapter(dataSource);
        QuestTemplatePort templatePort = new PostgresQuestTemplateAdapter(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter());
        TransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        return new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx);
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldPlayFullQuestLifecycle_withRealScript() {
        long characterId = createFreshCharacter("QuestPlayer1");

        AcceptQuestUseCase.AcceptResult acceptResult = newAcceptUseCase().execute(characterId, QUEST_CODE);
        assertThat(acceptResult).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // Kịch bản thật (kill_training_dummies.js) cần giết 3 lần mới hoàn thành.
        Optional<ProcessMonsterKilledUseCase.QuestStatusChange> afterFirst =
                newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterFirst).isEmpty();
        Optional<ProcessMonsterKilledUseCase.QuestStatusChange> afterSecond =
                newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterSecond).isEmpty();
        Optional<ProcessMonsterKilledUseCase.QuestStatusChange> afterThird =
                newProcessUseCase().execute(characterId, "training_dummy");
        assertThat(afterThird).isPresent();
        assertThat(afterThird.get().questCode()).isEqualTo(QUEST_CODE);
        assertThat(afterThird.get().status()).isEqualTo(QuestStatus.COMPLETED);

        long expBefore = readExp(characterId);
        int itemsBefore = countInventoryRows(characterId);

        ClaimQuestRewardUseCase.ClaimResult claimResult =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());
        assertThat(claimResult.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(claimResult.expGranted()).isEqualTo(500L);
        assertThat(readExp(characterId)).isEqualTo(expBefore + 500L);
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsBefore + 1); // 5 kunai trong 1 dòng mới

        // Không nhận thưởng lặp lại được (status đã CLAIMED).
        ClaimQuestRewardUseCase.ClaimResult secondClaim =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());
        assertThat(secondClaim.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
    }

    @Test
    void shouldListScriptRef_forStartupValidation() {
        // SCRIPTING.md §8 — validate lúc khởi động cần liệt kê ĐÚNG script_ref đang tham chiếu
        // thật trong DB (V11 seed), không phải danh sách tự khai trong code.
        List<String> scriptRefs = new PostgresQuestTemplateAdapter(dataSource).listAllScriptRefs();

        assertThat(scriptRefs).contains("/scripts/quest/kill_training_dummies.js");
    }

    @Test
    void shouldRejectDoubleAccept() {
        long characterId = createFreshCharacter("QuestPlayer2");
        newAcceptUseCase().execute(characterId, QUEST_CODE);

        AcceptQuestUseCase.AcceptResult result = newAcceptUseCase().execute(characterId, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
    }

    @Test
    void shouldRejectClaim_beforeAccepting() {
        long characterId = createFreshCharacter("QuestPlayer3");

        ClaimQuestRewardUseCase.ClaimResult result =
                newClaimUseCase().execute(characterId, QUEST_CODE, newIdempotencyKey());

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_ACCEPTED);
    }

    @Test
    void shouldRejectDuplicateClaim_whenIdempotencyKeyReused() {
        long characterId = createFreshCharacter("QuestPlayer4");
        newAcceptUseCase().execute(characterId, QUEST_CODE);
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        newProcessUseCase().execute(characterId, "training_dummy");
        String key = newIdempotencyKey();
        newClaimUseCase().execute(characterId, QUEST_CODE, key); // lần đầu — nhận thưởng thật

        long expAfterFirstClaim = readExp(characterId);
        int itemsAfterFirstClaim = countInventoryRows(characterId);
        ClaimQuestRewardUseCase.ClaimResult result = newClaimUseCase().execute(characterId, QUEST_CODE, key);

        // Trạng thái CLAIMED sau lần đầu đã tự chặn NOT_COMPLETED_YET rồi (business guard) — request
        // song song THẬT SỰ (2 request cùng đọc status COMPLETED trước khi request đầu commit) mới
        // chạm nhánh DUPLICATE_REQUEST của idempotencyKey; ở đây verify KHÔNG có phản ứng phụ nào
        // lặp lại dù nhánh nào được trả về (EXP/item không tăng lần 2 SO VỚI SAU lần nhận đầu tiên).
        assertThat(result.outcome())
                .isIn(
                        ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET,
                        ClaimQuestRewardUseCase.ClaimResult.Outcome.DUPLICATE_REQUEST);
        assertThat(readExp(characterId)).isEqualTo(expAfterFirstClaim); // KHÔNG cộng EXP lần 2
        assertThat(countInventoryRows(characterId)).isEqualTo(itemsAfterFirstClaim); // KHÔNG cấp item lần 2
    }
}
