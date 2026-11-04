package dev.kage.friend.api;

import java.util.Map;
import java.util.Optional;

import dev.kage.friend.domain.FriendNotePort;

/**
 * Ghi chú bạn bè (ADR-173). Chuẩn hoá ghi chú (bỏ ký tự điều khiển, gộp khoảng trắng, tối đa
 * {@link #MAX_LENGTH} ký tự); ghi chú rỗng = xoá. Kiểm "là bạn" thuộc về gateway (có {@link FriendFacade}). Chạm DB —
 * chỉ gọi trên Virtual Thread.
 */
public final class FriendNoteFacade {

    /** Độ dài tối đa (code point) của một ghi chú. */
    public static final int MAX_LENGTH = 24;

    /** Composition không có ghi chú (test): không lưu gì. */
    public static final FriendNoteFacade DISABLED = new FriendNoteFacade(new FriendNotePort() {
        @Override
        public Map<Long, String> notesOf(long ownerId) {
            return Map.of();
        }

        @Override
        public void put(long ownerId, long friendId, String note) {
            // không lưu
        }

        @Override
        public void remove(long ownerId, long friendId) {
            // không lưu
        }
    });

    private final FriendNotePort port;

    /** Dựng ở composition root. */
    public FriendNoteFacade(FriendNotePort port) {
        this.port = port;
    }

    /** Mọi ghi chú của {@code ownerId}, theo id người bạn. */
    public Map<Long, String> notesOf(long ownerId) {
        return port.notesOf(ownerId);
    }

    /**
     * Ghi chú đã chuẩn hoá; {@link Optional#empty()} nếu dài quá {@link #MAX_LENGTH}. Chuỗi rỗng hợp lệ (= xoá).
     */
    public static Optional<String> normalize(String raw) {
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }

    /** Lưu ghi chú ĐÃ chuẩn hoá; rỗng ⇒ xoá. */
    public void set(long ownerId, long friendId, String normalizedNote) {
        if (normalizedNote.isEmpty()) {
            port.remove(ownerId, friendId);
        } else {
            port.put(ownerId, friendId, normalizedNote);
        }
    }
}
