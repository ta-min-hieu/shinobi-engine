package dev.kage.friend.domain;

import java.time.LocalDate;
import java.util.Map;

/**
 * Tặng quà bạn bè (ADR-084) — hiện thực ở server-database. {@link #gift} là MỘT transaction trọn trong adapter (khoá
 * dòng
 * {@code friendship} → ghi nhật ký quà trong ngày → trừ vàng qua GoldLedger → cộng thân mật → audit), cùng khuôn góp
 * bang của feature-guild: chạm vàng nên không làm bằng {@link FriendPort}.
 */
public interface FriendGiftPort {

    /**
     * Kết quả một lần tặng.
     *
     * @param intimacyBefore thân mật trước khi tặng (0 nếu từ chối)
     * @param intimacyAfter thân mật sau khi tặng (bằng trước nếu từ chối)
     */
    record GiftResult(FriendGiftOutcome outcome, int intimacyBefore, int intimacyAfter) {

        public static GiftResult refused(FriendGiftOutcome outcome) {
            return new GiftResult(outcome, 0, 0);
        }
    }

    /** Tặng {@code recipientId} một món quà trong ngày {@code today}. */
    GiftResult gift(long senderId, long recipientId, LocalDate today);

    /** Thân mật với từng bạn đã chấp nhận: {@code friendId → thân mật}. */
    Map<Long, Integer> intimacyOf(long characterId);
}
