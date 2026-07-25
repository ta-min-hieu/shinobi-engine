// thuy_hoi_phuc.js — Hồi Phục, active 3 hệ Thủy: hồi phục THẬT (GDD §4) — ALLY target (SkillSystem
// cho phép nhắm chính mình), cộng thẳng HealthComponent, cap ở max (SkillSystem.applyHeal).
(function () {
    return { effect: "heal", value: ctx.skill_dmg * 1.1 + 10 };
})()
