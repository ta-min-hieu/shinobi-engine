package dev.kage.server.world;

/**
 * Chỉ số nền (STR/AGI/INT/VIT) — gateway đọc từ {@code character_stat} qua {@code CharacterFacade}
 * rồi truyền vào lúc attach (server-world không biết DB, /CLAUDE.md §5). {@link CombatConstants}
 * derive thành chỉ số chiến đấu (ATK/DEF/HIT%...) theo công thức GDD §5.
 */
public record CharacterBaseStats(int str, int agi, int intelligence, int vit) {
}
