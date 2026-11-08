# ADR-238: Lịch sử đăng nhập

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lịch sử đăng nhập" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: xem 10 lần đăng nhập gần nhất kèm IP đã che, để phát hiện người lạ dùng tài khoản). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-010](ADR-010-Realm-Model.md) · khoá bảo vệ PIN (lô trước, `SecurityPinFacade`) |

---

## Decision

**D1 — Bảng mới** `login_history(account_id, masked_ip, logged_at)` (V167). Mỗi lần đăng nhập **thành công**
(`ConnectCommand`) ghi một dòng; mỗi tài khoản giữ **50 dòng** mới nhất (cắt trong cùng giao dịch ghi).

**D2 — Không bao giờ lưu IP đầy đủ.** Che ở facade trước khi xuống DB: IPv4 giữ 2 khối đầu (`203.113.x.x`), IPv6 giữ 2
nhóm đầu (`2001:db8:x:x`), không phải IP ⇒ `?`. Đủ để người chơi nhận ra "nhà mạng / vùng lạ", không đủ để định vị ai —
tối thiểu hoá dữ liệu cá nhân. IP đầy đủ vẫn chỉ nằm trong log vận hành như trước.

**D3 — Xem** `GetLoginHistoryRequest{}` / `GetLoginHistoryResponse{repeated LoginHistoryEntry{masked_ip,
at_epoch_seconds}}` 265/266 (dải Character): **10 lần** gần nhất của **tài khoản** sở hữu nhân vật đang chơi (mọi nhân
vật cùng tài khoản thấy như nhau). Client `/lichsudangnhap` (hoặc `/loginlog`) in giờ theo máy người chơi.

**D4 — Ngoài phạm vi:** lần đăng nhập thất bại (đăng nhập hiện chỉ cần tên tài khoản — chưa có mật khẩu để "sai"),
báo động khi IP lạ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `login_history` (V167), giữ 50 dòng/tài khoản, IP che 2 khối; 265/266 trả 10 dòng; `/lichsudangnhap` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `LoginHistoryPort`/`LoginHistoryFacade.mask`, `PostgresLoginHistoryAdapter`, `Lot38Commands.recordLogin/loginHistory`, `LoginHistoryCoordinator`.
2. ✅ Test: `LoginHistoryFacadeTest` (che IPv4/IPv6), `Lot38bIntegrationTest.keepsOnlyTheNewestLogins`, `Lot38bCoordinatorsTest` (giờ).
3. ✅ Kiểm sống gameProbe 2026-11-08: sau khi đăng nhập qua probe, `loginLog` ⇒ `127.0.x.x` (IP đã che, cột DB không có IP đầy đủ).
