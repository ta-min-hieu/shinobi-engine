package dev.kage.achievement.domain;

/** Công tắc "khoe thành tựu vào bang" của một nhân vật (ADR-230). Mặc định bật. */
public interface AchievementSharePort {

    boolean enabled(long characterId);

    void setEnabled(long characterId, boolean enabled);
}
