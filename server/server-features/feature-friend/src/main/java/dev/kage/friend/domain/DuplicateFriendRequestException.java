package dev.kage.friend.domain;

/**
 * Ném khi đã có quan hệ (PENDING hoặc ACCEPTED) giữa 2 nhân vật THEO CHIỀU BẤT KỲ lúc adapter
 * định chèn lời mời mới — gồm cả race 2 lời mời gửi gần như đồng thời, cùng chiều hay ngược chiều
 * (A mời B đúng lúc B mời A). Xem javadoc {@code PostgresFriendAdapter#sendRequest}.
 */
public final class DuplicateFriendRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateFriendRequestException(long requesterId, long addresseeId) {
        super("Đã có lời mời kết bạn giữa " + requesterId + " và " + addresseeId);
    }
}
