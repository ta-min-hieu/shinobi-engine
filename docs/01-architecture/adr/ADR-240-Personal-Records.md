# ADR-240: Kỷ lục cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Kỷ lục cá nhân" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: ghi sát thương một đòn cao nhất, chuỗi hạ quái dài nhất… và báo khi phá kỷ lục). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-223](ADR-223-Personal-Stats.md) (thống kê cá nhân) · [ADR-195](ADR-195-Death-Recap.md) (cùng khuôn móc vào kết quả đòn đánh) |

---

## Decision

**D1 — Hai kỷ lục:** sát thương **một đòn** cao nhất (đánh thường hoặc kỹ năng sát thương, lên quái hay người) và
**chuỗi hạ quái** dài nhất không chết (bị hạ ⇒ chuỗi về 0). Bảng `personal_record(character_id, kind, value,
achieved_at)` (V168), chỉ nâng lên bằng upsert có điều kiện, không bao giờ hạ.

**D2 — Không chạm DB trên tick thread.** `PersonalRecordHooks` (cạnh `DeathRecapHooks` trong registry) so sánh trong RAM
ở `onAttackResult`/`onSkillResult`/`onMonsterKilled`/`onCharacterDefeated` — chỉ khi **phá kỷ lục** mới đẩy việc ghi
sang Virtual Thread. Kỷ lục đã lưu được nạp lúc vào world; **trước khi nạp xong thì không tính** (không báo nhầm). Chuỗi
hiện tại sống trong RAM (mất khi server khởi động lại).

**D3 — Báo khi phá.** Một dòng chat hệ thống song ngữ cho chính người đó — chỉ khi đã có kỷ lục cũ > 0 (kỷ lục đầu tiên
ghi lặng lẽ, khỏi báo đòn đầu đời), và chuỗi chỉ báo **một lần** mỗi chuỗi (lúc vượt kỷ lục; các con sau vẫn ghi
lặng). Xem: `GetPersonalRecordsRequest{}` / `GetPersonalRecordsResponse{highest_hit, longest_kill_streak,
current_kill_streak}` 267/268. Client `/kyluc` (hoặc `/records`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `personal_record` (V168) chỉ nâng; theo dõi RAM trên tick thread, ghi khi phá; báo chat một lần; 267/268; `/kyluc` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `PersonalRecordPort`/`PersonalRecordFacade`, `PostgresPersonalRecordAdapter`, `PersonalRecordHooks` (registry), `Lot39Commands.personalRecords`, `PersonalRecordsCoordinator`.
2. ✅ Test: `PersonalRecordHooksTest` (chỉ tính sau khi nạp, chuỗi về 0), `Lot39IntegrationTest.personalRecordsOnlyGoUp`.
3. ✅ Kiểm sống gameProbe 2026-11-08: lần đầu đánh ⇒ ghi lặng `HIGHEST_HIT=39`, `LONGEST_KILL_STREAK=1`; hạ kỷ lục trong DB xuống 10, khởi động lại server, đánh tiếp ⇒ chat hệ thống `Kỷ lục mới — đòn mạnh nhất: 39` rồi `… 59`. Dữ liệu thử đã xoá.
