# ADR-091: Chặn người chơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Chặn người chơi" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: danh sách chặn — người bị chặn không chat riêng, mời nhóm/kết bạn/giao dịch được với mình; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-friend · [ADR-084](ADR-084-Friend-Gifts.md) · [ADR-093](ADR-093-Mentorship.md) · [ADR-094](ADR-094-Dao-Lu.md) |

---

## Context

Chưa có cách nào để một người chơi tránh người quấy rối: ai biết tên mình là nhắn riêng, mời kết bạn, mời nhóm, mời giao
dịch, thách đấu được không giới hạn (chỉ có trần tần suất chung).

## Decision

**D1 — Dữ liệu (V85)**: `character_block(blocker_id, blocked_id, created_at)`, khoá chính theo cặp, mỗi dòng MỘT chiều.
Trần **50** người mỗi nhân vật, giữ bằng khoá dòng `character` của người chặn trong cùng giao dịch đếm-rồi-ghi.

**D2 — Chặn là cắt quan hệ**: cùng giao dịch ghi dòng chặn, xoá tình bạn (kể cả lời mời đang chờ, mất luôn thân mật),
đạo lữ và sư đồ giữa hai người. Bỏ chặn KHÔNG khôi phục gì.

**D3 — Luật thi hành ở gateway, theo CẢ HAI chiều** (A chặn B thì cả A lẫn B đều không chạm được tới nhau): chat riêng, mời
kết bạn, mời nhóm, mời giao dịch, thách đấu, tặng quà, bái sư, cầu/nhận đạo lữ. Từ chối bằng `ErrorResponse`
**403 `error.blocked`** — đường dùng chung cho mọi lệnh qua `CommandRefusedException`, không thêm giá trị outcome vào
các message đã chốt.

**D4 — Kênh chung (thế giới/khu vực/bang/nhóm)**: server KHÔNG lọc (một tin phát cho nhiều người). Client ẩn tin của người
mình đã chặn theo danh sách đọc về — việc hiển thị, không phải luật.

**D5 — Giao thức, chỉ thêm**: 1118/1119 `BlockCharacterCommand{target_name}`/`BlockCharacterResponse`, 1120/1121 bỏ chặn,
1122/1123 `GetRelationshipsRequest`/`GetRelationshipsResponse` — MỘT gói cho mọi quan hệ lô 21 (sư phụ, đồ đệ, đạo lữ,
danh sách chặn). `protocolVersion` không tăng.

**D6 — Client**: cửa sổ **Quan hệ (phím Q)** tab "Chặn" — danh sách kèm nút Bỏ chặn, ô tên + nút Chặn; lệnh chat
`/chan Tên`.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Chặn một chiều (chỉ người bị chặn không chạm tới mình) | Người chặn vẫn nhắn khiêu khích được mà người kia không trả lời được — bất đối xứng dễ thành công cụ quấy rối |
| Server lọc cả kênh chung | Mỗi tin thế giới phải tra danh sách chặn của mọi người nhận; tốn mà lợi ích chỉ là hiển thị |
| Giữ tình bạn khi chặn | "Bạn bè nhưng bị chặn" là trạng thái vô nghĩa, và tặng quà/đạo lữ đều dựa trên tình bạn |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng một chiều, trần 50, chặn = cắt mọi quan hệ, luật hai chiều ở gateway, kênh chung lọc ở client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V85__block_mentorship_dao_lu.sql` · `PostgresBlockAdapter` · `BlockFacade` · `BlockCommands` · `RelationshipQuery` ·
  `RelationshipCoordinator` (client)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 1).
2. ✅ Kiểm sống 2026-10-24 (gameProbe `--lot21=`): Kagemaru chặn Benkei ⇒ `OK`, quan hệ đọc về có Benkei trong danh sách chặn; tình bạn, đạo lữ, sư đồ của cặp đều bị xoá (đếm DB = 0). Benkei nhắn riêng / mời kết bạn / mời nhóm / cầu đạo lữ đều ra `403 error.blocked`. Bỏ chặn `OK`, lần hai `NOT_BLOCKED`.
