package dev.kage.server.world;

/**
 * Bản sao nhẹ của {@code feature-character.domain.Faction} riêng cho {@code server-world} —
 * module này KHÔNG import {@code feature-character} (đúng ranh giới module, cùng pattern
 * {@link CharacterBaseStats} tự có bản sao STR/AGI/INT/VIT thay vì phụ thuộc feature-character).
 * Gateway map {@code Faction} → {@code Element} lúc attach (xem {@code GatewayChannelHandler}).
 *
 * <p>{@link #NONE} = quái/boss "chưa có hệ phái" (CombatConstants.MONSTER_STATS/BOSS_STATS) — luôn
 * trung lập với mọi hệ, cùng hành vi với {@link #LOI} (GDD §4 "Lôi trung lập để cân bằng meta") dù
 * ý nghĩa khác nhau (LOI là hệ thật của nhân vật, NONE là "không có hệ").
 *
 * <p>{@code public} vì {@code component.CombatStatsComponent} (package con) cần tham chiếu — các
 * component khác trong dự án đều {@code public}, field {@code public} theo Điều 43 (ECS.md §3.1).
 */
public enum Element {
    HOA,
    THUY,
    PHONG,
    LOI,
    THO,
    NONE
}
