// hoa_tia_lua.js — Tia Lửa, active 2 hệ Hỏa: sát thương LIÊN TỤC THẬT (GDD §5 dòng 60 "sát thương
// liên tục, bùng nổ") — cooldown thấp nhất trong mọi skill hiện có (spam nhanh, dồn sát thương theo
// thời gian), hệ số damage thấp hơn Cầu Lửa để bù lại tần suất cast cao (xem SkillRegistry).
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 0.9 + 4 };
})()
