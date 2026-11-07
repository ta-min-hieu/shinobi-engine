package dev.kage.character.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Lần cuối online (ADR-192) — mốc nhân vật rời world gần nhất, cột {@code character.last_online_at}. Không dùng
 * {@code last_logout_at} của bế quan: cột đó bị xoá mỗi lần vào world và có nghĩa riêng của nó.
 */
public interface LastOnlinePort {

    /** Nhân vật vừa rời world lúc {@code at}. */
    void markLeft(long characterId, Instant at);

    /** Mốc rời world của những nhân vật có mốc; ai chưa từng có mốc thì vắng mặt khỏi map. */
    Map<Long, Instant> lastOnlineOf(Collection<Long> characterIds);
}
