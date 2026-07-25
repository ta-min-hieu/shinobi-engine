package dev.kage.server.world.component;

/**
 * Loại hiệu ứng tạm thời do skill hệ phái tạo ra (Phase 8, 4 hệ phái mới — GDD §4/§5 "khống chế,
 * hồi phục, khiên"). Hồi máu (heal) KHÔNG phải status effect — áp dụng NGAY (cộng thẳng
 * {@code HealthComponent}), không cần theo dõi theo thời gian.
 */
public enum StatusEffectType {
    /** ±% tốc độ di chuyển — dùng chung cho debuff Slow (âm) lẫn buff tăng tốc (dương). */
    MOVE_SPEED_MOD,
    /** Chặn hành động (đánh thường, dùng skill, di chuyển) — {@code magnitude} không dùng. */
    STUN,
    /** Pool HP hấp thụ TRƯỚC khi trừ {@code HealthComponent} — {@code magnitude} = HP còn lại,
     *  giảm dần khi bị đánh, hết hạn khi {@code magnitude<=0} HOẶC hết {@code remainingSeconds}. */
    SHIELD,
    /** ±% DEF — tính lại lúc resolve đòn đánh, KHÔNG sửa {@code CombatStatsComponent} gốc. */
    DEF_MOD,
    /** ±% RES — tính lại lúc resolve đòn đánh, KHÔNG sửa {@code CombatStatsComponent} gốc. */
    RES_MOD,
    /** ±% tỉ lệ bạo kích — tính lại lúc resolve đòn đánh, KHÔNG sửa {@code CombatStatsComponent} gốc. */
    CRIT_MOD
}
