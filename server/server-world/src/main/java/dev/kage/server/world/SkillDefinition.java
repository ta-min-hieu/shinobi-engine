package dev.kage.server.world;

/**
 * Tham số 1 skill (Phase 8 — thay hardcode {@code SkillConstants} 1-skill bằng registry nhiều
 * skill, xem {@link SkillRegistry}). TOÀN BỘ giá trị là PLACEHOLDER, chưa cân bằng qua playtest,
 * cùng tinh thần {@code CombatConstants}/{@code SkillConstants} cũ.
 *
 * @param code mã skill duy nhất, dùng ở {@code UseSkillCommand.skill_code}.
 * @param faction hệ phái được phép dùng skill này — {@code SkillSystem.tryCast} từ chối nếu
 *     caster khác hệ (chống dùng skill hệ khác, SECURITY.md).
 * @param slotIndex 0-2 = active, 3 = Tuyệt Kỹ — index vào {@code SkillCooldownComponent.remainingSeconds}
 *     (mảng cố định 4 phần tử/nhân vật, KHÔNG dùng Map&lt;String,Float&gt; để tránh boxing trong
 *     game loop — /CLAUDE.md §6).
 * @param targetKind cách diễn giải {@code targetEntityId} client gửi.
 * @param scriptResource đường dẫn script GraalJS (Điều 27/ADR-016) tính nội dung skill.
 * @param cooldownSeconds thời gian hồi chiêu.
 * @param chiCost Khí cần để cast — {@code -1} là dấu hiệu Tuyệt Kỹ "tốn TOÀN BỘ Khí hiện có" (GDD §4).
 * @param rangeMeters tầm skill.
 * @param requiredLevel cấp tối thiểu để cast — 1 cho active, 50 cho Tuyệt Kỹ (GDD §5).
 */
record SkillDefinition(
        String code,
        Element faction,
        int slotIndex,
        SkillTargetKind targetKind,
        String scriptResource,
        float cooldownSeconds,
        int chiCost,
        float rangeMeters,
        int requiredLevel) {

    static final int SLOT_COUNT = 4;
    static final int ULTIMATE_SLOT = 3;
    static final int CONSUME_ALL_CHI = -1;

    boolean isUltimate() {
        return chiCost == CONSUME_ALL_CHI;
    }
}
