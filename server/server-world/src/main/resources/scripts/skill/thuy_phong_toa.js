// thuy_phong_toa.js — Phong Toả, active 2 hệ Thủy: khống chế THẬT (GDD §4 "khống chế, hồi phục") —
// debuff làm chậm mục tiêu, không gây damage. "stat" khớp SkillSystem.parseStatusEffectType.
(function () {
    return { effect: "debuff", stat: "move_speed", value: -0.4, duration: 4.0 };
})()
