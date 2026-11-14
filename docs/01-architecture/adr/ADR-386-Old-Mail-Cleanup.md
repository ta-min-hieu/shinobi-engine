# ADR-386: Tự dọn thư cũ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tự dọn thư cũ" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: Server tự xoá thư đã đọc quá 30 ngày (không quà chưa nhận, không ghim, không lưu trữ)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-216](ADR-216-Delete-Finished-Mail.md) · [ADR-354](ADR-354-Mail-Archive.md) |

---

## Decision

**D1 — Luật:** thư bị xoá khi `read_at` cũ hơn 30 ngày VÀ không ghim (ADR-217) VÀ không lưu trữ (ADR-354) VÀ không còn quà chưa nhận (`claimed_at` đã có, hoặc không vàng và không vật phẩm). Thư chưa đọc không bao giờ bị dọn. Chạy trong `RetentionJanitor` sẵn có (6 giờ/lượt), một câu lệnh CTE xoá `mail_item` rồi `mail`.

**D2:** chỉ mục MỚI `idx_mail_read_at` một phần (V182). Không protocol, không client: muốn giữ thư thì ghim hoặc lưu trữ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Luật dọn trong `RetentionJanitor`, chỉ mục `idx_mail_read_at` (V182) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `RetentionJanitor.purgeOldReadMail` (gọi trong `purgeSocialQuietly`).
2. ✅ Test: `Lot57IntegrationTest.purgesOnlyOldReadMailWithoutPendingGifts` (5 loại thư, chỉ 1 bị xoá).
3. ✅ Server khởi động với V182 và lịch dọn mới; luật dọn kiểm bằng test Postgres (thư cũ đã đọc bị xoá, thư ghim/có vàng/mới/chưa đọc giữ nguyên) — không chạy dọn trên dữ liệu thử để giữ nguyên thư mẫu.
