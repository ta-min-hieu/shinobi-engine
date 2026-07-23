package dev.kage.mail.application;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailRepositoryPort;
import java.util.List;

/** Toàn bộ thư của {@code characterId}. */
public final class ListMailUseCase {

    private final MailRepositoryPort port;

    public ListMailUseCase(MailRepositoryPort port) {
        this.port = port;
    }

    public List<Mail> execute(long characterId) {
        return port.listForRecipient(characterId);
    }
}
