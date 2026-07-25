// tho_trong_kich.js — Trọng Kích, active 1 hệ Thổ (ENEMY damage-nuke nặng, cooldown dài hơn — xem
// SkillDefinition ở SkillRegistry, GDD §4 "phòng thủ, khiên" thiên phòng thủ nên đòn tấn công chậm).
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 1.6 + 10 };
})()
