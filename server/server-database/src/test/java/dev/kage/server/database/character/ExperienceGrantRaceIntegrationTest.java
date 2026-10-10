package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.ExperienceCurve;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;

/**
 * Hai nguồn EXP của MỘT nhân vật chạy đồng thời trên PostgreSQL thật: hạ quái
 * ({@link GrantExperienceUseCase#execute}) và thưởng quest ({@link PostgresQuestExpAdapter} trong
 * transaction của quest, rồi {@link GrantExperienceUseCase#syncAfterExternalGrant} như gateway gọi).
 *
 * <p>Đo GIÁ TRỊ CUỐI, không đo "có chặn không": tổng EXP phải bằng đúng tổng đã cấp, cột
 * {@code level} phải khớp cấp suy từ {@code exp}, và mốc cấp DUY NHẤT bị vượt trong mỗi vòng phải
 * được báo {@code leveledUp} ĐÚNG MỘT LẦN — đó là thứ client dùng để hiện "lên cấp".
 */
@Testcontainers
class ExperienceGrantRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int KILL_GRANTS = 8;
    private static final int QUEST_GRANTS = 8;
    private static final long EXP_PER_GRANT = 5L;
    // 60 + 16 × 5 = 140: vượt ĐÚNG một mốc (100 = lên cấp 2), còn cách mốc kế (300) rất xa.
    private static final long SEED_EXP = 60L;
    private static final long EXPECTED_FINAL_EXP = SEED_EXP + (KILL_GRANTS + QUEST_GRANTS) * EXP_PER_GRANT;
    private static final int ROUNDS = 10;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

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
    }

    @Test
    void shouldKeepEveryGrantAndAnnounceTheLevelUpOnce_whenKillAndQuestGrantsRace() throws Exception {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        GrantExperienceUseCase useCase = new GrantExperienceUseCase(adapter);
        List<Long> finalExp = new ArrayList<>();
        List<Integer> finalStoredLevel = new ArrayList<>();
        List<Integer> announcements = new ArrayList<>();
        int grants = KILL_GRANTS + QUEST_GRANTS;
        ExecutorService executor = Executors.newFixedThreadPool(grants);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long characterId = createCharacter(adapter, round);
                adapter.addExperience(characterId, SEED_EXP);
                CyclicBarrier barrier = new CyclicBarrier(grants);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < KILL_GRANTS; i++) {
                    futures.add(executor.submit(() -> killGrant(useCase, barrier, characterId)));
                    futures.add(executor.submit(() -> questGrant(useCase, barrier, characterId)));
                }
                int announced = 0;
                for (Future<Boolean> future : futures) {
                    if (future.get(1, TimeUnit.MINUTES)) {
                        announced++;
                    }
                }
                Character after = adapter.findById(characterId).orElseThrow();
                finalExp.add(after.exp());
                finalStoredLevel.add(after.level());
                announcements.add(announced);
            }
        } finally {
            executor.shutdownNow();
        }

        int expectedLevel = ExperienceCurve.progressOf(EXPECTED_FINAL_EXP).level();
        assertThat(finalExp).as("tổng EXP cuối mỗi vòng").containsOnly(EXPECTED_FINAL_EXP);
        assertThat(finalStoredLevel).as("cột level cuối mỗi vòng").containsOnly(expectedLevel);
        assertThat(announcements).as("số lần báo leveledUp mỗi vòng").containsOnly(1);
    }

    private static boolean killGrant(GrantExperienceUseCase useCase, CyclicBarrier barrier, long characterId)
            throws Exception {
        barrier.await(1, TimeUnit.MINUTES);
        return useCase.execute(characterId, EXP_PER_GRANT).leveledUp();
    }

    // Đúng hai bước của đường lĩnh thưởng quest: cộng EXP trong transaction của quest, commit, rồi
    // gateway mới gọi đồng bộ vạch EXP.
    private static boolean questGrant(GrantExperienceUseCase useCase, CyclicBarrier barrier, long characterId)
            throws Exception {
        PostgresQuestExpAdapter questExp = new PostgresQuestExpAdapter();
        JdbcQuestTransactionRunner questTx = new JdbcQuestTransactionRunner(dataSource);
        barrier.await(1, TimeUnit.MINUTES);
        questTx.inTransaction(tx -> questExp.grantExp(tx, characterId, EXP_PER_GRANT));
        return useCase.syncAfterExternalGrant(characterId).leveledUp();
    }

    private static long createCharacter(PostgresCharacterAdapter adapter, int round) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] account = new Account[1];
        accountTx.inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, "exprace" + round));
        NewCharacter newCharacter = new NewCharacter(
                account[0].id(),
                "ExpRace" + round,
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] character = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> character[0] = adapter.create(tx, newCharacter));
        return character[0].id();
    }
}
