package dev.kage.friend.domain;

import java.util.Optional;

/** Lời nhắn kèm lời mời kết bạn (ADR-376) — bảng {@code friend_request_note}. */
public interface FriendRequestNotePort {

    /** Ghi (đè) lời nhắn của {@code requesterId} gửi {@code addresseeId}. */
    void save(long requesterId, long addresseeId, String message);

    /** Lời nhắn đang có. */
    Optional<String> find(long requesterId, long addresseeId);
}
