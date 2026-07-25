package dev.kage.server.world.component;

/**
 * 1 hiệu ứng tạm thời đang hoạt động — mutable, gắn vào 1 slot của {@link StatusEffectComponent}.
 * Cấp phát lúc skill/status ÁP DỤNG (sự kiện rời rạc, không phải mỗi frame — giống
 * {@code SkillSystem.SkillOutcome}), KHÔNG cấp phát mới mỗi tick khi đếm ngược
 * ({@code StatusEffectSystem} chỉ mutate field {@code remainingSeconds}/{@code magnitude} tại chỗ).
 */
public final class StatusEffect {
    public StatusEffectType type;
    public float magnitude;
    public float remainingSeconds;

    public StatusEffect(StatusEffectType type, float magnitude, float remainingSeconds) {
        this.type = type;
        this.magnitude = magnitude;
        this.remainingSeconds = remainingSeconds;
    }
}
