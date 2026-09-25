package dev.kage.server.database.guild;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Duyệt đơn xin gia nhập ĐÚNG LÚC leader giải tán bang, đo bằng PostgreSQL THẬT.
 *
 * <p>Lớp riêng (không nhét vào {@link GuildIntegrationTest}) để giữ lớp kia dưới trần độ phức tạp
 * PMD tính cho cả lớp.
 */
@Testcontainers
class GuildConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int APPLICANTS = 4;
    private static final int ROUNDS = 15;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    // volatile: @BeforeAll ghi ở luồng JUnit, test đồng thời đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildracer"));
        accountId = accountHolder[0].id();
    }

    /**
     * Bản cũ khoá CHÉO: duyệt khoá dòng {@code guild_join_request} (FOR UPDATE) rồi INSERT
     * {@code guild_member} — lần kiểm FK của INSERT đó cần khoá dòng {@code guild}. Giải tán thì
     * NGƯỢC lại: DELETE dòng {@code guild} trước, rồi CASCADE sang dòng đơn đang bị duyệt khoá.
     * Postgres phát hiện deadlock và huỷ ngẫu nhiên một bên — người bấm "Duyệt" hoặc "Giải tán"
     * nhận lỗi máy chủ (cùng họ lỗi thứ tự khoá của party, 2026-09-23).
     *
     * <p>Kiểm GIÁ TRỊ CUỐI: không lời gọi nào ném lỗi, bang đã biến mất, và không còn một dòng
     * thành viên/đơn nào của nó.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldNeverDeadlock_whenLeaderDisbandsWhileApprovingRequests() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(APPLICANTS + 1);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                runDisbandVersusApprovals(executor, round);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runDisbandVersusApprovals(ExecutorService executor, int round) throws InterruptedException {
        PostgresGuildAdapter adapter = new PostgresGuildAdapter(dataSource);
        long leader = createFreshCharacter("GDis" + round + "L");
        Guild guild = adapter.createGuild("DisbandRace" + round, leader);
        List<GuildJoinRequest> requests = new ArrayList<GuildJoinRequest>();
        for (int i = 0; i < APPLICANTS; i++) {
            long applicant = createFreshCharacter("GDis" + round + "A" + i);
            requests.add(adapter.sendJoinRequest(guild.id(), applicant));
        }

        CyclicBarrier barrier = new CyclicBarrier(APPLICANTS + 1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (GuildJoinRequest request : requests) {
            futures.add(executor.submit(() -> {
                barrier.await(30, TimeUnit.SECONDS);
                return adapter.approveJoinRequest(request.id(), leader);
            }));
        }
        futures.add(executor.submit(() -> {
            barrier.await(30, TimeUnit.SECONDS);
            return adapter.disbandGuild(leader);
        }));

        List<String> failures = new ArrayList<String>();
        for (Future<?> future : futures) {
            try {
                Object result = future.get(60, TimeUnit.SECONDS);
                if (result instanceof GuildPort.ApproveResult approveResult) {
                    assertThat(approveResult).isIn(GuildPort.ApproveResult.OK, GuildPort.ApproveResult.NOT_FOUND);
                }
            } catch (ExecutionException e) {
                failures.add(describeChain(e.getCause()));
            } catch (TimeoutException e) {
                failures.add(describeChain(e));
            }
        }

        assertThat(failures)
                .as("vòng %d: không lời gọi nào được ném lỗi", round)
                .isEmpty();
        assertThat(adapter.findById(guild.id()))
                .as("vòng %d: bang phải đã giải tán", round)
                .isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM guild_member WHERE guild_id = ?", guild.id()))
                .as("vòng %d: không còn thành viên nào của bang đã giải tán", round)
                .isZero();
        assertThat(countRows("SELECT COUNT(*) FROM guild_join_request WHERE guild_id = ?", guild.id()))
                .as("vòng %d: không còn đơn nào của bang đã giải tán", round)
                .isZero();
    }

    /** Cả chuỗi nguyên nhân — {@code DatabaseAccessException} bọc mất câu "deadlock detected" của Postgres. */
    private static String describeChain(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            text.append(current.getClass().getSimpleName())
                    .append(": ")
                    .append(current.getMessage())
                    .append(" <- ");
        }
        return text.toString();
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

    private static int countRows(String sql, long guildId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đếm dòng: " + sql, e);
        }
    }
}
