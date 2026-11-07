# ADR-213: Đánh dấu mục tiêu nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Đánh dấu mục tiêu nhóm" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: trưởng nhóm đánh dấu một con quái để cả nhóm cùng đánh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-191](ADR-191-Party-Leader-Transfer.md) (trưởng nhóm) |

---

## Decision

**D1 — Chỉ là gợi ý, không phải quyền.** Trưởng nhóm gửi id entity đang nhắm (0 = bỏ dấu); server chỉ kiểm "là trưởng
nhóm" và id không âm, rồi gửi `PartyTargetMarkedEvent{target_entity_id, leader_name}` cho mọi thành viên đang online
(kể cả trưởng nhóm). Server KHÔNG kiểm entity còn sống hay ở cùng World: dấu không cho ai làm được gì mới — đồng đội
nhắm theo bằng lệnh chọn mục tiêu bình thường, và server vẫn kiểm mọi đòn như cũ. Không lưu DB (dấu mất khi server
khởi động lại hoặc client vào lại world).

**D2 — Protocol, chỉ thêm**: `MarkPartyTargetCommand{target_entity_id}` / `MarkPartyTargetResponse{outcome}` 1334/1335,
`PartyTargetMarkedEvent` 1336.

**D3 — Client**: trưởng nhóm `/danhdau` (hoặc `/mark`) đánh dấu mục tiêu đang nhắm, `/danhdau xoa` bỏ dấu; cả nhóm thấy
một dòng chat và gõ `/theodau` (hoặc `/assist`) để nhắm đúng mục tiêu đó (chỉ được khi mục tiêu đang hiện trong tầm nhìn
của mình). Lần này **chưa có ký hiệu vẽ trên đầu quái** — chỉ dòng chat + lệnh nhắm theo; ký hiệu 3D là việc client
thuần, làm sau không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Trưởng nhóm đánh dấu id entity, server phát event cho nhóm, không lưu; 1334–1336; `/danhdau`, `/theodau` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `Lot35Commands.markPartyTarget`, `PartyMarkCoordinator`,
   `LocalIntentSender.currentTargetId/focusTarget`.
2. ✅ Kiểm sống gameProbe 2026-11-07: nhóm 1 (trưởng nhóm ĐạoHữu01, thành viên BaoDo01 giữ online) — ĐạoHữu01
   `markTarget:4242` ⇒ `OK`, cả hai nhận `PARTY_TARGET_MARKED_EVENT` (1336); `markTarget:0` ⇒ `OK`, cả hai nhận event bỏ dấu;
   BaoDo01 (không phải trưởng nhóm) ⇒ `NOT_LEADER`; id âm ⇒ `INVALID_TARGET`. Không có gì để dọn (không lưu DB).
3. Ký hiệu trên đầu mục tiêu được đánh dấu (client).
