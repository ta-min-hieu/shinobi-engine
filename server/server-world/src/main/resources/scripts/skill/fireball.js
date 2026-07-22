// fireball.js — skill hệ Hỏa, nội dung damage thật nằm ở ĐÂY (Điều 27/ADR-016), KHÔNG hardcode
// Java. Java (SkillSystem/CombatConstants.resolveSkillAttack) chỉ lo cơ chế dùng chung mọi skill:
// miss/crit/kháng phép (RES) — script chỉ trả "damage thô" bước 2 của pipeline (GDD §6).
//
// ctx là facade dữ liệu thuần (SCRIPTING.md §4-5, ProxyObject.fromMap) — KHÔNG có object Java nào
// lộ ra đây:
//   ctx.skill_dmg          SKILL_DMG của người dùng skill (đã derive từ INT — CombatConstants)
//   ctx.target_hp_percent  % máu hiện tại của mục tiêu (0.0 - 1.0)
(function () {
    var base = ctx.skill_dmg * 1.4 + 8;
    if (ctx.target_hp_percent < 0.3) {
        return base * 1.5; // mục tiêu dưới 30% máu — đòn kết liễu
    }
    return base;
})()
