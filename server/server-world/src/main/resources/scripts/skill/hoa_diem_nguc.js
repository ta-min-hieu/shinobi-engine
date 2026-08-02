// hoa_diem_nguc.js — Diêm Ngục, Tuyệt Kỹ hệ Hỏa (GDD §5, Lv50, tốn TOÀN BỘ Khí) — đòn nặng NHẤT
// trong 4 active của Hỏa (fireball/hoa_tia_lua/hoa_bung_no đều nhẹ hơn); hệ số damage cao nhất
// trong mọi Tuyệt Kỹ hiện có vì Hỏa không có CC/khiên/hồi phục nào để chia sẻ ngân sách sức mạnh —
// toàn bộ dồn vào sát thương thuần (GDD §5 dòng 60 "bùng nổ" ở mức tối đa).
(function () {
    return { effect: "damage", value: ctx.skill_dmg * 3.0 + 33 };
})()
