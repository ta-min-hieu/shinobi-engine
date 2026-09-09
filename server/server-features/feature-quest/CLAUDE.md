# CLAUDE.md — feature-quest

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-quest/**`.

## Cấu trúc (Clean Arch)

```
api/            QuestFacade (+ AcceptResult/ClaimOutcome/ClaimResult/QuestStatusChange — enum/record RIÊNG,
                 không lộ type UseCase ra ngoài, giống InventoryFacade/ShopFacade/DialogueFacade)
application/    AcceptQuestUseCase · ProcessMonsterKilledUseCase · ClaimQuestRewardUseCase
                QuestScriptRunner (package-private, KHÔNG phải UseCase — chạy 1 hook script)
domain/         QuestProgress · QuestTemplate (VO) · QuestStatus (enum)
                QuestTemplatePort · QuestProgressPort · ExpPort · QuestInventoryGrantPort · AuditPort
                Transaction · TransactionRunner (bản khai riêng của feature-quest, KHÔNG dùng chung
                với feature khác — cùng lý do mọi feature khác tự khai marker type riêng)
resources/      scripts/quest/*.js — NỘI DUNG THẬT (Điều 27), Java chỉ chạy hook, không đọc nội dung
```

## Quyết định chốt cùng chủ dự án (2026-07-23, Phase 6 lát cắt 3)

Hỏi qua `AskUserQuestion` trước khi code:
1. **Nối thẳng vào callback `onMonsterKilled` có sẵn** (`WorldOutboundSink`/
   `ConnectedCharacterRegistry`, Phase 4) — KHÔNG kích hoạt `EventBus`/`Event` (ADR-008, tồn tại từ
   Phase 0, chưa dùng ở đâu trong codebase — xác nhận bằng grep trước khi quyết định). Quest là
   consumer thứ 2 của cùng callback (loot granting là thứ nhất), gọi trực tiếp, không phải lý do đủ
   để tổng quát hoá thành pub/sub thật.
2. **Làm trọn 1 quest DB-driven đầy đủ** (nhận → theo dõi → hoàn thành → nhận thưởng, xuyên
   Postgres thật), không chỉ shell/stub.
3. **Nhận quest bắt buộc qua dialogue NPC** — không thêm `AcceptQuestCommand` riêng, tái dùng
   `SelectDialogueOptionCommand` đã có từ lát cắt 2.

## Mô hình script — NGƯỢC với dialogue

`GetDialogueNodeUseCase` (feature-dialogue) đọc CẤU TRÚC TRẢ VỀ từ script — cần
`ScriptSandbox.eval(script, hostApi, extractor)`, extractor chạy TRONG lúc `Context` còn mở.

Quest thì **ngược lại**: script không trả cấu trúc cho Java đọc. Script tự gọi NGƯỢC vào các hàm
callback truyền qua `hostApi.ctx` (`ProxyExecutable`): `ctx.save_progress(json)` /
`ctx.complete()` / `ctx.give_exp(n)` / `ctx.give_item(code, qty)`. Java đọc lại 1 object Java thuần
(`QuestScriptRunner.MutableEffects`) SAU KHI `eval()` xong — không cần overload extractor vì không
đọc `Value` trả về của hook (luôn bỏ qua kết quả trả về, hiệu ứng chỉ tới qua callback).

Lý do chọn callback thay vì extractor: quest có 3 hook độc lập (`on_accept`/`on_event`/`on_claim`)
gọi ở 3 thời điểm khác nhau, không phải 1 lần trích 1 cây dữ liệu tĩnh như dialogue — mô hình
callback khớp tự nhiên hơn với "script làm hành động, Java ghi nhận hiệu ứng".

## `progress_json` là chuỗi đục với Java

Xác nhận bằng test thật (không đoán) TRƯỚC khi code: GraalJS có sẵn `JSON.stringify`/`JSON.parse`
trong sandbox (round-trip test qua). Nhờ vậy `progress_json` (cột TEXT trong
`character_quest_progress`) được truyền xuyên suốt dưới dạng String — script tự
`JSON.parse(ctx.progress_json)` đọc, tự `JSON.stringify(...)` rồi gọi `ctx.save_progress(...)` ghi
lại. Java KHÔNG BAO GIỜ phân tích nội dung — tránh phải thêm thư viện JSON phía Java (sẽ phải dừng
hỏi theo CLAUDE.md §4 vì không có trong TECHNOLOGY-STACK.md).

## Cross-feature reward — Port riêng, không gọi Facade khác

`ExpPort`/`QuestInventoryGrantPort` là Port RIÊNG của feature-quest (không phải gọi qua
`InventoryFacade`/character Facade), adapter thật (`PostgresQuestExpAdapter`/
`PostgresQuestInventoryGrantAdapter`) sống ở `server-database` — vì `ClaimQuestRewardUseCase` cần
trao EXP + item + ghi audit + đổi status CLAIMED **trong CÙNG 1 transaction** (trao thưởng thành
công nhưng không đánh dấu CLAIMED = nhận thưởng lặp lại được). Gọi qua Facade feature khác không
chia sẻ được `Transaction` xuyên feature — đúng mẫu `GoldPort` (cường hoá, Phase 5) đã dùng, áp dụng
lần thứ 3 (sau Gold, sau Shop's `InventoryGatewayPort`).

`QuestInventoryGrantPort.grantItemByCode` nhận `itemTemplateCode` (String, vd `"kunai"`), KHÔNG
nhận `itemTemplateId` — quest script author nội dung bằng code ổn định, không phải ID DB gán tự
động; adapter tự resolve code→id (giống `NpcTemplatePort`/`ItemTemplatePort.findIdByCode`).

## `QuestTemplateInfo` → `QuestTemplate` (đổi tên lúc code)

Checkstyle `RegexpSingleline` cấm hậu tố `Info` (CLAUDE.md §11). Đổi tên ngay khi build báo lỗi,
không đợi review — khớp tiền lệ `ItemTemplate` (feature-inventory).

## `ProcessMonsterKilledUseCase` — hardcode ĐÃ GỠ (2026-09-05)

~~Hardcode `"kill_training_dummies"` — CHỈ tra đúng 1 quest này mỗi lần quái chết.~~ Điều kiện mà
mục này tự đặt ra (*"khi có quest thứ 2 THEO DÕI GIẾT QUÁI, bắt buộc tổng quát hoá tra cứu này
trước, không copy-paste thêm hằng số hardcode thứ 2"*) **đã xảy ra**: quest thứ hai của arc 1
(STORY.md §6 — "đêm trực gặp tán tu cường đạo"). Tổng quát hoá đi TRƯỚC nội dung, đúng như đã hứa.

Nay `QuestProgressPort.listInProgress(characterId)` trả mọi quest ĐANG LÀM kèm `scriptRef` +
`progress_json`, và use case chạy hook `on_event` cho từng cái.

**Java KHÔNG lọc theo `monster_code`** — chính script quyết sự kiện có liên quan không
(`event.monster_code === "..."`). Đây là Điều 27: lọc trong Java sẽ rò rỉ nội dung quest vào code
và bắt phải sửa Java mỗi lần thêm quest — đúng thứ kiến trúc script sinh ra để tránh.

⚠️ **Vẫn CHƯA phải "index quest theo loại sự kiện"** mà GAMEPLAY.md §9 mong muốn. Nhưng phạm vi
quét nay bị chặn bởi **số quest ĐANG LÀM của một người chơi** (nhỏ, có trần thực tế) thay vì một
hằng số hardcode, và bộ lọc `status = 'IN_PROGRESS'` nằm trong SQL nên quest đã CLAIMED — thứ chỉ
tăng theo đời nhân vật — không bao giờ được trả về. Đo lại khi một nhân vật ôm hàng chục quest cùng lúc.

## Tích hợp Dialogue — không phá ranh giới 2 module

`DialogueOption.ACTION_ACCEPT_QUEST_PREFIX = "accept_quest:"` — `DialogueFacade.resolveAction` chỉ
tách chuỗi thành `SelectOptionResult.AcceptQuest(questCode)`, KHÔNG tự gọi `QuestFacade`.
**Gateway** (`GatewayChannelHandler`) mới là nơi gọi `questFacade.acceptQuest(...)` khi nhận được
biến thể `AcceptQuest` — giữ `feature-dialogue` và `feature-quest` không biết nhau, đúng nguyên tắc
đã dùng cho action `open_shop` gọi `ShopFacade` (lát cắt 2).

## ADR-025 retrofit (2026-08-02): `ClaimQuestRewardCommand` giờ có `idempotency_key`

Audit hệ thống (rà toàn bộ Command có side-effect theo ADR-025) phát hiện `ClaimQuestRewardUseCase`
trao EXP + item thật (cùng lớp rủi ro `ClaimMailCommand`) nhưng CHƯA enforce `idempotency_key` —
client tự retry sẽ có nguy cơ nhận thưởng 2 lần NẾU 2 request tới gần như đồng thời (trước khi
request đầu commit xong đổi status COMPLETED→CLAIMED — guard trạng thái tự nhiên chỉ chặn được
retry TUẦN TỰ SAU khi request đầu đã commit, không chặn được race thật). Vá theo ĐÚNG pattern đã
dùng cho `ClaimMailUseCase` (Command "nhận 1 lần" gần nhất về ngữ nghĩa): thêm
`IdempotencyPort`/`IdempotencyKeyReusedException` ở `domain/`, `QuestRewardPorts` giờ 4 Port (thêm
`idempotencyPort`), ghi key TRƯỚC trao EXP/item trong CÙNG transaction — vi phạm UNIQUE → rollback,
trả `ClaimResult.Outcome.DUPLICATE_REQUEST` (`ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_DUPLICATE_REQUEST
= 5` ở protocol, additive, cùng số 5 các Command khác đã dùng cho DUPLICATE_REQUEST).

## Cứng

✅ Nội dung quest (điều kiện hoàn thành, công thức thưởng) là SCRIPT (Điều 27) — Java chỉ là cơ chế
   chạy hook, KHÔNG hardcode ngưỡng/công thức trong Java
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Trao thưởng + đổi status CLAIMED trong 1 transaction (DATABASE.md §5)
✅ `QuestFacade` tự khai type kết quả riêng — không lộ `AcceptQuestUseCase.AcceptResult`/
   `ClaimQuestRewardUseCase.ClaimResult` ra ngoài module

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL hay GraalJS
❌ Quét toàn bộ bảng `character_quest_progress` mỗi lần có sự kiện — chỉ quét quest ĐANG LÀM của
   ĐÚNG người chơi đó (xem mục `ProcessMonsterKilledUseCase` ở trên)

## Test

`domain/` test không mock. `application/` test với Fake Port + `test_fixture.js` (ngưỡng khác script
sản phẩm — 2 kill thay vì 3 — để phân biệt rõ test dùng fixture, không phải script thật). Postgres
thật (`server-database/.../quest/QuestIntegrationTest`) verify TRỌN vòng đời qua script sản phẩm
thật (`kill_training_dummies.js`): nhận → 3 lần giết → hoàn thành → nhận thưởng → verify
`character.exp`/`inventory_item` bằng SQL trực tiếp, cộng 2 case từ chối (nhận trùng, nhận thưởng
trước khi hoàn thành). Socket thật (`GatewayServerIntegrationTest`) verify outcome
`QUEST_NOT_FOUND` (Fake `QuestTemplatePort` luôn rỗng) — nghiệp vụ vòng đời thật đã phủ ở tầng dưới.

Từ 2026-09-05 CÙNG lớp đó phủ luôn **quest thứ hai** (`night_watch_bandit.js`, seed V28) — 4 test
nữa, và chúng mới là bằng chứng phần tổng quát hoá ở trên chạy thật chứ không chỉ xanh trên Fake:
vòng đời đầy đủ (800 EXP + 1 `enhance_protection_charm`, đếm theo TỔNG `quantity` chứ không đếm
DÒNG vì bùa xếp chồng được), giết nhầm loài thì script tự bỏ qua, và **một cú giết đi tới đúng
quest quan tâm nó khi người chơi đang làm HAI quest cùng lúc** — đúng chỗ bản hardcode cũ sai.
Chung một lớp vì `@Container` là static: tách lớp riêng sẽ dựng thêm một Postgres cho cả suite.

## `prerequisite_quest_id` — ĐÃ THI HÀNH (ADR-044, 2026-09-05)

Cột có từ V11, **V28 là dòng dữ liệu đầu tiên dùng nó**, và cùng ngày
[ADR-044](../../../docs/01-architecture/adr/ADR-044-Quest-Prerequisite-Enforcement.md) được duyệt:
`AcceptQuestUseCase` nay từ chối bằng `AcceptResult.PREREQUISITE_NOT_MET` nếu quest tiên quyết chưa
ở trạng thái **`CLAIMED`**.

**`CLAIMED` chứ không phải `COMPLETED`** (chủ dự án chốt): đánh xong mà chưa quay lại lĩnh thưởng
thì chuỗi kể chuyện chưa đóng, và `CLAIMED` là trạng thái cuối duy nhất không bao giờ lùi.

**KHÔNG giấu lựa chọn hội thoại** — NPC vẫn bày câu hỏi, server mới là nơi từ chối (Điều 15). Giữ
`feature-dialogue` không phải biết gì về trạng thái quest.

⚠️ **KHÔNG kiểm bắc cầu**: chuỗi A→B→C chỉ kiểm B khi nhận C. Đủ dùng (muốn có B thì đã phải qua A)
nhưng đừng tưởng nó soi cả chuỗi.

⚠️ **Bẫy JDBC đã sập một lần ngay trong lần thi hành này**: `rs.wasNull()` nói về **lần đọc cột GẦN
NHẤT**. Gọi nó bên trong lời gọi constructor — sau `getLong("id")`/`getString("script_ref")` — làm
nó trả lời về `script_ref` (không bao giờ NULL), nên MỌI quest trông như có tiên quyết `id = 0` và
cả quest mở màn cũng bị chặn. Chụp `wasNull()` vào biến NGAY sau `getLong` của đúng cột đó.

## `ProcessQuestEventUseCase` — LOẠI sự kiện thứ hai (2026-09-09)

Lớp này trước tên `ProcessMonsterKilledUseCase`. Nó đã bị tổng quát hoá HAI lần, và cả hai lần đều
theo cùng một luật do chính mục trên đặt ra: **cơ chế đi trước nội dung, không copy-paste bản thứ hai**.

| Lần | Điều kiện kích hoạt | Thứ bị gỡ |
|---|---|---|
| 1 (2026-09-05) | quest thứ HAI theo dõi giết quái | hằng số mã quest hardcode |
| 2 (2026-09-09) | mục tiêu quest loại THỨ HAI (`npc_talked`, nhịp mở arc 2) | giả định "chỉ có một loại sự kiện" |

`runEvent` nay nhận `Map<String,Object>` chứ không phải `(eventType, monsterCode)`: tham số thứ hai
chỉ có nghĩa với đúng một loại sự kiện, nên thêm loại thứ hai bằng cách thêm tham số thứ ba là con
đường thẳng tới một chữ ký toàn `null`. Mỗi loại mang bộ trường RIÊNG (`monster_code` / `npc_code`);
đọc một trường không có trong map trả `undefined` (`ProxyObject.fromMap` khai `hasMember` theo
`containsKey`), nên script không vỡ khi nhận sự kiện loại khác — **nhưng mọi script vẫn PHẢI kiểm
`event.type` TRƯỚC**, đó là thứ duy nhất phân biệt được hai loại.

### Ba thứ riêng của mục tiêu "đi gặp NPC"

1. **Mốc ở lúc MỞ LỜI** (`TalkToNpcCommand`), không ở một lựa chọn thoại — nếu không, mục tiêu
   "tìm ra người" biến thành "bấm đúng nút". Hệ quả: sự kiện **LẶP LẠI**.
2. **Tiến độ là CỜ, không phải bộ đếm.** `report_to_outer_sect.js` ghi `reported = true`. Viết
   `+= 1` là quest tự đếm số lần chào hỏi.
3. **Không thêm opcode nào.** `TalkToNpcCommand` (601) và `QuestStatusChangedEvent` (612) đã có từ
   Phase 6; chỉ thiếu nhánh nối. `protocolVersion` không tăng.

### Bỏ ghi khi tiến độ KHÔNG đổi

Từ khi có hai loại sự kiện, mỗi quest đang làm nhận MỌI sự kiện, nên nhánh "không liên quan" trở
thành nhánh thường gặp nhất. `dispatch` bỏ hẳn lần `save` khi script trả lại đúng chuỗi cũ và chưa
`complete()` — không thì số transaction rỗng tăng theo tích (số quest × số sự kiện).
