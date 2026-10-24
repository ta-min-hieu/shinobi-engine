# ADR-081: Boss thế giới định kỳ — lịch, thông báo toàn server, thưởng theo sát thương

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Boss thế giới định kỳ" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: boss xuất hiện theo lịch data-driven, thông báo toàn server, thưởng theo bảng sát thương — top N qua thư; thẻ "Sát thương boss" ở Bia Anh Danh). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | GDD §7 (xếp hạng "boss damage") · [ADR-048](ADR-048-Boss-In-Monster-Template.md) (boss trong `monster_template`) · [ADR-057](ADR-057-Leaderboards.md) · [ADR-067](ADR-067-Double-Events.md) (thông báo toàn server) |

---

## Decision

**D1 — Lịch là DỮ LIỆU** (V78 `world_boss_schedule`: mã boss, toạ độ x/y ở world chính, chu kỳ phút;
`world_boss_reward`: bậc thưởng theo hạng). Bộ hẹn giờ ở gateway (`WorldBossScheduler`, một luồng nền, hỏi mỗi 15 s)
gọi `WorldFacade.spawnWorldBoss` ở khu vực ĐẦU TIÊN của world chính khi tới giờ VÀ con trước đã chết. Bản đầu: Yêu
Vương (`demon_king`, Lv35, 6.000 máu, model riêng dựng từ mesh kit) mỗi 60 phút tại x = −30. Lần đầu sau khởi động
chờ một chu kỳ, trừ khi `KAGE_WORLD_BOSS_FIRST_SPAWN_SECONDS` đặt sớm hơn.

**D2 — Thông báo toàn server** qua dòng chat hệ thống (kênh Thế giới, cùng đường `/gm event` của ADR-067): lúc xuất
hiện ("… xuất hiện tại …") và lúc bị hạ (kèm tên người đứng đầu).

**D3 — Sổ sát thương ở server-world** (`WorldBossDamageLedger`, sổ sách của `WorldFacade` như
`spawnIndexByMonsterId` — không system nào đọc mỗi tick nên không làm component): cộng dồn theo nhân vật, cả đòn thường
lẫn chiêu. Boss chết ⇒ `WorldBossListener.onWorldBossDefeated(mã, sổ)` trên tick thread (gateway đẩy sang luồng nền
ngay) — server-world chỉ báo SỰ KIỆN, không biết thư hay xếp hạng. Không đụng `WorldOutboundSink`.

**D4 — Thưởng**: gateway xếp hạng sổ, gửi THƯ HỆ THỐNG cho top N (mặc định 10) theo bảng thưởng (linh thạch + vật
phẩm theo hạng, tham số SQL); mọi người gây ≥ 1 sát thương nhận phần "tham gia". Người không online vẫn nhận (thư).

**D5 — Bia Anh Danh**: `world_boss_damage` (V78: tổng sát thương lên boss thế giới mỗi nhân vật) ⇒
`LEADERBOARD_KIND_BOSS_DAMAGE = 5` (dùng lại `LeaderboardEntry`, `value` = tổng sát thương).

**D6 — Không opcode mới**: thông báo đi chat hệ thống, thưởng đi thư, xếp hạng đi 1202/1203. Chỉ thêm giá trị enum.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Boss ở instance riêng | Mất ý nghĩa "thế giới" — mọi người cùng thấy |
| Thưởng rơi đồ dưới đất | Người hạ đòn cuối ăn hết; GDD muốn xếp hạng sát thương |
| Lịch theo giờ cố định trong ngày | Chu kỳ phút đơn giản và đổi được bằng SQL; giờ cố định là Revisit |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lịch DB, spawn ở world chính, sổ sát thương ở World, thưởng thư theo hạng, thẻ Bia Anh Danh | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V78__world_boss.sql` · `WorldBossScheduler` · `WorldBossDamageLedger` · `WorldBossFacade` (feature-monster) ·
  `tools/blender/build_monster_demon_king.py`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
3. ⏳ Chấm art Yêu Vương bằng ảnh render đối chứng ở camera client (ASSET-LOG).
2. ⏳ Kiểm sống bằng gameProbe.
