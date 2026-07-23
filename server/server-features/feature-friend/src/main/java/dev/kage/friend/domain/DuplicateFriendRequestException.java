package dev.kage.friend.domain;

/**
 * Ném khi adapter phát hiện vi phạm UNIQUE {@code (requester_id, addressee_id)} — 2 request gửi
 * lời mời gần như đồng thời cho CÙNG 1 cặp CÙNG 1 hướng. Race hướng ngược lại (A mời B trong lúc B
 * cũng đang mời A) KHÔNG bị chặn ở tầng DB (khác cặp cột) — chấp nhận có thể tạo 2 dòng PENDING
 * ngược hướng nhau, vô hại (một trong hai bên chấp nhận là xong), không phải lỗi cần transaction/
 * lock riêng cho MVP dự án cá nhân.
 */
public final class DuplicateFriendRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateFriendRequestException(long requesterId, long addresseeId) {
        super("Đã có lời mời kết bạn giữa " + requesterId + " và " + addresseeId);
    }
}
