# ADR-145: Boss arc 4 có kỹ năng — dậm đất có báo trước

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn mục lô 26 "Boss arc 4 có kỹ năng: Cổ Thạch Khổng Lồ có đòn đặc biệt (dậm đất diện rộng, có báo trước) thay vì chỉ đánh thường". Cơ chế và mọi con số do AI chọn — **đề xuất**. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-048](ADR-048-Boss-Into-Data-Tables.md) (boss world chính, script theo loài) · [ADR-139](ADR-139-Arc-Four-Descend-The-Mountain.md) · [ADR-016](ADR-016-Scripting-Engine.md) |

---

## Decision

**D1 — Cổ Thạch Khổng Lồ thành boss world chính (V117)**: `is_boss = true`, script
`/scripts/boss/ancient_stone_colossus.js`, hỏi script mỗi 0,5 giây, hồi chiêu 10 giây. Máu/ATK/giáp KHÔNG đổi.

**D2 — Hành động mới `ground_slam` (cơ chế Java, nội dung script — Điều 27)**: script trả
`{ action: "ground_slam", raw_damage }` thì `BossAiSystem`:
1. chốt tâm vòng = chỗ boss đứng, bán kính **4 m** (`BossConstants.SLAM_RADIUS_METERS`);
2. boss **tự choáng 1,5 giây** (`SLAM_TELEGRAPH_SECONDS`) qua đúng đường choáng có sẵn — không đánh thường, không đi;
3. phát vòng báo trước (gói 413) cho mọi người đang thấy boss;
4. hết 1,5 giây: mọi nhân vật còn sống đứng trong vòng ăn một đòn chiêu (`resolveSkillAttack`, khiên hấp thụ trước,
   chặn hồi máu ngoài chiến đấu) — phát qua `SkillResultEvent` sẵn có với `skill_code = "ground_slam"`. Bước ra kịp là
   thoát.

**D3 — Cân bằng (đề xuất)**: ×1,4 ATK ≈ 1,7 nhát thường sau kháng của bộ Kim Đan — xấp xỉ đúng số nhát thường boss bỏ
lỡ trong 1,5 giây gồng. Người đứng yên mất gần như y như trước (biên thật +2 của `GoldenCoreBalanceTest` không xấu đi);
người bước ra lời ~1,5 nhát. Chiêu không làm trận khó hơn — nó THƯỞNG người đọc được báo trước.

**D4 — Protocol, chỉ THÊM**: `BossTelegraphEvent` (opcode **413**): `monster_id, x, y, radius, seconds`.
`WorldOutboundSink.onBossTelegraph` mặc định no-op; registry gửi cho người thấy boss, sink hầm ngục chuyển tiếp (boss Bí
Cảnh sau này dùng chung). `protocolVersion` KHÔNG tăng.

**D5 — Client**: `BossTelegraphPresenter` vẽ trong không gian thế giới hai cột đỏ đứng ở hai mép vùng nổ + dải đỏ mỏng
trên mặt đất, đậm dần tới lúc nổ (camera gần như ngang nên vòng tròn nằm phẳng sẽ vô hình — `MonsterPresenter`). Tối đa
8 vòng, thời lượng ≤ 10 giây, bán kính ≤ 20 m; không cấp phát mỗi khung.

## Kiểm chứng

- `BossGroundSlamTest`: tick đầu chỉ có vòng báo trước, không ai mất máu, boss đang gồng; hết 1,5 giây người ở 2 m ăn đòn,
  người ở 6 m không; người bước ra 5 m trong lúc gồng không ăn gì.
- `BossTelegraphPresenterTest`: vòng sống đúng số giây; gói hỏng/thời lượng vô lý bị chặn.
- **Kiểm sống 2026-11-01** (gameProbe, server thật, V117, probe nay giải mã gói 413): Kagemaru (Cấp 72, bộ Kim Đan)
  đánh Cổ Thạch Khổng Lồ @408 — gói 413 `radius=4.0 seconds=1.5` ở t=1,16 s; `ground_slam` nổ ở t=2,67 s (đúng 1,51 s
  sau), 63 sát thương; đòn thường của boss cách nhau 1,93 s quanh lúc gồng (bình thường ~1 s) ⇒ boss bỏ đúng ~1 nhát 41.
  Người đứng yên lỗ ~0,5 nhát mỗi lần dậm, người bước ra lời ~1 nhát. Kagemaru hạ boss, còn 377/645. Vòng đỏ trên
  client thật CHƯA xem bằng mắt.

## Cần chủ dự án xem lại

- Bán kính 4 m, gồng 1,5 giây, ×1,4, hồi 10 giây đều là đề xuất.
- Chưa có hoạt ảnh gồng riêng cho model (đang dùng clip `idle` như mọi quái) — mục "VFX kỹ năng" của lô 26.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dậm đất = cơ chế Java chung (`ground_slam`), boss nào cũng gọi được qua script | Accepted |
| 2 | Boss tự choáng lúc gồng để chiêu không làm trận khó hơn cho người đứng yên | Accepted |
| 3 | Opcode 413 chỉ để vẽ; ai trúng do server quyết | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-048](ADR-048-Boss-Into-Data-Tables.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Hoạt ảnh gồng/dậm cho model khổng lồ.
