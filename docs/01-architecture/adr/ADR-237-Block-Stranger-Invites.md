# ADR-237: Chặn lời mời từ người lạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Chặn lời mời từ người lạ" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: chỉ bạn bè hoặc người cùng bang mới mời được vào nhóm/giao dịch). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-236](ADR-236-Do-Not-Disturb.md) · [ADR-212](ADR-212-Stranger-Mail-Block.md) (chặn thư người lạ — cùng tinh thần) |

---

## Decision

**D1 — Cột** `character.block_stranger_invites` (V167, mặc định tắt). Bật thì lời mời **vào nhóm** và **giao dịch** từ
người **không phải bạn và không cùng bang** bị từ chối: `ErrorResponse` 403 `error.target_blocks_strangers`. Tin riêng
và lời mời kết bạn vẫn qua (muốn chặn cả những thứ đó thì bật ADR-236).

**D2 — Cùng cổng `refuseContact` và cùng opcode 1153/1154 với ADR-236.** Luật thuần ở
`ContactPrivacyFacade.refusal`: bạn bè ⇒ qua; không làm phiền ⇒ chặn; chặn người lạ + (nhóm/giao dịch) + không cùng
bang ⇒ chặn; còn lại qua. Hai công tắc độc lập; bật cả hai thì không làm phiền thắng (rộng hơn).

**D3 — Client** `/chanloimoi bat|tat` (hoặc `/blockinvites on|off`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `block_stranger_invites` (V167); chặn mời nhóm/giao dịch từ người không phải bạn, không cùng bang; lỗi 403; dùng chung 1153/1154; `/chanloimoi` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): cùng các lớp ADR-236 (`ContactPrivacyFacade.Relation` mang cờ cùng bang).
2. ✅ Test: `ContactPrivacyFacadeTest.strangerInviteBlockOnlyCoversPartyAndTradeFromStrangers`, `Lot38bIntegrationTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: tắt dnd, bật chặn người lạ; PhuTanThu mời nhóm ⇒ `403 error.target_blocks_strangers`, nhắn riêng vẫn qua; BaoDo01 (bạn + cùng bang) mời nhóm đi tới tầng nhóm (`TARGET_ALREADY_IN_PARTY` — không bị cổng riêng tư chặn). Đã tắt lại.
