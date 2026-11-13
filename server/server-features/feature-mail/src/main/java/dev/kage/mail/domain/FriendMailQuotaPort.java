package dev.kage.mail.domain;

import java.time.Instant;

/** Giới hạn thư gửi mọi bạn bè (ADR-312) — đếm trên audit {@code FRIEND_MAIL}. */
public interface FriendMailQuotaPort {

    /** Số đợt {@code senderId} đã gửi kể từ {@code since}. */
    int sentSince(long senderId, Instant since);

    /** Ghi một đợt. */
    void record(long senderId, int recipients, Instant at);
}
