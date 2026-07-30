package dev.kage.server.database.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.NewMail;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V13 ({@code mail}/{@code
 * mail_item}) + nhận thư atomically: cộng gold + cấp item + đánh dấu claimed trong CÙNG 1
 * transaction (DATABASE.md §5, feature-mail/CLAUDE.md).
 */
@Testcontainers
class MailIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long kunaiTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "mailtester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode("kunai");
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

    private static long findItemTemplateIdByCode(String code) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lookup item_template: code=" + code, e);
        }
    }

    private static long readGold(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT gold FROM character_stat WHERE character_id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc gold: characterId=" + characterId, e);
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

    /** Điền túi đồ tới đầy (DEFAULT_CAPACITY=20) — dùng để test nhánh {@code INVENTORY_FULL}. */
    private static void fillInventoryToCapacity(long characterId) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        ArrayList<dev.kage.inventory.domain.ItemStack> stacks = new ArrayList<dev.kage.inventory.domain.ItemStack>();
        for (int i = 0; i < 20; i++) {
            stacks.add(dev.kage.inventory.domain.ItemStack.newItem(kunaiTemplateId, 1, 0));
        }
        dev.kage.inventory.domain.Inventory inventory =
                new dev.kage.inventory.domain.Inventory(characterId, 20, List.copyOf(stacks));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
    }

    private static SendMailUseCase newSendUseCase() {
        return new SendMailUseCase(
                new PostgresMailRepositoryAdapter(dataSource), new JdbcMailTransactionRunner(dataSource));
    }

    private static ClaimMailUseCase newClaimUseCase() {
        MailPorts ports = new MailPorts(
                new PostgresMailRepositoryAdapter(dataSource),
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(dataSource),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
        return new ClaimMailUseCase(ports, new JdbcMailTransactionRunner(dataSource));
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldSendMail_withGoldAndItemAttachment() {
        long recipientId = createFreshCharacter("MailRecv1");

        NewMail newMail = new NewMail(
                null, recipientId, "Chào mừng", "Quà tân thủ", 100, List.of(new MailAttachment(kunaiTemplateId, 5)));
        Mail created = newSendUseCase().execute(newMail);

        List<Mail> listed = new ListMailUseCase(new PostgresMailRepositoryAdapter(dataSource)).execute(recipientId);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).id()).isEqualTo(created.id());
        assertThat(listed.get(0).goldAmount()).isEqualTo(100);
        assertThat(listed.get(0).attachments()).containsExactly(new MailAttachment(kunaiTemplateId, 5));
        assertThat(listed.get(0).isClaimed()).isFalse();
    }

    @Test
    void shouldClaimMail_creditGoldGrantItemAndMarkClaimed() {
        long recipientId = createFreshCharacter("MailRecv2");
        NewMail newMail = new NewMail(
                null, recipientId, "Thưởng", "Thưởng quest", 50, List.of(new MailAttachment(kunaiTemplateId, 2)));
        Mail mail = newSendUseCase().execute(newMail);

        ClaimMailUseCase.ClaimOutcome result = newClaimUseCase().execute(mail.id(), recipientId, newIdempotencyKey());

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.OK);
        assertThat(readGold(recipientId)).isEqualTo(50);
        assertThat(countInventoryRows(recipientId)).isEqualTo(1);
    }

    @Test
    void shouldRejectDoubleClaim() {
        long recipientId = createFreshCharacter("MailRecv3");
        Mail mail = newSendUseCase().execute(new NewMail(null, recipientId, "T", "B", 10, List.of()));
        ClaimMailUseCase claimUseCase = newClaimUseCase();
        claimUseCase.execute(mail.id(), recipientId, newIdempotencyKey());

        ClaimMailUseCase.ClaimOutcome secondResult = claimUseCase.execute(mail.id(), recipientId, newIdempotencyKey());

        assertThat(secondResult).isEqualTo(ClaimMailUseCase.ClaimOutcome.ALREADY_CLAIMED);
        assertThat(readGold(recipientId)).isEqualTo(10); // không cộng lần 2
    }

    @Test
    void shouldRejectDuplicateClaim_whenIdempotencyKeyReused() {
        long recipientId = createFreshCharacter("MailRecv6");
        Mail mail = newSendUseCase().execute(new NewMail(null, recipientId, "T", "B", 10, List.of()));
        String key = newIdempotencyKey();
        newClaimUseCase().execute(mail.id(), recipientId, key);

        ClaimMailUseCase.ClaimOutcome result = newClaimUseCase().execute(mail.id(), recipientId, key);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.DUPLICATE_REQUEST);
        assertThat(readGold(recipientId)).isEqualTo(10); // chỉ cộng 1 lần, KHÔNG lặp lại
    }

    @Test
    void shouldRollbackGold_whenClaimAndInventoryFull() {
        long recipientId = createFreshCharacter("MailRecv4");
        fillInventoryToCapacity(recipientId);
        NewMail newMail =
                new NewMail(null, recipientId, "T", "B", 999, List.of(new MailAttachment(kunaiTemplateId, 1)));
        Mail mail = newSendUseCase().execute(newMail);

        ClaimMailUseCase.ClaimOutcome result = newClaimUseCase().execute(mail.id(), recipientId, newIdempotencyKey());

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.INVENTORY_FULL);
        // KHÔNG được "nhận gold nhưng mất item" (DATABASE.md §5) — rollback toàn bộ.
        assertThat(readGold(recipientId)).isZero();
        assertThat(countInventoryRows(recipientId)).isEqualTo(20);
        List<Mail> stillUnclaimed =
                new ListMailUseCase(new PostgresMailRepositoryAdapter(dataSource)).execute(recipientId);
        assertThat(stillUnclaimed.get(0).isClaimed()).isFalse();
    }

    @Test
    void shouldRejectDeleteUnclaimedMailWithReward_thenAllowAfterClaim() {
        long recipientId = createFreshCharacter("MailRecv5");
        Mail mail = newSendUseCase().execute(new NewMail(null, recipientId, "T", "B", 10, List.of()));
        DeleteMailUseCase deleteUseCase = new DeleteMailUseCase(new PostgresMailRepositoryAdapter(dataSource));

        DeleteMailUseCase.Result beforeClaim = deleteUseCase.execute(mail.id(), recipientId);
        assertThat(beforeClaim).isEqualTo(DeleteMailUseCase.Result.MUST_CLAIM_FIRST);

        newClaimUseCase().execute(mail.id(), recipientId, newIdempotencyKey());
        DeleteMailUseCase.Result afterClaim = deleteUseCase.execute(mail.id(), recipientId);
        assertThat(afterClaim).isEqualTo(DeleteMailUseCase.Result.OK);
    }
}
