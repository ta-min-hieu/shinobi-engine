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

    /**
     * Số lời mời ĐANG CHỜ tối đa một nhân vật nhận (dòng 71(b), 2026-10-17) — quá trần thì lời mời mới bị
     * từ chối ở phía người gửi. Trần mềm như {@link #MAX_FRIENDS}.
     */
    public static final int MAX_PENDING_INCOMING = 50;

    private FriendListLimit() {}

    /** {@code true} nếu {@code pendingCount} lời mời đang chờ đã chạm {@link #MAX_PENDING_INCOMING}. */
    public static boolean isPendingFull(int pendingCount) {
        return pendingCount >= MAX_PENDING_INCOMING;
    }

    /** {@code true} nếu danh sách {@code acceptedCount} bạn đã chạm trần — không nhận thêm ai. */
    public static boolean isFull(int acceptedCount) {
        return acceptedCount >= MAX_FRIENDS;
    }
}
