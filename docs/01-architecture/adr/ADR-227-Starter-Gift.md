# ADR-227: Gói quà tân thủ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Gói quà tân thủ" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: nhân vật mới nhận một thư quà — đan hồi máu, ít linh thạch — một lần duy nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (Tiểu Hồi Xuân Đan) |

---

## Decision

**D1 — Một thư hệ thống khi tạo nhân vật**: **5 Tiểu Hồi Xuân Đan + 200 linh thạch** (PLACEHOLDER cân bằng), tiêu đề
"Quà nhập môn / Welcome gift". Đúng một lần mỗi nhân vật: cờ `character.starter_gift_sent` (V166) lật bằng một câu
UPDATE có điều kiện (chỉ một lần thắng) **trước** khi gửi, và khoá chống trùng của thư suy từ id nhân vật. Nhân vật tạo
TRƯỚC ngày thi hành không nhận bù.

**D2 — Việc phụ**: gửi sau khi lệnh tạo nhân vật đã trả lời thành công; lỗi gửi thư chỉ ghi WARN, không làm hỏng việc
tạo. Không opcode mới — người chơi thấy thư trong hộp thư như mọi thư hệ thống.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thư 5 đan + 200 linh thạch lúc tạo nhân vật, cờ một lần (V166) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): V166, `StarterGiftPort`/`Facade`, `PostgresStarterGiftAdapter`,
   `Lot37Commands.grantStarterGift`.
2. ✅ Test: `Lot37IntegrationTest` (cờ chỉ lật một lần).
3. ✅ Kiểm sống gameProbe 2026-11-08 (V166): tạo nhân vật thử QuaTanThu ⇒ hộp thư có ngay "Quà nhập môn / Welcome gift" kèm
   200 linh thạch + 5 Tiểu Hồi Xuân Đan, cờ `starter_gift_sent = TRUE`. Nhân vật thử đã đặt hẹn xoá (ADR-205).
