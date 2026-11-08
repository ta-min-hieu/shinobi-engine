# ADR-236: Chế độ không làm phiền

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Chế độ không làm phiền" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: chặn tin riêng và lời mời từ người lạ, bạn bè vẫn liên lạc được). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) (chặn người) · [ADR-178](ADR-178-Away-Auto-Reply.md) (tự trả lời khi vắng) · [ADR-237](ADR-237-Block-Stranger-Invites.md) |

---

## Decision

**D1 — Một cột, một cổng.** `character.do_not_disturb` (V167, mặc định tắt). Bật thì người **không phải bạn** bị từ
chối khi: nhắn riêng, mời vào nhóm, mời giao dịch, gửi lời mời kết bạn. **Bạn bè luôn qua.** Người cùng bang cũng bị
chặn (đúng nghĩa "không làm phiền"; muốn chỉ lọc người lạ thì dùng ADR-237).

**D2 — Cổng chung ở gateway.** `refuseContact` thay chỗ kiểm chặn (ADR-091) ở bốn đường trên: kiểm chặn trước, rồi
riêng tư của người nhận. Từ chối ⇒ `ErrorResponse` 403 `error.target_do_not_disturb` (đi đường lỗi như mọi từ chối thêm
sau khi protocol đã chốt — không thêm giá trị outcome). Không bật gì ⇒ không tra bạn bè/bang (một lần đọc hai cột).
Tin riêng gửi lúc người nhận offline (ADR-187) cũng qua cổng này. Thách đấu, mời vào bang, thư **không** bị chặn bởi
chế độ này (thư có ADR-212 riêng).

**D3 — Công tắc** `SetContactPrivacyCommand{setting, enabled}` / `SetContactPrivacyResponse{do_not_disturb,
block_stranger_invites}` 1153/1154 (dải Friend), dùng chung với ADR-237; `setting = UNSPECIFIED` = chỉ hỏi. Client
`/khonglamphien bat|tat` (hoặc `/dnd on|off`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `do_not_disturb` (V167); chặn tin riêng/mời nhóm/giao dịch/kết bạn từ người không phải bạn qua `refuseContact`; lỗi 403; 1153/1154; `/khonglamphien` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `ContactPrivacyPort`/`ContactPrivacyFacade.refusal`, `PostgresContactPrivacyAdapter`, `ContactPrivacyCommands`, `GatewayChannelHandler.refuseContact`, `ContactPrivacyCoordinator`.
2. ✅ Test: `ContactPrivacyFacadeTest` (bạn bè luôn qua, chặn cả người cùng bang), `Lot38bIntegrationTest.storesBothPrivacySwitches`, `Lot38bCoordinatorsTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 bật `dnd`; PhuTanThu (không phải bạn) nhắn riêng / mời nhóm / kết bạn ⇒ cả ba `403 error.target_do_not_disturb`; BaoDo01 (bạn) nhắn riêng vẫn qua (`DELIVERED_LATER`). Đã tắt lại.
