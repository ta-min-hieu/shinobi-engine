// ancient_stone_colossus.js — AI của Cổ Thạch Khổng Lồ, trận ĐÓNG arc 4 (quest 30 fell_the_ancient_colossus, @408).
// Nội dung chiêu nằm ở ĐÂY (Điều 27/ADR-016); Java lo cơ chế dậm đất (ADR-145): gồng 1,5 giây (tự choáng, không đánh
// thường), vòng đỏ bán kính 4 m, hết giờ nổ vào mọi người còn đứng trong vòng — bước ra kịp là thoát.
//
// STORY: arc-4 §6 — khối đá cổ mà linh mạch chết đánh thức; nó không có trí, chỉ có NHỊP: cứ hồi chiêu xong là dậm.
//
// ctx (SCRIPTING.md §4-5): hp_percent, already_enraged, heavy_strike_ready, base_atk.
// Trả { action, raw_damage }; action "ground_slam" là chiêu diện rộng có báo trước (ADR-145).
//
// CÂN BẰNG (đề xuất, ADR-145): ×1,4 ATK ≈ 1,7 nhát thường sau kháng của bộ Kim Đan — xấp xỉ đúng số nhát thường boss
// bỏ lỡ trong 1,5 giây gồng. Người ĐỨNG YÊN mất gần như y như không có chiêu (biên thật +2 của GoldenCoreBalanceTest
// giữ nguyên); người BƯỚC RA được lời trọn ~1,5 nhát.
(function () {
    if (ctx.heavy_strike_ready) {
        return { action: "ground_slam", raw_damage: ctx.base_atk * 1.4 };
    }
    return { action: "basic_attack", raw_damage: 0 };
})()
