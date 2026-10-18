package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.character.domain.Character;
import dev.kage.checkin.application.ClaimDailyCheckInUseCase;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Điểm danh (ADR-060) qua đúng composition root, trên PostgreSQL THẬT: quà ngày 1 đi thư hệ thống, một lần
 * mỗi ngày, và hai lượt vào world cùng lúc vẫn chỉ ra MỘT lá thư (khoá ADR-058 + compare-and-set V62).
 */
@Testcontainers
@Timeout(240)
class DailyCheckInDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "checkin-redis-password";
    private static final String SYSTEM_MAILS = "SELECT COUNT(*) FROM mail WHERE recipient_id = ? AND sender_id IS NULL";
    private static final String DAY_ONE_GOLD =
            "SELECT COUNT(*) FROM mail WHERE recipient_id = ? AND sender_id IS NULL AND gold_amount = 100";
    private static final String LAST_DAY =
            "SELECT COALESCE(MAX(last_day), 0) FROM daily_check_in WHERE character_id = ?";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static GatewayFacades facades;

    @BeforeAll
    static void wire() {
        facades = GatewayBootstrap.wireFacades(new ServerDatabaseBootstrap(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP));
    }

    @Test
    void shouldMailDayOneOnce_perDay() {
        Character hero = newCharacter();

        ClaimDailyCheckInUseCase.Result first =
                facades.progression().checkInFacade().claimToday(hero.id());
        ClaimDailyCheckInUseCase.Result again =
                facades.progression().checkInFacade().claimToday(hero.id());

        assertThat(first).isInstanceOfSatisfying(ClaimDailyCheckInUseCase.Result.Claimed.class, claimed -> {
            assertThat(claimed.day()).isEqualTo(1);
            assertThat(claimed.mailId()).isPositive();
        });
        assertThat(again).isInstanceOf(ClaimDailyCheckInUseCase.Result.AlreadyClaimed.class);
        assertThat(count(DAY_ONE_GOLD, hero.id())).isEqualTo(1L);
        assertThat(count(LAST_DAY, hero.id())).isEqualTo(1L);
    }

    @Test
    void concurrentEntries_shouldStillProduceOneMail() throws Exception {
        Character hero = newCharacter();
        int racers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<ClaimDailyCheckInUseCase.Result>> calls = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                calls.add(() -> facades.progression().checkInFacade().claimToday(hero.id()));
            }
            long claimed = 0;
            for (Future<ClaimDailyCheckInUseCase.Result> result : pool.invokeAll(calls)) {
                if (result.get() instanceof ClaimDailyCheckInUseCase.Result.Claimed) {
                    claimed++;
                }
            }

            assertThat(claimed).isEqualTo(1L);
            assertThat(count(SYSTEM_MAILS, hero.id())).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Character newCharacter() {
        int n = NEXT.incrementAndGet();
        long accountId = facades.accountFacade().login("checkin" + n).accountId();
        return facades.characterFacade()
                .createCharacter(accountId, "CheckIn" + n, UUID.randomUUID().toString());
    }

    private static long count(String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
