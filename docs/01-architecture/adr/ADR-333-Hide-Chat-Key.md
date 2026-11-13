# ADR-333: Phím ẩn/hiện khung chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Phím ẩn/hiện khung chat" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: một phím tắt (đổi được) để giấu/hiện toàn bộ khung chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-190](ADR-190-Key-Rebinding.md) · [ADR-292](ADR-292-Chat-Idle-Fade.md) |

---

## Decision

**D1 — Phím:** thêm `KeyBindings.Binding.HIDE_CHAT` id `anchat`, mặc định **S** (chưa phím nào dùng), đổi được bằng `/phimtat anchat <phím>` (ADR-190). Bấm khi không gõ chữ và không có cửa sổ nào đang nhận phím ⇒ đảo ẩn/hiện.

**D2 — Ẩn:** `ChatPanel` vẫn nhận tin và chạy đồng hồ mờ (ADR-292) nhưng không vẽ; mở ô gõ (T) thì khung hiện lại trong lúc gõ. Thuần client, không lưu qua phiên.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Binding `anchat` (mặc định S); khung chat không vẽ khi ẩn, trừ lúc gõ; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `KeyBindings.Binding.HIDE_CHAT`, `ChatPanel.setHidden`/`isHidden` + nhánh phím trong `installKeyHandling`.
2. ✅ Test: `Lot50ClientTest.hideChatHasItsOwnDefaultKey`.
3. ⏳ Thuần client — cần chạy client thật để bấm phím.
