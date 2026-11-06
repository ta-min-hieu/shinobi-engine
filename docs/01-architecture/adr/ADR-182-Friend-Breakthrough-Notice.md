# ADR-182: Báo bạn bè đột phá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Báo bạn bè đột phá" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: bạn bè lên cảnh giới mới thì mình nhận một dòng báo, có thể bấm chúc mừng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) (đột phá cảnh giới — đã báo CẢ SERVER một dòng chung) · [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) (6 cảnh giới) |

---

## Decision

**D1 — Mốc**: đúng mốc ADR-136 — lên cấp MỞ ĐẦU một cảnh giới (19/37/55/73/91). ADR-136 đã có dòng hệ thống cho cả
server; ADR này thêm phần RIÊNG: mọi người bạn đang online của người đó nhận `FriendBreakthroughEvent` (tên, cảnh giới,
cấp) ⇒ client in "Bạn X vừa đột phá Trúc Cơ! Gõ /chucmung X để chúc mừng."

**D2 — Chúc mừng**: `/chucmung <tên>` (hoặc `/congrats`) — chỉ người đang là bạn, trong **30 phút** sau lần đột phá, **mỗi
người một lần cho mỗi lần đột phá** (lần đột phá mới mở lại). Người vừa đột phá nhận `FriendCongratulatedEvent` ⇒ "X chúc
mừng bạn đột phá Trúc Cơ!". Chỉ là lời chúc: không vật phẩm, không thân mật, không DB ⇒ không có gì để cày.

**D3 — Trạng thái chỉ trong bộ nhớ**: "ai vừa đột phá, ai đã chúc" ở `ConnectedCharacterRegistry.friendBreakthroughs` (dùng
chung mọi kết nối — bài học ADR-178). Khởi động lại server mất nhịp 30 phút là chấp nhận được.

**D4 — Giao thức, chỉ thêm**: `FriendBreakthroughEvent` 1147, `CongratulateFriendCommand`/`Response` 1148/1149 (outcome OK /
TARGET_NOT_FOUND / NOT_FRIENDS / NO_RECENT_BREAKTHROUGH / ALREADY_CONGRATULATED), `FriendCongratulatedEvent` 1150.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Báo riêng bạn bè ở mốc ADR-136, chúc mừng 1 lần / 30 phút, chỉ bộ nhớ, 1147–1150, `/chucmung` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): `FriendBreakthroughs` (bộ nhớ dùng chung), `FriendBreakthroughCommands` (nghe lên cấp
   cạnh `BreakthroughHooks`, lệnh chúc mừng), `FriendBreakthroughCoordinator` `/chucmung`.
2. ✅ Test: `FriendBreakthroughsTest` (một lần / lần đột phá, cửa sổ 30 phút), `FriendBreakthroughCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `congratulate:<tên>`): tạm đặt BaoDo01 sát cấp 19 (đã qua 1 kiếp), cho đánh
   cướp lang thang ⇒ lên cấp 19 Trúc Cơ; ĐạoHữu01 đang online nhận 1147; chúc mừng ⇒ `OK`, lần hai ⇒ `ALREADY_CONGRATULATED`;
   BaoDo01 nhận 1150. Trước lúc đột phá chúc mừng ⇒ `NO_RECENT_BREAKTHROUGH`. Đã trả BaoDo01 về cấp 1 sau khi đo.
4. Chờ chủ dự án: có muốn lời chúc cộng thân mật (ADR-084) không — khi đó phải có giới hạn chống cày và ghi DB.
