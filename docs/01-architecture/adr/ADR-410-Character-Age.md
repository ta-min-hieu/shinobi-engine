# ADR-410: Tuổi nhân vật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tuổi nhân vật" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /tuoi [tên]: ngày tạo nhân vật và đã tu luyện bao nhiêu ngày). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-289](ADR-289-Loyalty-Milestone-Gift.md) |

---

## Decision

**D1:** đọc `character.created_at` sẵn có; số ngày trọn tính tới lúc hỏi. Tên rỗng = chính mình; nhân vật nào cũng xem được (ngày tạo không phải thông tin riêng tư).

**D2 — Dây:** `GetCharacterAgeRequest{target_name}` / `GetCharacterAgeResponse{found, name, created_epoch_seconds, days}` **1208/1209**. Client `/tuoi [tên]` (`/age`) in ngày theo giờ máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1208/1209, đọc `created_at` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `Lot60Commands.characterAge/ageDays`, `LookupToolsCoordinator`.
2. ✅ Test: `Lot60ServerTest.ageInWholeDays`, `Lot60ClientTest.formatsCreationDate`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: `age:` ⇒ `ĐạoHữu01 ngày=11`; `age:BaoDo01` ⇒ `ngày=12`.
