package dev.kage.pvp.api;

import java.time.Instant;
import java.util.List;

import dev.kage.pvp.domain.EnemyListPort;

/** Danh sách cừu nhân (ADR-194) — ai đã hạ mình trong dải PvP. Chạm DB — Virtual Thread. */
public final class EnemyListFacade {

    /** Dài hơn mức này thì chắc chắn không phải tên nhân vật — không cần hỏi DB. */
    public static final int MAX_NAME_LENGTH = 64;

    /** Số cừu nhân giữ lại mỗi nhân vật (người cũ nhất rơi ra). */
    public static final int MAX_ENTRIES = 20;

    /** Composition không có tính năng này (test). */
    public static final EnemyListFacade DISABLED = new EnemyListFacade(new EnemyListPort() {
        @Override
        public void recordKill(long victimId, long killerId, Instant at, int maxEntries) {
            // không ghi
        }

        @Override
        public List<Enemy> list(long characterId) {
            return List.of();
        }

        @Override
        public boolean remove(long characterId, String enemyName) {
            return false;
        }
    });

    private final EnemyListPort port;

    /** Dựng ở composition root. */
    public EnemyListFacade(EnemyListPort port) {
        this.port = port;
    }

    /** Người chơi {@code killerId} vừa hạ {@code victimId} trong dải PvP. Tự hạ mình (không xảy ra) thì bỏ qua. */
    public void recordKill(long victimId, long killerId, Instant at) {
        if (victimId != killerId) {
            port.recordKill(victimId, killerId, at, MAX_ENTRIES);
        }
    }

    /** Cừu nhân của {@code characterId}, mới nhất trước. */
    public List<EnemyListPort.Enemy> list(long characterId) {
        return port.list(characterId);
    }

    /**
     * Xoá một cừu nhân theo tên. Tên không thể là tên nhân vật (rỗng, quá dài, có ký tự điều khiển — kể cả NUL mà
     * Postgres từ chối) ⇒ {@code false} ngay, không chạm DB (test fuzz gateway bắt được 500 ở đây).
     */
    public boolean remove(long characterId, String enemyName) {
        String name = enemyName.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        return port.remove(characterId, name);
    }
}
