# ADR-039: Tham số AI theo LOÀI — trả nốt món nợ D7 của ADR-037, và tách NỘI DUNG khỏi CƠ CHẾ

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-09-04) — chờ chủ dự án duyệt/bác D1–D7. **Chưa Accepted.** |
| Date | 2026-09-04 |
| Deciders | Chủ dự án quyết; AI đo + soạn |
| Related | [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) (D7 hoãn đúng việc này) · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (khuôn `EquipmentBonusSnapshot` mà đường dữ liệu đi theo) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (khoá trục X) · [ADR-022](ADR-022-Custom-FSM-No-Gdx-Ai.md) (FSM tự viết) · [AI.md §10](../../03-engine/AI.md) · [DATABASE.md §2.4](../../05-server/DATABASE.md) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi schema DB ⇒ dừng, tạo ADR) |

---

## Context

### Món nợ đã có tên từ trước

[ADR-037 D7](ADR-037-Monster-Template-And-Spawn-Tables.md) đưa chỉ số/EXP/vị trí quái vào DB nhưng
**cố ý hoãn tham số AI**, và ghi thẳng vào "Việc cần làm tiếp theo" mục 5:

> Còn lại: **tham số AI theo loài (D7)** — aggro/leash/tốc độ đuổi vẫn dùng chung `AiConstants`.

`Revisit Trigger` của chính ADR đó cũng đã đặt điều kiện mở lại: *"khi cần tham số AI theo loài
(quái bắn xa, quái không đuổi) ⇒ mở lại D7, thêm cột vào cùng bảng"*. ADR này là lần mở lại đó, và
**"quái không đuổi" hoá ra không phải chuyện tương lai — nó đang cần ngay hôm nay** (xem D4).

Hôm nay `server/server-world/.../AiConstants.java` giữ **8 hằng số mà MỌI loài dùng chung**:

| Hằng | Giá trị | Dùng ở đâu trong `AiSystem` |
|---|---|---|
| `AGGRO_RADIUS_METERS` | 6 | `tickIdle`, `tickPatrol` — bán kính phát hiện người chơi |
| `LEASH_RADIUS_METERS` | 10 | `tickChase` — quá xa spawn thì bỏ cuộc |
| `CHASE_SPEED_MPS` | 3 | `tickChase`, `tickReturn` |
| `PATROL_SPEED_MPS` | 1.5 | `tickPatrol` |
| `PATROL_RADIUS_METERS` | 3 | `startPatrolling` — bán kính chọn điểm tuần tra |
| `IDLE_DURATION_SECONDS` | 3 | `tickIdle` — đứng bao lâu rồi mới đi tuần |
| `ARRIVAL_THRESHOLD_METERS` | 0.3 | `tickPatrol`, `tickReturn` — "đã tới nơi chưa" |
| `MAX_PATROL_DURATION_SECONDS` | 5 | `tickPatrol` — van chống kẹt |

Javadoc của chính file đó tự tố cáo mình:

> AI.md §10 nói tham số AI nên là config data (`monster_template` DB) — chưa có bảng đó […] nên tạm
> đặt hằng số ở đây

Bảng đó **nay đã có** (V23). Lý do "tạm" đã hết hiệu lực.

### `AI.md §10` tự đánh dấu là SAI

[AI.md §10](../../03-engine/AI.md) mang sẵn cảnh báo:

> ⚠️ **CHƯA đúng tính đến 2026-08-30**: ADR-037 đưa chỉ số/EXP/vị trí vào DB nhưng **hoãn tham số
> AI** (D7) — chúng vẫn là hằng số trong `AiConstants`, mọi loài dùng chung

và Technical Decision #8 của nó ghi gọn lỏn: *"Tham số AI là **config**, không phải code"* (Điều 27).

⚠️ **Câu đó nói QUÁ, và ADR này nói thẳng ra chứ không im lặng làm theo.** Xem D1.

### Điều tra bắt buộc: `training_dummy` có ĐUỔI THEO người chơi không?

Câu trả lời: **CÓ. Và đó là một lỗi thật, không phải cảm giác.**

Bằng chứng đọc thẳng từ code, không suy đoán:

1. `WorldFacade.spawnMonster(int)` gắn cho **mọi** loài trong catalog đủ bộ
   `AiStateComponent` + `TargetComponent` + `AttackCooldownComponent` — không có nhánh nào phân biệt
   loài, không có cờ "hiền/dữ".
2. `AiSystem` khớp `Family.all(Position, Velocity, AiState, Target)` ⇒ con hình nộm rơi trọn vào FSM
   Idle → Chase → Attack.
3. `AiConstants.AGGRO_RADIUS_METERS = 6` áp cho mọi loài ⇒ người chơi đi tới cách hình nộm 6m là nó
   **rời cọc, đuổi theo với tốc độ 3 m/s** (`CHASE_SPEED_MPS`), tới tầm 2m thì đánh (`atk = 8`).
4. Không cần chạy game để xác nhận: **test trong repo đã khẳng định đúng điều đó**.
   `WorldFacadeTest.shouldChaseAndAttackBack_whenCharacterEntersAggroRange` dựng catalog
   `TestMonsterCatalogs.trainingGround()` (chỉ có `training_dummy`), **không** gọi `setTarget`, rồi
   assert `monsterMoved` và `attackResults` có đòn do CHÍNH con quái đó gây ra. Nghĩa là hành vi
   "hình nộm đuổi và đánh người" đang được **khoá lại bằng test** như thể nó là đặc tả.
5. Playtest ADR-037 (2026-08-30) cũng dựa vào chính hành vi này để đo **−7 sát thương** mà hình nộm
   gây ra cho người chơi.

Vì sao đó là lỗi chứ không phải "thiết kế đã chốt":

- `code = training_dummy`; i18n hiện là **"Mộc nhân luyện công"** (vi) / *"Practice Puppet"* (en).
  Sau [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) nó là **cọc gỗ luyện công của tông môn**. Một cây cọc
  gỗ chạy 3 m/s đuổi theo người chơi qua nửa bản đồ là chuyện không có cách nào kể cho xuôi.
- Javadoc `WorldFacade` gọi nó là *"Quái bù nhìn tập luyện"*. Bù nhìn không đuổi ai.
- Nó ở **x = 8 và x = 14**, tức hai điểm spawn GẦN NHẤT với điểm hồi sinh `(0,0)`. ADR-037 D2 đã ghi
  nhận đúng triệu chứng này ở dạng nhẹ: *"chết → hồi sinh → bị đuổi đánh lại trước khi kịp làm gì"*,
  và phải đặt ra ràng buộc "điểm spawn cách `(0,0)` xa hơn 6m" **chỉ để chống lại** hành vi đó.
- Lý do lịch sử rất rõ: khi `AiSystem` ra đời (Phase 4) thế giới có **đúng một loài quái**, nên
  "quái" và "hình nộm" là cùng một thứ và không ai phải chọn. ADR-037 vừa phá vỡ điều kiện đó bằng
  hai loài mới; hình nộm nay có thể trở lại làm đúng nghề của nó.

⚠️ Sửa nó **cần một tham số theo loài** — chính là thứ ADR này mở khoá. Không có `aggro_radius` theo
loài thì mọi cách sửa khác đều là hardcode `if (code.equals("training_dummy"))`, đúng loại code mà
ADR-037 D4 vừa xoá đi (`MonsterExperience`).

### Ràng buộc kiến trúc phải tôn trọng (không đổi từ ADR-037)

- **`server-database` KHÔNG phụ thuộc `server-world`** — nó chỉ phụ thuộc `shared-common` +
  `feature-*`. Chỉ `server-gateway` phụ thuộc cả hai. Đây là ràng buộc chi phối mọi đường dữ liệu
  world trong dự án.
- **`server-world` không được biết DB** (Điều 23) ⇒ nhận record THUẦN, không đi lấy.
- **Trong game loop cấm `new` mỗi frame, boxing, allocation thừa** (/CLAUDE.md §6). `AiSystem` chạy
  cho MỌI quái, MỌI tick, 60Hz ⇒ cách tra tham số phải **không cấp phát gì cả**. Xem D5 —
  đây là chỗ dễ làm sai nhất của cả đợt.
- `WorldFacade` constructor đã **4 tham số**, chạm đúng trần Checkstyle `ParameterNumber` (max 4) ⇒
  tham số AI **không được** đi vào world bằng một tham số constructor thứ 5.

---

## Decision

### D1 — Chia đôi `AiConstants`: 6 hằng là NỘI DUNG (vào DB), 2 hằng là CƠ CHẾ (ở lại code)

Đây là phần đắt nhất của ADR này, nên nói cho hết.

| Hằng hôm nay | Đi đâu | Vì sao |
|---|---|---|
| `AGGRO_RADIUS_METERS` | → cột `aggro_radius_meters` | **Nội dung.** "Con này hung dữ tới đâu" là câu hỏi thiết kế trò chơi, không phải câu hỏi kỹ thuật. Là tham số **duy nhất** cần để sửa lỗi D4. |
| `LEASH_RADIUS_METERS` | → cột `leash_radius_meters` | **Nội dung.** Bán kính "lãnh thổ" — quái canh cửa bám 3m, quái săn mồi bám 25m. |
| `CHASE_SPEED_MPS` | → cột `chase_speed_mps` | **Nội dung.** Nhanh/chậm là đặc trưng loài, và là đòn bẩy độ khó rẻ nhất đang có (chạy thoát được hay không). |
| `PATROL_SPEED_MPS` | → cột `patrol_speed_mps` | **Nội dung.** Cùng lý do. |
| `PATROL_RADIUS_METERS` | → cột `patrol_radius_meters` | **Nội dung.** Và `0` mang nghĩa hữu ích sẵn có: đứng NGUYÊN tại chỗ. |
| `IDLE_DURATION_SECONDS` | → cột `idle_duration_seconds` | **Nội dung.** Nhịp thở của loài — con bồn chồn 1s, con lười 10s. |
| `ARRIVAL_THRESHOLD_METERS` | **ở lại code** | **Cơ chế.** Xem dưới. |
| `MAX_PATROL_DURATION_SECONDS` | **ở lại code** | **Cơ chế.** Xem dưới. |

**Vì sao `ARRIVAL_THRESHOLD_METERS` KHÔNG được vào DB.** Nó không mô tả con quái, nó mô tả **cách một
vòng lặp rời rạc quyết định "đã tới nơi"**. Giá trị đúng của nó là hàm của `FIXED_DT` và tốc độ, chứ
không phải của tính cách loài: ở 60Hz, quái đuổi 3 m/s đi được 5cm mỗi tick, nên 0.3m ≈ 6 tick dung
sai. Đưa nó ra cho người thiết kế nội dung là **mời họ phá bất biến của FSM bằng một con số trông rất
hợp lý**: đặt `0` (hoặc bất kỳ giá trị nào nhỏ hơn `speed × FIXED_DT`) thì `tickReturn` **không bao
giờ** thoả điều kiện tới nơi — con quái rung lắc quanh điểm spawn **vĩnh viễn**, không bao giờ về
`IDLE`, và **không có gì báo lỗi cả**. Đúng loại hỏng mà `stats_json` sai chính tả từng bị ADR-036 và
ADR-037 bác vì im lặng.

**Vì sao `MAX_PATROL_DURATION_SECONDS` KHÔNG được vào DB.** Comment của chính nó nói rõ nó là gì:
*"tránh kẹt nếu vì lý do gì không tới được điểm patrol"* — một **van an toàn**, không phải một lựa
chọn thiết kế. Giá trị đúng là "đủ dài để một lượt tuần tra lành mạnh kịp xong, đủ ngắn để một con
quái kẹt tự thoát". Cả hai vế đều là tính chất của engine. Cho vào DB thì có hai cách hỏng: đặt `0`
(mọi lượt tuần tra kết thúc ngay tick sau ⇒ quái không bao giờ nhúc nhích nhưng vẫn đốt một lần
chuyển trạng thái mỗi tick) và đặt `3600` (một con quái kẹt sẽ kẹt nguyên một giờ). Van an toàn mà
người dùng chỉnh được thì không còn là van an toàn.

⚠️ **Tương tác đã biết, ghi ra chứ không giấu:** thời gian đi hết một lượt tuần tra là
`patrol_radius_meters / patrol_speed_mps`. Nếu con số đó vượt `MAX_PATROL_DURATION_SECONDS` (5s) thì
quái **không bao giờ tới** điểm tuần tra — nó luôn hết giờ giữa đường rồi về `IDLE`. Hỏng lành: quái
vẫn di chuyển, vẫn aggro, chỉ là đi ngắn hơn dự định. Cố ý **KHÔNG** dựng `CHECK` cho ràng buộc này
vì `CHECK` đó sẽ phải nhúng hằng số Java (5) vào schema — hai nguồn chân lý cho cùng một con số là
thứ mục rữa nhanh nhất. Ba loài hiện tại đều an toàn (3 / 1.5 = 2s < 5s).

### D1b — AI.md §10 nói QUÁ, và phải sửa

Bảng ở [AI.md §10](../../03-engine/AI.md) ghi *"**Tham số** (aggro range, tốc độ đuổi, cooldown skill)
→ **Config data** — `monster_template`"*, còn Technical Decision #8 rút gọn thành *"Tham số AI là
config, không phải code"*. Ba ví dụ nó liệt kê đều là **nội dung** — nhưng câu tổng quát thì phủ lên
**mọi con số** trong `AiSystem`, kể cả epsilon hình học và van chống kẹt.

⇒ ADR này **thu hẹp** câu đó (không bác): *tham số mô tả **tính cách một loài** là nội dung; con số
giữ **bất biến của FSM** là cơ chế và ở lại cạnh FSM*. AI.md §10 + Technical Decision #8 phải sửa
theo đúng phân định này, và gỡ dấu ⚠️ "CHƯA đúng".

### D2 — Migration V25: cột mới trên `monster_template`, DEFAULT = đúng hằng số hiện tại

```sql
ALTER TABLE monster_template
    ADD COLUMN aggro_radius_meters   REAL NOT NULL DEFAULT 6,
    ADD COLUMN leash_radius_meters   REAL NOT NULL DEFAULT 10,
    ADD COLUMN chase_speed_mps       REAL NOT NULL DEFAULT 3,
    ADD COLUMN patrol_speed_mps      REAL NOT NULL DEFAULT 1.5,
    ADD COLUMN patrol_radius_meters  REAL NOT NULL DEFAULT 3,
    ADD COLUMN idle_duration_seconds REAL NOT NULL DEFAULT 3;
```

🔒 **Ràng buộc bắt buộc của đợt này: migration KHÔNG được đổi hành vi.** Mỗi `DEFAULT` **bằng đúng**
giá trị hằng số đang chạy trong `AiConstants`, nên ba hàng đã có trong `monster_template` nhận lại
chính con số chúng đang dùng; chạy migration xong, game hành xử **y hệt**. Đây là cùng tinh thần
"`training_dummy` giữ NGUYÊN số cũ" mà ADR-037 D6 đặt ra để có một hàng đối chứng.

**Ngoại lệ DUY NHẤT, và nó là chủ đích:** hai `UPDATE` cho `training_dummy` ở D4. Không có ngoại lệ
nào khác, và ngoại lệ đó gỡ được bằng một dòng SQL.

`REAL` chứ không `INT`: `patrol_speed_mps = 1.5` là giá trị đang chạy thật, và các cột cùng nhóm phải
cùng kiểu để đọc bảng không phải nhớ ngoại lệ. Khác nhóm `hit_percent` (V23 chọn `INT` vì phần trăm
nguyên) — ở đây đơn vị là **mét** và **m/s**, số thập phân là tự nhiên.

### D3 — `CHECK` gác những bất biến mà một cặp hằng số chung KHÔNG BAO GIỜ vi phạm được

Đây là cái giá thật của việc mở tham số ra cho dữ liệu, nên phải trả ngay trong cùng migration:

```sql
CONSTRAINT monster_template_ai_non_negative CHECK (
    aggro_radius_meters >= 0
    AND patrol_radius_meters >= 0
    AND leash_radius_meters > 0
    AND chase_speed_mps > 0
    AND patrol_speed_mps > 0
    AND idle_duration_seconds > 0),
CONSTRAINT monster_template_ai_leash_covers_aggro CHECK (
    leash_radius_meters >= aggro_radius_meters),
CONSTRAINT monster_template_ai_patrol_within_leash CHECK (
    patrol_radius_meters <= leash_radius_meters)
```

- `leash >= aggro` là bất biến **thật**, không phải cho đẹp. `aggro` đo từ **quái tới người chơi**,
  còn `leash` đo từ **quái tới điểm spawn của nó**. Nếu `leash < aggro`, một con quái aggro người
  chơi đứng ngoài tầm leash sẽ đuổi được vài mét rồi lập tức `RETURN`, về tới nơi lại thấy người
  chơi vẫn trong tầm aggro ⇒ **dao động Chase↔Return vô hạn**, quái rung tại chỗ, không bao giờ đánh
  được ai. Với hằng số chung (6 và 10) điều đó là bất khả; với dữ liệu thì chỉ cách một lần gõ sai.
- `patrol_radius <= leash` chặn con quái tuần tra ra ngoài lãnh thổ của chính nó rồi aggro từ đó và
  bị leash ngay lập tức.
- `aggro_radius >= 0` (cho phép **0**) chứ không `> 0`: **0 là một giá trị có nghĩa** — xem D4.

### D4 — `training_dummy` thành **thụ động**: `aggro_radius = 0`, `patrol_radius = 0`

Kết quả điều tra ở Context ⇒ sửa. Hai `UPDATE`, không đổi schema:

```sql
UPDATE monster_template
   SET aggro_radius_meters = 0, patrol_radius_meters = 0
 WHERE code = 'training_dummy';
```

**`aggro_radius = 0` nghĩa là KHÔNG BAO GIỜ gây hấn** — và điều đó phải được viết thành một nhánh
tường minh trong `AiSystem`, không được để nó "tự đúng" nhờ phép so sánh. Lý do: hàm quét hiện tại là
`d <= nearestDistance` với `nearestDistance` khởi tạo bằng `range`, nên `range = 0` vẫn aggro một
người chơi đứng **đúng** tại toạ độ con quái, và vẫn quét toàn bộ danh sách người chơi mỗi tick cho
mỗi con quái. Nhánh tường minh vừa đúng nghĩa vừa **rẻ hơn** (bỏ hẳn vòng quét O(n) cho mọi quái
thụ động).

`patrol_radius = 0` để cây cọc đứng yên tại chỗ thay vì đi lang thang 3m quanh gốc. Tương thích sẵn
với FSM: `startPatrolling` chọn điểm trùng điểm spawn ⇒ `tickPatrol` thấy đã tới nơi ngay tick sau ⇒
về `IDLE`, vận tốc 0. Không thêm trạng thái, không thêm nhánh.

**Hệ quả, nói trước:**

- Hình nộm **không đánh trả nữa**. Người chơi vẫn giết được nó (đánh tay, ADR-034), quest
  `kill_training_dummies.js` vẫn chạy nguyên vẹn, EXP vẫn 20.
- Hai điểm spawn gần nhà (x = 8, x = 14) thành **vùng luyện tập an toàn**; nguy hiểm thật bắt đầu từ
  cướp lang thang (x = 21, 27) và quỷ đá (x = 34). Đây là bố cục tốt hơn cái đang có, không phải mất
  mát.
- Chuỗi "chết → hồi sinh → bị đuổi đánh lại" mà ADR-037 D2 ghi nhận **biến mất** với hai điểm gần
  nhất.
- `WorldFacadeTest.shouldChaseAndAttackBack_whenCharacterEntersAggroRange` đang khoá hành vi CŨ.
  Test đó kiểm **cơ chế FSM** (Idle→Chase→Attack) chứ không kiểm con hình nộm, nên nó giữ nguyên
  fixture **hung dữ** (aggro 6m) và được ghi rõ trong javadoc là fixture cơ chế; thêm test mới cho
  loài thụ động. Không test nào bị xoá.
- 🔁 **Gỡ bằng một dòng** nếu chủ dự án không đồng ý: `UPDATE monster_template SET
  aggro_radius_meters = 6, patrol_radius_meters = 3 WHERE code = 'training_dummy';` trong một
  migration sau.

### D5 — Đường dữ liệu: đúng đường ADR-037 đã mở, và tra cứu **không cấp phát**

```
feature-monster            server-database             server-gateway          server-world
  MonsterAiParams   ←  PostgresMonsterCatalog   →  MonsterCatalogLoader →  MonsterCatalogSnapshot
  (record, trong        Adapter (6 cột mới)         dịch sang record         .MonsterAiNumbers
   MonsterTemplate)                                 thuần của world               ↓
                                                                          AiParamsComponent
                                                                          gắn lên entity lúc spawn
                                                                                  ↓
                                                                          AiSystem đọc qua
                                                                          ComponentMapper
```

**Điểm quan trọng nhất của D5, và là chỗ dễ làm sai nhất:** cách hiển nhiên — `AiSystem` cầm
`MonsterCatalogSnapshot` rồi mỗi tick gọi `catalog.find(monster.monsterCode)` — **vi phạm /CLAUDE.md
§6**: một lần tra `HashMap` theo chuỗi **cộng một `Optional` mới** cho **mỗi con quái, mỗi tick**, 60
lần/giây. Thay vào đó tham số được **hoá thân thành một Component** đúng một lần lúc spawn; trong
tick, `AiSystem` chỉ làm `ComponentMapper.get(entity)` — O(1), không cấp phát, cùng đúng cách
`CombatStatsComponent` đang được đối xử.

`AiParamsComponent` là **`record`**, khác với mọi Component hiện có (mutable + field public,
ECS.md §3.1). Đây là ngoại lệ có lý do: mọi Component khác giữ **trạng thái chạy** (bị System ghi mỗi
tick); cái này giữ **cấu hình tĩnh của loài**, không ai được ghi. Bất biến là thứ khiến việc dùng
CHUNG một instance cho nhiều entity cùng loài trở nên an toàn hiển nhiên.

**`AiParamsComponent` vào `Family` của `AiSystem`.** Entity có `AiStateComponent` mà thiếu tham số
thì không phải "quái chạy bằng mặc định" — nó là entity dựng sai. Cho vào Family nghĩa là ECS tự gác:
nó đơn giản không phải entity AI. Rõ hơn hẳn một nhánh `has() ? get() : DEFAULT` âm thầm cứu lỗi.

Trạng thái chạy (`state`, `stateTimer`, `spawnX/Y`, `patrolTargetX/Y`) ở lại `AiStateComponent`,
không trộn vào. Hai vòng đời khác nhau ⇒ hai component.

### D6 — Boss vẫn dùng mặc định, và mặc định đó vẫn tên `AiConstants`

`sample_boss` **không có hàng** trong `monster_template` (ADR-037 D7 giữ boss hardcode: nó có
`BossAiSystem` + script GraalJS riêng). `WorldFacade.spawnBossEntity` gắn cho boss một
`AiParamsComponent` dựng từ `AiConstants` — tường minh, một chỗ, có comment. Không có đường lui ngầm
nào khác.

`AiConstants` **không biến mất**: nó giữ 2 hằng cơ chế (D1) **và** bộ giá trị mặc định dùng cho boss
+ cho mọi `DEFAULT` của D2. Nó thôi làm "tham số của mọi loài" và trở thành "mặc định + hằng cơ chế"
— javadoc phải nói đúng điều đó.

### D7 — KHÔNG làm ở đợt này

- **`ATTACK_RANGE_METERS` theo loài** (quái bắn xa). Nó nằm ở `CombatConstants`, không phải
  `AiConstants`, và `CombatSystem` cũng dùng nó — kéo vào đây là mở một mặt trận thứ hai (tầm đánh
  của **nhân vật** cũng đọc chính hằng đó). Ghi nhận là món nợ kế tiếp, đúng cách ADR-037 ghi nhận
  món nợ này.
- **Cooldown skill của quái** (AI.md §10 có nhắc). Quái thường chưa dùng skill; chỉ boss dùng, mà
  boss đang chạy script.
- **Tham số AI theo ĐIỂM SPAWN** (một con "tinh anh" của cùng loài ở một chỗ). Chỗ đúng của nó là
  `monster_spawn`, không phải bảng thứ ba — xem Alternatives C.
- **Time-slicing AI** (AI.md §9). Là tối ưu, phải đo trước (Điều 30).
- **Cho `wandering_bandit`/`stone_ogre` tính cách riêng.** Nay tốn đúng một câu `UPDATE` mỗi loài —
  đó chính là thứ ADR này mở khoá. Nhưng đặt số **chưa playtest** vào cùng migration đổi hạ tầng là
  đúng thứ ADR-037 đã học: bảng D6 của nó chỉ đáng tin sau khi playtest khớp từng ô. Cân bằng là
  một đợt riêng, có số đo. Hai loài đó giữ **nguyên mặc định** ở đợt này.

---

## Alternatives Considered

| Phương án | Vì sao KHÔNG chọn |
|---|---|
| **A. `ai_params_json` trên `monster_template`** (đúng đặc tả gốc DATABASE.md §2.2) | Vẫn là đặc tả gốc nên phải cân nhắc thật. Nhưng lập luận không đổi kể từ ADR-037 D1: đòi **một thư viện JSON mới** (§4 — quyết định lớn hơn hẳn quyết định này), mất kiểm kiểu của Postgres, khoá gõ sai **im lặng thành 0** — mà ở đây "im lặng thành 0" đặc biệt độc: `chase_speed = 0` là con quái đứng hình, `aggro = 0` là con quái mù, và không có gì báo. Và **mất luôn D3**: `CHECK` liên-cột `leash >= aggro` không viết được trên JSON. Đây là lần thứ TƯ dự án đứng ở ngã ba này (`drop_table_entry`, ADR-036, ADR-037) — đi ngược mà không có dữ kiện mới là tự mâu thuẫn. |
| **B. Giữ `AiConstants`, thêm bảng tra Java theo `code`** | Rẻ nhất, không migration. Nhưng đó **chính xác** là `MonsterExperience` — bảng tra `switch` mà ADR-037 D4 vừa xoá và javadoc của chính nó tự gọi là tạm. Tái phạm cùng một sai lầm trong cùng một tháng, ở cùng một bảng dữ liệu. |
| **C. Bảng riêng `monster_ai_params` (1:1 với `monster_template`)** | Gọn về mặt "một bảng một trách nhiệm". Nhưng 1:1 bắt buộc là một cái smell: thêm một JOIN, thêm một chỗ có thể thiếu hàng, đổi lại **không được gì** — mọi loài đều cần tham số AI, không có loài nào "không có". Nếu sau này tham số AI cần khác nhau **theo điểm spawn** (biến thể tinh anh) thì chỗ đúng là `monster_spawn`, và lúc đó vẫn không cần bảng thứ ba. |
| **D. Đưa CẢ 8 hằng vào DB** (đọc AI.md §10 theo nghĩa đen) | Nhất quán bề mặt với câu chữ của AI.md, và không phải phân xử ranh giới nào. Nhưng nó mở ra hai cách phá FSM âm thầm mà D1 đã mô tả (`arrival = 0` ⇒ quái rung vĩnh viễn; van chống kẹt chỉnh được ⇒ không còn là van). Chọn "nhất quán với một câu viết vội" thay vì "đúng" là đảo ngược thứ tự ưu tiên ở /CLAUDE.md §8. ⇒ Sửa AI.md (D1b), không sửa sự thật. |
| **E. `AiSystem` giữ `MonsterCatalogSnapshot`, tra theo `monsterCode` mỗi tick** | Ít file hơn, không cần Component mới. Nhưng cấp phát + tra `HashMap` theo chuỗi **mỗi quái mỗi tick** — /CLAUDE.md §6 cấm thẳng. Và nó buộc `AiSystem` phụ thuộc catalog, tức boss (không có trong catalog) phải có nhánh riêng. |
| **F. Sửa hình nộm bằng một cờ `is_passive BOOLEAN`** | Giải quyết D4 mà không cần cả ADR này. Nhưng nó là một cột **chỉ để nói "đừng chạy phần này của FSM"** — một cái công tắc bên cạnh một tham số vốn đã diễn tả được đúng điều đó (`aggro_radius = 0`). Hai nguồn chân lý cho cùng một hành vi, và câu hỏi "cờ bật nhưng aggro = 12 thì sao?" không có câu trả lời hay. |

---

## Consequences

**Được:**

- Món nợ D7 của ADR-037 đóng lại; `AiConstants` thôi là "tham số của mọi loài".
- Thêm một loài có **tính cách AI riêng** = một dòng SQL, không đụng Java — Điều 27.
- **Sửa được một lỗi thật đã sống từ Phase 4**: cây cọc gỗ thôi săn người chơi.
- `CHECK` liên-cột gác một bất biến (`leash >= aggro`) mà trước đây không ai kiểm vì không ai vi
  phạm được.
- AI.md §10 hết dấu ⚠️ "CHƯA đúng", và câu Technical Decision #8 của nó trở nên **chính xác** thay vì
  chỉ đúng đại khái.

**Mất / phải chấp nhận:**

- **Đổi schema DB** (§4) — 6 cột + 3 `CHECK`, một migration.
- **Một Component mới** (`AiParamsComponent`) và nó là `record`, phá thông lệ "component mutable"
  của ECS.md §3.1 — có lý do (D5) nhưng vẫn là ngoại lệ đầu tiên, và ngoại lệ đầu tiên bao giờ cũng
  là cái mở đường cho ngoại lệ thứ hai.
- **`training_dummy` đổi hành vi** — ngoại lệ có chủ đích duy nhất của D2, và là thứ chủ dự án phải
  duyệt riêng.
- Ba fixture test (`TestMonsterCatalogs` ×2 module, `MonsterCatalogSnapshotTest`) và mọi chỗ dựng
  `MonsterTemplate`/`MonsterSpecies` phải khai thêm tham số AI. Cùng loại chi phí ADR-037 đã trả.
- Người chỉnh cân bằng nay **có thể** làm quái cư xử kỳ quặc bằng SQL (quái nhanh 50 m/s, quái aggro
  cả bản đồ). `CHECK` chặn được cái vô lý về cấu trúc, **không** chặn được cái vô lý về thẩm mỹ. Đó
  là cái giá cố hữu của Điều 27, không phải khuyết điểm riêng của ADR này.
- `DATABASE.md §2.4` và `AI.md §10` mô tả sai so với kết quả ⇒ phải sửa cùng đợt.

**Không đổi:** không đổi protocol, không thêm opcode, không đổi `protocolVersion`; không đụng
`CombatConstants`; không đụng boss/script (chỉ gắn thêm mặc định tường minh); không đụng
`npc_template`; không thêm module; không thêm dependency.

---

## Revisit Trigger

- Khi có **quái bắn xa** ⇒ mở lại D7 mục `ATTACK_RANGE_METERS` (kéo theo cả tầm đánh của nhân vật).
- Khi cần **hai con cùng loài khác tính cách** (biến thể tinh anh) ⇒ thêm cột **ghi đè** trên
  `monster_spawn`, không phải bảng thứ ba (Alternatives C).
- Khi boss thứ hai xuất hiện ⇒ xem lại "boss dùng mặc định `AiConstants`" cùng lúc với "boss giữ
  hardcode" của ADR-037.
- Khi số Component `record` vượt một cái ⇒ sửa ECS.md §3.1 cho đúng thay vì tích luỹ ngoại lệ.
- Khi `AiSystem` đo thấy tốn CPU ở quy mô N lớn ⇒ AI.md §9 time-slicing (Điều 30: đo trước).

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chia `AiConstants`: 6 hằng → cột DB (nội dung); `ARRIVAL_THRESHOLD_METERS` + `MAX_PATROL_DURATION_SECONDS` ở lại code (cơ chế) | 🟡 Proposed |
| 1b | **AI.md §10 + Technical Decision #8 nói QUÁ** — thu hẹp thành "tham số mô tả tính cách loài là nội dung; con số giữ bất biến FSM là cơ chế" | 🟡 Proposed |
| 2 | Migration **V25** thêm 6 cột với `DEFAULT` = đúng hằng số hiện tại ⇒ **không đổi hành vi** | 🟡 Proposed |
| 3 | `CHECK` liên-cột: `leash >= aggro`, `patrol_radius <= leash`, tốc độ/thời gian > 0, bán kính >= 0 | 🟡 Proposed |
| 4 | **`training_dummy` thành thụ động** (`aggro_radius = 0`, `patrol_radius = 0`) — ngoại lệ có chủ đích DUY NHẤT của #2, gỡ được bằng 1 dòng SQL | 🟡 Proposed — **chủ dự án duyệt riêng** |
| 5 | Tham số hoá thân thành `AiParamsComponent` (record, bất biến) lúc spawn; `AiSystem` đọc qua `ComponentMapper`, **không cấp phát trong tick** | 🟡 Proposed |
| 6 | Boss dùng mặc định `AiConstants` một cách **tường minh**; `AiConstants` ở lại làm "mặc định + hằng cơ chế" | 🟡 Proposed |
| 7 | Tầm đánh theo loài, cooldown skill quái, tham số theo điểm spawn, time-slicing: KHÔNG làm đợt này | 🟡 Proposed |

## ADR cần tạo

Không. ADR này tự đứng được. Nó để lại **một** món nợ đã đặt tên: `ATTACK_RANGE_METERS` theo loài
(quái bắn xa) — sẽ là một ADR riêng nếu làm, vì nó đụng cả tầm đánh của nhân vật.

## Tài liệu liên quan

- [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) — D7 hoãn đúng việc này; `Revisit Trigger`
  của nó là điều kiện mở ADR này
- [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) — khuôn `EquipmentBonusSnapshot` mà
  đường dữ liệu D5 đi theo
- [ADR-033](ADR-033-Ninja-School-Style-Movement.md) — khoá trục X, lý do quái phải ở `y = 0`
- [ADR-022](ADR-022-Custom-FSM-No-Gdx-Ai.md) — FSM tự viết, tức những bất biến ở D1 là của CHÍNH
  dự án phải giữ
- [AI.md §10](../../03-engine/AI.md) — **sẽ phải sửa** (D1b)
- [DATABASE.md §2.4](../../05-server/DATABASE.md) — đặc tả `monster_template`, **sẽ phải sửa**
- [ECS.md §3.1](../../03-engine/ECS.md) — thông lệ "component mutable + field public" mà D5 làm
  ngoại lệ

## Việc cần làm tiếp theo

1. **Chủ dự án quyết**: duyệt/bác D1–D7. Hai câu cần trả lời riêng:
   - **D1** — có đồng ý giữ `ARRIVAL_THRESHOLD_METERS` và `MAX_PATROL_DURATION_SECONDS` trong code
     (tức **sửa AI.md §10** cho khớp), hay muốn đưa cả 8 hằng vào DB (Alternatives D)?
   - **D4** — có đồng ý cho `training_dummy` thành **thụ động** không? Đây là thay đổi hành vi thật
     mà người chơi cảm nhận được ngay.
2. Thứ tự thi hành đề xuất: migration V25 → `MonsterAiParams` + adapter → `MonsterAiNumbers` trên
   snapshot + `MonsterCatalogLoader` → `AiParamsComponent` + `WorldFacade` → `AiSystem` đọc theo
   loài → test → **sửa AI.md §10 và DATABASE.md §2.4**.
3. Thêm dòng ADR-039 vào [adr/README.md](README.md) (chỉ mục ADR).
4. **Playtest** sau khi Accept: đo lại "hình nộm không đuổi nữa" và "cướp/quỷ đá vẫn đuổi như cũ" —
   ADR-037 đã đặt tiền lệ rằng bảng số chỉ đáng tin sau khi playtest khớp từng ô.
