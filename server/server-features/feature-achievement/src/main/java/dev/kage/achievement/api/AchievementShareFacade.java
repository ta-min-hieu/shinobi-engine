package dev.kage.achievement.api;

import dev.kage.achievement.domain.AchievementSharePort;

/** API công tắc khoe thành tựu vào bang (ADR-230). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class AchievementShareFacade {

    /** Không khoe gì (composition test). */
    public static final AchievementShareFacade DISABLED = new AchievementShareFacade(new AchievementSharePort() {
        @Override
        public boolean enabled(long characterId) {
            return false;
        }

        @Override
        public void setEnabled(long characterId, boolean enabled) {
            // không lưu
        }
    });

    private final AchievementSharePort port;

    /** Dựng ở composition root. */
    public AchievementShareFacade(AchievementSharePort port) {
        this.port = port;
    }

    /** {@code characterId} có đang bật khoe thành tựu vào bang không. */
    public boolean enabled(long characterId) {
        return port.enabled(characterId);
    }

    /** Bật / tắt. */
    public void setEnabled(long characterId, boolean enabled) {
        port.setEnabled(characterId, enabled);
    }
}
