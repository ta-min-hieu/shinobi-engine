package dev.kage.friend.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import dev.kage.friend.domain.StatusMessagePort;

/**
 * API lời nhắn trạng thái (ADR-335): mỗi nhân vật một dòng ≤ {@value #MAX_LENGTH} ký tự, hiện cạnh tên trong danh sách
 * bạn bè của người khác. Người gọi đã kiểm nội dung. Chạm DB — Virtual Thread.
 */
public final class StatusMessageFacade {

    /** Độ dài tối đa. */
    public static final int MAX_LENGTH = 60;

    /** Không lưu gì (composition test). */
    public static final StatusMessageFacade DISABLED = new StatusMessageFacade(new StatusMessagePort() {
        @Override
        public Map<Long, String> messagesOf(Collection<Long> characterIds) {
            return Map.of();
        }

        @Override
        public Optional<String> messageOf(long characterId) {
            return Optional.empty();
        }

        @Override
        public void set(long characterId, String message) {
            // không lưu
        }
    });

    private final StatusMessagePort port;

    /** Dựng ở composition root. */
    public StatusMessageFacade(StatusMessagePort port) {
        this.port = port;
    }

    /** Lời nhắn của nhiều người (cho danh sách bạn bè). */
    public Map<Long, String> messagesOf(Collection<Long> characterIds) {
        return characterIds.isEmpty() ? Map.of() : port.messagesOf(characterIds);
    }

    /** Lời nhắn của mình ("" nếu không đặt). */
    public String messageOf(long characterId) {
        return port.messageOf(characterId).orElse("");
    }

    /** Đặt (rỗng = xoá). */
    public void set(long characterId, String message) {
        port.set(characterId, message);
    }
}
