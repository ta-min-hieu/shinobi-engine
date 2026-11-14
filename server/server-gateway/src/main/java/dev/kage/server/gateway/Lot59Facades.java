package dev.kage.server.gateway;

import dev.kage.friend.api.WhisperPrivacyFacade;
import dev.kage.mail.api.MailReceiptFacade;

/**
 * Facade lô 59 (ADR-399…) gom thành MỘT thành phần của {@link Lot58Facades}. Tìm bang dùng facade tra cứu bang của lô
 * 58; xem lại chat bang đọc bộ nhớ RAM của registry.
 */
record Lot59Facades(MailReceiptFacade mailReceipt, WhisperPrivacyFacade whisperPrivacy, Lot61Facades lot61) {

    /** Composition không có các tính năng này (test). */
    static final Lot59Facades DISABLED =
            new Lot59Facades(MailReceiptFacade.DISABLED, WhisperPrivacyFacade.DISABLED, Lot61Facades.DISABLED);
}
