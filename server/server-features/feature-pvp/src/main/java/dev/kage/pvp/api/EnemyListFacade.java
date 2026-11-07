package dev.kage.pvp.api;

import java.time.Instant;
import java.util.List;

import dev.kage.pvp.domain.EnemyListPort;

/** Danh sách cừu nhân (ADR-194) — ai đã hạ mình trong dải PvP. Chạm DB — Virtual Thread. */
public final class EnemyListFacade {

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

    /** Xoá một cừu nhân theo tên. */
    public boolean remove(long characterId, String enemyName) {
        return port.remove(characterId, enemyName.strip());
    }
}
