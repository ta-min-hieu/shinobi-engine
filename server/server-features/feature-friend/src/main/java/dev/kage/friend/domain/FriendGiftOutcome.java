package dev.kage.friend.domain;

/** Kết quả tặng quà (ADR-084). */
public enum FriendGiftOutcome {
    OK,
    /** Không phải bạn bè (hoặc chưa chấp nhận). */
    NOT_FRIENDS,
    /** Đã tặng người này hôm nay (ngày giờ Việt Nam). */
    ALREADY_GIFTED_TODAY,
    INSUFFICIENT_GOLD,
    CANNOT_GIFT_SELF
}
