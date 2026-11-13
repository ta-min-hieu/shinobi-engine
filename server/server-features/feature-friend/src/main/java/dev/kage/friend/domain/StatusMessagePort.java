package dev.kage.friend.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Lời nhắn trạng thái (ADR-335) — bảng {@code status_message}. */
public interface StatusMessagePort {

    /** Lời nhắn của từng nhân vật có đặt (nhân vật không đặt thì không có trong map). */
    Map<Long, String> messagesOf(Collection<Long> characterIds);

    /** Lời nhắn của một nhân vật. */
    Optional<String> messageOf(long characterId);

    /** Đặt lời nhắn; rỗng = xoá. */
    void set(long characterId, String message);
}
