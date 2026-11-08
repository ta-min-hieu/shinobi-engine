# ADR-277: Thư nháp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Thư nháp" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Lưu thư đang viết dở, mở lại không mất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-058](ADR-058-Send-Mail-Idempotency.md) |

---

## Decision

**D1 — Thuần client.** Đóng khung thư (kể cả lúc rời màn world) ⇒ bốn ô soạn (người nhận, tiêu đề, nội dung, linh thạch)
lưu vào Preferences; mở khung khi ô soạn đang trống ⇒ điền lại bản nháp; gửi thành công ⇒ xoá bản nháp. Một bản nháp, chỉ
trên máy này. Không gửi gì lên server.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Preferences; lưu lúc đóng, điền lúc mở, xoá khi gửi OK | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `MailDraftStore`, `MailPanel.composerContents/fillComposer/setOnClosed`, `MailCoordinator`.
2. ✅ Test: `Lot43ClientTest.mailDraftBlank`.
