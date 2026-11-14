# ADR-433: Báo bang khi đồng môn đột phá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Báo bang khi đồng môn đột phá" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: Lên cảnh giới mới ⇒ mọi đồng môn online thấy dòng chúc mừng trên kênh bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-182](ADR-182-Friend-Breakthrough-Notice.md) · [ADR-322](ADR-322-Breakthrough-History.md) |

---

## Decision

**D1 — Khi nào:** đúng các cấp mở cảnh giới mới (`BreakthroughHooks.realmOpenedAt`, cùng mốc ADR-182 báo bạn bè). Lên cấp thường không báo.

**D2 — Gửi:** `CHAT_MESSAGE_EVENT` kênh `CHAT_CHANNEL_GUILD`, người gửi rỗng, thân song ngữ vi/en, tới mọi thành viên bang đang online kể cả chính mình. Không lưu vào lịch sử chat bang, không opcode mới. Việc phụ: lỗi chỉ ghi log.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Không opcode, không migration | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `Lot63Commands.onLevelReached/breakthroughLine`, gọi từ `Lot49Commands.onLevelReached`.
2. ✅ Test: `Lot63ServerTest.breakthroughLineIsGuildChannelWithoutSender`. Kiểm sống khó dựng (cần lên đúng mốc cảnh giới) — dựa vào test.
3. Không kiểm sống (cần lên đúng cấp mở cảnh giới khi có đồng môn online); dựa vào test đơn vị.
