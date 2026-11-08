# ADR-245: Tô sáng khi bị nhắc tên

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tô sáng khi bị nhắc tên" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: tin chat có tên mình được tô màu nổi và báo một dòng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-152](ADR-152-Broadcast-Talisman.md) (màu loa) · [ADR-229](ADR-229-Chat-Channel-Filter.md) (lọc kênh chat) |

---

## Decision

**D1 — Nhận diện ở client.** Tin của **người khác** có tên nhân vật mình — không phân biệt hoa thường, **không dấu**
(`ĐạoHữu01` khớp `daohuu01`), phải đứng riêng thành từ (`xdaohuu01` không khớp). Tin hệ thống và tin của chính mình (kể
cả khi tên người gửi mang danh hiệu đứng trước) không tính.

**D2 — Tô màu + báo.** Dòng đó đổi sang màu riêng `MENTION` (xanh lơ sáng `#5FE7FF`, khác mọi kênh) trong khung chat,
và thêm một dòng hệ thống "X vừa nhắc tên bạn". Không âm thanh (client chưa có hệ âm thanh UI). Không opcode.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Khớp tên không dấu, nguyên từ, từ người khác ⇒ dòng màu `MENTION` + một dòng báo; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `MentionWatch`, `ChatCoordinator.useOwnName`, `ChatPanel.LineKind.MENTION`.
2. ✅ Test: `Lot39bAlertsTest.mentionMatchesWholeNameWithoutAccentsFromOthersOnly`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
