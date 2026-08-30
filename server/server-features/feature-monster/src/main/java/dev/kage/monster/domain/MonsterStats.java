package dev.kage.monster.domain;

/**
 * Chỉ số chiến đấu của một loài quái, tách khỏi {@link MonsterTemplate} để record đó không vượt
 * ngưỡng dễ đọc — và vì đây đúng là một nhóm có nghĩa riêng: mọi thứ {@code server-world} cần để
 * dựng {@code CombatStatsComponent}, không gồm tên/art/EXP/bảng rơi.
 *
 * <p>{@code element} là TÊN enum {@code Element} của {@code server-world} dưới dạng chuỗi
 * ({@code "NONE"}, {@code "HOA"}...). Để chuỗi thay vì kiểu enum thật vì module này KHÔNG được phụ
 * thuộc {@code server-world} (và ngược lại) — gateway là nơi dịch, đúng khuôn ADR-036 đã dựng cho
 * {@code EquipmentBonus} → {@code EquipmentBonusSnapshot}.
 */
public record MonsterStats(
        int level,
        int maxHp,
        int atk,
        int skillDmg,
        int def,
        int res,
        int hitPercent,
        int dodgePercent,
        int critPercent,
        String element) {

    public MonsterStats {
        if (maxHp <= 0) {
            throw new IllegalArgumentException("maxHp phải > 0: " + maxHp);
        }
        if (level < 1) {
            throw new IllegalArgumentException("level phải >= 1: " + level);
        }
        requirePercent(hitPercent, "hitPercent");
        requirePercent(dodgePercent, "dodgePercent");
        requirePercent(critPercent, "critPercent");
        if (element == null || element.isBlank()) {
            throw new IllegalArgumentException("element không được rỗng");
        }
    }

    private static void requirePercent(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " phải trong 0..100: " + value);
        }
    }
}
