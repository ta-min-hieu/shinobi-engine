package dev.kage.mail.domain;

import java.util.Set;

/** Dọn hộp thư (ADR-216 xoá hàng loạt, ADR-217 ghim thư). */
public interface MailHousekeepingPort {

    /**
     * Xoá mọi thư đã xong của {@code recipientId}: đã nhận, hoặc không có gì đính kèm — trừ thư đang ghim.
     *
     * @return số thư đã xoá
     */
    int deleteFinished(long recipientId);

    /**
     * Ghim / bỏ ghim một thư của chính {@code recipientId}.
     *
     * @return {@code false} nếu thư không tồn tại hoặc không phải của người này
     */
    boolean setPinned(long recipientId, long mailId, boolean pinned);

    /** Id các thư đang ghim của {@code recipientId}. */
    Set<Long> pinned(long recipientId);
}
