package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
 * Nhiều SỰ KIỆN quest của cùng một nhân vật tới cùng lúc — đo bằng PostgreSQL THẬT, qua đúng
 * {@link QuestFacade} gateway gọi (2026-10-06, bắt được bởi {@code QuestStateMachinePropertyIntegrationTest}).
 *
 * <p>Gateway đẩy MỖI con quái chết sang một Virtual Thread riêng ({@code ConnectedCharacterRegistry
 * .lootExecutor}), nên một chiêu diện rộng hạ bốn con cùng nhịp là bốn lời gọi
 * {@code onMonsterKilled} chồng nhau. Mỗi lời gọi đọc tiến độ, chạy script, rồi ghi có điều kiện
 * (compare-and-set); thua thì đọc lại và chạy lại — nhưng chỉ ba lần. Kẻ về chót trong bốn kẻ cùng
 * xuất phát có thể thua đúng ba lần, và cú hạ đó biến mất khỏi tiến độ quest: đo thật, fuzz thấy
 * {@code felled=4} sau năm con.
 */
@Testcontainers
class QuestEventConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    // Quest có bộ đếm KHÔNG dừng ở mốc (còn chờ báo cáo), nên đếm được trọn số cú hạ.
    private static final String QUEST_CODE = "blood_sand_trail";
    private static final String QUEST_MONSTER = "blood_sand_acolyte";
    private static final int CONCURRENT_KILLS = 8;
    private static final int ROUNDS = 4;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresAccountAdapter().findOrCreate(tx, "questeventracer"));
        accountId = holder[0].id();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void shouldCountEveryKill_whenManyKillsOfTheSameCharacterLandTogether() throws Exception {
        QuestFacade facade = wireQuestFacade();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_KILLS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long characterId = createCharacterWithQuestInProgress("QRaceKill" + round);
                CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_KILLS);
                List<Future<?>> kills = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_KILLS; i++) {
                    kills.add(executor.submit(() -> {
                        barrier.await(30, TimeUnit.SECONDS);
                        return facade.onMonsterKilled(characterId, QUEST_MONSTER);
                    }));
                }
                for (Future<?> kill : kills) {
                    kill.get(60, TimeUnit.SECONDS);
                }
                assertThat(readProgressJson(characterId))
                        .as("tiến độ sau %d cú hạ cùng lúc (vòng %d)", CONCURRENT_KILLS, round)
                        .isEqualTo("{\"felled\":%d,\"reported\":false}".formatted(CONCURRENT_KILLS));
            }
        } finally {
            executor.shutdownNow();
        }
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

    /** Nhân vật mới, quest đang làm với tiến độ khởi điểm đúng như {@code on_accept} của script ghi. */
    private static long createCharacterWithQuestInProgress(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        long characterId = holder[0].id();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO character_quest_progress
                               (character_id, quest_template_id, status, progress_json, updated_at)
                        SELECT ?, id, 'IN_PROGRESS', '{"felled":0,"reported":false}', now()
                          FROM quest_template WHERE code = ?
                        """)) {
            statement.setLong(1, characterId);
            statement.setString(2, QUEST_CODE);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only gieo tiến độ quest", e);
        }
        return characterId;
    }

    private static String readProgressJson(long characterId) {
        String sql = "SELECT p.progress_json FROM character_quest_progress p"
                + " JOIN quest_template t ON t.id = p.quest_template_id"
                + " WHERE p.character_id = ? AND t.code = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            statement.setString(2, QUEST_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc tiến độ quest", e);
        }
    }
}
