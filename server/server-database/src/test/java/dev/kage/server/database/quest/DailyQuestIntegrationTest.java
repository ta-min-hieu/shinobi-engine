package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import dev.kage.quest.api.QuestLogUseCases;
import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessQuestEventUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.DailyReset;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Nhiệm vụ ngày (ADR-055) trên PostgreSQL THẬT: lĩnh xong thì hôm đó không nhận lại được, qua 0h giờ
 * Việt Nam thì nhận lại được và nhật ký không còn hiện "đã nhận thưởng" của hôm qua; thưởng vàng
 * ({@code ctx.give_gold}) vào {@code character_stat.gold} trong cùng lượt lĩnh.
 *
 * <p>Lượt lĩnh ghi {@code updated_at} bằng giờ THẬT ({@code markClaimedIfCompleted}), nên đồng hồ của
 * test đặt tương đối so với bây giờ: "cùng ngày" = bây giờ, "hôm sau" = 0h ngày mai + 1 phút.
 */
@Testcontainers
class DailyQuestIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String DAILY = "daily_dummy_drill";
    private static final String MONSTER = "training_dummy";
    private static final int KILLS = 5;
    private static final long GOLD_REWARD = 30L;
    private static final String GOLD = "SELECT gold FROM character_stat WHERE character_id = ?";

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
                .inTransaction(tx -> holder.set(new PostgresAccountAdapter().findOrCreate(tx, "dailyquester")));
        accountId = holder.get().id();
    }

    @Test
    void shouldPayGold_andBlockReaccepting_untilTheNextVietnameseMidnight() {
        Instant now = Instant.now();
        Instant tomorrow = DailyReset.startOfDay(now).plus(Duration.ofDays(1)).plus(Duration.ofMinutes(1));
        QuestFacade today = wireQuestFacade(Clock.fixed(now, ZoneOffset.UTC));
        QuestFacade nextDay = wireQuestFacade(Clock.fixed(tomorrow, ZoneOffset.UTC));
        long characterId = newCharacter();
        long goldBefore = gold(characterId);

        assertThat(today.acceptQuest(characterId, DAILY)).isEqualTo(QuestFacade.AcceptResult.OK);
        for (int i = 0; i < KILLS; i++) {
            today.onMonsterKilled(characterId, MONSTER);
        }
        QuestFacade.ClaimResult claim =
                today.claimReward(characterId, DAILY, UUID.randomUUID().toString());

        assertThat(claim.outcome()).isEqualTo(QuestFacade.ClaimOutcome.OK);
        assertThat(claim.goldGranted()).isEqualTo(GOLD_REWARD);
        assertThat(gold(characterId)).isEqualTo(goldBefore + GOLD_REWARD);
        assertThat(today.acceptQuest(characterId, DAILY))
                .as("cùng ngày: đã lĩnh thì chưa nhận lại được")
                .isEqualTo(QuestFacade.AcceptResult.ALREADY_ACCEPTED);
        assertThat(statusIn(today, characterId)).isEqualTo(QuestStatus.CLAIMED);

        assertThat(statusIn(nextDay, characterId))
                .as("qua 0h: nhật ký không còn dòng đã lĩnh hôm qua")
                .isNull();
        assertThat(nextDay.acceptQuest(characterId, DAILY)).isEqualTo(QuestFacade.AcceptResult.OK);
        assertThat(statusIn(nextDay, characterId)).isEqualTo(QuestStatus.IN_PROGRESS);
    }

    @Test
    void shouldNeverResetAOnceOnlyQuest_evenOnTheNextDay() {
        Instant tomorrow =
                DailyReset.startOfDay(Instant.now()).plus(Duration.ofDays(1)).plus(Duration.ofMinutes(1));
        QuestFacade today = wireQuestFacade(Clock.systemUTC());
        QuestFacade nextDay = wireQuestFacade(Clock.fixed(tomorrow, ZoneOffset.UTC));
        long characterId = newCharacter();
        today.acceptQuest(characterId, "kill_training_dummies");
        for (int i = 0; i < 3; i++) {
            today.onMonsterKilled(characterId, MONSTER);
        }
        today.claimReward(
                characterId, "kill_training_dummies", UUID.randomUUID().toString());

        assertThat(nextDay.acceptQuest(characterId, "kill_training_dummies"))
                .isEqualTo(QuestFacade.AcceptResult.ALREADY_ACCEPTED);
    }

    private static QuestStatus statusIn(QuestFacade facade, long characterId) {
        return facade.listQuests(characterId).stream()
                .filter(entry -> entry.questCode().equals(DAILY))
                .map(QuestLogEntry::status)
                .findFirst()
                .orElse(null);
    }

    /** Lắp y hệt {@code ServerDatabaseBootstrap#wireQuestFacade}, chỉ khác đồng hồ. */
    private static QuestFacade wireQuestFacade(Clock clock) {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        PostgresQuestAuditAdapter auditPort = new PostgresQuestAuditAdapter();
        JdbcQuestTransactionRunner tx = new JdbcQuestTransactionRunner(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                auditPort,
                new PostgresQuestIdempotencyAdapter(),
                new PostgresQuestGoldAdapter());
        return new QuestFacade(
                new AcceptQuestUseCase(progressPort, templatePort, tx, clock),
                new ProcessQuestEventUseCase(progressPort, new PostgresQuestItemTakeAdapter(), auditPort, tx),
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, tx),
                new QuestLogUseCases(
                        new ListQuestsUseCase(progressPort, clock),
                        new AbandonQuestUseCase(progressPort, templatePort, auditPort, tx)));
    }

    private static long newCharacter() {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "Daily" + NAME_COUNTER.incrementAndGet(),
                Faction.HOA,
                UUID.randomUUID().toString());
        AtomicReference<Character> holder = new AtomicReference<>();
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(t -> holder.set(characterAdapter.create(t, newCharacter)));
        return holder.get().id();
    }

    private static long gold(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(GOLD)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc vàng", e);
        }
    }
}
