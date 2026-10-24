package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
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
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Transaction;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailDraft;
import dev.kage.mail.domain.Postage;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.mail.JdbcMailTransactionRunner;
import dev.kage.server.database.mail.PostgresMailAuditAdapter;
import dev.kage.server.database.mail.PostgresMailGoldAdapter;
import dev.kage.server.database.mail.PostgresMailIdempotencyAdapter;
import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.mail.PostgresMailRepositoryAdapter;
import dev.kage.server.database.shop.JdbcShopTransactionRunner;
import dev.kage.server.database.shop.PostgresShopAuditAdapter;
import dev.kage.server.database.shop.PostgresShopCatalogAdapter;
import dev.kage.server.database.shop.PostgresShopGoldAdapter;
import dev.kage.server.database.shop.PostgresShopIdempotencyAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.application.ShopPorts;

/**
 * "Mặc đồ" chạy ĐÚNG lúc món đó đang rời túi qua đường của feature KHÁC (giao dịch đổi chủ, bán
 * cửa hàng xoá dòng). Cả hai đường kia khoá dòng {@code FOR UPDATE} rồi mới kiểm "đang mặc không"
 * — nhưng {@code EquipItemUseCase} cũ đọc túi đồ NGOÀI giao dịch, không khoá gì, nên nó xác thực
 * món đồ TRƯỚC khi đường kia xong rồi ghi ô trang bị SAU khi đường kia đã commit.
 *
 * <p>Xen kẽ được ép TẤT ĐỊNH, không cần móc vào code production: kết nối {@code holder} đóng vai
 * giao dịch/cửa hàng, giữ khoá {@code FOR UPDATE} trên dòng; lệnh mặc đồ chạy ở luồng khác; test
 * chờ tới khi Postgres báo có một khoá ĐANG CHỜ (lệnh mặc đồ đã đụng vào dòng) rồi mới cho
 * {@code holder} đổi chủ / xoá dòng và commit. Đo GIÁ TRỊ CUỐI: ô vũ khí trỏ vào đâu.
 */
@Testcontainers
class EquipWhileItemLeavesIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    // Đủ cao để không bao giờ vướng required_level (ADR-036 D4) — chủ đề test là khoá, không phải cấp.
    private static final int ANY_LEVEL = 100;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long swordTemplateId;
    static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "equipracetester"));
        accountId = accountHolder[0].id();
        swordTemplateId = queryLong("SELECT id FROM item_template WHERE code = ?", "wooden_sword");
    }

    @AfterAll
    static void stopExecutor() {
        EXECUTOR.shutdownNow();
    }

    /**
     * Giao dịch đổi chủ thanh kiếm sang B đúng lúc A bấm "mặc". Bản cũ: lệnh mặc đã xác thực "kiếm
     * của A" từ trước, rồi ghi ô vũ khí của A trỏ vào dòng giờ là của B — A được cộng chỉ số của
     * thanh kiếm trong World, B cũng mặc được: một thanh kiếm, hai người cùng hưởng.
     */
    @Test
    void shouldNotEquip_whenATradeHandsTheItemToSomeoneElseMidEquip() throws Exception {
        long owner = createFreshCharacter("EqRcA");
        long receiver = createFreshCharacter("EqRcB");
        long sword = insertSword(owner);

        Optional<EquipItemUseCase.EquipItemResult> result = equipWhileAnotherTransactionHolds(
                owner, sword, "UPDATE inventory_item SET character_id = ? WHERE id = ?", receiver, sword);

        assertThat(queryLong("SELECT character_id FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(receiver);
        assertThat(equippedWeaponOf(owner)).isEmpty();
        assertThat(result).contains(EquipItemUseCase.EquipItemResult.ITEM_NOT_FOUND);
    }

    /**
     * Bán thanh kiếm cho cửa hàng (xoá dòng) đúng lúc A bấm "mặc". FK {@code ON DELETE SET NULL}
     * không để ô trỏ vào dòng đã xoá — nhưng bản cũ ném lỗi vi phạm khoá ngoại ra tận gateway
     * ({@code error.server_internal}) thay vì trả lời sạch "không có món đó".
     */
    @Test
    void shouldAnswerItemNotFound_whenTheItemIsSoldMidEquip() throws Exception {
        long owner = createFreshCharacter("EqRcC");
        long sword = insertSword(owner);

        Optional<EquipItemUseCase.EquipItemResult> result =
                equipWhileAnotherTransactionHolds(owner, sword, "DELETE FROM inventory_item WHERE id = ?", sword);

        assertThat(result).contains(EquipItemUseCase.EquipItemResult.ITEM_NOT_FOUND);
        assertThat(equippedWeaponOf(owner)).isEmpty();
    }

    /**
     * Đối chứng: không ai tranh dòng thì mặc vẫn chạy như trước.
     */
    @Test
    void shouldEquip_whenNobodyElseTouchesTheItem() {
        long owner = createFreshCharacter("EqRcD");
        long sword = insertSword(owner);

        EquipItemUseCase.EquipItemResult result =
                newEquipItemUseCase().execute(owner, sword, EquipmentSlotType.WEAPON, ANY_LEVEL);

        assertThat(result).isEqualTo(EquipItemUseCase.EquipItemResult.OK);
        assertThat(equippedWeaponOf(owner)).contains(sword);
    }

    /**
     * Chiều NGƯỢC của hai ca trên: lệnh mặc tới TRƯỚC (đã khoá dòng, đã ghi ô vũ khí, chưa commit),
     * lệnh bán tới sau. Lệnh bán chờ khoá dòng {@code FOR UPDATE}, nhưng câu {@code EXISTS
     * (equipment_slot)} của nó đọc theo ảnh chụp lúc câu lệnh BẮT ĐẦU — Postgres chỉ đánh giá lại
     * dòng bị khoá, không đánh giá lại bảng khác — nên nó thấy "chưa mặc" và bán luôn. Kết quả cũ:
     * cả hai cùng báo OK, vàng vào túi, dòng bị xoá (ô vũ khí rơi về NULL) mà World vẫn cộng chỉ số
     * thanh kiếm tới lần đăng nhập sau — đúng cái lỗ "bán món đang mặc" đã vá 2026-09-24, mở lại qua
     * một cuộc đua.
     */
    @Test
    void shouldRefuseToSell_whenTheSaleLandsWhileTheItemIsBeingEquipped() throws Exception {
        long owner = createFreshCharacter("EqRcE");
        long sword = insertSword(owner);
        long goldBefore = goldOf(owner);

        RacedOutcomes<SellItemUseCase.SellResult> outcomes = equipThenRace(owner, sword, () -> sellOne(owner, sword));

        assertThat(outcomes.equip()).isEqualTo(EquipItemUseCase.EquipItemResult.OK);
        assertThat(outcomes.other()).isEqualTo(SellItemUseCase.SellResult.ITEM_EQUIPPED);
        assertThat(goldOf(owner)).as("vàng").isEqualTo(goldBefore);
        assertThat(queryLong("SELECT COUNT(*) FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(1L);
        assertThat(equippedWeaponOf(owner)).contains(sword);
    }

    /** Như ca bán hàng, qua đường gửi thư đính kèm (gom theo LOẠI, cùng câu {@code EXISTS}). */
    @Test
    void shouldRefuseToMail_whenTheSendLandsWhileTheOnlyCopyIsBeingEquipped() throws Exception {
        long owner = createFreshCharacter("EqRcF");
        long recipient = createFreshCharacter("EqRcG");
        long sword = insertSword(owner);

        RacedOutcomes<SendMailUseCase.SendOutcome> outcomes =
                equipThenRace(owner, sword, () -> mailOneSword(owner, recipient));

        assertThat(outcomes.equip()).isEqualTo(EquipItemUseCase.EquipItemResult.OK);
        assertThat(outcomes.other()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(queryLong("SELECT COUNT(*) FROM inventory_item WHERE id = ?", sword))
                .isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM mail WHERE recipient_id = ?", recipient))
                .isZero();
        assertThat(equippedWeaponOf(owner)).contains(sword);
    }

    /**
     * Mặc đồ bằng use case THẬT, bị giữ lại ngay sau khi ghi ô trang bị (còn trong giao dịch, chưa
     * commit); chạy {@code other}; chờ tới khi Postgres báo {@code other} đang CHỜ một khoá rồi mới
     * thả lệnh mặc cho commit. Không móc gì vào code production: chỗ giữ là một
     * {@link EquipmentPort} bọc ngoài adapter thật.
     */
    private static <T> RacedOutcomes<T> equipThenRace(long characterId, long itemId, Callable<T> other)
            throws Exception {
        CountDownLatch slotWritten = new CountDownLatch(1);
        CountDownLatch mayCommit = new CountDownLatch(1);
        EquipmentPort holdingPort =
                new HoldAfterSaveEquipmentPort(new PostgresEquipmentAdapter(dataSource), slotWritten, mayCommit);
        EquipItemUseCase equipUseCase = new EquipItemUseCase(
                new PostgresInventoryAdapter(dataSource),
                new PostgresItemTemplateAdapter(dataSource),
                holdingPort,
                new JdbcInventoryTransactionRunner(dataSource));
        Future<EquipItemUseCase.EquipItemResult> equip =
                EXECUTOR.submit(() -> equipUseCase.execute(characterId, itemId, EquipmentSlotType.WEAPON, ANY_LEVEL));
        try {
            assertThat(slotWritten.await(30, TimeUnit.SECONDS))
                    .as("lệnh mặc tới được bước ghi ô")
                    .isTrue();
            Future<T> otherFuture = EXECUTOR.submit(other);
            awaitSomeoneWaitingForALock();
            mayCommit.countDown();
            return new RacedOutcomes<T>(equip.get(30, TimeUnit.SECONDS), otherFuture.get(30, TimeUnit.SECONDS));
        } finally {
            mayCommit.countDown();
        }
    }

    private record RacedOutcomes<T>(EquipItemUseCase.EquipItemResult equip, T other) {}

    /** Ghi ô thật rồi đứng chờ — giao dịch mặc đồ vẫn mở, mọi khoá của nó vẫn giữ. */
    private static final class HoldAfterSaveEquipmentPort implements EquipmentPort {

        private final EquipmentPort delegate;
        private final CountDownLatch slotWritten;
        private final CountDownLatch mayCommit;

        HoldAfterSaveEquipmentPort(EquipmentPort delegate, CountDownLatch slotWritten, CountDownLatch mayCommit) {
            this.delegate = delegate;
            this.slotWritten = slotWritten;
            this.mayCommit = mayCommit;
        }

        @Override
        public Optional<Equipment> findByCharacter(long characterId) {
            return delegate.findByCharacter(characterId);
        }

        @Override
        public void save(Transaction tx, Equipment equipment) {
            delegate.save(tx, equipment);
            slotWritten.countDown();
            try {
                if (!mayCommit.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test không bao giờ thả lệnh mặc đồ");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Bị ngắt khi đang giữ lệnh mặc đồ", e);
            }
        }
    }

    private static SellItemUseCase.SellResult sellOne(long characterId, long itemId) {
        ShopPorts ports = new ShopPorts(
                new PostgresShopCatalogAdapter(dataSource),
                new PostgresShopGoldAdapter(),
                new PostgresShopInventoryGatewayAdapter(dataSource),
                new PostgresShopAuditAdapter(),
                new PostgresShopIdempotencyAdapter());
        return new SellItemUseCase(ports, new JdbcShopTransactionRunner(dataSource))
                .execute(characterId, itemId, 1, UUID.randomUUID().toString());
    }

    private static SendMailUseCase.SendOutcome mailOneSword(long senderId, long recipientId) {
        MailPorts ports = new MailPorts(
                new PostgresMailRepositoryAdapter(dataSource),
                new PostgresMailGoldAdapter(),
                new PostgresMailInventoryGatewayAdapter(),
                new PostgresMailAuditAdapter(),
                new PostgresMailIdempotencyAdapter());
        // Đủ phí thư (ADR-076) — ca này đo đường VẬT PHẨM, không được dừng sớm ở vàng.
        queryLong(
                "UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold", Postage.BASE_FEE, senderId);
        MailDraft draft = new MailDraft(
                senderId, recipientId, "sword", "", 0L, List.of(new MailAttachmentDraft(swordTemplateId, 1)));
        return new SendMailUseCase(ports, new JdbcMailTransactionRunner(dataSource))
                .execute(draft)
                .outcome();
    }

    private static long goldOf(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    /**
     * Rỗng nếu lệnh mặc NÉM (không trả lời được gì) — đúng thứ bản cũ làm ở ca bán hàng.
     */
    private static Optional<EquipItemUseCase.EquipItemResult> equipWhileAnotherTransactionHolds(
            long characterId, long itemId, String changeUnderLock, Object... params) throws Exception {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT id FROM inventory_item WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, itemId);
                lock.executeQuery().close();
            }
            Future<EquipItemUseCase.EquipItemResult> equip = EXECUTOR.submit(
                    () -> newEquipItemUseCase().execute(characterId, itemId, EquipmentSlotType.WEAPON, ANY_LEVEL));
            awaitSomeoneWaitingForALock();
            try (PreparedStatement change = holder.prepareStatement(changeUnderLock)) {
                for (int i = 0; i < params.length; i++) {
                    change.setObject(i + 1, params[i]);
                }
                change.executeUpdate();
            }
            holder.commit();
            try {
                return Optional.of(equip.get(30, TimeUnit.SECONDS));
            } catch (java.util.concurrent.ExecutionException e) {
                return Optional.empty();
            }
        }
    }

    // Postgres tự báo khi một phiên đang CHỜ khoá — tín hiệu "lệnh mặc đồ đã đụng tới dòng bị giữ",
    // không cần móc gì vào code production. Mỗi lớp test có container riêng nên không lẫn phiên khác.
    private static void awaitSomeoneWaitingForALock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (queryLong("SELECT COUNT(*) FROM pg_locks WHERE NOT granted") == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Lệnh mặc đồ không bao giờ chạm tới dòng đang bị giữ khoá");
            }
            Thread.sleep(10);
        }
    }

    private static EquipItemUseCase newEquipItemUseCase() {
        return new EquipItemUseCase(
                new PostgresInventoryAdapter(dataSource),
                new PostgresItemTemplateAdapter(dataSource),
                new PostgresEquipmentAdapter(dataSource),
                new JdbcInventoryTransactionRunner(dataSource));
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

    private static long insertSword(long characterId) {
        return queryLong("""
                INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
                VALUES (?, ?, 1, 0, 0) RETURNING id
                """, characterId, swordTemplateId);
    }

    private static Optional<Long> equippedWeaponOf(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT inventory_item_id FROM equipment_slot"
                        + " WHERE character_id = ? AND slot_type = 'WEAPON'")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long itemId = rs.getLong(1);
                return rs.wasNull() ? Optional.empty() : Optional.of(itemId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc ô vũ khí: characterId=" + characterId, e);
        }
    }

    private static long queryLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only truy vấn: " + sql, e);
        }
    }
}
