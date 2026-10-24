# ADR-092: Biểu cảm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Biểu cảm (emote)" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: lệnh /chao /cui /cuoi... phát cho người xung quanh, client hiện bong bóng trên đầu; opcode, không migration). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | GAMEPLAY.md §10 (chat khu vực — cùng đường AoI) · [ADR-045](ADR-045-Appearance-Changed-Event.md) |

---

## Decision

**D1 — Sáu biểu cảm cố định** (enum `Emote`): chào (`/chao`), cúi đầu (`/cui`), cười (`/cuoi`), khóc (`/khoc`), giận
(`/gian`), reo hò (`/vui`). Lệnh gõ không dấu để gõ được trên mọi bộ gõ. Chỉ là chữ — không animation riêng ở bản đầu.

**D2 — Ai thấy**: mọi người đang NHÌN THẤY người làm biểu cảm (AoI) cộng chính họ — cùng tập nhận với chat kênh Khu vực.
World có lệnh chung mới `ObserversCommand(characterId, action)`: trên tick thread, gọi `action` với id từng người quan
sát. World không biết action làm gì; gateway dùng nó để xếp gói ra dây (không chặn tick).

**D3 — Không DB, chạy thẳng trên EventLoop**; trần **1 biểu cảm / 2 giây** mỗi kết nối (nhiều hơn chỉ để xả chữ che màn hình
người khác). Mã không hợp lệ / chưa vào world / quá nhịp ⇒ outcome tương ứng, không phát.

**D4 — Giao thức, chỉ thêm**: 803 `SendEmoteCommand{emote}`, 804 `SendEmoteResponse{outcome}`, 805
`EmoteEvent{character_id, emote}`.

**D5 — Client**: gõ lệnh trong ô chat (móc lệnh gạch chéo phía client mới của `ChatCoordinator`); `EmoteEvent` ⇒ chữ nổi
màu xanh trời trên đầu nhân vật đó (chính mình hoặc người đang thấy) qua `CombatFeedbackPresenter`.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Gửi biểu cảm như một tin chat khu vực đặc biệt | Trộn hiển thị vào luồng chat, đi qua kiểm duyệt/ghi DB chat vô ích |
| Animation nhân vật riêng cho mỗi biểu cảm | Cần clip mới cho 15 model; để bản sau khi có art |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 6 biểu cảm chữ, phát theo AoI qua `ObserversCommand`, 1/2 giây, 803–805 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `EmoteCommands` (gateway) · `WorldFacade#enqueueForObservers` · `EmoteCoordinator` (client)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 1).
2. ✅ Kiểm sống 2026-10-24: Benkei `/chao` ⇒ `OK`, Kagemaru đứng cạnh nhận `EmoteEvent` (805) và chính Benkei cũng nhận; `/cui` ngay sau ⇒ `RATE_LIMITED`.
