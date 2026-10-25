# ADR-104: Mã quà tặng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Mã quà tặng" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: GM tạo mã (/gm code), người chơi nhập mã nhận quà qua thư; mỗi mã giới hạn lượt dùng + hạn; mỗi nhân vật dùng 1 lần). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) (thư chống trùng) |

---

## Decision

**D1 — Dữ liệu (V89)**: `gift_code(code PK, gold, item_template_id NULL, quantity, max_uses, uses, expires_at,
created_by)` + `gift_code_redemption(code, character_id, redeemed_at, mailed)` khoá chính `(code, character_id)` chặn dùng
lần hai. Mã lưu CHỮ HOA, 4–24 ký tự `[A-Z0-9_-]`.

**D2 — Tạo mã**: `/gm code <MÃ> <vàng> <item_code|-> <số lượng> <lượt 1–100000> <ngày 1–365>` — ADMIN (tạo tài sản từ
không khí, cùng lý do `/gm give`).

**D3 — Đổi mã**: người chơi gõ `/giftcode <MÃ>` trong ô chat (client chuyển thành lệnh, không gửi lên kênh chat). Server
trong một giao dịch: khoá dòng mã → còn hạn? còn lượt? → chèn dòng đổi (đã đổi ⇒ `ALREADY_REDEEMED`) → `uses + 1`. Sau
giao dịch gửi thư quà với khoá chống trùng suy từ (mã, nhân vật) rồi đánh dấu `mailed`. Thư hỏng giữa chừng ⇒ lần gõ lại
mã đó gửi lại đúng lá thư ấy (không cộng lượt lần hai).

**D4 — Giao thức, chỉ thêm**: 909/910 `RedeemGiftCodeCommand{code}` / `RedeemGiftCodeResponse{outcome}` (dải Mail —
quà đi bằng thư). Kết quả: OK, UNKNOWN_CODE, EXPIRED, USED_UP, ALREADY_REDEEMED.

**D5 — Chống dò mã**: tối đa 10 lần đổi mã mỗi phút mỗi nhân vật (RAM); vượt ⇒ 429 `error.rate_limited`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mã chữ hoa, lượt + hạn, mỗi nhân vật 1 lần, quà qua thư chống trùng, `/giftcode` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (lô 22 phần 1, 2026-10-25): V89 `gift_code` + `gift_code_redemption`; `/gm code …`; `/giftcode <MÃ>` ở
   client; opcode 909/910; trần 10 lần/phút (RAM).
2. ✅ Kiểm sống gameProbe 2026-10-25: tạo `TET2027` (5.000 + 3 Linh Thảo, 2 lượt) ⇒ tạo lại ⇒ `already exists`; Kagemaru
   đổi bằng chữ thường `tet2027` ⇒ OK + thư 5.000, đổi lại ⇒ `ALREADY_REDEEMED`; Benkei ⇒ OK (lượt 2/2); tanthua44 ⇒
   `USED_UP`; mã lạ ⇒ `UNKNOWN_CODE`. `mailed = true`, audit GM `code OK`. Test Postgres phủ nhánh hết hạn + gửi lại
   thư khi `mailed = false`.
