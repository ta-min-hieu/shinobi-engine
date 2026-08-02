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

## `ProcessMonsterKilledUseCase.TRACKED_QUEST_CODE` — hardcode có chủ đích

Hardcode `"kill_training_dummies"` — CHỈ tra đúng 1 quest này mỗi lần quái chết, không quét toàn bộ
`character_quest_progress` của người chơi. Đây là placeholder khớp cảnh báo hiệu năng đã ghi ở
GAMEPLAY.md §9 (đầu mục Quest, viết từ trước lát cắt này): "cần index quest theo loại sự kiện,
không quét tuyến tính — đo ở Phase 6". **Khi có quest thứ 2 THEO DÕI GIẾT QUÁI, bắt buộc tổng quát
hoá tra cứu này trước** (vd tra theo `monster_code` → danh sách quest liên quan), không copy-paste
thêm hằng số hardcode thứ 2.

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
❌ Quét toàn bộ quest của người chơi mỗi lần có sự kiện (xem `TRACKED_QUEST_CODE` ở trên)

## Test

`domain/` test không mock. `application/` test với Fake Port + `test_fixture.js` (ngưỡng khác script
sản phẩm — 2 kill thay vì 3 — để phân biệt rõ test dùng fixture, không phải script thật). Postgres
thật (`server-database/.../quest/QuestIntegrationTest`) verify TRỌN vòng đời qua script sản phẩm
thật (`kill_training_dummies.js`): nhận → 3 lần giết → hoàn thành → nhận thưởng → verify
`character.exp`/`inventory_item` bằng SQL trực tiếp, cộng 2 case từ chối (nhận trùng, nhận thưởng
trước khi hoàn thành). Socket thật (`GatewayServerIntegrationTest`) verify outcome
`QUEST_NOT_FOUND` (Fake `QuestTemplatePort` luôn rỗng) — nghiệp vụ vòng đời thật đã phủ ở tầng dưới.
