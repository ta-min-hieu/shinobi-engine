// tho_khien_da.js — Khiên Đá, active 2 hệ Thổ: khiên THẬT (GDD §4 "khiên") — SELF target, pool HP
// hấp thụ TRƯỚC khi trừ máu thật (SkillSystem.applyShield/StatusEffects.absorbWithShield).
(function () {
    return { effect: "shield", value: ctx.skill_dmg * 1.8 + 15, duration: 8.0 };
})()
