# ADR-368: Cấp tối thiểu chat thế giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Cấp tối thiểu chat thế giới" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: nhân vật dưới cấp 5 không chat kênh thế giới/giao dịch (chống tài khoản rác spam)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-222](ADR-222-Trade-Chat-Channel.md) · [ADR-052](ADR-052-In-Game-GM-Commands.md) |

---

## Decision

**D1 — Luật:** gửi kênh THẾ GIỚI hoặc GIAO DỊCH (ADR-222) khi cấp < 5 ⇒ outcome mới `LEVEL_TOO_LOW = 16` (CHỈ THÊM vào enum), không phát tin. Kênh khu vực, bang, nhóm, riêng không đổi.

**D2 — Cấu hình:** `KAGE_WORLD_CHAT_MIN_LEVEL` (mặc định 5; `0` = tắt; giá trị lạ ⇒ lỗi khởi động to tiếng, cùng khuôn `KAGE_IDLE_KICK_MINUTES`). Task test của gateway đặt `0` để các test chat cũ (nhân vật cấp 1) giữ nguyên ý nghĩa; luật có test riêng.

**D3 — Hệ quả cần biết:** nhân vật mới dưới cấp 5 không trả lời được hỏi đáp tông môn (ADR-122, trả lời bằng chat thế giới); load test `SimulatedPlayer` (nhân vật cấp thấp chat thế giới) giờ nhận LEVEL_TOO_LOW thay vì phát tin — **đo sức chứa phải đặt `KAGE_WORLD_CHAT_MIN_LEVEL=0`** để giữ đúng hồ sơ tải cũ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cấp < 5 ⇒ `LEVEL_TOO_LOW` ở kênh thế giới/giao dịch; cấu hình `KAGE_WORLD_CHAT_MIN_LEVEL` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `Lot55Commands.mayChatPublicly`/`parseMinLevel`, `GatewayChannelHandler.isChannelMembershipValid`, `ChatController` (câu báo).
2. ✅ Test: `Lot55ServerTest.parsesMinLevel`, `Lot55ClientTest.newOutcomesHaveMessages`.
3. LIVE368
