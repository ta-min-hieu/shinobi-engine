# ADR-110: Kết nghĩa huynh đệ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Kết nghĩa huynh đệ" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: 2–5 người thân mật ≥300 kết nghĩa (tốn vàng), cùng nhóm +5% EXP và kênh chat riêng; rời được; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-094](ADR-094-Dao-Lu.md) (đạo lữ — cùng khuôn thân mật + thưởng EXP cùng nhóm) · ADR-084 (thân mật) |

---

## Decision

**D1 — Dữ liệu (V90)**: `sworn_group(id, created_at)` + `sworn_member(character_id PK, group_id, joined_at)` —
khoá chính chặn một người ở hai nhóm.

**D2 — Mời & nhận**: A mời B theo tên: phải là bạn với thân mật **≥ 300**, B chưa kết nghĩa, nhóm của A (nếu có) dưới
**5** người. B nhận lời (lời mời RAM 5 phút) ⇒ một giao dịch: chưa có nhóm thì lập nhóm mới và A trả **3.000** vàng; đã
có thì thêm B và A trả **1.000**. Thân mật kiểm lại trong giao dịch.

**D3 — Rời**: ai cũng rời được; nhóm còn 1 người tự giải tán.

**D4 — Thưởng**: EXP đánh quái **+5%** khi có ít nhất một huynh đệ CÙNG NHÓM (party) — cộng sau thưởng đạo lữ, không
cộng dồn theo số huynh đệ. Kênh chat **`CHAT_CHANNEL_SWORN = 6`** (giá trị enum mới) — gửi cho mọi huynh đệ đang online;
client gõ `/kn <chữ>`.

**D5 — Giao thức, chỉ thêm**: 1138/1139 `InviteSwornCommand{target_name}`/`Response`, 1140 `SwornInviteEvent{from_name}`,
1141/1142 `AcceptSwornCommand{inviter_name}`/`Response`, 1143/1144 `LeaveSwornCommand`/`Response`; danh sách huynh đệ đi
trong `GetRelationshipsResponse` (field mới `sworn`).

**D6 — Client**: tab "Kết nghĩa" trong cửa sổ Quan hệ (Q).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhóm 2–5 theo thân mật ≥300, 3.000/1.000 vàng, +5% EXP cùng nhóm, kênh chat 6 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (lô 22 phần 2, 2026-10-25): V90 `sworn_group/sworn_member`; `SwornFacade` (lời mời RAM 5 phút, nhớ đệm
   nhóm); `SwornEffects` +5% EXP sau thưởng đạo lữ; `CHAT_CHANNEL_SWORN = 6` + outcome `NOT_SWORN = 11`; opcode
   1138–1144; `GetRelationshipsResponse.sworn`; tab "Kết nghĩa" + `/kn <chữ>`.
2. ✅ Kiểm sống gameProbe 2026-10-25 (Kagemaru + Benkei, thân mật 300): chat kết nghĩa trước khi kết ⇒ `NOT_SWORN`; mời ⇒
   OK, Benkei nhận gói 1140 rồi nhận lời ⇒ OK; vàng Kagemaru 80.174 → 77.174 (lập nhóm 3.000); hai bên thấy nhau trong
   `kếtNghĩa=[…]`; tin `/kn` tới cả hai chiều; Benkei rời ⇒ OK, rời lần hai ⇒ `NOT_SWORN`, nhóm 2 người tự giải tán (0
   dòng). +5% EXP cùng nhóm chưa đo sống (Benkei Lv10 không cùng party lúc kiểm).
