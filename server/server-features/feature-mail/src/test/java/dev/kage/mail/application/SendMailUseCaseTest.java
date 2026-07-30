package dev.kage.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;

class SendMailUseCaseTest {

    @Test
    void shouldCreateMail_withAttachments() {
        FakeMailRepositoryPort port = new FakeMailRepositoryPort();
        SendMailUseCase useCase = new SendMailUseCase(port, action -> action.accept(null));

        Mail created = useCase.execute(new NewMail(7L, 5, "Chào", "Nội dung", 100, List.of(new MailAttachment(1, 3))));

        assertThat(created.senderId()).isEqualTo(7L);
        assertThat(created.recipientId()).isEqualTo(5);
        assertThat(created.goldAmount()).isEqualTo(100);
        assertThat(created.attachments()).containsExactly(new MailAttachment(1, 3));
        assertThat(port.stored).hasSize(1);
    }

    @Test
    void shouldAllowNullSenderId_forSystemMail() {
        FakeMailRepositoryPort port = new FakeMailRepositoryPort();
        SendMailUseCase useCase = new SendMailUseCase(port, action -> action.accept(null));

        Mail created = useCase.execute(new NewMail(null, 5, "Đền bù", "Xin lỗi", 0, List.of()));

        assertThat(created.senderId()).isNull();
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
            throw new UnsupportedOperationException("không dùng ở test này");
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
