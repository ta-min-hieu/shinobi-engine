package dev.kage.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.mail.domain.AuditPort;
import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.IdempotencyKeyReusedException;
import dev.kage.mail.domain.IdempotencyPort;
import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaimMailUseCaseTest {

    private static final String IDEMPOTENCY_KEY = "key-1";

    @Test
    void shouldReturnNotFound_whenMailDoesNotExist() {
        var repositoryPort = new FakeMailRepositoryPort();
        var goldPort = new FakeGoldPort();
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var ports = new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort, new FakeIdempotencyPort());
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));

        var result = useCase.execute(999, 5, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.NOT_FOUND);
    }

    @Test
    void shouldReturnNotFound_whenRecipientMismatch() {
        var repositoryPort = new FakeMailRepositoryPort();
        repositoryPort.mails.add(new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now()));
        var ports = new MailPorts(repositoryPort, new FakeGoldPort(), new FakeInventoryGatewayPort(true),
                new FakeAuditPort(), new FakeIdempotencyPort());
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));

        var result = useCase.execute(1, 999, IDEMPOTENCY_KEY); // 999 không phải recipient thật (5)

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.NOT_FOUND);
    }

    @Test
    void shouldReturnAlreadyClaimed_whenClaimedAtSet() {
        var repositoryPort = new FakeMailRepositoryPort();
        repositoryPort.mails.add(new Mail(1, null, 5, "s", "b", 0, List.of(), Instant.now(), Instant.now()));
        var ports = new MailPorts(repositoryPort, new FakeGoldPort(), new FakeInventoryGatewayPort(true),
                new FakeAuditPort(), new FakeIdempotencyPort());
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));

        var result = useCase.execute(1, 5, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.ALREADY_CLAIMED);
    }

    @Test
    void shouldCreditGoldGrantItemsMarkClaimedAndAudit_whenSuccessful() {
        var repositoryPort = new FakeMailRepositoryPort();
        repositoryPort.mails.add(
                new Mail(1, 7L, 5, "s", "b", 50, List.of(new MailAttachment(1, 2)), null, Instant.now()));
        var goldPort = new FakeGoldPort();
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var ports = new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort, new FakeIdempotencyPort());
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));

        var result = useCase.execute(1, 5, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.OK);
        assertThat(goldPort.creditedAmount).isEqualTo(50);
        assertThat(inventoryGatewayPort.grantedCalls).isEqualTo(1);
        assertThat(repositoryPort.markClaimedCalled).isTrue();
        assertThat(auditPort.loggedAction).isEqualTo("MAIL_CLAIM");
    }

    @Test
    void shouldRollbackAndReturnInventoryFull_whenGrantFails() {
        // Túi đầy giữa chừng — verify ĐÚNG control-flow: gold "đã cộng" ở tầng Port (Fake không tự
        // rollback thật, đó là việc của JdbcTransactionRunner — Postgres thật verify ở
        // MailIntegrationTest), NHƯNG markClaimed/audit KHÔNG BAO GIỜ được gọi tới — cùng cách
        // BuyItemUseCaseTest verify shouldRollbackAndReturnInventoryFull_whenGrantFails.
        var repositoryPort = new FakeMailRepositoryPort();
        repositoryPort.mails.add(
                new Mail(1, 7L, 5, "s", "b", 50, List.of(new MailAttachment(1, 2)), null, Instant.now()));
        var goldPort = new FakeGoldPort();
        var inventoryGatewayPort = new FakeInventoryGatewayPort(false);
        var auditPort = new FakeAuditPort();
        var ports = new MailPorts(repositoryPort, goldPort, inventoryGatewayPort, auditPort, new FakeIdempotencyPort());
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));

        var result = useCase.execute(1, 5, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.INVENTORY_FULL);
        assertThat(goldPort.creditedAmount).isEqualTo(50); // đã gọi Port (rollback thật là việc của DB)
        assertThat(repositoryPort.markClaimedCalled).isFalse();
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldReturnDuplicateRequest_whenIdempotencyKeyReused() {
        var repositoryPort = new FakeMailRepositoryPort();
        repositoryPort.mails.add(
                new Mail(1, 7L, 5, "s", "b", 50, List.of(new MailAttachment(1, 2)), null, Instant.now()));
        var idempotencyPort = new FakeIdempotencyPort();
        var ports = new MailPorts(repositoryPort, new FakeGoldPort(), new FakeInventoryGatewayPort(true),
                new FakeAuditPort(), idempotencyPort);
        var useCase = new ClaimMailUseCase(ports, action -> action.accept(null));
        useCase.execute(1, 5, IDEMPOTENCY_KEY);

        var result = useCase.execute(1, 5, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(ClaimMailUseCase.ClaimOutcome.DUPLICATE_REQUEST);
    }

    static final class FakeMailRepositoryPort implements MailRepositoryPort {
        private final List<Mail> mails = new ArrayList<>();
        private boolean markClaimedCalled;

        @Override
        public Mail send(Transaction tx, NewMail newMail) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public Optional<Mail> findById(long mailId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public Optional<Mail> findByIdForUpdate(Transaction tx, long mailId) {
            return mails.stream().filter(m -> m.id() == mailId).findFirst();
        }

        @Override
        public List<Mail> listForRecipient(long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean markClaimed(Transaction tx, long mailId, long recipientId) {
            markClaimedCalled = true;
            for (int i = 0; i < mails.size(); i++) {
                var m = mails.get(i);
                if (m.id() == mailId && m.recipientId() == recipientId && !m.isClaimed()) {
                    mails.set(i, new Mail(m.id(), m.senderId(), m.recipientId(), m.subject(), m.body(),
                            m.goldAmount(), m.attachments(), Instant.now(), m.createdAt()));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean delete(long mailId, long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }
    }

    static final class FakeGoldPort implements GoldPort {
        private long creditedAmount;

        @Override
        public void creditGold(Transaction tx, long characterId, long amount) {
            creditedAmount += amount;
        }
    }

    static final class FakeInventoryGatewayPort implements InventoryGatewayPort {
        private final boolean shouldSucceed;
        private int grantedCalls;

        FakeInventoryGatewayPort(boolean shouldSucceed) {
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            grantedCalls++;
            return shouldSucceed;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        private String loggedAction;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            loggedAction = action;
        }
    }

    static final class FakeIdempotencyPort implements IdempotencyPort {
        private final java.util.Set<String> usedKeys = new java.util.HashSet<>();

        @Override
        public void recordOrThrow(Transaction tx, String idempotencyKey) {
            if (!usedKeys.add(idempotencyKey)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
        }
    }
}
