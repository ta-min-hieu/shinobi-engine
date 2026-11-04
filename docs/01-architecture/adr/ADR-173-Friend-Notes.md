# ADR-173: Ghi chú bạn bè

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Ghi chú bạn bè" trong lượt hỏi lô 30 (AskUserQuestion). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-084](ADR-084-Friend-Gifts.md) (thân mật trong `FriendView`) · [ADR-094](ADR-094-Dao-Lu.md) (`FriendView.dao_lu`) |

---

## Decision

**D1 — Ghi chú riêng**: mỗi người ghi một dòng ngắn về từng người bạn (vd "sư tỷ, hay đi Bí Cảnh tối"). Chỉ CHÍNH người
ghi thấy; người kia không biết. Tối đa **24 ký tự** (code point) sau khi chuẩn hoá (bỏ ký tự điều khiển, gộp khoảng
trắng); ghi chú rỗng = xoá. Chỉ ghi được cho người **đang là bạn** (`NOT_FRIENDS` nếu không).

**D2 — Lưu (V139)**: bảng `friend_note(owner_id, friend_id, note, updated_at)`, khoá chính (owner, friend), xoá theo
nhân vật (`ON DELETE CASCADE`). Hết là bạn thì dòng ở lại vô hại — ghi chú chỉ được đọc kèm danh sách bạn hiện tại, kết
bạn lại thì ghi chú cũ hiện lại. Không chạm vàng/vật phẩm ⇒ không audit.

**D3 — Giao thức, chỉ thêm**: `FriendView.note = 6` (danh sách bạn 1109 trả kèm ghi chú);
`SetFriendNoteCommand{target_name, note}` / `SetFriendNoteResponse{outcome, target_name, note}` — **1145/1146**. Theo
TÊN như `RemoveFriendCommand`. `protocolVersion` không tăng.

**D4 — Client**: lệnh chat `/ghichu <tên bạn> <ghi chú>` (hoặc `/note`), bỏ trống ghi chú để xoá; danh sách bạn hiện
"Tên (ghi chú)". Ghi xong client tự hỏi lại danh sách bạn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ghi chú riêng ≤ 24 ký tự cho người đang là bạn, V139 `friend_note`, `FriendView.note`, 1145/1146, `/ghichu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): V139, `FriendNotePort`/`FriendNoteFacade`, `PostgresFriendNoteAdapter`,
   `FriendNoteCommands` 1145/1146, `FriendView.note`, `FriendNoteCoordinator` `/ghichu`.
2. ✅ Test: `FriendNoteIntegrationTest` (Postgres thật: ghi, thay, xoá, riêng từng chủ), `FriendNoteFacadeTest` (chuẩn hoá,
   trần 24), `FriendNoteCoordinatorTest` (tên kèm ghi chú, câu báo).
3. ✅ Kiểm sống gameProbe 2026-11-04 (bước `noteFriend:<tên>=<ghi chú>`, `listFriends`): ĐạoHữu01 ghi "sư huynh  hay đi
   BíCảnh" cho BaoDo01 ⇒ `OK`, lưu đã gộp khoảng trắng, danh sách bạn trả kèm ghi chú; 25 ký tự ⇒ `TOO_LONG`; tên lạ ⇒
   `TARGET_NOT_FOUND`; ghi chú rỗng ⇒ xoá; BaoDo01 xem danh sách của mình KHÔNG thấy ghi chú; hết là bạn ⇒ `NOT_FRIENDS`.
4. Sau này (nếu cần): ô sửa ghi chú ngay trong cửa sổ bạn bè (hiện chỉ qua lệnh chat).
