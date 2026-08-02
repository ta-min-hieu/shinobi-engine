// hoa_bung_no.js — Bùng Nổ, active 3 hệ Hỏa: sát thương BÙNG NỔ THẬT (GDD §5 dòng 60) — đòn nặng
// hơn Cầu Lửa/Tia Lửa, xuyên giáp NHẸ (vụ nổ phá một phần kháng phép mục tiêu, không phải bản sắc
// xuyên giáp chính như hệ Lôi nên armor_pierce nhỏ hơn loi_tia_set.js).
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 1.6 + 12, armor_pierce: 0.15 };
})()
