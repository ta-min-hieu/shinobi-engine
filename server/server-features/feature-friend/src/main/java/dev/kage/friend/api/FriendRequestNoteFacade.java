package dev.kage.friend.api;

import java.util.Optional;

import dev.kage.friend.domain.FriendRequestNotePort;

/** API lời nhắn kèm lời mời kết bạn (ADR-376): ≤ {@value #MAX_LENGTH} ký tự. Chạm DB — Virtual Thread. */
public final class FriendRequestNoteFacade {

    /** Độ dài tối đa. */
    public static final int MAX_LENGTH = 60;

    /** Không lưu gì (composition test). */
    public static final FriendRequestNoteFacade DISABLED = new FriendRequestNoteFacade(new FriendRequestNotePort() {
        @Override
        public void save(long requesterId, long addresseeId, String message) {
            // không lưu
        }

        @Override
        public Optional<String> find(long requesterId, long addresseeId) {
            return Optional.empty();
        }
    });

    private final FriendRequestNotePort port;

    /** Dựng ở composition root. */
    public FriendRequestNoteFacade(FriendRequestNotePort port) {
        this.port = port;
    }

    /** Ghi lời nhắn (người gọi đã kiểm nội dung; rỗng thì không ghi). */
    public void save(long requesterId, long addresseeId, String message) {
        if (!message.isEmpty()) {
            port.save(requesterId, addresseeId, message);
        }
    }

    /** Lời nhắn của một lời mời ("" nếu không có). */
    public String messageOf(long requesterId, long addresseeId) {
        return port.find(requesterId, addresseeId).orElse("");
    }
}
