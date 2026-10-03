# CLAUDE.md — feature-monster

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-monster/**`.
> Sinh ra bởi [ADR-037](../../../docs/01-architecture/adr/ADR-037-Monster-Template-And-Spawn-Tables.md);
> mở rộng bởi [ADR-039](../../../docs/01-architecture/adr/ADR-039-Per-Species-AI-Parameters.md) (tham số AI theo loài).

## Cấu trúc

```
api/       MonsterFacade — đọc thuần, KHÔNG use case nào
domain/    MonsterTemplate · MonsterStats · MonsterAiParams · MonsterSpawnPoint (VO) · MonsterCatalogPort
```

**Không có `application/`** — và đó là chủ ý, không phải thiếu sót: module này chỉ tra cứu config
tĩnh, không điều phối nghiệp vụ gì. Thêm một `GetMonsterCatalogUseCase` chỉ để "cho giống module
khác" là thêm một lớp không làm gì.

## Điều phải nhớ khi sửa

- **Nạp MỘT LẦN lúc khởi động**, không có đường nóng. `monster_template` là config tĩnh (DATABASE.md
  §182 xếp nó cùng nhóm `item_template`). Đổi cân bằng ⇒ migration + khởi động lại.
- **Module này KHÔNG biết `server-world` tồn tại**, và ngược lại. `server-database` thậm chí không
  phụ thuộc `server-world` — chỉ `server-gateway` phụ thuộc cả hai, nên nó là nơi dịch
  `MonsterTemplate` → `MonsterCatalogSnapshot` (`WorldContentLoader`, đổi tên từ `MonsterCatalogLoader`
  ở ADR-040 vì nó nạp cả NPC). Đúng khuôn ADR-036 đã dựng
  cho `EquipmentBonus` → `EquipmentBonusSnapshot`.
- **`element` là CHUỖI**, không phải enum: enum `Element` sống ở `server-world`. Gateway/world dịch;
  chuỗi lạ bị coi là `NONE` chứ không làm chết world (`WorldFacade#toStats`).
- **Phần trăm là số nguyên 0..100**, không phải 0..1. Nhầm hai thang này là con quái né 100% hoặc
  không bao giờ trúng, và không có gì báo — có test gác.
- **`dropTableCode` được phép `null`** (loài không rơi gì) ⇒ adapter dùng `LEFT JOIN`. `INNER JOIN`
  sẽ âm thầm nuốt cả loài đó khỏi catalog, tức quái biến mất khỏi thế giới vì thiếu bảng rơi.
- **Tham số AI CHỈ có SÁU trong tám hằng của `AiConstants`** (ADR-039 D1): aggro/leash/tốc độ
  đuổi/tốc độ tuần tra/bán kính tuần tra/thời gian đứng yên là **nội dung** ⇒ vào DB.
  `ARRIVAL_THRESHOLD_METERS` (epsilon hình học) và `MAX_PATROL_DURATION_SECONDS` (van chống kẹt) là
  **cơ chế** ⇒ ở lại `server-world`. Đừng "hoàn thiện" bằng cách thêm nốt hai cột đó.
- **`aggroRadiusMeters == 0` là giá trị HỢP LỆ**, nghĩa là loài thụ động, không bao giờ gây hấn
  (`training_dummy`). Đừng thêm `CHECK > 0` hay cờ `is_passive`.
- **`MonsterSpawnPoint` từ chối số KHÔNG hữu hạn** (2026-10-03, `f57bcf3`): `CHECK (respawn_seconds
  > 0)` của DB để lọt `'NaN'`/`'Infinity'` (Postgres xếp NaN lớn hơn mọi số). Đồng hồ hồi sinh NaN
  không bao giờ `< 0` (sống) cũng không `<= 0` (tới hạn) ⇒ quái ở điểm đó chết lần đầu là biến mất
  vĩnh viễn, không lỗi nào. Tọa độ NaN/vô cực thì đặt quái ở chỗ không ai tới được. Compact
  constructor nay ném ngay lúc nạp catalog (nạp một lần lúc khởi động ⇒ server không khởi động với
  dữ liệu hỏng thay vì chạy âm thầm sai). `MonsterAiParams` vốn đã chặn NaN. Test:
  `MonsterSpawnPointTest`.
- **Bảng rơi tra theo MÃ quái**, không theo `monster_template.drop_table_id`
  (`grantLoot` → `drop_table.code`). Loài cố ý không rơi gì vẫn cần một `drop_table` RỖNG mang đúng
  mã của nó (quy ước V33; V51 áp cho `blood_sand_enforcer`), nếu không mỗi lần chạy server sẽ WARN
  "không có bảng rơi" ở lượt giết đầu. `ContentReferenceIntegrationTest` (server-database) gác các
  tham chiếu chéo mà FK không bắt được — xem [DATABASE.md](../../../docs/05-server/DATABASE.md).

## Cố ý CHƯA làm

- **Tầm đánh theo loài** (`CombatConstants.ATTACK_RANGE_METERS`, cho quái bắn xa) — đụng cả tầm
  đánh của nhân vật nên là một ADR riêng (ADR-039 D7).
- ~~**Boss** (`sample_boss`) giữ hardcode: nó có script GraalJS + `BossAiSystem` riêng.~~ *(Lỗi
  thời — rà 2026-10-03)* [ADR-048](../../../docs/01-architecture/adr/ADR-048-Boss-Into-Data-Tables.md)
  ✅ Accepted 2026-09-12 đã kéo boss vào `monster_template` (V41: `is_boss` + `ai_script_ref` + nhịp
  AI, `MonsterBossBehaviour`). `BossConstants` chỉ còn hằng cơ chế (VD trần lệnh của sandbox script).
- ~~**`npc_template`**~~ — nợ này KHÔNG còn thuộc về đây. Nó đã có ADR riêng
  ([ADR-040](../../../docs/01-architecture/adr/ADR-040-NPC-Template-And-Placement.md), ✅ Accepted
  2026-09-05 — chữ "🟡 Proposed" cũ ở đây đã lỗi thời)
  và được thi hành trong **`feature-dialogue`** (module đã sở hữu `npc_template` từ V10), không phải
  module này. NPC không có chỉ số chiến đấu, không bảng rơi, không hồi sinh — gộp vào đây sẽ là
  bảng chung mà hơn nửa cột là `NULL` cho mọi hàng NPC (ADR-040 Alternatives E).

## Test
`domain/` test **không mock**. Adapter thật có test Postgres qua Testcontainers ở `server-database`.
