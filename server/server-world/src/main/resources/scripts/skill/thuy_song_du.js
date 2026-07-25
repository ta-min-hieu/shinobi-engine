// thuy_song_du.js — Sóng Dữ, active 1 hệ Thủy (ENEMY damage-nuke, GDD §4/§5).
// ctx.skill_dmg: SKILL_DMG người dùng skill; ctx.target_hp_percent: % máu mục tiêu (0.0-1.0).
// Hợp đồng trả về chung mọi skill (SkillSystem.parseScriptEffect):
//   { effect: "damage"|"heal"|"shield"|"buff"|"debuff", value, stat?, duration?, armor_pierce? }
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 1.2 + 5 };
})()
