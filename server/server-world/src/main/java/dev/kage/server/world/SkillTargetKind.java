package dev.kage.server.world;

/**
 * Cách {@code SkillSystem.tryCast} diễn giải {@code targetEntityId} client gửi (Phase 8, 4 hệ
 * phái) — server KHÔNG tin client chọn đúng loại mục tiêu, tự resolve/validate lại theo định nghĩa
 * skill ({@link SkillDefinition}), giống mọi validate khác trong dự án (SECURITY.md §4).
 */
enum SkillTargetKind {
    /** Mục tiêu địch — phải còn sống, trong tầm (hành vi gốc của fireball, giữ nguyên). */
    ENEMY,
    /** Bỏ qua {@code targetEntityId} từ client — LUÔN nhắm chính caster (buff tự thân). */
    SELF,
    /** Mục tiêu phải là NHÂN VẬT (entity ID dương) còn sống, trong tầm — dùng cho heal đồng minh. */
    ALLY
}
