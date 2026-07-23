package dev.kage.mail.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root — 1 lá thư. {@code senderId == null} nghĩa là thư hệ thống (không có người gửi
 * thật, vd thư đền bù GM) — KHÔNG dùng sentinel số 0/-1 (dễ nhầm với ID thật).
 *
 * <p>{@code claimedAt == null} = CHƯA nhận gold/item đính kèm — {@link #isClaimed()}.
 */
public record Mail(
        long id,
        Long senderId,
        long recipientId,
        String subject,
        String body,
        long goldAmount,
        List<MailAttachment> attachments,
        Instant claimedAt,
        Instant createdAt) {

    public Mail {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(attachments, "attachments");
        Objects.requireNonNull(createdAt, "createdAt");
        if (recipientId <= 0) {
            throw new IllegalArgumentException("recipientId phải > 0: " + recipientId);
        }
        if (goldAmount < 0) {
            throw new IllegalArgumentException("goldAmount không được âm: " + goldAmount);
        }
        attachments = List.copyOf(attachments);
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    /** Có gì để nhận không — quyết định {@code DeleteMailUseCase} có cho xoá thư CHƯA nhận không. */
    public boolean hasReward() {
        return goldAmount > 0 || !attachments.isEmpty();
    }
}
