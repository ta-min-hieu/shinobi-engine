package dev.kage.server.world;

/**
 * Bảng chỉ số của một nhân vật (ADR-056) — chỉ số NỀN + trang bị, KHÔNG gồm buff/debuff tạm thời.
 * Dựng bởi {@link WorldFacade#statSheet}; cùng công thức {@code CombatConstants} mà World dùng lúc
 * attach, nên con số trên bảng là con số trận đánh thật dùng (trừ hiệu ứng tạm thời).
 *
 * <p>Tỉ lệ bằng PHẦN NGHÌN nguyên — đúng thứ đi trên dây.
 */
public record CharacterStatSheet(
        int level,
        int strength,
        int agility,
        int intelligence,
        int vitality,
        int maxHp,
        int equipmentBonusHp,
        int maxChi,
        int attack,
        int skillDamage,
        int defense,
        int resistance,
        int hitPermille,
        int dodgePermille,
        int critPermille,
        int critDamagePercent,
        long combatPower) {}
