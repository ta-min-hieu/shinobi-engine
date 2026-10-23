# ADR-055: Nhiệm vụ ngày — cột `repeat_policy`, làm mới lười lúc 0h giờ Việt Nam, thưởng vàng qua `ctx.give_gold`

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn mục "Nhiệm vụ ngày (tạp dịch)" trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi ghi rõ "chọn = Accept ADR đó và thi hành luôn"; mô tả kèm: 3 quest lặp reset 0h giờ VN ở Chu Hoành, vàng + EXP, migration cột `repeat_policy`, không opcode mới). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [STORY.md §7 dòng "Nhiệm vụ ngày", §8 quy tắc 5](../../13-game-design/STORY.md) · [GDD §8](../../13-game-design/GAME-DESIGN-DOCUMENT.md) · [docs/README.md dòng 64(a)](../../README.md) (nhân vật mới 0 vàng, quest không thưởng vàng) · [ADR-054](ADR-054-Abandon-Quest.md) · [ADR-044](ADR-044-Quest-Prerequisite-Enforcement.md) |

---

## Context

- GDD §8 và STORY §7 đã ghi "nhiệm vụ ngày — công việc tông môn (gánh nước, tuần sơn…)" là nguồn
  tài nguyên ổn định, nhưng code chỉ có quest MỘT LẦN: `character_quest_progress` khoá chính
  `(character_id, quest_template_id)`, `CLAIMED` không bao giờ lùi.
- Dòng 64(a): nhân vật mới 0 vàng, quest không thưởng vàng, quái không rơi vàng — bộ Tạp Dịch 700
  vàng mà tới Cấp 5 chỉ gom được 644. Không có nguồn vàng lặp lại nào ngoài bán đồ.
- `markClaimedIfCompleted` đã ghi `updated_at` = lúc lĩnh, nên "lĩnh hôm nào" đọc được mà không cần
  cột mới.

## Decision

1. **Schema (V59)**: `quest_template.repeat_policy VARCHAR(8) NOT NULL DEFAULT 'ONCE'
   CHECK (repeat_policy IN ('ONCE', 'DAILY'))`. Mọi quest cũ là `ONCE` — không đổi hành vi nào.
2. **Làm mới LƯỜI, không cron**: một quest `DAILY` đã `CLAIMED` với `updated_at` TRƯỚC mốc 0h hôm nay
   (giờ `Asia/Ho_Chi_Minh`) được coi là **chưa nhận**:
   - `ListQuestsUseCase` không trả dòng đó (nhật ký không hiện "đã nhận thưởng" của hôm qua);
   - `ctx.quest_status` của hội thoại đọc ra `NOT_STARTED` (cùng đường `listQuests`);
   - `AcceptQuestUseCase` xoá dòng cũ bằng compare-and-delete
     (`... AND status = 'CLAIMED' AND updated_at < :reset`) rồi `insertIfAbsent` như thường — cùng
     transaction, nên hai lệnh nhận song song vẫn chỉ một thắng.
   Quest `DAILY` còn dở từ hôm qua (`IN_PROGRESS`/`COMPLETED`) **không** bị xoá — làm tiếp/lĩnh tiếp
   được; chỉ sau khi lĩnh mới phải đợi qua 0h.
3. **Mốc ngày là một hàm thuần** `DailyReset.startOfDay(Instant now)` ở `domain/` (múi giờ hằng số,
   không đọc múi giờ máy chủ — container chạy UTC). `Clock` tiêm vào use case để test đứng được ở
   23:59/00:01.
4. **`ctx.give_gold(n)` ở `on_claim`** — port riêng `QuestGoldPort` (cùng khuôn `ExpPort`), ghi
   `character_stat.gold` trong CÙNG transaction lĩnh thưởng, kèm audit; `n` phải `1..1_000_000`
   (ngoài miền = lỗi script, đúng luật biên `ctx.*` 2026-10-10); chạm trần vàng 9.999.999.999 thì
   rollback cả lượt lĩnh như túi đầy (`GOLD_CAP_REACHED`). Gateway đẩy `GoldChangedEvent` (209) sau
   khi lĩnh có vàng.
5. **Protocol — chỉ THÊM**: `ClaimQuestRewardResponse.gold_granted = 4` (int64) để client nói
   "+N linh thạch"; `CLAIM_QUEST_REWARD_OUTCOME_GOLD_CAP_REACHED = 8`. Không opcode mới,
   `protocolVersion` không tăng.
6. **Nội dung — 3 quest ngày, nhận ở Chu Hoành** (STORY §7 "công việc tông môn"; mỗi script khai
   `// STORY: arc-1 §7`; không tiên quyết, mở từ Cấp 1):

   | Mã | Tên | Mục tiêu | Thưởng |
   |---|---|---|---|
   | `daily_dummy_drill` | Luyện Công Sớm | hạ 5 mộc nhân (`training_dummy`) | 150 EXP · 30 linh thạch |
   | `daily_mountain_patrol` | Tuần Sơn | hạ 3 tán tu cường đạo (`wandering_bandit`) | 300 EXP · 60 linh thạch |
   | `daily_water_duty` | Gánh Nước | mang nước cho Tô Vân Nhi ở sân luyện công, rồi về báo Chu Hoành | 100 EXP · 20 linh thạch |

   Tổng 110 linh thạch/ngày — đủ nhích bài toán 64(a) mà không biến quest ngày thành mỏ vàng.
   ⚠️ Số là ĐỀ XUẤT CÂN BẰNG ban đầu, chưa playtest; đổi số là việc của script, không phải Java.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Cron/job 0h xoá dòng `CLAIMED` | Thêm tiến trình lịch; server tắt lúc 0h là lỡ; lười đọc là đủ và đúng mọi lúc |
| Bảng riêng `daily_quest_progress` | Nhân đôi đường đọc/ghi tiến độ; mọi bảo đảm concurrency đã có phải viết lại |
| Reset 24h kể từ lúc lĩnh | Người chơi không đoán được "bao giờ làm lại"; mọi game cùng thể loại dùng mốc giờ cố định |
| Thưởng vàng qua thư hệ thống | Thêm một bước bấm; lĩnh thưởng đã có transaction chung với EXP/đồ |

## Consequences

- Có nguồn vàng lặp lại đầu tiên; số cân bằng "vàng tới Cấp N" ở 64(a) đổi nghĩa khi người chơi làm
  quest ngày.
- Ô `shouldLandExactlyAtLevelThirtySix_afterTheWholeArcTwoChain` không đổi (nó chơi đường tối thiểu,
  không làm quest ngày); EXP quest ngày là phần cộng thêm, làm nới biên "tới Cấp 37" vốn quá mỏng.
- `ctx.give_gold` mở cho MỌI quest — script chính tuyến cũng dùng được về sau.

## Revisit Trigger

- Đa vùng giờ / máy chủ nước ngoài ⇒ mốc ngày theo realm.
- Người chơi farm quest ngày bằng nhiều nhân vật để chuyển vàng ⇒ giới hạn theo tài khoản.

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | `repeat_policy` ONCE/DAILY, V59 |
| 2 | Làm mới lười theo `updated_at < 0h Asia/Ho_Chi_Minh` |
| 3 | `ctx.give_gold` 1..1.000.000, cùng transaction lĩnh thưởng |
| 4 | Thêm `gold_granted` + outcome `GOLD_CAP_REACHED` |

## ADR cần tạo

Không.

## Tài liệu liên quan

[feature-quest/CLAUDE.md](../../../server/server-features/feature-quest/CLAUDE.md) · [DATABASE.md §2.5](../../05-server/DATABASE.md) · [SCRIPTING.md](../../06-plugin/SCRIPTING.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18). `DailyQuestIntegrationTest` (Postgres thật: vàng vào túi, cùng ngày không nhận lại, qua 0h nhận lại; quest ONCE không bao giờ làm mới), `QuestScriptRunnerBoundaryTest` (biên `give_gold`). Cap vàng: dùng lại `error.gold_cap_reached` 409 sẵn có của gateway thay cho outcome mới (D5 thu hẹp lúc thi hành — không cần `GOLD_CAP_REACHED`).
2. ✅ Kiểm sống 2026-10-23 bằng gameProbe: nhận `daily_mountain_patrol` ở nút `daily_duty` (0 → 19 → 1), hạ 3 tán tu ⇒ COMPLETED, lĩnh ⇒ +300 EXP, +60 linh thạch (209 `quest_reward`). Nhận lại cùng ngày ⇒ `ALREADY_ACCEPTED`; lùi `updated_at` một ngày bằng SQL (mô phỏng qua 0h, không chờ giờ thật) ⇒ nhận lại OK, tiến độ về 0. Số thưởng vẫn là đề xuất chưa playtest cân bằng.
