# ADR-214: Tự ngắt khi treo máy

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Tự ngắt khi treo máy" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: đứng yên quá lâu thì server tự ngắt để giải phóng chỗ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | NETWORK.md §10 (`READER_IDLE_TIMEOUT` — kết nối chết) |

---

## Decision

**D1 — "Treo máy" = không gửi gói nào ngoài `PING`.** Khác `READER_IDLE_TIMEOUT` sẵn có (kết nối CHẾT, không cả
`PING`): ở đây kết nối còn sống nhưng người chơi không làm gì. Mọi gói khác (đi lại, đánh, mở cửa sổ, chat…) đặt lại
đồng hồ. Chỉ áp khi đang có nhân vật trong world — màn chọn nhân vật không bị ngắt theo luật này.

**D2 — Ngưỡng cấu hình**: `KAGE_IDLE_KICK_MINUTES` (mặc định **30**, `0` = tắt; giá trị lạ ⇒ server từ chối khởi động,
cùng khuôn `KAGE_GM_ENABLED`). Kiểm theo nhịp ≤ 60 giây trên event loop của chính kết nối (không luồng riêng), nên ngắt
trễ nhất ngưỡng + 60 giây.

**D3 — Khi ngắt**: gửi một dòng chat hệ thống "Treo máy quá N phút — tự ngắt kết nối" rồi đóng kết nối; `channelInactive`
dọn nhân vật như mọi lần ngắt khác (lưu vị trí, rời world). Không opcode mới, không migration.

**Lưu ý**: bế quan offline (thưởng khi vắng) vẫn tính theo lần cuối online như cũ — bị ngắt là "offline", nên người treo
máy không mất gì so với tự thoát.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ngắt khi im (trừ PING) quá `KAGE_IDLE_KICK_MINUTES` (30, 0 = tắt), chỉ trong world, báo một dòng chat | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `IdleKick` (mỗi kết nối một bản), nối vào `GatewayChannelHandler`.
2. ✅ Test: `IdleKickTest` (đọc cấu hình, PING không tính, tắt = không bao giờ ngắt).
3. ✅ Kiểm sống gameProbe 2026-11-07: server chạy `KAGE_IDLE_KICK_MINUTES=1`, ĐạoHữu01 vào world rồi đứng im
   (`--observeSeconds=110`) ⇒ ở giây thứ 90 nhận dòng chat "Treo máy quá 1 phút — tự ngắt kết nối." rồi kết nối bị
   đóng; đăng nhập lại vào world bình thường. Server đã chạy lại với cấu hình mặc định (30 phút).
