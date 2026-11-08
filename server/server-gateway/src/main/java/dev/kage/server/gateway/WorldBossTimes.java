package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Optional;

/**
 * Lịch boss thế giới nhìn từ ngoài (ADR-224 lịch sự kiện): lần xuất hiện kế tiếp và boss có đang sống không.
 * {@link WorldBossScheduler} ghi (luồng lịch boss), lệnh lịch sự kiện đọc (Virtual Thread) — các trường {@code
 * volatile}, một lịch boss (server hiện có đúng một con boss thế giới).
 */
final class WorldBossTimes {

    private volatile Instant nextSpawnAt;
    private volatile boolean alive;

    void scheduled(Instant at) {
        this.nextSpawnAt = at;
        this.alive = false;
    }

    void spawned() {
        this.alive = true;
    }

    /** Lần xuất hiện kế tiếp; rỗng nếu chưa có lịch (server chưa nạp lịch boss). */
    Optional<Instant> nextSpawnAt() {
        return Optional.ofNullable(nextSpawnAt);
    }

    boolean alive() {
        return alive;
    }
}
