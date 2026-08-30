# ADR-037: Quái đọc từ DB — `monster_template` + `monster_spawn`, thay bốn chỗ hardcode

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-08-30) — chờ chủ dự án quyết. AI đo + soạn, KHÔNG tự thi hành |
| Date | 2026-08-30 |
| Deciders | Chủ dự án quyết; AI đo + soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (chỉ số trang bị — chính playtest của nó sinh ra ADR này) · [ADR-034](ADR-034-Manual-Attack-And-Experience.md) (EXP hardcode, tự nhận là tạm) · [AI.md §10](../../03-engine/AI.md) · [DATABASE.md §2.2](../../05-server/DATABASE.md) · [GAMEPLAY.md §5](../../05-server/GAMEPLAY.md) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi schema + protocol + THÊM MODULE đều phải dừng hỏi) |

---

## Context

### Vì sao bây giờ: playtest ADR-036 đo được một con số vô nghĩa

Ngày 2026-08-30, sau khi ADR-036 cho trang bị chỉ số thật, buổi playtest (ROADMAP mục 42) đo trên
server thật:

| Trang bị | Sát thương hình nộm gây ra |
|---|---|
| Không mặc gì | **−7** |
| Trọn bộ "Hạ Nhẫn" **+0** (10.900 vàng) | **−6** |
| Trọn bộ "Hạ Nhẫn" **+15** (thêm hàng vạn vàng cường hoá) | **−4** |

Quái duy nhất trong game có `atk = 8`, mà giảm damage là **nhân** (`100/(100+DEF)`), nên toàn bộ
`bonus_def`/`bonus_res` của V22 chỉ dịch được ba nấc trong khoảng 7→4. Nói cách khác: **những con số
DEF/RES vừa đặt trong V22 hiện không có gì để đối chứng** — không thể biết chúng quá cao hay quá
thấp, vì không tồn tại kẻ địch nào đủ mạnh để chúng có ý nghĩa.

Chủ dự án chốt hướng ngay trong buổi đó: **có quái mạnh hơn trước, rồi mới cân lại DEF.** ADR này là
việc hạ tầng chặn đường đó.

### Tham số quái đang nằm ở BỐN chỗ, không phải một

Đọc thẳng code, không suy đoán:

| Chỗ | Nội dung | Ghi chú của chính nó |
|---|---|---|
| `CombatConstants` (server-world) | `MONSTER_STATS` = atk 8 / def 5 / res 3 / hit 90%, `MONSTER_MAX_HP = 50`, `BOSS_STATS`, `BOSS_MAX_HP = 320`, `MONSTER_RESPAWN_SECONDS = 10` | "chưa có `monster_template` DB-driven (lát cắt sau mới per-loại-quái)" |
| `WorldFacade.MONSTER_SPAWN_X` | `{8, 14, 21, 27}` | "Quái bù nhìn tập luyện — hardcode, chưa có `monster_template` DB" |
| `MonsterExperience` (feature-character) | `switch` Java: `training_dummy` → 20, `sample_boss` → 200 | "Khi nó ra đời, EXP phải là một **CỘT** trong đó chứ không phải bảng tra Java" |
| `AiConstants` (server-world) | aggro 6m, leash 10m, đuổi 3 m/s, tuần tra 1,5 m/s | [AI.md §10](../../03-engine/AI.md) ghi những tham số này thuộc `monster_template` |

Điểm đắt nhất, và là lý do `monster_template` một mình KHÔNG đủ: **cả bốn điểm spawn đều tạo cùng
một mã** (`spawnTrainingDummy(i)` cho mọi `i`). Overworld hôm nay có **đúng một loài quái, bốn bản
sao**. Thêm một hàng vào bảng template mà không đụng tới spawn thì thế giới vẫn y nguyên.

### Đặc tả gốc và tiền lệ của chính dự án đang mâu thuẫn

[DATABASE.md §2.2](../../05-server/DATABASE.md) đã đặc tả sẵn bảng này từ lâu:

```
monster_template | id, code, name_key, level, base_stats_json, ai_params_json, drop_table_id
```

Tức **JSON blob**. Nhưng dự án **không có thư viện JSON nào** trong
[TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md), và đã hai lần đứng trước đúng ngã ba này rồi chọn
ngược lại:

- `drop_table.entries_json` → bẻ thành bảng quan hệ `drop_table_entry` (feature-inventory/CLAUDE.md),
  lý do ghi rõ: thêm Jackson/Gson là một quyết định riêng, lớn hơn hẳn quyết định đang làm.
- **ADR-036 (hôm qua)** bác thẳng phương án `stats_json` cho `item_template`, cùng lập luận.

Báo cáo mâu thuẫn chứ không tự chọn bên là luật (§14) — nhưng mâu thuẫn này nằm gọn trong phạm vi
ADR này nên D1 giải quyết nó, và DATABASE.md sẽ phải sửa theo kết quả.

⚠️ ADR-036 có Revisit Trigger *"khi số chỉ số vượt 6 ⇒ đo lại phương án JSON"*. Bảng quái sẽ có ~9
cột chỉ số, tức đã chạm ngưỡng đó — nên D1 **đo lại thật** thay vì viện dẫn tiền lệ suông. Xem
Alternatives A.

### Ràng buộc kiến trúc phải tôn trọng

- **`server-world` không được biết DB** (Điều 23). Quái lại được spawn **bên trong** `WorldFacade`
  lúc world khởi động, nên dữ liệu phải được **tiêm vào**, không phải được world đi lấy.
- **`server-database` KHÔNG phụ thuộc `server-world`** (kiểm `build.gradle.kts`: nó chỉ phụ thuộc
  `shared-common` + 13 `feature-*`). Chỉ `server-gateway` phụ thuộc cả hai. Đây chính là hình dạng
  đã buộc ADR-036 phải dịch `EquipmentBonus` → `EquipmentBonusSnapshot` ở gateway.
- `WorldFacade` có **4 chỗ dựng** (world chính ×2, dungeon, arena) và constructor đã 3 tham số —
  chạm sát trần Checkstyle `ParameterNumber` (max 4).

---

## Decision

> ⚠️ **Chưa được duyệt.** Mục này là ĐỀ XUẤT để chủ dự án quyết, không phải việc đã chốt.
> Phạm vi đã được chủ dự án chốt trước khi soạn: **template + spawn + EXP**, tham số AI để đợt sau;
> và **loài mới dùng tạm model đã có**, không chờ art.

### D1 — `monster_template`: cột quan hệ, KHÔNG JSON

```sql
CREATE TABLE monster_template (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(32) NOT NULL UNIQUE,   -- khớp monsterCode trên dây + drop_table.code
    name_key       VARCHAR(64) NOT NULL,          -- khoá i18n, client tra bundle (hud.monster.*)
    model_key      VARCHAR(64) NOT NULL,          -- khoá art; xem D5
    level          INT NOT NULL DEFAULT 1,
    max_hp         INT NOT NULL,
    atk            INT NOT NULL,
    skill_dmg      INT NOT NULL DEFAULT 0,
    def            INT NOT NULL,
    res            INT NOT NULL,
    hit_percent    INT NOT NULL DEFAULT 90,       -- 0..100, chia 100 khi dựng CombatStats
    dodge_percent  INT NOT NULL DEFAULT 0,
    crit_percent   INT NOT NULL DEFAULT 0,
    element        VARCHAR(8) NOT NULL DEFAULT 'NONE',
    exp_reward     BIGINT NOT NULL DEFAULT 0,     -- D4
    drop_table_id  BIGINT REFERENCES drop_table (id)  -- NULL = không rơi gì
);
```

**Vì sao cột chứ không JSON, đo lại theo Revisit Trigger của ADR-036:** ngưỡng "6 chỉ số" của ADR đó
là dấu hiệu để **đo lại**, không phải để tự động đổi. Đo lại cho ra cùng kết luận, vì cái đắt không
đổi: JSON đòi **một dependency mới** (§4 — quyết định riêng, lớn hơn quyết định này), trong khi 15
cột trên một bảng ~6 dòng thì `SELECT` ra là đọc được bằng mắt, `UPDATE` chỉnh cân bằng được bằng
một dòng SQL, và Postgres kiểm kiểu giúp. Một `stats_json` sai chính tả khoá thì **im lặng thành 0**
— đúng loại lỗi mà cả ADR-036 lẫn `drop_table_entry` sinh ra để tránh.

`element` là `VARCHAR` chứ không FK: `Element` là enum Java (Điều: enum thay int constant), bảng
tham chiếu 5 dòng cố định không đáng một JOIN — cùng khuôn `equip_slot` của `item_template`.

**`drop_table_id` thay cho quy ước ngầm hiện nay.** Hôm nay `drop_table.code` **trùng chuỗi** với
`monsterCode` và không có gì bắt buộc điều đó (V4 chỉ ghi bằng comment); đổi tên một con quái là bảng
rơi lặng lẽ không khớp nữa. FK biến quy ước thành ràng buộc. ⚠️ `GrantLootUseCase` hiện tra theo
`dropTableCode` — hoặc giữ nguyên chữ ký đó và để gateway tra `code` ra từ template, hoặc đổi sang
tra theo id. Đề xuất: **giữ nguyên**, vì đổi chữ ký kéo theo cả đường `onMonsterKilled`.

### D2 — `monster_spawn`: quái nào, ở đâu, hồi sinh sau bao lâu

```sql
CREATE TABLE monster_spawn (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monster_template_id BIGINT NOT NULL REFERENCES monster_template (id),
    world_code          VARCHAR(32) NOT NULL DEFAULT 'main',
    pos_x               REAL NOT NULL,
    pos_y               REAL NOT NULL DEFAULT 0,
    respawn_seconds     REAL NOT NULL DEFAULT 10
);
```

`world_code` tồn tại vì world **không** đồng nhất: dungeon/arena dựng với
`spawnOverworldContent = false` và boss vào bằng lệnh riêng (`SpawnBossCommand`). `'main'` là world
chính; các world khác chưa đọc bảng này ở đợt này.

⚠️ **Ràng buộc vị trí phải giữ, đã ghi trong javadoc `MONSTER_SPAWN_X` và có lý do thật:**
`pos_y` phải bằng **0** (ADR-033 khoá di chuyển vào trục X — quái đặt lệch y là quái người chơi
không bao giờ chạm tới), và điểm spawn phải cách điểm hồi sinh `(0,0)` xa hơn `AGGRO_RADIUS_METERS`
(6m), nếu không nhân vật vừa hồi sinh đã bị đánh ngay. Buổi playtest 2026-08-30 gặp đúng cái đó ở
dạng nhẹ: chết → hồi sinh → bị đuổi đánh lại trước khi kịp làm gì.

### D3 — Đường dữ liệu: gateway dịch, world nhận dữ liệu THUẦN

Đúng khuôn ADR-036 (`EquipmentBonus` → `EquipmentBonusSnapshot`), vì hình dạng phụ thuộc module bắt
buộc thế:

```
feature-monster (mới)          server-database              server-gateway            server-world
  MonsterTemplate (record)  ←  PostgresMonsterCatalog    →  dịch sang          →  MonsterCatalogSnapshot
  MonsterSpawnPoint (record)   Adapter (implements Port)     record của world      (record THUẦN, không
  MonsterCatalogPort                                                                biết DB là gì)
  MonsterFacade.loadCatalog()
```

`WorldFacade` nhận `MonsterCatalogSnapshot` làm **tham số thứ 4** (chạm đúng trần Checkstyle, không
vượt). Catalog rỗng ⇒ world không spawn quái nào — đúng hành vi dungeon/arena hiện nay.

🛑 **Thêm module `feature-monster` là việc §4 bắt dừng hỏi.** Xem Alternatives C nếu chủ dự án muốn
tránh module mới.

**Nạp một lần lúc khởi động, không nạp lại khi chạy** — `monster_template` là config tĩnh, cùng cách
`item_template` đang được đối xử (DATABASE.md §182 xếp nó vào nhóm "load lúc khởi động, không đổi khi
chạy"). Đổi cân bằng ⇒ migration + khởi động lại, không có đường nóng.

### D4 — `MonsterExperience` (Java) biến mất, EXP thành cột

`ConnectedCharacterRegistry.grantExperienceBlocking` đang gọi `MonsterExperience.rewardFor(code)`.
Đổi sang đọc `exp_reward` của template. Chính javadoc của class đó đã yêu cầu điều này. Hành vi giữ
nguyên ở một điểm quan trọng: **mã lạ cho 0 EXP và bỏ qua sớm**, không ném lỗi — một con quái chưa
khai báo EXP không đáng làm hỏng lượt giết của người chơi.

### D5 — Art: `model_key` là một CỘT, và nó phải đi qua dây

Chủ dự án chọn "loài mới dùng tạm model đã có". Điều đó **không miễn phí như nó nghe**, và đây là chỗ
tôi phải nói thẳng:

Client hôm nay suy ra model bằng một **phép biến đổi thuần** (`MonsterModelKey`:
`training_dummy` → `mob_training-dummy`), và javadoc của nó nói rõ vì sao **cố ý không phải bảng
tra**: *"Bảng tra sẽ bắt phải sửa client mỗi lần thêm một con quái… client không nên là bên thứ ba
phải đồng ý."* Muốn hai loài khác nhau dùng chung một model thì phép biến đổi thuần không làm được
nữa, và chỉ còn hai lối:

1. **Server gửi `model_key`** — thêm **một field additive** vào `MonsterSpawnedEvent`
   (`protocolVersion` KHÔNG tăng, không thêm opcode). `MonsterModelKey` ở lại làm **đường lui**: field
   rỗng ⇒ suy như cũ, nên server cũ vẫn chạy với client mới.
2. Bảng tra phía client — **đã bị một quyết định cố ý bác bỏ** ở chính file đó.

⇒ Đề xuất lối 1. Nghĩa là **ADR này CÓ đổi protocol**, dù chỉ một field. Nói trước còn hơn để lộ ra
lúc thi hành.

Loài chưa có `.glb` riêng trỏ tạm `mob_training-dummy` và **phải ghi trong migration là tạm**. Loài
không khai `model_key` hợp lệ vẫn ra hộp thay thế như `sample_boss` hôm nay — không thêm cơ chế mới.

### D6 — Nội dung khởi điểm: ba loài, đủ để DEF có nghĩa

| code | max_hp | atk | def | res | exp | Ý đồ |
|---|---|---|---|---|---|---|
| `training_dummy` | 50 | 8 | 5 | 3 | 20 | **Giữ NGUYÊN số hiện tại** — không đợt nào được lặng lẽ đổi con quái mọi test đang dựa vào |
| `wandering_bandit` | 90 | 22 | 10 | 6 | 35 | Bậc giữa: đủ đau để DEF bắt đầu đọc được |
| `stone_ogre` | 200 | 40 | 25 | 15 | 90 | Mối đe doạ thật; đặt xa điểm hồi sinh |

Với `atk = 40` của `stone_ogre`, tiến trình phòng thủ **nhìn thấy được** lần đầu (tính theo
`100/(100+DEF)` và máu tối đa đo được ở playtest):

| Người chơi | Sát thương nhận | Số đòn chịu được |
|---|---|---|
| Không giáp (DEF 20, 100 máu) | 33 | 3 |
| Bộ Hạ Nhẫn +0 (DEF 43, 158 máu) | 28 | **6** |
| Bộ Hạ Nhẫn +15 (DEF 78, 245 máu) | 22 | **11** |

So với hình nộm hôm nay (15 → 26 → 61 đòn — ba con số đều là "không bao giờ chết"). ⚠️ Toàn bộ số ở
D6 là **đề xuất khởi điểm chưa playtest**, đúng thân phận mà bảng tỉ lệ §7.1 và `ENHANCE_STEP` từng có.

### D7 — KHÔNG làm ở đợt này

- **Tham số AI theo loài** (`AiConstants` → cột). Chủ dự án chốt để đợt sau. `AiConstants` giữ
  nguyên, mọi loài dùng chung aggro/leash/tốc độ.
- **Boss** (`BOSS_STATS`, `BossConstants`, script GraalJS) giữ nguyên hardcode: boss có thêm
  `sample_boss.js` và `BossAiSystem` riêng, kéo nó vào bảng là một quyết định khác.
- **`npc_template`** — cùng loại nợ (`SAMPLE_NPC_CODE` hardcode) nhưng NPC không có chỉ số chiến đấu;
  không gộp bừa vào để ADR khỏi phình.

---

## Alternatives Considered

| Phương án | Vì sao KHÔNG chọn |
|---|---|
| **A. `base_stats_json` + `ai_params_json`** (đúng DATABASE.md §2.2) | Đây là đặc tả GỐC nên phải cân nhắc thật, không bác cho nhanh. Nhưng nó đòi **một thư viện JSON mới** — thứ §4 bắt dừng hỏi và là quyết định lớn hơn hẳn quyết định này. Đổi lại được gì? Thêm chỉ số không cần migration. Cái giá: mất kiểm kiểu của Postgres, khoá sai chính tả **im lặng thành 0**, và `UPDATE` chỉnh cân bằng thành sửa chuỗi. Dự án đã hai lần chọn ngược (`drop_table_entry`, ADR-036) — đi ngược lại ở lần thứ ba mà không có dữ kiện mới là tự mâu thuẫn. **Hệ quả: DATABASE.md phải sửa theo.** |
| **B. Giữ hardcode, chỉ thêm hằng số cho loài mới** | Rẻ nhất, và thêm được quái thật. Nhưng mỗi lần cân bằng là **sửa Java + build lại + triển khai lại**, đúng thứ Điều 27 ("DB = tham số") sinh ra để tránh; và bốn chỗ hardcode sẽ thành sáu. Nó cũng không sửa được cái gốc: `MONSTER_SPAWN_X` vẫn cứng. |
| **C. Không thêm module: đặt Port ở `server-world`, adapter ở `server-database`** | Tránh được việc §4 (thêm module) và ít file hơn. Nhưng phải thêm cạnh phụ thuộc `server-database → server-world` **chưa từng tồn tại**, và phá thế đối xứng vừa dựng ở ADR-036 (world nhận record thuần, gateway dịch). Nếu chủ dự án ưu tiên "không thêm module", đây là phương án thay thế hợp lệ — chỉ cần chấp nhận đánh đổi đó có ý thức. |
| **D. Script hoá quái như skill** (GraalJS, ADR-016) | Nhất quán với "Java = cơ chế, Script = nội dung". Nhưng chỉ số quái là **dữ liệu bảng**, không phải hành vi; script hoá một hàng số là chọn công cụ nặng nhất cho việc nhẹ nhất, và sandbox GraalJS mới ở mức `CONSTRAINED` (ADR-016). Để dành cho hành vi boss, chỗ nó đang được dùng đúng. |
| **E. Chỉ `monster_template`, không `monster_spawn`** | Hẹp hơn, ít rủi ro hơn. Nhưng cả bốn điểm spawn hardcode cùng một mã, nên thế giới **vẫn chỉ có hình nộm** ⇒ không mở khoá được việc cân lại DEF, tức không giải quyết lý do ADR ra đời. |

---

## Consequences

**Được:**

- Thêm/sửa một loài quái = **một migration**, không đụng Java — đúng Điều 27.
- Bốn chỗ hardcode gom về một bảng; `MonsterExperience` (bảng tra Java tự nhận là tạm) biến mất.
- Lần đầu có kẻ địch đủ mạnh để `bonus_def`/`bonus_res` của V22 **đo được**, mở khoá đợt cân bằng
  mà playtest 2026-08-30 kết luận là cần.
- `drop_table` gắn bằng FK thay vì trùng chuỗi ngầm.

**Mất / phải chấp nhận:**

- **Đổi schema DB** (§4) — hai bảng mới, có seed.
- **Đổi protocol** (§4) — một field additive `model_key` trên `MonsterSpawnedEvent` (D5).
- **Thêm module `feature-monster`** (§4) — hoặc chọn Alternatives C.
- **`CombatConstants.MONSTER_STATS`/`MONSTER_MAX_HP` biến mất khỏi code** ⇒ mọi test đang dựa vào
  chúng phải đổi. `WorldFacadeTest` dùng số 50 máu quái ở nhiều chỗ; test phải tự dựng catalog thay
  vì mượn hằng số. Đây là phần việc lớn nhất của đợt, và là chỗ dễ vỡ nhất.
- Mỗi loài mới vẫn cần **khoá i18n `hud.monster.<code>` ở CẢ HAI bundle** — thiếu là hiện khoá trần
  giữa giao diện (`MessageBundleIntegrityTest` bắt được lệch bundle, KHÔNG bắt được khoá thiếu hẳn
  ở cả hai).
- Ba loài dùng chung một model là **nợ art nhìn thấy được ngay**, không giấu đi đâu được.
- `DATABASE.md §2.2` và `AI.md §10` mô tả sai so với kết quả ⇒ phải sửa cùng đợt.

**Không đổi:** không thêm opcode; không đổi `AiConstants`; không đụng boss/script; không đụng
`npc_template`; không đổi `protocolVersion`.

---

## Revisit Trigger

- Khi cần **tham số AI theo loài** (quái bắn xa, quái không đuổi) ⇒ mở lại D7, thêm cột vào cùng bảng.
- Khi boss thứ hai xuất hiện ⇒ mở lại "boss giữ hardcode".
- Khi số cột chỉ số vượt ~20, hoặc khi dự án **đã có** thư viện JSON vì lý do khác ⇒ đo lại
  Alternatives A (lúc đó cái giá của nó đã khác).
- Khi có `npc_template` ⇒ xem xét gộp quy ước `model_key`/`name_key` cho cả hai loại thực thể.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `monster_template` cột quan hệ, KHÔNG JSON — đi ngược DATABASE.md §2.2, phải sửa doc đó | Proposed |
| 2 | `monster_spawn` riêng, có `world_code`; ràng buộc `pos_y = 0` + cách điểm hồi sinh > 6m | Proposed |
| 3 | Gateway dịch, world nhận record thuần; **module `feature-monster` mới** (hoặc Alternatives C) | Chờ chủ dự án |
| 4 | `exp_reward` thành cột; xoá `MonsterExperience` | Proposed |
| 5 | `model_key` thành cột **và** một field additive trên `MonsterSpawnedEvent` | Chờ chủ dự án |
| 6 | Ba loài khởi điểm; `training_dummy` giữ NGUYÊN số cũ | Proposed |
| 7 | Tham số AI, boss, `npc_template` KHÔNG làm đợt này | Proposed |

## ADR cần tạo

Không. ADR này tự đứng được. Nhưng nó **để lại hai nợ đã đặt tên**, mỗi cái là một ADR sau nếu làm:
tham số AI theo loài, và `npc_template`.

## Tài liệu liên quan

- [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) — chỉ số trang bị; playtest của nó là
  lý do ADR này tồn tại, và khuôn `EquipmentBonusSnapshot` là khuôn D3 đi theo
- [ADR-034](ADR-034-Manual-Attack-And-Experience.md) — EXP hardcode, mục 5 tự nhận là placeholder
- [DATABASE.md §2.2](../../05-server/DATABASE.md) — đặc tả `monster_template` **sẽ phải sửa**
- [AI.md §10](../../03-engine/AI.md) — tham số AI thuộc `monster_template` (D7 hoãn)
- [GAMEPLAY.md §5](../../05-server/GAMEPLAY.md) — rơi đồ theo `drop_table`
- [ROADMAP mục 42](../../11-process/ROADMAP.md) — số đo playtest sinh ra ADR này

## Việc cần làm tiếp theo

1. **Chủ dự án quyết**: duyệt/bác D1-D7, và trả lời riêng hai câu §4 — **module mới hay Alternatives
   C** (Technical Decision 3), và **có chấp nhận field `model_key` trên `MonsterSpawnedEvent` không**
   (Technical Decision 5).
2. Nếu duyệt, thứ tự thi hành đề xuất: migration V23 (2 bảng + seed 3 loài) → `feature-monster` +
   adapter → `MonsterCatalogSnapshot` + `WorldFacade` đọc catalog thay hằng số → sửa
   `WorldFacadeTest` → `exp_reward` thay `MonsterExperience` → field `model_key` + client →
   i18n 2 bundle → **sửa DATABASE.md §2.2 và AI.md §10**.
3. Sau khi chạy: **playtest lại**, đo đúng bảng "số đòn chịu được" ở D6 — đó mới là lúc biết số
   DEF/RES của V22 đúng hay sai, và là lý do cả ADR này tồn tại.
