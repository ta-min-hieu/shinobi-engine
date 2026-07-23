package dev.kage.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeleteMailUseCaseTest {

    @Test
    void shouldDelete_whenMailHasNoReward() {
        var port = new FakeMailRepositoryPort();
        port.mails.add(new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now()));
        var useCase = new DeleteMailUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(DeleteMailUseCase.Result.OK);
        assertThat(port.deletedCalled).isTrue();
    }

    @Test
    void shouldDelete_whenAlreadyClaimed() {
        var port = new FakeMailRepositoryPort();
        port.mails.add(new Mail(1, null, 5, "s", "b", 50, List.of(), Instant.now(), Instant.now()));
        var useCase = new DeleteMailUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(DeleteMailUseCase.Result.OK);
    }

    @Test
    void shouldRejectDelete_whenUnclaimedAndHasReward() {
        var port = new FakeMailRepositoryPort();
        port.mails.add(new Mail(1, null, 5, "s", "b", 50, List.of(), null, Instant.now()));
        var useCase = new DeleteMailUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(DeleteMailUseCase.Result.MUST_CLAIM_FIRST);
        assertThat(port.deletedCalled).isFalse();
    }

    @Test
    void shouldRejectDelete_whenNotFoundOrRecipientMismatch() {
        var port = new FakeMailRepositoryPort();
        port.mails.add(new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now()));
        var useCase = new DeleteMailUseCase(port);

        var result = useCase.execute(1, 999);

        assertThat(result).isEqualTo(DeleteMailUseCase.Result.NOT_FOUND);
    }

    static final class FakeMailRepositoryPort implements MailRepositoryPort {
        private final List<Mail> mails = new ArrayList<>();
        private boolean deletedCalled;

        @Override
        public Mail send(Transaction tx, NewMail newMail) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public Optional<Mail> findById(long mailId) {
            return mails.stream().filter(m -> m.id() == mailId).findFirst();
        }

        @Override
        public Optional<Mail> findByIdForUpdate(Transaction tx, long mailId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public List<Mail> listForRecipient(long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean markClaimed(Transaction tx, long mailId, long recipientId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean delete(long mailId, long recipientId) {
            deletedCalled = true;
            return mails.removeIf(m -> m.id() == mailId && m.recipientId() == recipientId);
        }
    }
}
