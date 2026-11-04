# ADR-167: Nhận hết thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Nhận hết thư" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: một nút nhận toàn bộ vàng/đồ đính kèm của mọi thư (dừng khi túi đầy) + xoá hết thư đã nhận). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-058](ADR-058-Send-Mail-Idempotency.md) · luồng lĩnh/xoá thư Phase 7 (opcode 904–907) |

---

## Decision

**D1 — Không đường ghi mới**: lệnh mới chỉ là vòng lặp ở gateway (`MailBulkCommands`) gọi ĐÚNG `MailFacade.claimMail`
cho từng thư còn vàng/đồ chưa lĩnh, theo thứ tự hộp thư — mỗi thư vẫn một transaction riêng, khoá túi, audit
`MAIL_CLAIM` như bấm tay. Không đổi schema.

**D2 — Túi đầy thì dừng**: thư đầu tiên trả `INVENTORY_FULL` ⇒ dừng, các thư sau còn nguyên; báo `bag_full`.

**D3 — Dọn thư**: `delete_claimed` ⇒ xoá mọi thư không còn gì để lĩnh (đã lĩnh, hoặc thư chỉ có chữ) qua
`MailFacade.deleteMail` (thư còn đồ chưa lĩnh vẫn bị facade từ chối như cũ). Client luôn gửi `true`.

**D4 — Giao thức, chỉ thêm**: `ClaimAllMailCommand{delete_claimed}` / `ClaimAllMailResponse{claimed, gold, items,
deleted, bag_full}` — 911/912.

**D5 — Client**: nút **Nhận hết** trong hòm thư (cạnh Làm mới); kết quả một dòng hệ thống (`MailBulkCoordinator`), hỏi
lại hộp thư + túi đồ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhận hết = lặp đường lĩnh thư sẵn có, dừng khi túi đầy, dọn thư đã xong; 911/912 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): `MailBulkCommands`, `Lot30Commands`, 911/912, nút Nhận hết, `MailBulkCoordinator`.
2. ✅ Test: `MailBulkCoordinatorTest`. Logic lĩnh/xoá là đường cũ đã có test Postgres.
3. ✅ Kiểm sống gameProbe 2026-11-04 (`--lot21=claimAllMail`): hộp thư 7 thư còn quà + 1 thư chữ ⇒ `lĩnh=7 vàng=750 món=30
   xoá=8` (gồm thư điểm danh 150 vàng vừa tới lúc vào game), lần hai rỗng; 5 thư mỗi thư một thanh kiếm khi túi còn 4
   ô ⇒ `lĩnh=4 đầy=true`, thư thứ năm còn nguyên.
4. Chờ chủ dự án: duyệt chữ `mail.claim_all.*`.
