# ADR-040: NPC đọc từ DB — `npc_spawn` gắn vào `npc_template` đã có, xoá `SAMPLE_NPC_CODE`

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-05) — chủ dự án duyệt trọn D1–D7. Migration V26 đã chạy qua Postgres thật và kiểm chứng bằng mắt: Chu Hoành đứng đúng `(-5, 0)`, bấm vào nói chuyện được. |
| Date | 2026-09-03 |
| Deciders | Chủ dự án quyết; AI đo + soạn |
| Related | [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) (khuôn mẫu đã duyệt cho đúng loại việc này; ADR này trả **nợ thứ hai** nó đặt tên) · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (khuôn `EquipmentBonusSnapshot`) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (khoá di chuyển vào trục X) · [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) (tường thuật tu tiên — NPC là Chu Hoành) · [GAMEPLAY.md §9.2](../../05-server/GAMEPLAY.md) · [DATABASE.md](../../05-server/DATABASE.md) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi schema DB ⇒ dừng, viết ADR) |

---

## Context

### Vì sao bây giờ: nợ cuối cùng mà ADR-037 để lại, và nó cản đúng một việc cụ thể

[ADR-037 D7](ADR-037-Monster-Template-And-Spawn-Tables.md) cố ý **không** làm hai thứ, và tự gọi
chúng là *"hai nợ đã đặt tên"*: tham số AI theo loài, và `npc_template`. Nợ AI đang được xử lý ở một
luồng khác. ADR này xử lý nợ còn lại.

Việc bị cản là cụ thể, không trừu tượng: **không có cách nào thêm một NPC thứ hai vào thế giới mà
không sửa Java.** Bằng chứng chạy được, không phải suy đoán —
`./gradlew :client:client-core:runWorldPreview` in ra:

```
model NPC: town_elder=true shop_keeper=false (shop_keeper=false là ĐÚNG, mã NPC chưa tồn tại)
```

Client **đã** dựng sẵn chỗ cho một NPC thứ hai (`NpcModelKey` biến `shop_keeper` →
`char_shop-keeper`, `NpcPresenter` vẽ hộp thay thế khi thiếu `.glb`). Server thì không có đường nào
để đẻ ra mã NPC đó ngoài việc thêm một hằng số Java nữa.

### Trạng thái thật hôm nay: bảng đã có, nhưng nó chỉ trả lời NỬA câu hỏi

Đây là khác biệt quan trọng nhất so với ADR-037, và nếu đọc lướt sẽ hiểu sai phạm vi:
**`npc_template` KHÔNG phải bảng mới** — nó ra đời từ **V10** (Phase 6 lát cắt 2) và đang chạy thật:

```sql
CREATE TABLE npc_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name_key VARCHAR(64) NOT NULL,
    dialogue_script_ref VARCHAR(128) NOT NULL
);
INSERT INTO npc_template VALUES ('town_elder', 'npc.town_elder.name', '/scripts/dialogue/town_elder.js');
```

Chính comment của V10 đã ghi thẳng chỗ thiếu:

> *"vị trí spawn VẪN hardcode trong Java (chưa có spawn-point DB cho NPC lẫn quái), bảng này chỉ
> phục vụ tra `dialogue_script_ref`."*

Nói cách khác: NPC hôm nay có **danh tính** trong DB nhưng không có **vị trí**. Bảng được đọc đúng
một lần, ở một chỗ, cho một mục đích (`GetDialogueNodeUseCase` tra script lúc người chơi bấm nói
chuyện). Không ai đọc nó để biết *"thế giới này có NPC nào, đứng ở đâu"* — vì không ai trả lời được.

### Phần còn hardcode, đọc thẳng code

| Chỗ | Nội dung |
|---|---|
| `WorldFacade.SAMPLE_NPC_CODE` | `"town_elder"` — mã NPC duy nhất tồn tại |
| `WorldFacade.SAMPLE_NPC_SPAWN_X` | `-5f` |
| `WorldFacade.SAMPLE_NPC_SPAWN_Y` | `CHARACTER_PLAY_LINE_Y` (= `0f`) |
| `WorldFacade.spawnNpc()` | **không tham số** — spawn đúng một entity, đúng một lần, không vòng lặp |

Chính javadoc của `NpcComponent` cũng đã tự khai: *"hardcode 1 NPC, chưa có `npc_template` DB, cùng
tinh thần `MonsterComponent`"* — dòng đó nay đã lỗi thời một nửa, vì `MonsterComponent` đã thoát ra
từ ADR-037.

### Điều bất ngờ khi đọc code: "vai trò NPC" đã có lời giải, và nó không nằm ở DB

Trước khi thiết kế cột `role`, phải đọc `town_elder.js` đang chạy:

```js
start: {
    options: [
        { label_key: "dialogue.option.ask_about_town",  action: "about_town" },
        { label_key: "...accept_quest_kill_dummies",    action: "accept_quest:kill_training_dummies" },
        { label_key: "dialogue.option.open_shop",       action: "open_shop" },
        { label_key: "dialogue.option.bye",             action: "end" }
    ]
}
```

Trưởng lão Chu Hoành **đã là cả ba vai cùng lúc**: kể chuyện, giao nhiệm vụ, **và mở cửa hàng**. Cửa
hàng trong game này là **toàn cục** (Phase 5, `shop_item` không có cột chủ tiệm; feature-dialogue/
CLAUDE.md ghi rõ *"shop đã toàn cục từ Phase 5, không đổi gì về authorization thật"*), nên `open_shop`
chỉ là một lối tắt giao diện, không phải một quyền gắn với NPC.

⇒ Vai trò của một NPC **đã được cây hội thoại phát biểu đầy đủ**, ở đúng một chỗ, và server đã tin
chỗ đó (`DialogueFacade.selectOption` tra lại action THẬT theo index, không tin client). Thêm cột
`role` là ghi cùng một sự thật ở chỗ thứ hai mà không có gì bắt hai chỗ khớp nhau — chính xác cái
bẫy "quy ước ngầm trùng chuỗi" mà [ADR-037 D5/D1](ADR-037-Monster-Template-And-Spawn-Tables.md) vừa
mới dọn cho `drop_table`.

### Ràng buộc kiến trúc phải tôn trọng

- **`server-world` không được biết DB** (Điều 23), mà NPC lại được spawn **bên trong** `WorldFacade`
  lúc world khởi động ⇒ dữ liệu phải được **tiêm vào**.
- **`server-database` KHÔNG phụ thuộc `server-world`** (kiểm `build.gradle.kts`: chỉ `shared-common`
  + 14 `feature-*`). Chỉ `server-gateway` phụ thuộc cả hai.
- ⚠️ **`WorldFacade` constructor đã dùng HẾT 4 tham số** — Checkstyle `ParameterNumber` max = 4, và
  ADR-037 D3 ghi rõ nó *"chạm đúng trần Checkstyle, không vượt"*. **Không còn chỗ cho tham số thứ
  năm.** Đây là ràng buộc cứng, không phải chi tiết thẩm mỹ; xem D6.
- **Feature không được đọc bảng DB của feature khác** (server/CLAUDE.md) — `npc_template` hôm nay
  thuộc `feature-dialogue`. Xem D3.

---

## Decision

### D1 — Hai bảng, không phải một: giữ `npc_template`, THÊM `npc_spawn`

```sql
CREATE TABLE npc_spawn (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    npc_template_id BIGINT NOT NULL REFERENCES npc_template (id),
    world_code      VARCHAR(32) NOT NULL DEFAULT 'main',
    pos_x           REAL NOT NULL,
    pos_y           REAL NOT NULL DEFAULT 0,

    CONSTRAINT npc_spawn_on_play_line CHECK (pos_y = 0)
);
CREATE INDEX idx_npc_spawn_world_code ON npc_spawn (world_code);
```

Câu hỏi đặt ra đúng: NPC tĩnh có cần tách như quái không, hay một bảng là đủ? **Cân thật, không chép
máy móc** — vì lập luận gốc của ADR-037 D2 (*"một loài có NHIỀU điểm spawn"*) hôm nay **KHÔNG áp
dụng**: có đúng một NPC, đứng đúng một chỗ.

Ba lý do tách vẫn thắng, và không lý do nào là "phòng xa cho tương lai chưa tồn tại":

1. **`npc_template.code` là `UNIQUE`, và nó là danh tính TRÊN DÂY.** Client gửi `TalkToNpcCommand
   { npc_code }`, server tra script theo đúng chuỗi đó. Nhét `pos_x`/`world_code` vào bảng đó là
   khoá cứng "danh tính ⇔ vị trí" thành 1:1 **vĩnh viễn**. Ngày muốn cùng một chủ tiệm đứng ở hai
   thị trấn, lối thoát duy nhất là hoặc đẻ hai `code` khác nhau (⇒ hai script trùng lặp, hai khoá
   i18n, hai model — nhân bản nội dung để giải một bài toán vị trí), hoặc tách bảng lúc đó (⇒ đúng
   migration này, muộn hơn, kèm dữ liệu phải chuyển). Đây không phải tương lai giả định: `world_code`
   ở điểm 2 đã là "hai thế giới" ngay hôm nay.
2. **`world_code` là nhu cầu CÓ THẬT ngay bây giờ, không phải dự phòng.** World không đồng nhất:
   dungeon và arena dựng với `spawnOverworldContent = false`, và `WorldFacadeTest` có test khoá
   chuyện đó (`shouldNotSpawnTrainingDummyOrNpc_whenCreatedForDungeonInstance`). Trưởng lão **không
   được** xuất hiện trong hầm ngục. Hôm nay điều đó do một cờ boolean Java gác; đưa vào `npc_spawn`
   thì nó do cùng một cơ chế gác cả quái lẫn NPC, thay vì hai cơ chế khác nhau cho hai loại thực thể
   đứng cạnh nhau.
3. **`npc_template` đang bị một module KHÁC đọc, cho một mục đích khác.**
   `PostgresNpcTemplateAdapter` (feature-dialogue) `SELECT dialogue_script_ref ... WHERE code = ?`
   mỗi lần người chơi bấm nói chuyện. Hàng đó là "NPC này nói gì". Trộn toạ độ gameplay vào chính
   hàng đó là bắt bảng nội dung hội thoại mang dữ liệu bố trí bản đồ mà nó không bao giờ được quan
   tâm.

**Khác `monster_spawn` ở đúng một chỗ, và khác có chủ đích: KHÔNG có `respawn_seconds`.** NPC không
chết. `WorldFacade` đã ghi điều đó thành comment (*"NPC tĩnh, không chết/hồi sinh — spawn 1 lần duy
nhất, khác quái"*) và `spawnNpc()` nằm ngoài toàn bộ đường `tickMonsterRespawn`. Chép cột đó sang
cho "đối xứng" là đẻ ra một cột không ai đọc, tức một lời hứa sai với người đọc schema sau này.

`CHECK (pos_y = 0)` giữ nguyên lý do của V23: [ADR-033](ADR-033-Ninja-School-Style-Movement.md) khoá di
chuyển vào trục X, nên NPC đặt lệch `y` là NPC người chơi **vĩnh viễn** không tới đủ gần để tương
tác. Javadoc của `SAMPLE_NPC_SPAWN_Y` đã cảnh báo đúng điều này bằng lời; `CHECK` biến nó thành luật.

**Không thêm cột nào vào `npc_template`.** Xem D5 về `model_key` — đó là chỗ dễ thêm bừa nhất.

### D2 — Cây hội thoại: `dialogue_script_ref` GIỮ NGUYÊN, và cái gác nó đã có thật

Câu hỏi: cột nên trỏ tới cây hội thoại bằng gì, và ai chịu trách nhiệm khi mã trỏ sai?

**Không đổi gì.** Cây hội thoại **không phải một hàng trong DB** — nó là một **classpath resource**
nằm trong jar của `feature-dialogue` (`/scripts/dialogue/town_elder.js`), đúng Điều 27 (*"Java = cơ
chế, Script = nội dung"*). Không có bảng `dialogue_script` để mà FK tới, và đẻ một bảng chỉ để chứa
danh sách đường dẫn file là dựng một hàng rào giả: nó bảo đảm chuỗi có trong bảng, **không** bảo đảm
file có thật trong jar.

Bài học ADR-037 D5 (*"quy ước ngầm trùng chuỗi làm bảng rơi lặng lẽ không khớp"*) **vẫn được tôn
trọng, chỉ bằng cơ chế khác**, và điều quyết định là: ở `drop_table` không có gì kiểm tra cả, còn ở
đây **đã có một cái gác chạy thật lúc khởi động**:

```java
// GatewayBootstrap.validateContentScripts()
scriptRefs.addAll(databaseBootstrap.listContentScriptRefs());  // quest + npc_template
new ScriptValidator(new ScriptSandbox(...)).validateAll(scriptRefs);
```

`PostgresNpcTemplateAdapter.listAllDialogueScriptRefs()` gom **mọi** `dialogue_script_ref` đang được
tham chiếu; `ScriptValidator` nạp và eval từng cái. Một `code` trỏ sai file ⇒ **server không khởi
động được**, ngay, ồn ào, trước khi có người chơi nào kết nối (SCRIPTING.md §8). Đó là câu trả lời
cho *"ai chịu trách nhiệm"*: **bước khởi động chịu**, và nó chịu ngay lúc build/deploy chứ không phải
lúc một người chơi bấm vào NPC. FK không đưa thêm được gì mà cái gác này chưa đưa.

Ngược lại, FK **đúng chỗ** thì vẫn dùng: `npc_spawn.npc_template_id` là FK thật (không trùng chuỗi
`code` ngầm như `drop_table` từng làm) — điểm đặt trỏ sai NPC là lỗi lúc migration, không phải lỗi
im lặng lúc chạy.

⚠️ Nợ nhỏ ghi lại cho trung thực: `dialogue_script_ref` là `NOT NULL`, nên hôm nay **không khai báo
được NPC câm** (bảng chỉ đường, tượng đá). Adapter đã viết `WHERE dialogue_script_ref IS NOT NULL`
như thể cột nullable — tức người viết V10 đã lường trước nhưng chưa mở. **Không mở ở đợt này**: chưa
có NPC câm nào cần, và nới `NOT NULL` mà không có người dùng là đúng loại "thiết kế quá tay" Điều
9/30 cấm. Ghi vào Revisit Trigger.

### D3 — Vai trò NPC: **KHÔNG cột enum, KHÔNG bảng phụ**

Cây hội thoại **đã là** lời khai vai trò, và nó là lời khai duy nhất mà server tin (xem Context).
Thêm `role VARCHAR` hoặc `npc_role` (bảng nhiều-nhiều) sẽ:

- **Nhân đôi sự thật** — `role = 'MERCHANT'` và `action: "open_shop"` trong script phải luôn khớp
  nhau, nhưng **không có gì bắt buộc điều đó**. Đúng cái bẫy ADR-037 vừa dọn.
- **Không được ai dùng.** Grep toàn repo: không có đường nào phân nhánh theo "loại NPC" —
  `NpcComponent` chỉ mang `npcCode`, `NpcSpawnedEvent` chỉ mang `npc_id/npc_code/x/y`,
  `DialogueCoordinator` phân nhánh theo **action của lựa chọn**, không theo loại NPC. Một cột không
  có người đọc là một cột sẽ lệch.
- **Đi ngược Điều 9/30** ("đúng-đủ-hiện-tại"): hai NPC, một cửa hàng toàn cục, không có luật nghiệp
  vụ nào cần biết loại.

Khi nào lời khai này sai? Khi cửa hàng **thôi toàn cục** (mỗi chủ tiệm một danh mục riêng). Lúc đó
thứ cần không phải cột `role` mà là FK `shop_id` / bảng `shop_inventory` — một quyết định về
`feature-shop`, không phải về NPC. Ghi vào Revisit Trigger.

### D4 — Di trú KHÔNG đổi hành vi (ràng buộc bắt buộc)

Seed đúng NPC đang chạy, đúng chỗ đang đứng:

```sql
INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT id, 'main', -5, 0 FROM npc_template WHERE code = 'town_elder';
```

`-5` là `SAMPLE_NPC_SPAWN_X`; `0` là `CHARACTER_PLAY_LINE_Y`. `town_elder` là `SAMPLE_NPC_CODE`.
Sau migration, thế giới phải **giống hệt**: một trưởng lão, ở `(-5, 0)`, cùng cây hội thoại, cùng
`npc_code` trên dây, cùng khoá art client suy ra. Đây là hàng đối chứng của cả đợt — đúng vai trò mà
`training_dummy` giữ trong V23.

Ba test hiện có là thước đo, không phải ba test mới:
`shouldSpawnNpc_toCharacterNearby`, `shouldDespawnNpc_whenCharacterMovesAway`,
`shouldNotSpawnTrainingDummyOrNpc_whenCreatedForDungeonInstance` — tất cả phải xanh **mà không đổi
kỳ vọng**, chỉ đổi cách dựng world (tự khai catalog thay vì mượn hằng số), đúng như ADR-037 đã làm.

### D5 — `model_key`: **KHÔNG thêm cột, KHÔNG đụng protocol**

[ADR-037 Revisit Trigger](ADR-037-Monster-Template-And-Spawn-Tables.md) yêu cầu thẳng: *"Khi có
`npc_template` ⇒ xem xét gộp quy ước `model_key`/`name_key` cho cả hai loại thực thể."* Đây là câu
trả lời, và nó là **không**:

- Quái **cần** `model_key` vì D5 của ADR-037 có một nhu cầu cụ thể: **ba loài dùng chung một model**
  trong lúc chờ art. Phép biến đổi thuần không làm được điều đó.
- NPC **không có** nhu cầu đó. `NpcModelKey` (`town_elder` → `char_town-elder`) vẫn đúng cho mọi NPC
  hình dung được lúc này, kể cả `shop_keeper`. Javadoc của chính `MonsterModelKey` đã chốt vì sao
  phép biến đổi thuần được ưu tiên: *"Bảng tra sẽ bắt phải sửa client mỗi lần thêm một con quái…
  client không nên là bên thứ ba phải đồng ý."*
- Thêm cột `model_key` mà **không** gửi nó qua dây = một cột không ai đọc. Gửi nó qua dây = **đổi
  protocol** (thêm field vào `NpcSpawnedEvent`) — việc /CLAUDE.md §4 bắt dừng hỏi, để giải một vấn
  đề **chưa tồn tại**.

⇒ **ADR này KHÔNG đổi protocol, KHÔNG thêm opcode, KHÔNG tăng `protocolVersion`.** Khác ADR-037 ở
đúng điểm này, và khác vì lý do thật chứ không vì né việc. `name_key` cũng giữ nguyên chỗ cũ (cột đã
có từ V10, hôm nay chưa ai gửi đi — client tự tra `npc.<code>.name`; không mở rộng ở đợt này).

🛑 **Nếu chủ dự án muốn NPC dùng chung model** (ví dụ ba lão bán hàng dùng một `.glb`), thì cần đúng
thay đổi ADR-037 D5 đã làm cho quái, và đó là **một field additive trên `NpcSpawnedEvent`** — xin
trả lời riêng câu này thay vì để AI tự quyết.

### D6 — Đường dữ liệu: đúng khuôn ADR-037, và `WorldContentCatalog` gỡ trần 4 tham số

```
feature-dialogue (module ĐÃ CÓ)      server-database             server-gateway          server-world
  NpcPlacement (record)          ←  PostgresNpcPlacement     →  WorldContentLoader  →  NpcCatalogSnapshot
  NpcPlacementPort                   Adapter (mới, cạnh           (đổi tên từ            (record THUẦN)
  NpcPlacementFacade (api/)          PostgresNpcTemplateAdapter)  MonsterCatalogLoader)
```

Adapter RIÊNG chứ không thêm câu SQL vào `PostgresNpcTemplateAdapter`: hai adapter đọc hai bảng và
implement hai Port khác nhau — gộp lại là một lớp trả lời hai câu hỏi không liên quan cho hai người
gọi ở hai thời điểm khác nhau.

**Không thêm module.** Đây là khác biệt có ý thức so với ADR-037 (chỗ đó chủ dự án duyệt module
`feature-monster` mới), vì hình dạng bài toán khác:

- `npc_template` **đã thuộc `feature-dialogue`** từ V10, và `NpcTemplatePort` cũng ở đó. Thêm module
  `feature-npc` nghĩa là **hai module cùng đọc một bảng** (server/CLAUDE.md cấm), hoặc phải chuyển
  `NpcTemplatePort` sang module mới rồi bắt `feature-dialogue` gọi ngược qua facade — một refactor
  cross-module trên code đang chạy đúng, để đổi lấy một cái tên đẹp hơn.
- Thêm module là việc **§4 bắt dừng hỏi**. ADR đang ở `Proposed`; dựng module trước khi được duyệt
  là tự Accept phần mình thích.

Thực tế `feature-dialogue` **đã là** module "nội dung tĩnh của NPC" (nó sở hữu bảng NPC duy nhất);
ADR này chỉ gọi đúng tên chuyện đó. Muốn tách thành `feature-npc` thì đó là **Alternatives B** —
rẻ, vì mọi thứ đã nằm sau Port.

**Trần 4 tham số của `WorldFacade`** (ràng buộc cứng ở Context): tham số thứ 4 hôm nay là
`MonsterCatalogSnapshot`. Không nhét được cái thứ 5, nên gói cả hai:

```java
public record WorldContentCatalog(MonsterCatalogSnapshot monsters, NpcCatalogSnapshot npcs) {
    public static final WorldContentCatalog EMPTY = ...;
    public static WorldContentCatalog ofMonsters(MonsterCatalogSnapshot monsters) { ... }
}
```

Đây là **đổi public API của `server-world`** (constructor 4 tham số đổi kiểu tham số cuối). Không
phá backward compatibility trên dây (không packet, không DB, không client) — nhưng phải nói ra, vì
§2 câu 4 bắt hỏi. Ba constructor còn lại (1/2/3 tham số) giữ nguyên chữ ký ⇒ mọi chỗ dựng
dungeon/arena/test không đụng tới.

**Nạp MỘT LẦN lúc khởi động**, cùng lý do ADR-037 D3: `npc_template`/`npc_spawn` là config tĩnh. Đổi
NPC ⇒ migration + khởi động lại, không có đường nóng.

### D7 — `shop_keeper`: **KHÔNG seed ở đợt này**

Tự quyết như được yêu cầu, và câu trả lời là **để riêng**. Lý do, xếp theo sức nặng:

1. **Nó phá D4.** Ràng buộc bắt buộc của ADR này là *"sau migration game hành xử y hệt"*. Thêm một
   NPC vào thế giới **là** đổi hành vi. Trộn hai việc vào một migration là bỏ mất hàng đối chứng
   đúng lúc cần nó nhất.
2. **Nó chưa có gì để nói.** Chưa có `shop_keeper.js`. NPC không có `dialogue_script_ref` hợp lệ ⇒
   `ScriptValidator` **làm server không khởi động được** (D2). Tức seed bừa không phải "thừa một
   NPC", mà là **server chết lúc boot**.
3. **Nó sẽ hiện ra sai.** Chưa có khoá i18n `npc.shop_keeper.name` ở cả hai bundle ⇒ hiện khoá trần
   giữa giao diện (đúng cảnh báo ADR-037 Consequences: `MessageBundleIntegrityTest` bắt được **lệch**
   bundle, KHÔNG bắt được khoá thiếu hẳn ở **cả hai**). Chưa có `char_shop-keeper.glb` ⇒ hộp thay
   thế đứng giữa thị trấn — trong world thật, không phải trong công cụ xem trước.
4. **Nó chưa mở khoá được gì.** Cửa hàng đã mở được từ hội thoại của Chu Hoành (`open_shop`). Chủ
   tiệm thứ hai hôm nay là trang trí, không phải tính năng.
5. **[ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md)/STORY.md §8** bắt mọi NPC phải map về một arc và tuân
   nhịp hé lộ. `shop_keeper` chưa có tên tu tiên, chưa có arc, chưa có tông môn. Đẻ ra một "shop
   keeper" vô danh là đúng thứ ADR-038 vừa dọn xong.

**Cái ADR này thật sự giao**: sau nó, thêm `shop_keeper` là **một migration + một script + hai dòng
i18n**, không sửa Java dòng nào. Việc còn lại được ghi thành checklist ở "Việc cần làm tiếp theo" để
chủ dự án bấm nút khi muốn.

---

## Alternatives Considered

| Phương án | Vì sao KHÔNG chọn |
|---|---|
| **A. Một bảng — thêm `world_code`/`pos_x`/`pos_y` thẳng vào `npc_template`** | Rẻ nhất: một `ALTER TABLE`, không bảng mới, không JOIN. Và với **một** NPC ở **một** chỗ thì nó đủ dùng thật — đây là phương án đáng cân nhắc nghiêm túc, không phải bù nhìn. Bác vì `code` là `UNIQUE` **và** là danh tính trên dây: khoá "danh tính ⇔ vị trí" thành 1:1 vĩnh viễn, nên NPC thứ hai ở thị trấn thứ hai buộc phải nhân bản `code` + script + i18n + model. Thêm nữa nó bắt hàng dữ liệu mà `feature-dialogue` đọc mỗi lần nói chuyện phải mang theo toạ độ bản đồ. Cái giá của việc tách: **một bảng 5 cột và một JOIN**. |
| **B. Module `feature-npc` mới** (đúng khuôn ADR-037 đã duyệt) | Tên đúng hơn: `feature-dialogue` sở hữu bảng NPC là hơi lệch nghĩa. Nhưng `npc_template` + `NpcTemplatePort` **đã ở** feature-dialogue và đang chạy đúng ⇒ hoặc hai module cùng đọc một bảng (server/CLAUDE.md cấm), hoặc refactor cross-module để đổi lấy một cái tên. **Và thêm module là việc §4 bắt dừng hỏi** — không tự làm khi ADR còn `Proposed`. Nếu chủ dự án muốn, đây là phương án hợp lệ và **rẻ** (mọi thứ đã sau Port; ~30 phút, không đổi SQL, không đổi migration). |
| **C. Cột `role` (enum) hoặc bảng `npc_role`** | Là câu hỏi được đặt ra, nên phải trả lời bằng đo chứ không bằng khẩu hiệu. Đo: **không có đường code nào phân nhánh theo loại NPC** — client phân nhánh theo `action` của lựa chọn hội thoại; cửa hàng toàn cục nên "chủ tiệm" không phải một quyền. ⇒ cột sẽ không có người đọc, và sẽ lệch với script. Mở lại khi cửa hàng thôi toàn cục. |
| **D. Giữ hardcode, chỉ thêm hằng số cho NPC thứ hai** | Rẻ nhất và thêm được NPC thật. Nhưng mỗi NPC mới là **sửa Java + build lại + triển khai lại** (đúng thứ Điều 27 sinh ra để tránh), và `spawnNpc()` không tham số phải thành vòng lặp dù sao ⇒ làm nửa việc rồi vẫn phải quay lại. Nó cũng bỏ lỡ `world_code`, tức trưởng lão vẫn bị chặn khỏi hầm ngục bằng một cờ boolean thay vì bằng dữ liệu. |
| **E. Gộp NPC vào `monster_template`** (một bảng "entity" chung) | Ít bảng hơn, một đường nạp. Nhưng NPC **không có** 9 cột chỉ số chiến đấu, không có `drop_table_id`, không có `exp_reward`, không có `respawn_seconds` — bảng chung nghĩa là hơn nửa số cột là `NULL` cho mọi hàng NPC, và mọi `CHECK` phải rẽ nhánh theo loại. Hai thực thể trông giống nhau trên màn hình nhưng khác hẳn về dữ liệu. |
| **F. Script hoá danh sách NPC** (GraalJS như dialogue) | Nhất quán với "Script = nội dung", và NPC **đúng là** nội dung. Nhưng thứ đang chuyển là **toạ độ + world**, tức dữ liệu bảng thuần; script hoá nó là chọn công cụ nặng (sandbox mới ở mức `CONSTRAINED`, ADR-016) cho việc nhẹ, và tách vị trí NPC khỏi vị trí quái — hai thứ vừa được ADR-037 đặt vào DB. |

---

## Consequences

**Được:**

- Thêm/dời một NPC = **một migration**, không đụng Java — Điều 27.
- Nợ thứ hai của ADR-037 đóng lại; `SAMPLE_NPC_CODE`/`SAMPLE_NPC_SPAWN_X`/`SAMPLE_NPC_SPAWN_Y` biến
  mất khỏi `WorldFacade`.
- `npc_template` (V10) **cuối cùng cũng được dùng đúng như tên nó** — hôm nay bảng "template" chỉ
  trả một đường dẫn script.
- NPC và quái dùng **cùng một cơ chế** để trả lời "world nào có gì" (`world_code`), thay vì một cờ
  boolean Java cho NPC và một bảng cho quái.
- Ràng buộc `pos_y = 0` (ADR-033) thành `CHECK` của DB thay vì javadoc.
- `WorldContentCatalog` gỡ trần 4 tham số ⇒ đợt sau (tham số AI, bệ/địa hình từ DB) không đâm vào
  cùng bức tường.

**Mất / phải chấp nhận:**

- **Đổi schema DB** (§4) — một bảng mới + seed. Đây là lý do ADR này tồn tại.
- **Đổi public API của `server-world`**: tham số 4 của `WorldFacade` đổi kiểu
  (`MonsterCatalogSnapshot` → `WorldContentCatalog`). Không đụng dây/DB/client, nhưng **mọi chỗ dựng
  world chính + test dùng catalog quái phải sửa** (~8 chỗ). Đây là phần việc dễ vỡ nhất của đợt.
- `MonsterCatalogLoader` đổi tên thành `WorldContentLoader` (nó nạp cả hai loại) — đổi tên file
  trong `server-gateway`, không public API ra ngoài module.
- **`feature-dialogue` chính thức mang hai trách nhiệm** (hội thoại + bố trí NPC) với hai facade.
  Tên module hơi lệch nghĩa cho tới khi ai đó chọn Alternatives B. Nói ra chứ không giấu.
- Mỗi NPC mới vẫn cần **script + khoá i18n ở CẢ HAI bundle + model** — bảng không sinh ra ba thứ đó
  (xem D7).
- **DATABASE.md chưa từng liệt kê `npc_template`** (grep: 0 lần xuất hiện) ⇒ phải bổ sung cả hai
  bảng cùng đợt.

**Không đổi:** không thêm/đổi opcode; không đổi `protocolVersion`; không đổi `NpcSpawnedEvent`;
không thêm module; không đụng `AiConstants`/boss/quái; không đụng `dialogue_script_ref` hay
`town_elder.js`; không thêm dependency.

---

## Revisit Trigger

- Khi cửa hàng **thôi toàn cục** (mỗi chủ tiệm một danh mục) ⇒ mở lại D3 — nhưng thứ cần là
  `shop_id`/`shop_inventory` ở `feature-shop`, không phải cột `role`.
- Khi cần **NPC câm** (bảng chỉ đường, tượng đá) ⇒ nới `dialogue_script_ref` thành nullable (D2);
  adapter đã sẵn sàng (`WHERE dialogue_script_ref IS NOT NULL`).
- Khi cần **hai NPC dùng chung một model** ⇒ mở lại D5, và chấp nhận một field additive trên
  `NpcSpawnedEvent` đúng như ADR-037 D5 đã làm cho quái.
- Khi có **thị trấn/khu vực thứ hai** ⇒ `world_code` bắt đầu có nhiều giá trị; kiểm lại
  `idx_npc_spawn_world_code` bằng `EXPLAIN` thật (DATABASE.md §8 — không thêm/giữ index theo cảm tính).
- Khi số NPC vượt ~20 hoặc NPC bắt đầu **di chuyển** (tuần tra) ⇒ `npc_spawn` cần thêm khái niệm
  đường đi, và lúc đó nó gần `monster_spawn` hơn ⇒ đo lại Alternatives E.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hai bảng: giữ `npc_template` (V10), thêm `npc_spawn` — **không** `respawn_seconds`, có `CHECK pos_y = 0` | 🟡 Proposed |
| 2 | `dialogue_script_ref` giữ nguyên (classpath resource, không FK); cái gác là `ScriptValidator` lúc khởi động | 🟡 Proposed |
| 3 | **Không** cột `role`, **không** bảng `npc_role` — vai trò do cây hội thoại phát biểu | 🟡 Proposed |
| 4 | Seed `town_elder` tại `(-5, 0)` world `main` — hành vi **y hệt** sau migration | 🟡 Proposed |
| 5 | **Không** `model_key`, **không** đổi protocol — trả lời "không" cho Revisit Trigger của ADR-037 | 🟡 Proposed |
| 6 | Mở rộng `feature-dialogue` (**không** module mới); `WorldContentCatalog` gói monsters + npcs để không vượt trần 4 tham số | 🟡 Proposed |
| 7 | **Không** seed `shop_keeper` ở đợt này — nó phá ràng buộc "không đổi hành vi" của D4 | 🟡 Proposed |

## ADR cần tạo

Không. ADR này tự đứng được, và nó **đóng nợ thứ hai** trong hai nợ ADR-037 đặt tên (nợ còn lại —
tham số AI theo loài — đang được xử lý riêng, ADR này không đụng `AiConstants`/`AiSystem`).

Không đẻ nợ mới **đã đặt tên** nào, chỉ ba Revisit Trigger có điều kiện kích hoạt rõ ràng ở trên.

## Tài liệu liên quan

- [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) — khuôn `monster_template`/`monster_spawn`
  ADR này bám theo; D7 của nó đặt tên món nợ này; Revisit Trigger của nó hỏi về `model_key` (D5 trả lời)
- [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) — khuôn "gateway dịch, world nhận
  record thuần" (`EquipmentBonusSnapshot`)
- [ADR-033](ADR-033-Ninja-School-Style-Movement.md) — vì sao `CHECK (pos_y = 0)` tồn tại
- [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) + [STORY.md §8](../../13-game-design/STORY.md) — luật
  đặt tên/arc cho mọi NPC mới (lý do 5 của D7)
- [GAMEPLAY.md §9.2](../../05-server/GAMEPLAY.md) — hội thoại NPC
- [DATABASE.md](../../05-server/DATABASE.md) — **chưa liệt kê `npc_template`**, phải bổ sung
- [feature-dialogue/CLAUDE.md](../../../server/server-features/feature-dialogue/CLAUDE.md) — quyết
  định 2026-07-23 sinh ra V10; mục "Chưa kiểm tra khoảng cách thật" vẫn nguyên hiệu lực

## Việc cần làm tiếp theo

1. **Chủ dự án quyết**: duyệt/bác D1–D7. Ba câu đáng trả lời riêng:
   - **D1** — chấp nhận bảng thứ hai cho *một* NPC không? (Alternatives A rẻ hơn và đủ dùng hôm nay.)
   - **D6** — giữ trong `feature-dialogue`, hay muốn module `feature-npc` (Alternatives B)?
   - **D5** — có muốn `model_key` cho NPC không? Nếu có thì **phải đổi protocol**, và đó là quyết
     định của chủ dự án chứ không phải của AI.
2. Thứ tự thi hành đề xuất (đã làm theo thứ tự này ở nhánh kèm ADR): migration **V26** (`npc_spawn`
   + seed) → `NpcPlacement`/`NpcPlacementPort`/`NpcPlacementFacade` ở `feature-dialogue` → câu SQL
   mới trong `PostgresNpcTemplateAdapter` → `NpcCatalogSnapshot` + `WorldContentCatalog` ở
   `server-world` → `WorldFacade.spawnNpc()` đọc catalog → `MonsterCatalogLoader` →
   `WorldContentLoader` → sửa test → **bổ sung DATABASE.md §2.9** + sửa ADR-037 D7/`feature-monster`
   CLAUDE.md (nợ `npc_template` đã trả).
3. ~~**Chưa playtest.**~~ ✅ **Đã kiểm sống** (ghi nhận muộn 2026-09-30, chỉ tính bằng chứng SAU
   ngày V26 vào repo 2026-09-04 — playtest D11 của ADR-038 là 09-01, tức TRƯỚC, nên không đếm): server
   thật phát `NPC_SPAWNED_EVENT npcId=-19 code=town_elder x=-5.00` (`gameProbe`, 2026-09-29) và client
   vẽ ông đúng chỗ đó; mọi quest arc 1–2 chơi thật từ 2026-09-06 trở đi (ROADMAP mục 71–75, 77, 80)
   đều được NHẬN qua hội thoại của NPC đặt bằng `npc_spawn` (lĩnh thưởng thì qua khung nhiệm vụ). Mở cửa hàng qua hội thoại: kiểm riêng 2026-09-30 bằng `gameProbe --talkToNpc=town_elder --dialogueChoices=open_shop` — node `start` có 4 lựa chọn, chọn `[2] open_shop` ⇒ server trả `LIST_SHOP_ITEMS_RESPONSE` (1001, 171 byte) và hội thoại đóng, đúng hợp đồng `dialogue.proto`. Nguyên văn yêu cầu cũ:
   chạy server thật, đi tới `x = -5`, thấy Chu Hoành, nói chuyện, nhận nhiệm vụ, mở cửa hàng — tất
   cả **y hệt** trước migration (D4).
4. Nếu muốn `shop_keeper` (D7 để riêng), checklist **đầy đủ**, không thiếu bước nào:
   1. Chọn **tên tu tiên + arc** theo STORY.md §8 (đừng để tên là "shop keeper").
   2. `feature-dialogue/src/main/resources/scripts/dialogue/shop_keeper.js` — tối thiểu node `start`
      với `open_shop` + `end`.
   3. Khoá i18n ở **CẢ HAI** bundle: `npc.shop_keeper.name` + mọi `dialogue.*` mới
      (thiếu ở cả hai bundle thì `MessageBundleIntegrityTest` **không** bắt được).
   4. Art `char_shop-keeper.glb` (ART-PIPELINE §6.6) — thiếu thì hiện hộp thay thế trong world thật.
   5. Migration: `INSERT INTO npc_template (code, name_key, dialogue_script_ref)` +
      `INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)`. Chọn `pos_x` **cách
      `(0,0)` xa hơn `AGGRO_RADIUS_METERS` (6m)** và **không trùng** `-5` của trưởng lão.
   6. **Không sửa Java dòng nào** — nếu phải sửa, ADR này chưa xong việc của nó.
