# ADR-321: Báo toàn server khi cường hoá cao

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo toàn server khi cường hoá cao" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: cường hoá thành công từ +10 trở lên thì cả server thấy một dòng chúc mừng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) · [ADR-188](ADR-188-Chat-Item-Link.md) |

---

## Decision

**D1 —** Sau khi trả kết quả cường hoá `SUCCESS`, đọc lại món: mức mới ≥ +10 ⇒ báo toàn server một dòng chat song ngữ
"X cường hoá thành công lên +N!" **kèm `linked_item`** (ADR-188) để client ghi đúng tên món từ dữ liệu server (server
không có chữ hiển thị tên món). Việc phụ: lỗi dừng ở đây, không ảnh hưởng kết quả cường hoá. Không opcode, không migration.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cường hoá ≥ +10 ⇒ chat toàn server kèm `linked_item` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `Lot49Commands.announceEnhance`/`enhanceNotice`, nhánh cường hoá trong `GatewayChannelHandler`.
2. ✅ Test: `Lot49ServerTest.enhanceNoticeCarriesLinkedItem`.
3. ⏳ Cần một lần cường hoá thật lên ≥ +10 (tỉ lệ thấp, tốn vàng) — chưa ép được bằng probe; nội dung dòng báo + món kèm phủ bằng `Lot49ServerTest.enhanceNoticeCarriesLinkedItem`.
