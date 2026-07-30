package dev.kage.mail.application;

import java.util.List;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailRepositoryPort;

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
