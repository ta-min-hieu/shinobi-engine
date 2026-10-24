# ADR-083: Thư chở món cường hoá giữ nguyên cấp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Thư gửi đồ cường hoá" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: mở khoá điều feature-mail ghi "còn mở" — `mail_item` + `MailAttachmentView` chở `enhance_level`, gửi được món +7 mà không mất cấp). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-mail/CLAUDE.md ("Còn mở — chỉ ký quỹ được món chưa cường hoá") · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) |

---

## Context

Từ 2026-08-28 thư chỉ ký quỹ món `enhance_level = 0`: `mail_item` không có cột cấp, cấp món sẽ bị xoá khi đi qua thư,
nên gửi món cường hoá bị từ chối (`ATTACHMENT_NOT_OWNED`).

## Decision

**D1 — Dữ liệu** (V79): `mail_item.enhance_level INT NOT NULL DEFAULT 0 CHECK 0..15`. Dòng cũ = 0.

**D2 — Một dòng đính kèm = mẫu + số lượng + cấp.** Người gửi chọn đúng cấp; server chỉ trừ chồng ở ĐÚNG cấp đó (cùng
luật cũ: không đụng món đang mặc, đã khảm ngọc, linh thú, phần đang chào giao dịch). Gửi sai cấp ⇒ `ATTACHMENT_NOT_OWNED`.

**D3 — Nhận thư**: món cường hoá mở ô RIÊNG mang đúng cấp, không bao giờ gộp chồng.

**D4 — Giao thức, chỉ thêm**: `MailAttachmentView.enhance_level = 3` (dùng cho cả lệnh gửi lẫn danh sách thư).
Client hiện "+N" sau dòng đính kèm.

## Consequences

- Port `InventoryGatewayPort` có thêm `consumeItem(tx, nhân vật, MailAttachment)`; bản mặc định từ chối món cường hoá
  (fake cũ không xoá cấp im lặng). Helper SQL túi đồ nhận `ItemLine` (mẫu + số lượng + cấp).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột cấp ở `mail_item`, trừ đúng cấp, nhận ra ô riêng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V79__mail_item_enhance_level.sql` · `PostgresMailInventoryGatewayAdapter` · `TemplateQuantityConsumer`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20) — test Postgres `shouldCarryEnhanceLevelThroughMail` (gửi +7 ⇒ người nhận có +7;
   gửi cấp 0 khi chỉ có +7 ⇒ `ATTACHMENT_NOT_OWNED`).
2. ✅ Kiểm sống 2026-10-24 (gameProbe `--sendMail=Benkei:0:10:1:7`, dạng đính kèm mới của probe): gửi trường kiếm +7 ⇒ `mail_item.enhance_level=7`, lần hai cùng khoá `DUPLICATE_REQUEST`; Benkei lĩnh ⇒ món +7 nằm ô RIÊNG (slot 1), không gộp chồng.
