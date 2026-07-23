package dev.kage.mail.domain;

import java.util.List;
import java.util.Objects;

/**
 * Tham số tạo 1 lá thư mới — gộp lại để {@link MailRepositoryPort#send}/{@code SendMailUseCase}
 * không vượt quá 4 tham số (Checkstyle {@code ParameterNumber}), cùng tinh thần {@code NewCharacter}
 * (feature-character).
 */
public record NewMail(
        Long senderId, long recipientId, String subject, String body, long goldAmount,
        List<MailAttachment> attachments) {

    public NewMail {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(attachments, "attachments");
        if (recipientId <= 0) {
            throw new IllegalArgumentException("recipientId phải > 0: " + recipientId);
        }
        if (goldAmount < 0) {
            throw new IllegalArgumentException("goldAmount không được âm: " + goldAmount);
        }
        attachments = List.copyOf(attachments);
    }
}
