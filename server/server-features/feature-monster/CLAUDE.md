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
  `MonsterTemplate` → `MonsterCatalogSnapshot` (`MonsterCatalogLoader`). Đúng khuôn ADR-036 đã dựng
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

## Cố ý CHƯA làm

- **Tầm đánh theo loài** (`CombatConstants.ATTACK_RANGE_METERS`, cho quái bắn xa) — đụng cả tầm
  đánh của nhân vật nên là một ADR riêng (ADR-039 D7).
- **Boss** (`sample_boss`) giữ hardcode: nó có script GraalJS + `BossAiSystem` riêng.
- **`npc_template`** — cùng loại nợ, NPC không có chỉ số chiến đấu nên không gộp bừa vào đây.

## Test
`domain/` test **không mock**. Adapter thật có test Postgres qua Testcontainers ở `server-database`.
