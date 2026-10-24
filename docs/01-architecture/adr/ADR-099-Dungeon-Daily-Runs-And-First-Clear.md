# ADR-099: Bí Cảnh — lượt mỗi ngày và thưởng lần đầu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Bí Cảnh: lượt/ngày + thưởng lần đầu" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: mỗi Bí Cảnh 3 lượt/ngày; lần đầu hạ boss nhận thư thưởng lớn; migration). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-047](ADR-047-Dungeon-Entered-Event.md) (kéo cả nhóm vào) · [ADR-048](ADR-048-Boss-Into-Data-Tables.md) · [ADR-060](ADR-060-Daily-Check-In.md) (ngày giờ Việt Nam) |

---

## Decision

**D1 — Lượt mỗi ngày (V87)**: `dungeon_entry(character_id, dungeon_code, day, entries)`. Mỗi nhân vật **3 lượt/ngày** mỗi Bí
Cảnh (ngày giờ Việt Nam). Một lượt = được KÉO VÀO một instance mới (người đã ở trong instance của nhóm không tốn thêm).
Lệnh vào kiểm MỌI thành viên sắp được kéo: ai hết lượt ⇒ cả nhóm bị từ chối `DAILY_LIMIT_REACHED` (giá trị outcome mới).
Ghi lượt cho cả nhóm trong MỘT câu có điều kiện trước khi dời người — hai lệnh vào song song không vượt trần.

**D2 — Thưởng lần đầu**: `dungeon_first_clear(character_id, dungeon_code)` PK. Boss Bí Cảnh chết ⇒ mọi người đang ở trong
instance đó: ai CHƯA từng hạ boss này được ghi và nhận thư **5.000 linh thạch + 3 Yêu Đan (`demon_core`)**. Số là
PLACEHOLDER cân bằng. Việc phụ trên Virtual Thread của đường thưởng hạ quái — lỗi dừng ở đó, log WARN.

**D3 — Giao thức, chỉ thêm**: `EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_DAILY_LIMIT_REACHED`; `EnterDungeonResponse.
entries_left = <n>` (lượt còn của người gửi sau lệnh). `protocolVersion` không tăng.

**D4 — Client**: báo "hết lượt hôm nay" và "còn N lượt" trong khung Nhóm/chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 3 lượt/ngày/nhân vật, cả nhóm bị từ chối nếu một người hết, thưởng lần đầu qua thư | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V87__lot21_content.sql` · `DungeonQuota` · `DungeonInstanceRegistry` · `DungeonOutboundSink`

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 3).
2. ⏳ Kiểm sống bằng gameProbe.
