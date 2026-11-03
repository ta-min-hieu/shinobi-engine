# ADR-165: GM gửi thư bồi thường hàng loạt

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "GM gửi thư bồi thường hàng loạt" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: lệnh /gm compensate gửi thư hệ thống kèm vàng/vật phẩm cho mọi nhân vật (hoặc theo cấp tối thiểu), có idempotency + audit). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) (lệnh GM, quyền, audit) · [ADR-104](ADR-104-Gift-Codes.md) (cùng cú pháp phần quà) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) (khoá chống trùng của thư) |

---

## Decision

**D1 — Lệnh**: `/gm compensate <MÃ> <vàng> <item_code|-> <số lượng> <cấp tối thiểu 1-100>` — cùng cú pháp phần quà với
`/gm code`; **ADMIN** (tạo tài sản, cùng lý do `give`/`gold`). Thuộc nhóm "bàn trực" (`GmCommand.Desk`); logic ở
`GmCompensationCommand` (gateway). Người nhận: MỌI nhân vật cấp ≥ ngưỡng, kể cả đang offline — thư hệ thống (túi đầy
không mất), người đang online nhận `NEW_MAIL_EVENT`.

**D2 — Chống trùng hai tầng**: (a) mỗi thư mang khoá chống trùng TẤT ĐỊNH `UUID.nameUUIDFromBytes("gm-compensation:<mã>:
<id>")` (bảng `idempotency_key`, ADR-058) — chạy lại một đợt dang dở (server sập giữa chừng) chỉ gửi phần còn thiếu;
(b) sổ đợt `gm_compensation` (V133): cùng mã + cùng phần quà chưa xong ⇒ chạy tiếp; cùng mã khác quà ⇒ từ chối; đã
xong (`completed_at`) ⇒ từ chối — vì khoá chống trùng cũ có thể đã bị dọn (`RetentionJanitor`).

**D3 — Audit**: dòng `gm_audit_log` của lệnh ghi mã, kiểu bắt đầu (NEW/RESUME), số người nhận, số đã gửi / đã có / lỗi;
mỗi thư còn audit riêng của feature-mail.

**D4 — Chạy đồng bộ** trên Virtual Thread của lệnh GM, trả lời khi xong ("sent X, already had Y, failed Z of N"). Một
server vài nghìn nhân vật: vài giây–vài chục giây. Nếu số nhân vật lớn hơn nhiều, chuyển sang chạy nền + báo khi xong
(Revisit trigger).

**D5 — Giao thức**: KHÔNG đổi — lệnh đi bằng chat `/gm` như mọi lệnh GM.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `/gm compensate` ADMIN, thư hệ thống tới mọi nhân vật cấp ≥ ngưỡng, khoá chống trùng tất định + sổ đợt V133 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V133, `CompensationPort`/`CompensationFacade` (feature-gm), `GmCommand.Compensate` +
   parser, `PostgresCompensationAdapter`, `GmCompensationCommand`.
2. ✅ Test: `GmCommandParserTest` (cú pháp đúng + 6 dòng sai), `CompensationIntegrationTest` (Postgres thật: mới / chạy
   tiếp / xung đột / đã xong; lọc người nhận theo cấp).
3. ✅ Kiểm sống gameProbe 2026-11-03 (cấp ADMIN tạm cho `tanthu01`, đã thu lại): `compensate LIVE01 500
   minor_healing_pill 2 1` ⇒ 2 thư "Bồi thường" (500 vàng + 2 đan) cho cả 2 nhân vật, `NEW_MAIL_EVENT` tới người online,
   sổ `LIVE01 sent=2` đã xong; chạy lại ⇒ "already completed", không thư trùng.
4. Chờ chủ dự án: tiêu đề/nội dung thư cố định song ngữ — có muốn GM tự gõ nội dung không.
