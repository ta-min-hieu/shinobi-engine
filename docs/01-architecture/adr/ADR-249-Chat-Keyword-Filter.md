# ADR-249: Lọc chat theo từ khoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lọc chat theo từ khoá" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: tự ẩn tin chat chứa những từ mình chọn, lưu trên máy (thuần client)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-229](ADR-229-Chat-Channel-Filter.md) (lọc kênh chat — cùng chỗ lưu Preferences) |

---

## Decision

**D1 — Thuần client, lưu Preferences** (cùng tệp với lọc kênh ADR-229). `/locchu them <từ>` (hoặc `/mute add`),
`/locchu bo <từ>`, `/locchu` liệt kê. Tối đa 20 từ, mỗi từ 2–24 ký tự. Khớp **không dấu, không phân biệt hoa thường,
chứa trong tin** (`ban vang` lọc cả "BÁN VÀNG"). Chỉ ẩn tin **của người chơi** — dòng hệ thống không bao giờ bị ẩn —
và tin vẫn tới máy (chỉ không hiện), giống ẩn người bị chặn (ADR-091).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Từ khoá lưu Preferences, khớp không dấu chứa-trong, chỉ ẩn tin người chơi; `/locchu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `KeywordFilterCoordinator`, `ChatCoordinator.hideBodiesMatching`.
2. ✅ Test: `Lot40bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
