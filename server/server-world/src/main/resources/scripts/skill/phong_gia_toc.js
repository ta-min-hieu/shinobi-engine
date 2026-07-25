// phong_gia_toc.js — Gia Tốc, active 2 hệ Phong: buff tốc độ THẬT (GDD §4 "tốc độ") — SELF target
// (SkillSystem ép targetEntityId = casterId, bỏ qua giá trị client gửi).
(function () {
    return { effect: "buff", stat: "move_speed", value: 0.5, duration: 6.0 };
})()
