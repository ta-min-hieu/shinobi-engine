package dev.kage.server.database.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
import dev.kage.mail.domain.SentMailPort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.mail.PostgresSentMailAdapter;
import dev.kage.shop.domain.PurchaseHistoryPort;

/** Lô 45 trên Postgres thật: lịch sử mua cửa hàng NPC (ADR-288), thư đã gửi (ADR-290), migration V172. */
@Testcontainers
final class Lot45IntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot45tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void readsShopPurchasesNewestFirst() throws SQLException {
        long buyer = createFreshCharacter("PhBuyer");
        execute(
                "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'SHOP_BUY', "
                        + "'{\"itemTemplateId\":1,\"quantity\":3,\"goldCost\":30}', now())",
                buyer);
        execute(
                "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'SHOP_BUY', "
                        + "'{\"itemTemplateId\":2,\"quantity\":1,\"goldCost\":50}', now())",
                buyer);
        execute(
                "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'SHOP_SELL', "
                        + "'{}', now())",
                buyer);

        List<PurchaseHistoryPort.Purchase> purchases = new PostgresPurchaseHistoryAdapter(dataSource).recent(buyer, 20);
        assertThat(purchases).hasSize(2);
        assertThat(purchases.getFirst().itemTemplateId()).isEqualTo(2L);
        assertThat(purchases.get(1).quantity()).isEqualTo(3);
        assertThat(purchases.get(1).goldCost()).isEqualTo(30L);
    }

    @Test
    void readsSentMailWithRecipientAndAttachments() throws SQLException {
        long sender = createFreshCharacter("SmSender");
        long recipient = createFreshCharacter("SmRecv");
        execute(
                "INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at) " + "VALUES (?, "
                        + recipient + ", 'chao', 'x', 15, now())",
                sender);
        execute(
                "INSERT INTO mail_item (mail_id, item_template_id, quantity) "
                        + "SELECT id, 1, 2 FROM mail WHERE sender_id = ?",
                sender);

        List<SentMailPort.Sent> sent = new PostgresSentMailAdapter(dataSource).recent(sender, 20);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().recipientName()).isEqualTo("SmRecv");
        assertThat(sent.getFirst().goldAmount()).isEqualTo(15L);
        assertThat(sent.getFirst().attachments()).isEqualTo(1);
        assertThat(sent.getFirst().claimed()).isFalse();
        assertThat(new PostgresSentMailAdapter(dataSource).recent(recipient, 20))
                .isEmpty();
    }

    private static void execute(String sql, long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
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
}
