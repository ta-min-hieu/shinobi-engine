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
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailDraft;
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

    private static MailPorts newPorts() {
        return new MailPorts(
                new PostgresMailRepositoryAdapter(dataSource),
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
    }

    private static SendMailUseCase newSendUseCase() {
        return new SendMailUseCase(newPorts(), new JdbcMailTransactionRunner(dataSource));
    }

    private static ClaimMailUseCase newClaimUseCase() {
        return new ClaimMailUseCase(newPorts(), new JdbcMailTransactionRunner(dataSource));
    }

    /** Gửi thư HỆ THỐNG (senderId=null) — không bị ký quỹ, dùng cho các test chỉ quan tâm phía NHẬN. */
    private static Mail sendSystemMail(long recipientId, long goldAmount, List<MailAttachmentDraft> attachments) {
        SendMailUseCase.SendResult result =
                newSendUseCase().execute(new MailDraft(null, recipientId, "T", "B", goldAmount, attachments));
        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        return result.mail().orElseThrow();
    }

    // "SET gold" trong TEST không đụng GoldLedgerSourceGuardTest (nó chỉ quét src/main/java) —
    // đây là dựng dữ liệu ban đầu, không phải một đường đổi vàng của game.
    private static void grantGold(long characterId, long amount) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE character_stat SET gold = ? WHERE character_id = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only cấp gold: characterId=" + characterId, e);
        }
    }

    private static void persistOneItem(long characterId, long itemTemplateId, int quantity) {
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        dev.kage.inventory.domain.Inventory inventory = new dev.kage.inventory.domain.Inventory(
                characterId, 20, List.of(dev.kage.inventory.domain.ItemStack.newItem(itemTemplateId, quantity, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));
    }

    private static int readItemQuantity(long characterId, long itemTemplateId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COALESCE(SUM(quantity), 0) FROM inventory_item"
                                + " WHERE character_id = ? AND item_template_id = ?")) {
            statement.setLong(1, characterId);
            statement.setLong(2, itemTemplateId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc số lượng item: characterId=" + characterId, e);
        }
    }

    private static int countMailRows(long recipientId) {
        return new ListMailUseCase(new PostgresMailRepositoryAdapter(dataSource))
                .execute(recipientId)
                .size();
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldSendMail_withGoldAndItemAttachment() {
        long recipientId = createFreshCharacter("MailRecv1");

        Mail created = sendSystemMail(recipientId, 100, List.of(new MailAttachmentDraft(kunaiTemplateId, 5)));

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
        Mail mail = sendSystemMail(recipientId, 50, List.of(new MailAttachmentDraft(kunaiTemplateId, 2)));

        ClaimMailUseCase.ClaimOutcome result = newClaimUseCase().execute(mail.id(), recipientId, newIdempotencyKey());

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.OK);
        assertThat(readGold(recipientId)).isEqualTo(50);
        assertThat(countInventoryRows(recipientId)).isEqualTo(1);
    }

    @Test
    void shouldRejectDoubleClaim() {
        long recipientId = createFreshCharacter("MailRecv3");
        Mail mail = sendSystemMail(recipientId, 10, List.of());
        ClaimMailUseCase claimUseCase = newClaimUseCase();
        claimUseCase.execute(mail.id(), recipientId, newIdempotencyKey());

        ClaimMailUseCase.ClaimOutcome secondResult = claimUseCase.execute(mail.id(), recipientId, newIdempotencyKey());

        assertThat(secondResult).isEqualTo(ClaimMailUseCase.ClaimOutcome.ALREADY_CLAIMED);
        assertThat(readGold(recipientId)).isEqualTo(10); // không cộng lần 2
    }

    @Test
    void shouldRejectDuplicateClaim_whenIdempotencyKeyReused() {
        long recipientId = createFreshCharacter("MailRecv6");
        Mail mail = sendSystemMail(recipientId, 10, List.of());
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
        Mail mail = sendSystemMail(recipientId, 999, List.of(new MailAttachmentDraft(kunaiTemplateId, 1)));

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
        Mail mail = sendSystemMail(recipientId, 10, List.of());
        DeleteMailUseCase deleteUseCase = new DeleteMailUseCase(new PostgresMailRepositoryAdapter(dataSource));

        DeleteMailUseCase.Result beforeClaim = deleteUseCase.execute(mail.id(), recipientId);
        assertThat(beforeClaim).isEqualTo(DeleteMailUseCase.Result.MUST_CLAIM_FIRST);

        newClaimUseCase().execute(mail.id(), recipientId, newIdempotencyKey());
        DeleteMailUseCase.Result afterClaim = deleteUseCase.execute(mail.id(), recipientId);
        assertThat(afterClaim).isEqualTo(DeleteMailUseCase.Result.OK);
    }

    // ===== KÝ QUỸ lúc GỬI (2026-08-28) — vá lỗ đúc vàng/vật phẩm qua hòm thư =====
    // Trước đây gold_amount/attachments đi thẳng từ packet client vào bảng mail mà không trừ của
    // ai, còn ClaimMailUseCase thì cộng thật. Các test dưới đây chạy trên Postgres THẬT vì thứ
    // cần chứng minh là hành vi TRANSACTION (trừ + tạo thư nguyên tử), không phải logic thuần.

    private static MailDraft playerDraft(long senderId, long recipientId, long gold, List<MailAttachmentDraft> items) {
        return new MailDraft(senderId, recipientId, "Quà", "Gửi bạn", gold, items);
    }

    @Test
    void shouldDeductSenderGoldAndItems_whenSendingWithAttachments() {
        long senderId = createFreshCharacter("MailSend1");
        long recipientId = createFreshCharacter("MailRecvE1");
        grantGold(senderId, 1_000);
        persistOneItem(senderId, kunaiTemplateId, 5);

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 300, List.of(new MailAttachmentDraft(kunaiTemplateId, 2))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(readGold(senderId)).isEqualTo(700);
        assertThat(readItemQuantity(senderId, kunaiTemplateId)).isEqualTo(3);
    }

    /**
     * Test hồi quy CHÍNH của lỗ hổng: tự gửi thư cho chính mình rồi nhận, tổng vàng và tổng vật
     * phẩm không đổi. Trước bản vá, 500 vàng này sinh ra từ không khí mỗi lần lặp.
     */
    @Test
    void shouldNotMintGoldOrItems_whenSendingToSelfAndClaiming() {
        long characterId = createFreshCharacter("MailSelfMint");
        grantGold(characterId, 500);
        persistOneItem(characterId, kunaiTemplateId, 4);

        SendMailUseCase.SendResult sent = newSendUseCase()
                .execute(playerDraft(
                        characterId, characterId, 500, List.of(new MailAttachmentDraft(kunaiTemplateId, 4))));
        assertThat(sent.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(readGold(characterId)).isZero(); // đã bị ký quỹ vào thư
        assertThat(readItemQuantity(characterId, kunaiTemplateId)).isZero();

        ClaimMailUseCase.ClaimOutcome claimed =
                newClaimUseCase().execute(sent.mail().orElseThrow().id(), characterId, newIdempotencyKey());

        assertThat(claimed).isEqualTo(ClaimMailUseCase.ClaimOutcome.OK);
        assertThat(readGold(characterId)).isEqualTo(500); // ĐÚNG bằng lúc đầu, không hơn
        assertThat(readItemQuantity(characterId, kunaiTemplateId)).isEqualTo(4);
    }

    /** Hai người: vàng chỉ CHUYỂN chỗ, tổng của cả hai bên không đổi. */
    @Test
    void shouldConserveTotalGold_whenTransferringBetweenTwoCharacters() {
        long senderId = createFreshCharacter("MailConsA");
        long recipientId = createFreshCharacter("MailConsB");
        grantGold(senderId, 800);
        grantGold(recipientId, 200);
        long totalBefore = readGold(senderId) + readGold(recipientId);

        SendMailUseCase.SendResult sent = newSendUseCase().execute(playerDraft(senderId, recipientId, 800, List.of()));
        newClaimUseCase().execute(sent.mail().orElseThrow().id(), recipientId, newIdempotencyKey());

        assertThat(readGold(senderId)).isZero();
        assertThat(readGold(recipientId)).isEqualTo(1_000);
        assertThat(readGold(senderId) + readGold(recipientId)).isEqualTo(totalBefore);
    }

    @Test
    void shouldRejectSendAndCreateNoMail_whenSenderLacksGold() {
        long senderId = createFreshCharacter("MailPoor");
        long recipientId = createFreshCharacter("MailRecvE2");
        grantGold(senderId, 99);

        SendMailUseCase.SendResult result =
                newSendUseCase().execute(playerDraft(senderId, recipientId, 100, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INSUFFICIENT_GOLD);
        assertThat(readGold(senderId)).isEqualTo(99);
        assertThat(countMailRows(recipientId)).isZero();
    }

    @Test
    void shouldRejectSendAndCreateNoMail_whenSenderLacksItem() {
        long senderId = createFreshCharacter("MailNoItem");
        long recipientId = createFreshCharacter("MailRecvE3");
        grantGold(senderId, 1_000);
        persistOneItem(senderId, kunaiTemplateId, 1);

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 0, List.of(new MailAttachmentDraft(kunaiTemplateId, 2))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(readItemQuantity(senderId, kunaiTemplateId)).isEqualTo(1);
        assertThat(countMailRows(recipientId)).isZero();
    }

    /**
     * Vàng bị trừ TRƯỚC khi phát hiện thiếu món — nếu transaction không bao trọn cả hai, người gửi
     * mất tiền mà thư không tồn tại. Chỉ Postgres thật mới kiểm được điều này.
     */
    @Test
    void shouldRollbackDeductedGold_whenItemEscrowFailsInSameTransaction() {
        long senderId = createFreshCharacter("MailHalfWay");
        long recipientId = createFreshCharacter("MailRecvE4");
        grantGold(senderId, 1_000);

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 400, List.of(new MailAttachmentDraft(kunaiTemplateId, 1))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(readGold(senderId)).isEqualTo(1_000); // KHÔNG mất tiền cho một lá thư không tồn tại
        assertThat(countMailRows(recipientId)).isZero();
    }

    /** item_template_id không tồn tại: outcome tử tế, KHÔNG phải SQLException vì vi phạm khoá ngoại. */
    @Test
    void shouldRejectSend_whenItemTemplateDoesNotExist() {
        long senderId = createFreshCharacter("MailBadTpl");
        long recipientId = createFreshCharacter("MailRecvE5");

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 0, List.of(new MailAttachmentDraft(999_999_999L, 1))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(countMailRows(recipientId)).isZero();
    }

    /** Chuỗi dài hơn cột VARCHAR: trước đây rơi xuống SQLException và client không nhận được gì. */
    @Test
    void shouldRejectSend_whenSubjectExceedsColumnWidth() {
        long senderId = createFreshCharacter("MailLongSub");
        long recipientId = createFreshCharacter("MailRecvE6");
        String tooLong = "s".repeat(MailDraft.MAX_SUBJECT_LENGTH + 1);

        SendMailUseCase.SendResult result =
                newSendUseCase().execute(new MailDraft(senderId, recipientId, tooLong, "B", 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.SUBJECT_TOO_LONG);
        assertThat(countMailRows(recipientId)).isZero();
    }

    @Test
    void shouldRejectSend_whenBodyExceedsColumnWidth() {
        long senderId = createFreshCharacter("MailLongBod");
        long recipientId = createFreshCharacter("MailRecvE7");
        String tooLong = "b".repeat(MailDraft.MAX_BODY_LENGTH + 1);

        SendMailUseCase.SendResult result =
                newSendUseCase().execute(new MailDraft(senderId, recipientId, "S", tooLong, 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.BODY_TOO_LONG);
        assertThat(countMailRows(recipientId)).isZero();
    }

    @Test
    void shouldRejectSend_whenGoldAmountIsNegative() {
        long senderId = createFreshCharacter("MailNegGold");
        long recipientId = createFreshCharacter("MailRecvE8");
        grantGold(senderId, 100);

        SendMailUseCase.SendResult result =
                newSendUseCase().execute(playerDraft(senderId, recipientId, -50, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_GOLD_AMOUNT);
        // Số âm mà lọt xuống GoldLedger.deduct sẽ thành CỘNG tiền cho người gửi.
        assertThat(readGold(senderId)).isEqualTo(100);
        assertThat(countMailRows(recipientId)).isZero();
    }

    @Test
    void shouldRejectSend_whenAttachmentQuantityIsNotPositive() {
        long senderId = createFreshCharacter("MailZeroQty");
        long recipientId = createFreshCharacter("MailRecvE9");

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 0, List.of(new MailAttachmentDraft(kunaiTemplateId, 0))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_ATTACHMENT);
        assertThat(countMailRows(recipientId)).isZero();
    }

    /** Gom đủ số lượng từ NHIỀU stack cùng loại — đính kèm chỉ có template + số lượng, không có itemId. */
    @Test
    void shouldConsumeAcrossMultipleStacks_whenOneStackIsNotEnough() {
        long senderId = createFreshCharacter("MailStacks");
        long recipientId = createFreshCharacter("MailRecvE10");
        PostgresInventoryAdapter inventoryAdapter = new PostgresInventoryAdapter(dataSource);
        JdbcInventoryTransactionRunner inventoryTx = new JdbcInventoryTransactionRunner(dataSource);
        dev.kage.inventory.domain.Inventory inventory = new dev.kage.inventory.domain.Inventory(
                senderId,
                20,
                List.of(
                        dev.kage.inventory.domain.ItemStack.newItem(kunaiTemplateId, 2, 0),
                        dev.kage.inventory.domain.ItemStack.newItem(kunaiTemplateId, 3, 0)));
        inventoryTx.inTransaction(tx -> inventoryAdapter.save(tx, inventory));

        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 0, List.of(new MailAttachmentDraft(kunaiTemplateId, 4))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(readItemQuantity(senderId, kunaiTemplateId)).isEqualTo(1);
    }

    // ===== ADR-035 Decision-3 — người GỬI phải thấy vàng mình tụt xuống ngay =====

    // ArrayList thường, KHÔNG cần đồng bộ hoá: GoldLedger phát trên CHÍNH thread vừa chạy
    // transaction (ngay sau commit), mà ở test này đó là thread JUnit — một luồng ghi duy nhất.
    private final List<dev.kage.server.database.GoldChange> recordedGoldChanges =
            new ArrayList<dev.kage.server.database.GoldChange>();

    @org.junit.jupiter.api.BeforeEach
    void recordGoldChanges() {
        recordedGoldChanges.clear();
        dev.kage.server.database.GoldLedger.configureListener(recordedGoldChanges::add);
    }

    @org.junit.jupiter.api.AfterEach
    void stopRecordingGoldChanges() {
        // Listener là state TĨNH (cùng khuôn DbMetricsPort) — không gỡ ra thì test kế thừa lại nó.
        dev.kage.server.database.GoldLedger.configureListener(dev.kage.server.database.GoldChangeListener.NO_OP);
    }

    @Test
    void shouldPublishGoldChange_forSenderWhenSendCommits() {
        long senderId = createFreshCharacter("MailEvtSend");
        long recipientId = createFreshCharacter("MailEvtRecv");
        grantGold(senderId, 1_000);

        newSendUseCase().execute(playerDraft(senderId, recipientId, 250, List.of()));

        assertThat(recordedGoldChanges).hasSize(1);
        dev.kage.server.database.GoldChange change = recordedGoldChanges.get(0);
        assertThat(change.characterId()).isEqualTo(senderId);
        assertThat(change.gold()).isEqualTo(750L); // SỐ DƯ TUYỆT ĐỐI sau thay đổi
        assertThat(change.delta()).isEqualTo(-250L);
        assertThat(change.reasonKey()).isEqualTo("gold.reason.send_mail");
    }

    @Test
    void shouldPublishNothing_whenSendRollsBackAfterDeductingGold() {
        long senderId = createFreshCharacter("MailEvtRoll");
        long recipientId = createFreshCharacter("MailEvtRcv2");
        grantGold(senderId, 1_000);

        newSendUseCase()
                .execute(playerDraft(senderId, recipientId, 400, List.of(new MailAttachmentDraft(kunaiTemplateId, 1))));

        // Vàng ĐÃ bị trừ bên trong transaction rồi mới abort vì thiếu món — báo lúc đó sẽ gửi cho
        // client một số dư KHÔNG BAO GIỜ tồn tại.
        assertThat(recordedGoldChanges).isEmpty();
        assertThat(readGold(senderId)).isEqualTo(1_000);
    }
}
