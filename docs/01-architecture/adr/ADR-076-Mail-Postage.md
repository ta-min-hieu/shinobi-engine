# ADR-076: Phí thư (gold sink)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Phí thư (gold sink)" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: gửi thư kèm đồ/vàng tốn phí, đề xuất 10 vàng + 1% vàng đính kèm, không đổi schema, thêm error_key). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | GDD §7 (Thư "phí chống spam gold-selling"), §10 (Gold Sink bắt buộc) · feature-mail · [ADR-063](ADR-063-Market.md) (thuế phường thị 5%) |

---

## Context

Từ 2026-08-28 thư người chơi ký quỹ đúng số vàng/vật phẩm đính kèm nhưng KHÔNG mất phí (feature-mail/CLAUDE.md
ghi "phí bưu điện là quyết định thiết kế game chưa chốt"). Hệ quả: thư là kênh chuyển vàng miễn phí, rẻ hơn
phường thị (thuế 5%) — đúng kênh mà người bán vàng lậu dùng, và GDD §10 đòi mọi kênh chuyển tài sản có gold sink.

## Decision

**D1 — Công thức** `phí = 10 + floor(vàng đính kèm × 1%)` (`dev.kage.mail.domain.Postage`). Phần trăm chỉ đánh
trên VÀNG — server không có "giá trị chuẩn" của một vật phẩm (phường thị mới định giá).

**D2 — Ai trả**: chỉ thư NGƯỜI CHƠI có đính kèm (vàng > 0 hoặc ≥ 1 dòng vật phẩm). Thư chào hỏi rỗng miễn phí
(đã có trần 100 thư/hộp + trần tần suất chống spam). Thư hệ thống (`senderId` rỗng: thưởng quest tồn, thư bang,
GM) không bao giờ mất phí.

**D3 — Trừ cùng câu với vàng đính kèm**: một `UPDATE ... WHERE gold >= vàng + phí` qua `GoldLedger`, trong cùng
transaction gửi thư ⇒ một `GoldChangedEvent` (delta = −(vàng + phí)). Thiếu phí = `INSUFFICIENT_GOLD` như thiếu
vàng — **không thêm giá trị enum, protocol không đổi**. Cộng tràn `long` ⇒ `INSUFFICIENT_GOLD`.

**D4 — Phí bị đốt** (không đến tay ai) và ghi vào `audit_log.MAIL_SEND.detail_json.postage` — sổ bảo toàn vàng
(`EconomyInvariants`) trừ khoản này như trừ tiền mua hàng/cường hoá.

**D5 — Client không tự tính phí** (client/CLAUDE.md "không tự tính giá"): tiêu đề mục Soạn thư và câu báo
`mail.error.insufficient_gold` nói rõ công thức bằng chữ.

## Alternatives Considered

- **Phí phẳng cho mọi thư (kể cả thư rỗng)** — chống spam tốt hơn nhưng phạt cả người chỉ nhắn tin; trần hộp thư
  và trần tần suất đã chặn spam.
- **5% như phường thị** — đắt hơn cho chuyển khoản nội bộ bang/bạn bè; 1% đủ để thư không còn là kênh miễn phí.

## Consequences

- Không đổi schema, không đổi protocol. Đổi số dư thấy được: các test bảo toàn vàng hai người nay hụt đúng phí.
- Số 10/1% là PLACEHOLDER cân bằng, đổi trong `Postage`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 10 + 1% vàng, chỉ thư người chơi có đính kèm, trừ cùng câu, đốt + audit | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [feature-mail/CLAUDE.md](../../../server/server-features/feature-mail/CLAUDE.md)
- [GAME-DESIGN-DOCUMENT.md](../../13-game-design/GAME-DESIGN-DOCUMENT.md) §7, §10

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
2. ✅ Kiểm sống 2026-10-24 (gameProbe `--sendMail=Benkei:1000`): vàng Kagemaru 97.134 → 96.114 (−1.020 = 1.000 + 10 + 1%); gửi lại CÙNG khoá ⇒ `DUPLICATE_REQUEST`, không trừ lần hai; `audit_log.MAIL_SEND` có `"postage":20`.
