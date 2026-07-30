package dev.kage.mail.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;
import dev.kage.mail.domain.TransactionRunner;

/**
 * Gửi thư — MVP KHÔNG mất gold để gửi (feature-mail/CLAUDE.md), nên chỉ cần
 * {@link MailRepositoryPort} (không cần {@link MailPorts} đầy đủ như lúc nhận). Vẫn chạy trong
 * transaction vì 1 lá thư = 1 dòng {@code mail} + N dòng {@code mail_item}, phải nguyên tử.
 */
public final class SendMailUseCase {

    private final MailRepositoryPort port;
    private final TransactionRunner tx;

    public SendMailUseCase(MailRepositoryPort port, TransactionRunner tx) {
        this.port = port;
        this.tx = tx;
    }

    public Mail execute(NewMail newMail) {
        AtomicReference<Mail> resultHolder = new AtomicReference<Mail>();
        tx.inTransaction((Transaction t) -> resultHolder.set(port.send(t, newMail)));
        return resultHolder.get();
    }
}
