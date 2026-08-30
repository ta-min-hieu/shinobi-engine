# CLAUDE.md — feature-monster

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-monster/**`.
> Sinh ra bởi [ADR-037](../../../docs/01-architecture/adr/ADR-037-Monster-Template-And-Spawn-Tables.md).

## Cấu trúc

```
api/       MonsterFacade — đọc thuần, KHÔNG use case nào
domain/    MonsterTemplate · MonsterStats · MonsterSpawnPoint (VO) · MonsterCatalogPort
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

## Cố ý CHƯA làm (ADR-037 D7)

- **Tham số AI theo loài** (aggro/leash/tốc độ đuổi) — vẫn ở `AiConstants`, mọi loài dùng chung.
  AI.md §10 mô tả chúng thuộc bảng này; tài liệu đó đã được ghi chú là chưa đúng.
- **Boss** (`sample_boss`) giữ hardcode: nó có script GraalJS + `BossAiSystem` riêng.
- **`npc_template`** — cùng loại nợ, NPC không có chỉ số chiến đấu nên không gộp bừa vào đây.

## Test
`domain/` test **không mock**. Adapter thật có test Postgres qua Testcontainers ở `server-database`.
