package dev.kage.gm.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Một báo cáo người chơi (ADR-105).
 *
 * @param chatExcerpt JSON mảng các tin chat gần nhất của người bị báo cáo, trích lúc báo cáo
 */
public record PlayerReport(
        long id, long reporterId, long targetId, Reason reason, String note, String chatExcerpt, Instant createdAt) {

    /** Độ dài ghi chú tối đa — khớp cột {@code player_report.note}. */
    public static final int MAX_NOTE_LENGTH = 200;

    /** Số tin chat trích kèm. */
    public static final int EXCERPT_MESSAGES = 20;

    /** Mỗi người gửi tối đa chừng này báo cáo mỗi ngày (giờ Việt Nam). */
    public static final int MAX_PER_DAY = 5;

    /** Lý do báo cáo. */
    public enum Reason {
        CHAT,
        CHEAT,
        SCAM,
        OTHER;

        /** Đọc chữ người gõ ({@code chat}, {@code cheat}...); rỗng nếu không khớp. */
        public static Optional<Reason> parse(String raw) {
            for (Reason reason : values()) {
                if (reason.name().equalsIgnoreCase(raw.strip())) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }

        /** Chữ thường để hiện. */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
