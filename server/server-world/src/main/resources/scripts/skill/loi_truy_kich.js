// loi_truy_kich.js — Truy Kích, active 2 hệ Lôi: "đơn mục tiêu" mở đòn (GDD §4) — bonus damage khi
// mục tiêu CÒN GẦN ĐẦY máu (ngược fireball "đòn kết liễu" mục tiêu yếu — Lôi thiên về mở màn burst).
(function () {
    var base = ctx.skill_dmg * 1.3 + 6;
    if (ctx.target_hp_percent > 0.7) {
        base = base * 1.4;
    }
    return { effect: "damage", value: base };
})()
