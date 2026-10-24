package dev.kage.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.mail.domain.AuditPort;
import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.IdempotencyPort;
import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailDraft;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;

/**
 * Trọng tâm: gửi thư PHẢI trừ đúng thứ mình đính kèm. Trước 2026-08-28 use-case này chỉ ghi thư,
 * còn {@code ClaimMailUseCase} thì cấp thật — chênh lệch đó chính là lỗ đúc vàng/vật phẩm.
 */
class SendMailUseCaseTest {

    private static final long SENDER_ID = 7L;
    private static final long RECIPIENT_ID = 5L;
    private static final long KUNAI = 1L;

    private static MailDraft draft(long goldAmount, List<MailAttachmentDraft> attachments) {
        return new MailDraft(SENDER_ID, RECIPIENT_ID, "Chào", "Nội dung", goldAmount, attachments);
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    @Test
    void shouldCreateMail_andEscrowGoldAndItemsFromSender() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);
        fixture.inventoryGatewayPort.owned.put(KUNAI, 10);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(draft(100, List.of(new MailAttachmentDraft(KUNAI, 3))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(result.mail()).isPresent();
        assertThat(result.mail().get().goldAmount()).isEqualTo(100);
        assertThat(fixture.goldPort.balances.get(SENDER_ID))
                .as("100 đính kèm + phí thư 10 + 1 phần trăm của 100")
                .isEqualTo(389L);
        assertThat(fixture.inventoryGatewayPort.owned.get(KUNAI)).isEqualTo(7);
        assertThat(fixture.repositoryPort.stored).hasSize(1);
        assertThat(fixture.auditPort.loggedActions).containsExactly("MAIL_SEND");
    }

    /** ADR-076: thư chào hỏi rỗng không mất phí. */
    @Test
    void plainLetterShouldBeFree() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(500L);
    }

    /** ADR-076: chỉ đính vật phẩm vẫn mất phí nền. */
    @Test
    void itemOnlyMailShouldPayTheBaseFee() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);
        fixture.inventoryGatewayPort.owned.put(KUNAI, 10);

        fixture.useCase().execute(draft(0, List.of(new MailAttachmentDraft(KUNAI, 1))));

        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(490L);
    }

    /** ADR-076: đủ vàng đính kèm nhưng thiếu phí ⇒ từ chối, không đổi gì. */
    @Test
    void shouldRefuseAndChangeNothing_whenTheSenderCanAffordTheGoldButNotThePostage() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 105L);

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(100, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INSUFFICIENT_GOLD);
        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(105L);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    /** Cộng phí làm tràn long ⇒ không đủ vàng, không ném ArithmeticException hay trừ số âm. */
    @Test
    void shouldRefuse_whenAttachedGoldPlusPostageOverflows() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, Long.MAX_VALUE);

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(Long.MAX_VALUE, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INSUFFICIENT_GOLD);
        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(Long.MAX_VALUE);
    }

    /** Thư hệ thống không bao giờ mất phí. */
    @Test
    void systemMailShouldNotPayPostage() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(null, RECIPIENT_ID, "Thưởng", "Nội dung", 50, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(fixture.goldPort.balances).isEmpty();
    }

    /** Dòng 41(iv) (2026-10-17): hộp người nhận đủ 100 thư ⇒ người chơi khác không gửi thêm được. */
    @Test
    void shouldRefuseAndChangeNothing_whenTheRecipientsMailboxIsFull() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);
        for (int i = 0; i < SendMailUseCase.MAILBOX_CAPACITY; i++) {
            fixture.useCase().execute(draft(0, List.of()));
        }

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(100, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.MAILBOX_FULL);
        assertThat(fixture.goldPort.balances.get(SENDER_ID))
                .as("không trừ vàng")
                .isEqualTo(500L);
        assertThat(fixture.repositoryPort.stored).hasSize(SendMailUseCase.MAILBOX_CAPACITY);
    }

    @Test
    void systemMailShouldIgnoreTheMailboxCap_becauseItCarriesRewards() {
        Fixture fixture = fixture();
        for (int i = 0; i < SendMailUseCase.MAILBOX_CAPACITY; i++) {
            fixture.useCase().execute(draft(0, List.of()));
        }

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(null, RECIPIENT_ID, "Thưởng", "Nội dung", 50, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
    }

    @Test
    void shouldRejectAndChangeNothing_whenSenderCannotAffordAttachedGold() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 99L);

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(100, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INSUFFICIENT_GOLD);
        assertThat(result.mail()).isEmpty();
        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(99L);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    @Test
    void shouldRejectAndChangeNothing_whenSenderDoesNotOwnEnoughItems() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);
        fixture.inventoryGatewayPort.owned.put(KUNAI, 2);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(draft(0, List.of(new MailAttachmentDraft(KUNAI, 3))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(fixture.inventoryGatewayPort.owned.get(KUNAI)).isEqualTo(2);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    /** item_template_id không tồn tại = không ai sở hữu nó — cùng nhánh, không phải SQLException. */
    @Test
    void shouldRejectAttachment_whenItemTemplateDoesNotExist() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(draft(0, List.of(new MailAttachmentDraft(999_999L, 1))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    /**
     * Trừ vàng chạy TRƯỚC ký quỹ vật phẩm; nếu vật phẩm thiếu thì vàng đã trừ phải quay lại. Fake
     * transaction ở đây rollback thật (khôi phục ảnh chụp) nên nhánh này kiểm được, không phải chỉ
     * tin vào {@code JdbcTransactionRunner}.
     */
    @Test
    void shouldRollbackDeductedGold_whenItemEscrowFailsAfterwards() {
        Fixture fixture = fixture();
        fixture.goldPort.balances.put(SENDER_ID, 500L);
        fixture.inventoryGatewayPort.owned.put(KUNAI, 1);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(draft(100, List.of(new MailAttachmentDraft(KUNAI, 5))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.ATTACHMENT_NOT_OWNED);
        assertThat(fixture.goldPort.balances.get(SENDER_ID)).isEqualTo(500L);
        assertThat(fixture.repositoryPort.stored).isEmpty();
        assertThat(fixture.auditPort.loggedActions).isEmpty();
    }

    @Test
    void shouldRejectNegativeGoldAmount_withOutcomeNotException() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(-1, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_GOLD_AMOUNT);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveQuantity_withOutcomeNotException() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(draft(0, List.of(new MailAttachmentDraft(KUNAI, 0))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_ATTACHMENT);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveItemTemplateId_withOutcomeNotException() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(0, List.of(new MailAttachmentDraft(0, 1))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_ATTACHMENT);
    }

    @Test
    void shouldRejectSubjectLongerThanColumn() {
        Fixture fixture = fixture();
        String tooLong = "s".repeat(MailDraft.MAX_SUBJECT_LENGTH + 1);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(SENDER_ID, RECIPIENT_ID, tooLong, "b", 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.SUBJECT_TOO_LONG);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    @Test
    void shouldAcceptSubjectExactlyAtColumnWidth() {
        Fixture fixture = fixture();
        String exact = "s".repeat(MailDraft.MAX_SUBJECT_LENGTH);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(SENDER_ID, RECIPIENT_ID, exact, "b", 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
    }

    @Test
    void shouldRejectBodyLongerThanColumn() {
        Fixture fixture = fixture();
        String tooLong = "b".repeat(MailDraft.MAX_BODY_LENGTH + 1);

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(SENDER_ID, RECIPIENT_ID, "s", tooLong, 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.BODY_TOO_LONG);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    @Test
    void shouldRejectNulCharacter_beforeEscrowingAnything() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result =
                fixture.useCase().execute(new MailDraft(SENDER_ID, RECIPIENT_ID, "s", "hi\0there", 0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.INVALID_TEXT);
        assertThat(fixture.repositoryPort.stored).isEmpty();
    }

    /** Thư hệ thống không có ai để trừ — nhánh này chỉ với tới được từ code server, không từ client. */
    @Test
    void shouldSkipEscrow_forSystemMailWithNullSender() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result = fixture.useCase()
                .execute(new MailDraft(
                        null, RECIPIENT_ID, "Đền bù", "Xin lỗi", 500, List.of(new MailAttachmentDraft(KUNAI, 1))));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(result.mail().orElseThrow().senderId()).isNull();
        assertThat(fixture.goldPort.deductCalls).isZero();
        assertThat(fixture.inventoryGatewayPort.consumeCalls).isZero();
        // Không ai bị trừ thì không có gì để audit về phía người gửi.
        assertThat(fixture.auditPort.loggedActions).isEmpty();
    }

    @Test
    void shouldNotAudit_whenMailCarriesNothing() {
        Fixture fixture = fixture();

        SendMailUseCase.SendResult result = fixture.useCase().execute(draft(0, List.of()));

        assertThat(result.outcome()).isEqualTo(SendMailUseCase.SendOutcome.OK);
        assertThat(fixture.auditPort.loggedActions).isEmpty();
    }

    /**
     * Gộp fake + {@link TransactionRunnerWithRollback} — mỗi test dựng một bản mới nên không có
     * trạng thái rò rỉ giữa các test.
     */
    private static final class Fixture {
        private final FakeMailRepositoryPort repositoryPort = new FakeMailRepositoryPort();
        private final FakeGoldPort goldPort = new FakeGoldPort();
        private final FakeInventoryGatewayPort inventoryGatewayPort = new FakeInventoryGatewayPort();
        private final FakeAuditPort auditPort = new FakeAuditPort();

        private SendMailUseCase useCase() {
            MailPorts ports =
                    new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort, new FakeIdempotencyPort());
            return new SendMailUseCase(ports, new TransactionRunnerWithRollback(this));
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    List.copyOf(repositoryPort.stored),
                    new HashMap<Long, Long>(goldPort.balances),
                    new HashMap<Long, Integer>(inventoryGatewayPort.owned),
                    List.copyOf(auditPort.loggedActions));
        }

        private void restore(Snapshot snapshot) {
            repositoryPort.stored.clear();
            repositoryPort.stored.addAll(snapshot.mails());
            goldPort.balances.clear();
            goldPort.balances.putAll(snapshot.balances());
            inventoryGatewayPort.owned.clear();
            inventoryGatewayPort.owned.putAll(snapshot.owned());
            auditPort.loggedActions.clear();
            auditPort.loggedActions.addAll(snapshot.actions());
        }
    }

    private record Snapshot(
            List<Mail> mails, Map<Long, Long> balances, Map<Long, Integer> owned, List<String> actions) {}

    /**
     * Fake transaction runner có rollback THẬT (chụp ảnh trước, khôi phục khi action ném) — bản
     * {@code action -> action.accept(null)} một dòng của các test cũ sẽ để lại hiệu ứng nửa vời và
     * làm nhánh "trừ vàng rồi mới phát hiện thiếu món" báo xanh giả.
     */
    private record TransactionRunnerWithRollback(Fixture fixture) implements dev.kage.mail.domain.TransactionRunner {

        // PMD AvoidCatchingGenericException: đúng ý — bắt chước CHÍNH xác JdbcTransactionRunner,
        // nơi transaction phải rollback khi action ném BẤT KỲ RuntimeException nào. Liệt kê hẹp
        // hơn (chỉ SendMailAbortedException) sẽ làm fake này rộng lượng hơn transaction thật.
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void inTransaction(java.util.function.Consumer<Transaction> action) {
            Snapshot before = fixture.snapshot();
            try {
                action.accept(null);
            } catch (RuntimeException e) {
                fixture.restore(before);
                throw e;
            }
        }
    }

    /** Fake = implementation in-memory THẬT (TESTING.md §4), không Mockito. */
    static final class FakeMailRepositoryPort implements MailRepositoryPort {
        private final List<Mail> stored = new ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Mail send(Transaction tx, NewMail newMail) {
            Mail mail = new Mail(
                    nextId.getAndIncrement(),
                    newMail.senderId(),
                    newMail.recipientId(),
                    newMail.subject(),
                    newMail.body(),
                    newMail.goldAmount(),
                    newMail.attachments(),
                    null,
                    Instant.now());
            stored.add(mail);
            return mail;
        }

        @Override
        public Optional<Mail> findById(long mailId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public Optional<Mail> findByIdForUpdate(Transaction tx, long mailId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public List<Mail> listForRecipient(long recipientId) {
            return stored.stream()
                    .filter(mail -> mail.recipientId() == recipientId)
                    .toList();
        }

        @Override
        public boolean markClaimed(Transaction tx, long mailId, long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean delete(long mailId, long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }
    }

    /** Ví in-memory — trừ có điều kiện y như câu UPDATE ... WHERE gold >= ? của GoldLedger. */
    static final class FakeGoldPort implements GoldPort {
        private final Map<Long, Long> balances = new HashMap<>();
        private int deductCalls;

        @Override
        public boolean deductGold(Transaction tx, long characterId, long amount) {
            deductCalls++;
            long current = balances.getOrDefault(characterId, 0L);
            if (current < amount) {
                return false;
            }
            balances.put(characterId, current - amount);
            return true;
        }

        @Override
        public void creditGold(Transaction tx, long characterId, long amount) {
            balances.merge(characterId, amount, Long::sum);
        }
    }

    /** Túi in-memory theo item_template_id — đủ để kiểm nhánh "không đủ món". */
    static final class FakeInventoryGatewayPort implements InventoryGatewayPort {
        private final Map<Long, Integer> owned = new HashMap<>();
        private int consumeCalls;

        @Override
        public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            owned.merge(itemTemplateId, quantity, Integer::sum);
            return true;
        }

        @Override
        public boolean consumeItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            consumeCalls++;
            int current = owned.getOrDefault(itemTemplateId, 0);
            if (current < quantity) {
                return false;
            }
            owned.put(itemTemplateId, current - quantity);
            return true;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        private final List<String> loggedActions = new ArrayList<>();

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            loggedActions.add(action);
        }
    }

    static final class FakeIdempotencyPort implements IdempotencyPort {

        @Override
        public void recordOrThrow(Transaction tx, String idempotencyKey) {
            throw new UnsupportedOperationException("gửi thư không dùng idempotency key");
        }
    }
}
