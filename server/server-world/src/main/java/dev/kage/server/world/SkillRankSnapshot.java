package dev.kage.server.world;

/**
 * Bậc chiêu theo Ô (0..3) của linh căn hiện tại (ADR-071) — bản dịch gateway đưa vào World, cùng khuôn
 * {@link EquipmentBonusSnapshot}: server-world không biết feature-character tồn tại.
 */
public record SkillRankSnapshot(int slot0, int slot1, int slot2, int slot3) {

    /** Mọi chiêu bậc 1 — không tăng gì. */
    public static final SkillRankSnapshot NONE = new SkillRankSnapshot(1, 1, 1, 1);

    /** Bậc của ô {@code slot}; ô lạ = 1. */
    public int rankOf(int slot) {
        return switch (slot) {
            case 0 -> slot0;
            case 1 -> slot1;
            case 2 -> slot2;
            case 3 -> slot3;
            default -> 1;
        };
    }
}
