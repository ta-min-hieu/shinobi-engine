package dev.kage.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;

class ListMailUseCaseTest {

    @Test
    void shouldReturnAllMailForRecipient() {
        FakeMailRepositoryPort port = new FakeMailRepositoryPort();
        port.mails.add(new Mail(1, null, 5, "s1", "b1", 0, List.of(), null, Instant.now()));
        ListMailUseCase useCase = new ListMailUseCase(port);

        List<Mail> result = useCase.execute(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subject()).isEqualTo("s1");
    }

    static final class FakeMailRepositoryPort implements MailRepositoryPort {
        private final List<Mail> mails = new ArrayList<>();

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
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public List<Mail> listForRecipient(long recipientId) {
            return mails.stream().filter(m -> m.recipientId() == recipientId).toList();
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
}
