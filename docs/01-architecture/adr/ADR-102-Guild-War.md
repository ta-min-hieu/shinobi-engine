# ADR-102: Bang chiến

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Bang chiến" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: bang chủ tuyên chiến bang khác (tốn vàng): 1 giờ hai bang đánh nhau ngoài vùng an toàn, đếm số lần hạ, bang thắng nhận thư; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (vùng an toàn) · [ADR-090](ADR-090-Spirit-Vein-Contest.md) (luồng nền + chốt nguyên tử) · `WorldFacade#pvpAllowed` (cờ PvP theo World, 2026-09-24) |

---

## Decision

**D1 — Tuyên chiến**: bang chủ tuyên chiến một bang khác theo tên, trả **10.000 linh thạch** (của bang chủ, đốt). Hai bang
mỗi bên tối đa một trận đang chờ/đang đánh; cặp vừa đánh xong phải chờ **24 giờ** mới tuyên lại. Trận bắt đầu **5 phút**
sau khi tuyên (báo cả hai bang), kéo dài **60 phút**.

**D2 — PvP theo CẶP người**: World chính nhận một luật mới "A được đánh B không" (thay cho cờ boolean cả World): đúng khi
World cho PvP (đấu trường) HOẶC A và B thuộc hai bang đang chiến VÀ cả hai đứng ngoài vùng an toàn. Bảng "nhân vật →
bang đang chiến" là ảnh chụp RAM luồng nền dựng lại mỗi 10 giây (tick thread chỉ đọc, không chạm DB). Đòn đánh thường
lẫn chiêu ENEMY đều đi qua luật này.

**D3 — Đếm**: mỗi lần một thành viên bị thành viên bang đối địch hạ trong lúc trận đang đánh ⇒ +1 điểm cho bang người hạ
(`guild_war.kills_a`/`kills_b`). Chết trong bang chiến hồi sinh như thường, không phạt thêm.

**D4 — Chốt**: hết giờ ⇒ chốt NGUYÊN TỬ một lần (khoá dòng `guild_war`). Bang nhiều điểm hơn thắng, mọi thành viên bang
thắng nhận thư **2.000 linh thạch**; hoà không ai nhận. Báo toàn server. Số là PLACEHOLDER cân bằng.

**D5 — Giao thức, chỉ thêm**: 733/734 `DeclareGuildWarCommand{target_guild_name}`/`Response{outcome}`, 735/736
`GetGuildWarStatusRequest`/`Response{wars[]}`.

**D6 — Client**: nút "Bang chiến" trong cửa sổ Bang (G) mở bảng trạng thái + ô tên bang + nút Tuyên chiến.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 10.000 vàng, chờ 5 phút, đánh 60 phút, PvP theo cặp ngoài vùng an toàn, đếm lần hạ, thư 2.000 cho bang thắng | Accepted |

## ADR cần tạo

- Không.

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 4).
2. ⏳ Kiểm sống bằng gameProbe.
