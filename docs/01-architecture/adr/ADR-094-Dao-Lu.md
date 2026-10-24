# ADR-094: Đạo lữ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Đạo lữ" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: thân mật ≥600 kết đạo lữ (tốn vàng); cùng nhóm +10% EXP đánh quái; giải được; dùng thân mật lô 20). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-084](ADR-084-Friend-Gifts.md) (thân mật) · [ADR-085](ADR-085-Guild-Skills.md) (cùng ống EXP) · [ADR-091](ADR-091-Block-List.md) |

---

## Decision

**D1 — Điều kiện**: hai người là bạn với thân mật **≥ 600** (mốc cao nhất của ADR-084), chưa ai có đạo lữ. Người cầu
gửi lời (chờ 5 phút trong RAM, người kia phải online), người kia nhận lời.

**D2 — Phí**: người CẦU trả **5.000 linh thạch** lúc được nhận lời, trong CÙNG giao dịch ghi quan hệ (khoá dòng
`friendship` → kiểm thân mật → chèn → GoldLedger → audit `DAO_LU_BOUND`). Thiếu vàng ⇒ rollback, `INSUFFICIENT_GOLD`.

**D3 — Dữ liệu (V85)**: `dao_lu(character_id PK, partner_id UNIQUE)`, HAI dòng mỗi cặp — khoá chính giữ "mỗi người tối
đa một đạo lữ"; chèn được ít hơn 2 dòng nghĩa là một trong hai vừa kết với người khác.

**D4 — Hiệu lực**: EXP ĐÁNH QUÁI **+10%** khi đạo lữ đang CÙNG NHÓM — nhân sau sự kiện x2 (ADR-067) và Ngộ Tính của bang
(ADR-085), cùng ống `newCharacterRegistry`. Đạo lữ đọc từ bộ nhớ đệm của use case (chỉ kết/giải/chặn đổi được nó, server
một instance — ADR-010); nhóm chỉ được tra khi có đạo lữ. EXP quest không đi đường này. Số là PLACEHOLDER cân bằng.

**D5 — Giải**: một trong hai giải bất cứ lúc nào, miễn phí, không hoàn phí; người kia nhận một dòng hệ thống. Chặn nhau
cũng giải.

**D6 — Giao thức, chỉ thêm**: 1131/1132 `ProposeDaoLuCommand{target_name}`, 1133 `DaoLuProposalEvent`, 1134/1135
`AcceptDaoLuCommand{proposer_name}`, 1136/1137 `DissolveDaoLuCommand`; `FriendView.dao_lu = 5`; đọc qua 1122/1123
(ADR-091 D5).

**D7 — Client**: tab "Đạo lữ" của cửa sổ Quan hệ (Q).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Hai bên cùng trả phí | Phải giữ tiền một bên trong lúc chờ bên kia — thêm trạng thái ký quỹ cho một khoản nhỏ |
| Thưởng EXP cả khi không cùng nhóm | Không khuyến khích chơi cùng nhau — mục đích của đạo lữ |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thân mật ≥600, người cầu trả 5.000, hai dòng mỗi cặp, +10% EXP cùng nhóm, giải miễn phí | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V85__block_mentorship_dao_lu.sql` · `DaoLuPolicy` · `DaoLuUseCase` · `PostgresDaoLuAdapter` · `DaoLuCommands` ·
  `DaoLuEffects`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 1) — test `DaoLuUseCaseTest`.
2. ✅ Kiểm sống 2026-10-24 (thân mật đặt 600 bằng SQL): Benkei cầu ⇒ Kagemaru nhận `DaoLuProposalEvent` (1133), nhận lời ⇒ `OK`, hai dòng `dao_lu`, Benkei bị trừ đúng 5.000. Một mình hạ hình nhân +20 EXP; cùng nhóm với Kagemaru ⇒ +22 (20 × 1,1).
