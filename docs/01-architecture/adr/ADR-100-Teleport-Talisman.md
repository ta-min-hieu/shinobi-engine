# ADR-100: Truyền tống phù

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Truyền tống phù" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: vật phẩm dùng để về tông môn / điểm truyền tống đã mở dọc trục chính; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) D4 (dời nhân vật trong world chính — `goto`) · [ADR-051](ADR-051-World-Changed-Event.md) · [ADR-046](ADR-046-Health-Recovery.md) |

---

## Decision

**D1 — Điểm truyền tống (V87)**: `waypoint(code, name_key, x, y, sort_order)` là THAM SỐ; seed **Tông Môn** (x=0, luôn mở)
và ba điểm dọc trục chính. `character_waypoint(character_id, waypoint_code)` — điểm đã MỞ.

**D2 — Mở điểm**: luồng nền của gateway mỗi **5 giây** xem vị trí world chính (registry) của người đang online; đứng trong
**4 m** quanh một điểm chưa mở ⇒ ghi mở + báo một dòng hệ thống. Không cần lệnh.

**D3 — Truyền tống**: lệnh `TeleportCommand{waypoint_code}` — điểm phải đã mở, nhân vật ở world chính, có ≥ 1
**Truyền Tống Phù** (`teleport_talisman`, bán ở cửa hàng tông môn 500 linh thạch). Trừ một phù (khoá túi chung) rồi dời
nhân vật trong CÙNG khu vực world chính bằng đúng đường đổi World của ADR-051 (lý do mới `TELEPORT`) — cùng thân với
lệnh GM `goto`, đích là toạ độ điểm. Dời hỏng sau khi đã trừ phù ⇒ trả lại phù (bù trong giao dịch riêng, log WARN).

**D4 — Giao thức, chỉ thêm**: 317/318 `ListWaypointsRequest`/`Response{waypoints[]}`, 319/320
`TeleportCommand{waypoint_code}`/`Response{outcome}`; `WorldChangeReason.WORLD_CHANGE_REASON_TELEPORT`.

**D5 — Client**: cửa sổ Hằng ngày (E) tab "Truyền tống" — các điểm (đã mở / chưa), nút Đi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Điểm là dữ liệu, mở bằng cách đứng gần, phù tiêu hao, dời qua đường ADR-051 cùng khu vực | Accepted |

## ADR cần tạo

- Không.

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 3).
2. ⏳ Kiểm sống bằng gameProbe.
