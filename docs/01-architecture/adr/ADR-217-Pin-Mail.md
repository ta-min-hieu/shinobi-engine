# ADR-217: Ghim thư quan trọng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Ghim thư quan trọng" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: ghim một thư để nó nằm đầu hộp thư và không bị xoá hàng loạt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-216](ADR-216-Delete-Finished-Mail.md) (xoá hàng loạt) |

---

## Decision

**D1 — Cột `mail.pinned`** (V164, mặc định FALSE). Thư ghim: server trả lên đầu danh sách (giữ thứ tự còn lại), `/xoathu`
bỏ qua. Xoá từng thư bằng tay vẫn xoá được thư ghim (người chơi tự chọn). Chỉ ghim được thư của chính mình.

**D2 — Protocol, chỉ thêm**: `MailView.pinned = 9`; `PinMailCommand{mail_id, pinned}` / `PinMailResponse{ok, pinned}`
917/918.

**D3 — Client**: mở một lá thư rồi `/ghimthu` (hoặc `/pinmail`), `/boghim` (`/unpinmail`) để bỏ. Tiêu đề thư ghim có
dấu `[*]` (ký tự ASCII — font tiếng Việt không có ký hiệu ghim).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `pinned` (V164), thư ghim lên đầu + không bị xoá hàng loạt; field 9; 917/918; `/ghimthu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): V164, `MailHousekeepingPort.setPinned/pinned`, sắp thư ghim lên đầu ở
   `handleListMailBlocking`, `MailCleanupCoordinator`.
2. ✅ Test: `Lot36IntegrationTest` (chỉ ghim được thư của mình, thư ghim không bị xoá hàng loạt).
3. ✅ Kiểm sống gameProbe 2026-11-08: `pinMail:12/on` ⇒ `ok=true ghim=true`; ghim thư của người khác (thư 23 của BaoDo01)
   ⇒ `ok=false`; `cleanMail` không đụng thư ghim; `pinMail:12/off` ⇒ bỏ ghim (cột về FALSE).
