package dev.kage.friend.domain;

import java.util.Map;

/** Ghi chú bạn bè (ADR-173) — ghi chú riêng của một người về từng người bạn. Chạm DB. */
public interface FriendNotePort {

    /** Mọi ghi chú của {@code ownerId}, theo id người bạn. Không có ⇒ map rỗng. */
    Map<Long, String> notesOf(long ownerId);

    /** Ghi (hoặc thay) ghi chú của {@code ownerId} về {@code friendId}. */
    void put(long ownerId, long friendId, String note);

    /** Xoá ghi chú (không có thì thôi). */
    void remove(long ownerId, long friendId);
}
