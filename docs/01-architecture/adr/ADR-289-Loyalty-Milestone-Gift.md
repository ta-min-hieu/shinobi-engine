# ADR-289: Quà mốc gắn bó

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Quà mốc gắn bó" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: mỗi 30 ngày kể từ lúc tạo nhân vật, nhận một thư quà). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-058](ADR-058-Send-Mail-Idempotency.md) (khoá chống trùng thư) · [ADR-227](ADR-227-Starter-Gift.md) |

---

## Decision

**D1 — Mốc:** `n = floor((now − character.created_at) / 30 ngày)`. Lúc vào world (`Lot39Commands.onEnteredWorld` →
`Lot45Commands.onEnteredWorld`), `n ≥ 1` ⇒ gửi thư hệ thống "Quà gắn bó" kèm `1000 × min(n, 12)` linh thạch.

**D2 — Không migration, không opcode.** Khoá chống trùng thư (ADR-058) = UUID tên của `loyalty:<charId>:<n>` ⇒ vào world
bao nhiêu lần cũng chỉ một thư mỗi mốc. **Vắng trọn một mốc thì chỉ nhận mốc mới nhất** (không bù mốc cũ — tránh dồn cả
chục thư một lúc). Người chơi thấy quà qua hộp thư như mọi thư hệ thống.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mốc 30 ngày từ `created_at`; thư idempotent; 1000×min(n,12) linh thạch | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `Lot45Commands.onEnteredWorld`/`milestone`/`loyaltyGold`/`loyaltyKey`.
2. ✅ Test: `Lot45ServerTest`.
3. ✅ Kiểm sống gameProbe 2026-11-12: lùi `created_at` của ĐạoHữu01 31 ngày ⇒ vào world nhận thư "Quà gắn bó" 1000 linh thạch ("đồng hành 30 ngày"); vào world thêm 3 lần nữa vẫn đúng MỘT thư (khoá chống trùng giữ). Đã trả `created_at`, xoá thư + khoá thử.
