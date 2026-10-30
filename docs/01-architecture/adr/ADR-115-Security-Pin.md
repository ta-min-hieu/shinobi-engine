# ADR-115: Khoá an toàn (PIN cấp 2)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Khoá an toàn (mật khẩu cấp 2)" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: PIN 4–6 số do người chơi đặt, bắt buộc trước giao dịch/phường thị/gửi thư đồ/phân giải/bỏ vật phẩm; sai 5 lần khoá 30 phút). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-30 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-017 (session token) · ADR-063 (phường thị) · ADR-097 (phân giải) · ADR-090 (thư đồ cường hoá) |

---

## Decision

**D1 — PIN theo TÀI KHOẢN**, 4–6 chữ số ASCII. Lưu băm PBKDF2-HMAC-SHA256 (100.000 vòng, muối 16 byte, JDK — không
thư viện mới) ở bảng `account_security_pin` (V95). Không bao giờ lưu/ghi log PIN thô. Chưa đặt PIN thì không chặn gì
(tính năng tự nguyện).

**D2 — Mở theo PHIÊN**: kiểm đúng một lần ⇒ mở cho tới khi ngắt kết nối (đăng nhập lại là khoá lại). Đặt PIN mới cũng mở
phiên (người đặt vừa chứng minh biết PIN). Đổi PIN phải đưa PIN cũ.

**D3 — Việc bị chặn** khi có PIN mà phiên chưa mở: mọi bước giao dịch trừ huỷ (1400/1402/1406/1408), đăng bán và mua ở
phường thị (1008/1010), thư kèm linh thạch hoặc vật phẩm (900 — thư chữ không chặn), phân giải (522). Bị chặn ⇒
`ErrorResponse` 403 `error.security_pin_required`. Cổng đọc trạng thái trong bộ nhớ của kết nối (nạp lúc đăng nhập) — không
chạm DB trên EventLoop. ⚠️ "Bỏ vật phẩm" trong mô tả: game KHÔNG có lệnh bỏ/huỷ vật phẩm nào, nên không có gì để chặn.

**D4 — Sai 5 lần liên tiếp ⇒ khoá 30 phút** (kể cả PIN đúng cũng bị từ chối trong lúc khoá); hết khoá thì đếm lại từ đầu.
Đếm áp dụng cho cả kiểm PIN lẫn "PIN cũ" khi đổi.

**D5 — Giao thức, chỉ thêm**: 8/9 `GetSecurityPinStatus`, 10/11 `SetSecurityPin`, 12/13 `VerifySecurityPin` (dải hệ
thống 0–99 còn trống; dùng được trước khi chọn nhân vật). `protocolVersion` không tăng. Client: lệnh chat `/pin`,
`/pin <mã>`, `/pin set <mới> [cũ]` — cùng khuôn `/giftcode` (ADR-104).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | PIN theo tài khoản, PBKDF2 JDK, mở theo phiên, chặn 4 nhóm việc, khoá 30 phút sau 5 lần sai | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-30 (lô 23 phần 1): `feature-account` `SecurityPinRules`/`SecurityPinPort`/`SecurityPinFacade`;
   `PostgresSecurityPinAdapter` (V95); gateway `SecurityPinGate` (theo kết nối) + `SecurityPinCommands` + bọc route
   `pinGuarded`/`pinGuardedWhen`; client `SecurityPinCoordinator` (`/pin`).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-30** (DB dev V98): `setPin:12` → INVALID_FORMAT; `setPin:2468` → OK và phiên
   đó mở luôn (phân giải chạy). Đăng nhập lại: trạng thái khoá; phân giải và đặt cầu mua đều nhận `LỖI 403
   error.security_pin_required`; PIN sai → còn 4 lượt; PIN đúng → mở, cả hai lệnh chạy. Sai 5 lần liên tiếp → LOCKED,
   PIN đúng cũng bị từ chối; DB ghi `failed_attempts=5` + khoá tới tương lai. Không có PIN thô trong DB.
3. Đề xuất (chưa làm): lệnh GM xoá PIN cho người quên (`SecurityPinFacade.clear` đã có, chưa có lệnh `/gm`).
