# ADR-114: Nhiệm vụ hộ tống

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Nhiệm vụ hộ tống" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: loại mục tiêu quest mới: hộ tống NPC đi từ A tới B dọc trục, NPC có máu và bị quái nhắm; NPC chết = thất bại, nhận lại được; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-055 (nhiệm vụ ngày) · feature-quest/CLAUDE.md (mục tiêu loại 2/3 đi qua sự kiện, không đường vào mới) |

---

## Decision

**D1 — Đoàn hộ tống là thực thể của World**: nhận quest hộ tống ⇒ server sinh một thực thể "người được hộ tống" (có
máu, không đánh trả) ở điểm A trên world chính, đi thẳng theo trục X về B với tốc độ cố định **khi người hộ tống đứng
trong 6 m** (bỏ đi xa thì đoàn đứng chờ). Tham số đoàn ở bảng `escort_route(quest_code, start_x, end_x, hp,
speed)` (V91). Một người một đoàn.

**D2 — Bị quái nhắm**: quái ĐANG CÓ MỤC TIÊU (đang giao chiến) trong **2 m** quanh đoàn gây sát thương bằng công của nó
mỗi nhịp đánh lên đoàn — người hộ tống phải dọn quái dọc đường. Không đổi AI quái (không thêm loại mục tiêu mới).

**D3 — Kết quả là SỰ KIỆN quest**: đoàn tới B ⇒ sự kiện `escort_arrived{route}` vào `ProcessQuestEventUseCase` (đường vào
duy nhất, như `npc_talked`) ⇒ script quest `complete()`. Đoàn hết máu ⇒ sự kiện `escort_failed` ⇒ script xoá tiến độ;
server bỏ quest về "chưa nhận" (dùng lại đường bỏ quest ADR-054) để nhận lại được. Người hộ tống rời world chính/ngắt
kết nối ⇒ đoàn biến mất, quest về "chưa nhận".

**D4 — Nội dung**: quest ngày mới `daily_caravan_escort` (Chu Hoành nhờ đưa xe linh thảo từ tông môn tới trạm Linh
Mạch, x = 0 → 82).

**D5 — Giao thức, chỉ thêm**: 621..624 đã dùng ⇒ 625 `EscortStateEvent{entity_id, x, hp, max_hp, state}` (gửi người hộ tống
mỗi khi máu/trạng thái đổi và mỗi 1 giây khi đang đi). Client vẽ đoàn bằng hộp tạm (chưa có model) + thanh máu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thực thể đoàn trong World, quái đang giao chiến trong 2 m gây sát thương, kết quả là sự kiện quest | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 3).
2. ⏳ Kiểm sống bằng gameProbe.
