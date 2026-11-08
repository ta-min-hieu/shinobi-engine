# ADR-216: Xoá hàng loạt thư đã xong

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Xoá hàng loạt thư" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: một lệnh xoá mọi thư đã nhận xong, giữ lại thư còn quà). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-167](ADR-167-Claim-All-Mail.md) (nhận hết thư) · [ADR-217](ADR-217-Pin-Mail.md) (ghim thư) |

---

## Decision

**D1 — "Đã xong"** = cùng luật xoá từng thư (`DeleteMailUseCase`): đã nhận, hoặc không có vàng / vật phẩm đính kèm.
Thư còn quà chưa nhận **không bao giờ** bị xoá; thư đang ghim (ADR-217) cũng giữ lại. Một transaction, trả số thư đã
xoá.

**D2 — Protocol**: `DeleteFinishedMailCommand{}` / `DeleteFinishedMailResponse{deleted}` 915/916.

**D3 — Client**: `/xoathu` (hoặc `/cleanmail`); xong thì hỏi lại hộp thư. Gợi ý dùng sau nút "Nhận hết" trong hộp thư (ADR-167).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Xoá thư đã nhận / không đính kèm, trừ thư ghim; 915/916; `/xoathu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `MailHousekeepingPort`/`Facade`, `PostgresMailHousekeepingAdapter`,
   `MailCleanupCoordinator`.
2. ✅ Test: `Lot36IntegrationTest` (xoá thư thông báo + thư đã nhận, giữ thư còn vàng và thư ghim).
3. ✅ Kiểm sống gameProbe 2026-11-08: chèn một thư thông báo (không quà) cho ĐạoHữu01; `cleanMail` ⇒ `đãXoá=1` — chỉ lá
   đó; ba thư còn quà chưa nhận (kể cả lá đang ghim) giữ nguyên.
