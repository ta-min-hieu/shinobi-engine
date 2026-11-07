# ADR-212: Chặn thư người lạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Chặn thư người lạ" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: chỉ nhận thư từ bạn bè, chống thư rác). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) (chặn từng người) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) (gửi thư) |

---

## Decision

**D1 — Công tắc theo nhân vật**: V163 `character.block_stranger_mail` (mặc định TẮT). Bật ⇒ thư NGƯỜI CHƠI gửi từ
người không phải bạn bè bị từ chối trước khi tạo thư hay ký quỹ gì; người gửi nhận `SEND_MAIL_OUTCOME_STRANGER_MAIL_BLOCKED
= 10`. Thư hệ thống (thưởng, GM, quà bạn bè, điểm danh…) không đi qua lệnh gửi thư nên luôn tới.

**D2 — Protocol, chỉ thêm**: giá trị enum mới trên `SendMailOutcome`; `SetStrangerMailBlockCommand{enabled,
query_only}` / `Response{enabled}` 913/914.

**D3 — Client**: `/chanthu bat|tat` (hoặc `/blockmail on|off`), không kèm gì = xem trạng thái; người gửi thấy "Người nhận
chỉ nhận thư của bạn bè".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `block_stranger_mail` (V163), từ chối thư người chơi không phải bạn, outcome 10, 913/914, `/chanthu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): V163, `StrangerMailPort`/`Facade`, `PostgresStrangerMailAdapter`,
   `GatewayChannelHandler.refusesStrangerMail`, `Lot35Commands.setStrangerMailBlock`, `StrangerMailCoordinator`.
2. ✅ Test: `StrangerMailFacadeTest` (chỉ chặn người lạ khi bật; bạn bè và tự gửi luôn được).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V163): PhuTanThu `strangerMail:?` ⇒ `false`, `strangerMail:on` ⇒ `true`;
   ĐạoHữu01 gửi thư cho PhuTanThu ⇒ `STRANGER_MAIL_BLOCKED` (không tạo thư, cả lần gửi lại); BaoDo01 bật chặn nhưng là bạn
   ⇒ thư của ĐạoHữu01 tới (`OK`). Đã tắt công tắc của cả hai và xoá lá thư thử.
