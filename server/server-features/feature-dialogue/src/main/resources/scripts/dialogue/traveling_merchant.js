// traveling_merchant.js — nội dung hội thoại NPC "traveling_merchant" (Điều 27, GAMEPLAY.md §9.2), ADR-131.
//
// STORY: arc-2 §6 — thương hội liên tông (STORY §7 "Đấu giá: thương hội liên tông tổ chức") cử người đi buôn dọc
// các trạm tiếp ứng. NPC ĐỘNG: chỉ đứng cạnh trạm của lần ghé đang diễn ra (ADR-118 D1), server đặt/gỡ mỗi phút.
// Vô danh có chủ đích (không thêm tên riêng vào §5).
//
// ⚠️ NHỊP HÉ LỘ (§8 quy tắc 2): chỉ chuyện buôn bán. Không Thiên Môn, không Huyết Sa.
//
// "open_merchant" (ADR-131) mở hàng của lần ghé; mua vẫn phải đứng gần trạm (ADR-118 D3 — server kiểm).
({
    start: {
        text_key: "dialogue.traveling_merchant.greeting",
        options: [
            { label_key: "dialogue.option.open_merchant", action: "open_merchant" },
            { label_key: "dialogue.option.bye", action: "end" }
        ]
    }
})
