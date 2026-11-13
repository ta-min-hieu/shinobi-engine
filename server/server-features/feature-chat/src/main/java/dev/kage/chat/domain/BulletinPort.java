package dev.kage.chat.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bảng rao vặt (ADR-336) — bảng {@code bulletin_post}, mỗi nhân vật một tin. */
public interface BulletinPort {

    /** Một tin. */
    record Post(long characterId, String body, Instant postedAt) {}

    /** Lúc đăng tin hiện có của nhân vật (kể cả đã hết hạn). */
    Optional<Instant> postedAt(long characterId);

    /** Đăng (thay tin cũ nếu có). */
    void post(long characterId, String body, Instant at);

    /** Gỡ tin của mình; {@code false} nếu không có. */
    boolean remove(long characterId);

    /** Tin đăng từ {@code since} trở đi, mới trước, tối đa {@code limit}. */
    List<Post> since(Instant since, int limit);
}
