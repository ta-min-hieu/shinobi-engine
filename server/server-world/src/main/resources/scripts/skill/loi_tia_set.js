// loi_tia_set.js — Tia Sét, active 1 hệ Lôi: xuyên giáp THẬT (GDD §4 "đơn mục tiêu, xuyên giáp") —
// armor_pierce bỏ qua % RES hiệu dụng của mục tiêu TRƯỚC mitigation (CombatConstants.resolveSkillAttack
// overload 4 tham số).
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 1.25 + 6, armor_pierce: 0.3 };
})()
