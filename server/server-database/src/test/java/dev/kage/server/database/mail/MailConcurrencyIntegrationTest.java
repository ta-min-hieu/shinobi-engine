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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
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
import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.MailPorts;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.Mail;
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

/**
 * Hòm thư dưới tải ĐỒNG THỜI trên PostgreSQL thật — đo GIÁ TRỊ CUỐI (vàng, số vật phẩm, số thư),
 * không chỉ "có bị chặn không" (bài học PvP rating race: một phép đo "có khoá" báo xanh trong khi
 * số Elo cuối vẫn sai).
 *
 * <p>Bốn bất biến bảo toàn tài sản: nhận cùng một lá thư song song chỉ cộng MỘT lần; gửi song song
 * không tiêu được cùng một đồng vàng hay cùng một món hai lần; xoá song song với nhận không bao giờ
 * xoá mất phần thưởng chưa nhận.
 */
@Testcontainers
class MailConcurrencyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int THREADS = 6;
    private static final int RACE_ROUNDS = 8;
    private static final long TASK_TIMEOUT_SECONDS = 30L;
    private static final long MAIL_GOLD = 250L;
    private static final int MAIL_KUNAI = 3;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static volatile long accountId;
    static volatile long kunaiTemplateId;
    static volatile long woodenSwordTemplateId;
    static ExecutorService executor;
    static int nameCounter;

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
        Account[] accountHolder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "mailracetester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = queryLong("SELECT id FROM item_template WHERE code = 'kunai'");
        woodenSwordTemplateId = queryLong("SELECT id FROM item_template WHERE code = 'wooden_sword'");
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterAll
    static void shutdown() {
        executor.shutdownNow();
    }

    /** Nhiều request nhận CÙNG một lá thư (mỗi cái một khoá idempotency riêng) ⇒ cộng đúng một lần. */
    @Test
    void shouldCreditExactlyOnce_whenSameMailIsClaimedConcurrently() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long recipientId = newCharacter();
            Mail mail = sendSystemMail(recipientId);

            List<ClaimMailUseCase.ClaimOutcome> outcomes =
                    runConcurrently(() -> newClaimUseCase().execute(mail.id(), recipientId, newKey()));

            assertThat(outcomes)
                    .as("vòng %d: đúng một lần nhận thành công", round)
                    .filteredOn(outcome -> outcome == ClaimMailUseCase.ClaimOutcome.OK)
                    .hasSize(1);
            assertThat(outcomes)
                    .as("vòng %d", round)
                    .containsOnly(ClaimMailUseCase.ClaimOutcome.OK, ClaimMailUseCase.ClaimOutcome.ALREADY_CLAIMED);
            assertThat(readGold(recipientId)).as("vòng %d: vàng", round).isEqualTo(MAIL_GOLD);
            assertThat(readItemQuantity(recipientId, kunaiTemplateId))
                    .as("vòng %d: phi đao", round)
                    .isEqualTo(MAIL_KUNAI);
        }
    }

    /** Người gửi có đúng đủ vàng cho MỘT lá thư, bấm gửi nhiều lần cùng lúc ⇒ chỉ một lá đi được. */
    @Test
    void shouldNotSpendTheSameGoldTwice_whenSendingConcurrently() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long senderId = newCharacter();
            long recipientId = newCharacter();
            setGold(senderId, MAIL_GOLD + Postage.feeFor(MAIL_GOLD, 0));

            List<SendMailUseCase.SendResult> results = runConcurrently(() ->
                    newSendUseCase().execute(new MailDraft(senderId, recipientId, "Quà", "", MAIL_GOLD, List.of())));

            assertThat(results)
                    .as("vòng %d: đúng một lá thư được gửi", round)
                    .filteredOn(result -> result.outcome() == SendMailUseCase.SendOutcome.OK)
                    .hasSize(1);
            assertThat(readGold(senderId)).as("vòng %d: vàng người gửi", round).isZero();
            assertThat(sumMailGold(recipientId))
                    .as("vòng %d: tổng vàng trong hòm thư người nhận", round)
                    .isEqualTo(MAIL_GOLD);
        }
    }

    /** Một thanh kiếm duy nhất, đính kèm vào nhiều lá thư cùng lúc ⇒ chỉ một lá mang được nó. */
    @Test
    void shouldNotEscrowTheSameItemTwice_whenSendingConcurrently() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long senderId = newCharacter();
            long recipientId = newCharacter();
            giveSword(senderId);
            // Đủ phí thư cho MỌI lá (ADR-076) — chỉ thanh kiếm được phép là thứ thiếu.
            setGold(senderId, Postage.BASE_FEE * 100);

            List<SendMailUseCase.SendResult> results = runConcurrently(() -> newSendUseCase()
                    .execute(new MailDraft(
                            senderId,
                            recipientId,
                            "Kiếm",
                            "",
                            0,
                            List.of(new MailAttachmentDraft(woodenSwordTemplateId, 1)))));

            assertThat(results)
                    .as("vòng %d: đúng một lá thư được gửi", round)
                    .filteredOn(result -> result.outcome() == SendMailUseCase.SendOutcome.OK)
                    .hasSize(1);
            assertThat(readItemQuantity(senderId, woodenSwordTemplateId))
                    .as("vòng %d: kiếm còn trong túi", round)
                    .isZero();
            assertThat(sumMailItemQuantity(recipientId, woodenSwordTemplateId))
                    .as("vòng %d: kiếm nằm trong thư", round)
                    .isEqualTo(1);
        }
    }

    /**
     * Nhận và xoá bắn CÙNG LÚC. Bất biến: thư chỉ biến mất khi phần thưởng của nó ĐÃ vào túi — không
     * bao giờ "xoá xong, vàng chưa cộng".
     */
    @Test
    void shouldNeverLoseTheReward_whenClaimAndDeleteRace() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long recipientId = newCharacter();
            Mail mail = sendSystemMail(recipientId);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<ClaimMailUseCase.ClaimOutcome> claim = executor.submit(() -> {
                barrier.await();
                return newClaimUseCase().execute(mail.id(), recipientId, newKey());
            });
            Future<DeleteMailUseCase.Result> delete = executor.submit(() -> {
                barrier.await();
                return new DeleteMailUseCase(new PostgresMailRepositoryAdapter(dataSource))
                        .execute(mail.id(), recipientId);
            });
            ClaimMailUseCase.ClaimOutcome claimOutcome = claim.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            DeleteMailUseCase.Result deleteResult = delete.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(claimOutcome).as("vòng %d", round).isEqualTo(ClaimMailUseCase.ClaimOutcome.OK);
            assertThat(readGold(recipientId))
                    .as("vòng %d: vàng (%s)", round, deleteResult)
                    .isEqualTo(MAIL_GOLD);
            assertThat(readItemQuantity(recipientId, kunaiTemplateId))
                    .as("vòng %d: phi đao (%s)", round, deleteResult)
                    .isEqualTo(MAIL_KUNAI);
        }
    }

    // ---------------------------------------------------------------- dựng dữ liệu

    private static <T> List<T> runConcurrently(Callable<T> task) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<T>> futures = new ArrayList<Future<T>>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return task.call();
            }));
        }
        List<T> results = new ArrayList<T>();
        for (Future<T> future : futures) {
            results.add(future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return results;
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

    private static Mail sendSystemMail(long recipientId) {
        SendMailUseCase.SendResult result = newSendUseCase()
                .execute(new MailDraft(
                        null,
                        recipientId,
                        "Đền bù",
                        "",
                        MAIL_GOLD,
                        List.of(new MailAttachmentDraft(kunaiTemplateId, MAIL_KUNAI))));
        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        return result.mail().orElseThrow();
    }

    private static synchronized long newCharacter() {
        nameCounter++;
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "MailRace" + nameCounter,
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static void giveSword(long characterId) {
        queryLong(
                "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                        + " VALUES (?, ?, 1, 0, 0) RETURNING id",
                characterId,
                woodenSwordTemplateId);
    }

    // "SET gold" trong TEST không đụng GoldLedgerSourceGuardTest (nó chỉ quét src/main/java).
    private static void setGold(long characterId, long amount) {
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

    private static long readGold(long characterId) {
        return queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    private static long readItemQuantity(long characterId, long itemTemplateId) {
        return queryLong(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                itemTemplateId);
    }

    private static long sumMailGold(long recipientId) {
        return queryLong("SELECT COALESCE(SUM(gold_amount), 0) FROM mail WHERE recipient_id = ?", recipientId);
    }

    private static long sumMailItemQuantity(long recipientId, long itemTemplateId) {
        return queryLong(
                "SELECT COALESCE(SUM(mi.quantity), 0) FROM mail_item mi JOIN mail m ON m.id = mi.mail_id"
                        + " WHERE m.recipient_id = ? AND mi.item_template_id = ?",
                recipientId,
                itemTemplateId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static long queryLong(String sql, long... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setLong(i + 1, params[i]);
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
