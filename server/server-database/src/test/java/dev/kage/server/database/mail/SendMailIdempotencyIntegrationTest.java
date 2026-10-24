package dev.kage.server.database.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
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
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.Postage;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * ADR-058 trên PostgreSQL THẬT: gửi lại CÙNG khoá ⇒ {@code DUPLICATE_REQUEST}, vàng ký quỹ chỉ bị trừ
 * MỘT lần và chỉ MỘT lá thư được tạo; khoá rỗng (client cũ) vẫn gửi được như trước.
 */
@Testcontainers
class SendMailIdempotencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final long START_GOLD = 1_000L;
    private static final long GOLD_SENT = 100L;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ?";
    private static final String GOLD = "SELECT gold FROM character_stat WHERE character_id = ?";
    private static final String MAIL_COUNT = "SELECT COUNT(*) FROM mail WHERE recipient_id = ?";

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
                .inTransaction(tx -> holder.set(new PostgresAccountAdapter().findOrCreate(tx, "mailretrier")));
        accountId = holder.get().id();
    }

    @Test
    void shouldSendOnce_whenTheSameKeyArrivesTwice() {
        MailFacade mail = wireMailFacade();
        long sender = newCharacter();
        long recipient = newCharacter();
        String key = UUID.randomUUID().toString();

        MailFacade.SendOutcome first = mail.sendMail(request(sender, recipient), key);
        MailFacade.SendOutcome retry = mail.sendMail(request(sender, recipient), key);

        assertThat(first.result()).isEqualTo(MailFacade.SendResult.OK);
        assertThat(retry.result()).isEqualTo(MailFacade.SendResult.DUPLICATE_REQUEST);
        assertThat(queryLong(GOLD, sender))
                .as("vàng ký quỹ (kèm phí thư) trừ đúng một lần")
                .isEqualTo(START_GOLD - GOLD_SENT - Postage.feeFor(GOLD_SENT, 0));
        assertThat(queryLong(MAIL_COUNT, recipient)).isEqualTo(1L);
    }

    @Test
    void shouldStillSend_whenTheClientSendsNoKey() {
        MailFacade mail = wireMailFacade();
        long sender = newCharacter();
        long recipient = newCharacter();

        assertThat(mail.sendMail(request(sender, recipient), "").result()).isEqualTo(MailFacade.SendResult.OK);
        assertThat(mail.sendMail(request(sender, recipient), "").result()).isEqualTo(MailFacade.SendResult.OK);
        assertThat(queryLong(MAIL_COUNT, recipient))
                .as("không khoá = hai lá như trước")
                .isEqualTo(2L);
    }

    private static MailFacade.SendMailRequest request(long sender, long recipient) {
        return new MailFacade.SendMailRequest(sender, recipient, "Gửi sư đệ", "Linh thạch đây.", GOLD_SENT, List.of());
    }

    /** Lắp y hệt {@code ServerDatabaseBootstrap#wireMailFacade}. */
    private static MailFacade wireMailFacade() {
        PostgresMailRepositoryAdapter repositoryPort = new PostgresMailRepositoryAdapter(dataSource);
        JdbcMailTransactionRunner tx = new JdbcMailTransactionRunner(dataSource);
        MailPorts ports = new MailPorts(
                repositoryPort,
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
        return new MailFacade(
                new SendMailUseCase(ports, tx),
                new ListMailUseCase(repositoryPort),
                new ClaimMailUseCase(ports, tx),
                new DeleteMailUseCase(repositoryPort));
    }

    private static long newCharacter() {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "MailKey" + NAME_COUNTER.incrementAndGet(),
                Faction.HOA,
                UUID.randomUUID().toString());
        AtomicReference<Character> holder = new AtomicReference<>();
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(t -> holder.set(characterAdapter.create(t, newCharacter)));
        long id = holder.get().id();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SET_GOLD)) {
            statement.setLong(1, START_GOLD);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đặt vàng", e);
        }
        return id;
    }

    private static long queryLong(String sql, long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
