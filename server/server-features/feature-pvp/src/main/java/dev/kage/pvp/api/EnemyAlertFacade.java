package dev.kage.pvp.api;

import java.util.List;

import dev.kage.pvp.domain.EnemyAlertPort;

/** API báo cừu nhân online (ADR-426). Chạm DB — Virtual Thread. */
public final class EnemyAlertFacade {

    /** Không ai theo dõi (composition test). */
    public static final EnemyAlertFacade DISABLED = new EnemyAlertFacade(new EnemyAlertPort() {
        @Override
        public List<Long> watchersOf(long enemyId) {
            return List.of();
        }

        @Override
        public boolean muted(long characterId) {
            return false;
        }

        @Override
        public void setMuted(long characterId, boolean muted) {
            // không lưu
        }
    });

    private final EnemyAlertPort port;

    /** Dựng ở composition root. */
    public EnemyAlertFacade(EnemyAlertPort port) {
        this.port = port;
    }

    /** Ai cần được báo khi {@code enemyId} vào game. */
    public List<Long> watchersOf(long enemyId) {
        return port.watchersOf(enemyId);
    }

    /** Báo đang bật không. */
    public boolean enabled(long characterId) {
        return !port.muted(characterId);
    }

    /** Bật / tắt báo. */
    public void setEnabled(long characterId, boolean enabled) {
        port.setMuted(characterId, !enabled);
    }
}
