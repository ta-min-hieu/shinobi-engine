package dev.kage.friend.domain;

/**
 * Trần danh sách bạn bè (quyết định chủ dự án 2026-10-11). Trước đó không có trần nào: một nhân vật
 * kết bạn được với cả server, và mỗi lần đăng nhập / mở khung bạn bè phải nạp toàn bộ danh sách đó.
 *
 * <p>Trần kiểm ở application (đếm rồi mới ghi), KHÔNG phải ràng buộc DB: hai lời mời được chấp nhận
 * đúng cùng lúc có thể đẩy một danh sách vượt trần một hai người. Chấp nhận được — trần này chặn
 * danh sách phình vô hạn, không phải một bất biến kinh tế.
 */
public final class FriendListLimit {

    /** Số bạn bè (đã chấp nhận) tối đa của một nhân vật. */
    public static final int MAX_FRIENDS = 100;

    private FriendListLimit() {}

    /** {@code true} nếu danh sách {@code acceptedCount} bạn đã chạm trần — không nhận thêm ai. */
    public static boolean isFull(int acceptedCount) {
        return acceptedCount >= MAX_FRIENDS;
    }
}
