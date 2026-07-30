package dev.kage.friend.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root — 1 lời mời/quan hệ kết bạn, có hướng {@code requesterId -> addresseeId}. Sau khi
 * {@link FriendshipStatus#ACCEPTED}, dòng KHÔNG tạo bản ghi ngược lại — đọc quan hệ của 1 nhân vật
 * phải xét cả 2 cột (xem {@link FriendPort#listAccepted}).
 */
public record Friendship(
        long id, long requesterId, long addresseeId, FriendshipStatus status, Instant createdAt, Instant respondedAt) {

    public Friendship {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (requesterId == addresseeId) {
            throw new IllegalArgumentException("requesterId và addresseeId không được trùng: " + requesterId);
        }
    }

    /** ID phía còn lại trong quan hệ, nhìn từ {@code characterId}. */
    public long otherParty(long characterId) {
        if (characterId == requesterId) {
            return addresseeId;
        }
        if (characterId == addresseeId) {
            return requesterId;
        }
        throw new IllegalArgumentException("characterId không thuộc friendship này: " + characterId);
    }

    public boolean involves(long characterId) {
        return characterId == requesterId || characterId == addresseeId;
    }
}
