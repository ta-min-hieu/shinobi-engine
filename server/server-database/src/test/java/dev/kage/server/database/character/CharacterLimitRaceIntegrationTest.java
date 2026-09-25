package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.application.CharacterLimitExceededException;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Giới hạn 4 nhân vật/account dưới tải ĐỒNG THỜI, trên PostgreSQL thật.
 *
 * <p>Trước bản vá 2026-09-25, {@link CreateCharacterUseCase} đếm nhân vật NGOÀI transaction rồi
 * mới INSERT trong transaction, không có khoá chung: nhiều yêu cầu tạo nhân vật cùng lúc trên một
 * account đang có 3 nhân vật đều đọc được "3 &lt; 4" và cùng INSERT — account vượt giới hạn.
 *
 * <p>Đo GIÁ TRỊ CUỐI (số nhân vật thật trong DB), không đo "có chặn không" — xem bài học PvP
 * rating race: phép đo "có chặn" là phép đo yếu cho concurrency.
 */
@Testcontainers
class CharacterLimitRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int LIMIT = 4;
    private static final int EXISTING_CHARACTERS = 3;
    private static final int CONCURRENT_CREATES = 8;
    private static final int ROUNDS = 5;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
    void shouldNeverExceedLimit_whenCreatesRaceOnTheSameAccount() throws Exception {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        CreateCharacterUseCase useCase =
                new CreateCharacterUseCase(adapter, new JdbcCharacterTransactionRunner(dataSource));
        List<Integer> finalCounts = new ArrayList<>();
        List<Integer> successCounts = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CREATES);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long accountId = createAccount("limitrace" + round);
                for (int i = 0; i < EXISTING_CHARACTERS; i++) {
                    useCase.execute(
                            accountId,
                            "Seed" + round + "x" + i,
                            UUID.randomUUID().toString());
                }
                CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_CREATES);
                List<Future<Optional<RuntimeException>>> futures = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_CREATES; i++) {
                    String name = "Race" + round + "x" + i;
                    futures.add(executor.submit(() -> createAfterBarrier(useCase, barrier, accountId, name)));
                }
                int successes = 0;
                for (Future<Optional<RuntimeException>> future : futures) {
                    Optional<RuntimeException> failure = future.get(1, TimeUnit.MINUTES);
                    if (failure.isEmpty()) {
                        successes++;
                    } else {
                        // Bên thua phải nhận đúng lỗi nghiệp vụ, không phải lỗi DB chung.
                        assertThat(failure.get()).isInstanceOf(CharacterLimitExceededException.class);
                    }
                }
                finalCounts.add(adapter.countByAccount(accountId));
                successCounts.add(successes);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(finalCounts).as("số nhân vật cuối cùng mỗi vòng").containsOnly(LIMIT);
        assertThat(successCounts).as("số lượt tạo thành công mỗi vòng").containsOnly(LIMIT - EXISTING_CHARACTERS);
    }

    private static long createAccount(String username) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, username));
        return holder[0].id();
    }

    // Trả về exception của lượt tạo (rỗng = tạo thành công) để luồng test tổng hợp kết quả.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static Optional<RuntimeException> createAfterBarrier(
            CreateCharacterUseCase useCase, CyclicBarrier barrier, long accountId, String name) throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        barrier.await(1, TimeUnit.MINUTES);
        try {
            useCase.execute(accountId, name, idempotencyKey);
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(e);
        }
    }
}
