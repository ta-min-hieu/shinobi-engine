package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Mảng slot CỐ ĐỊNH (tránh boxing/allocation mỗi frame — /CLAUDE.md §6) cho hiệu ứng tạm thời
 * (Phase 8, 4 hệ phái). {@code null} = slot trống. {@code MAX_SLOTS=4} đủ cho 1 debuff từ đối thủ +
 * vài buff tự thân cùng lúc (mỗi hệ tối đa 2-3 buff/debuff riêng theo thiết kế skill hiện tại).
 * Gắn cho CẢ nhân vật lẫn quái/boss lúc attach ({@code WorldFacade}), đối xứng 2 phe giống
 * {@link CombatStatsComponent}.
 */
public final class StatusEffectComponent implements Component {
    public static final int MAX_SLOTS = 4;
    public final StatusEffect[] slots = new StatusEffect[MAX_SLOTS];
}
