# CLAUDE.md — feature-dialogue

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-dialogue/**`.

## Cấu trúc (Clean Arch)

```
api/            DialogueFacade (+ SelectOptionResult sealed: Node/OpenShop/Ended/NotFound)
application/    GetDialogueNodeUseCase (chạy script qua ScriptSandbox, không giữ state)
domain/         DialogueNode · DialogueOption (VO) · NpcTemplatePort
resources/      scripts/dialogue/*.js — NỘI DUNG THẬT (Điều 27), Java chỉ trích xuất
```

Chiều: `api → application → domain`. `domain/` không import `engine-scripting` (chỉ
`application/` — engine-scripting là thư viện engine dùng chung, hợp lệ ở application layer, khác
lý do domain/ vẫn không đụng infrastructure DB/network thật).

## Quyết định chốt cùng chủ dự án (2026-07-23, Phase 6 lát cắt 2)

Hỏi qua `AskUserQuestion` trước khi code (GDD/SCRIPTING.md chưa có định dạng dialogue cụ thể):
1. **Xây cấu trúc node/nhánh đầy đủ ngay** — không chỉ 1 câu chào đơn, dù quest (lát cắt 3) chưa
   tồn tại để dùng nhánh thật. Script trả về TOÀN BỘ cây (object, key = nodeId), Java trích 1 node.
2. **Thêm bảng `npc_template` thật** (migration V10: id, code, name_key, `dialogue_script_ref`) —
   không tiếp tục hardcode script_ref trong Java như NPC lát cắt 1 hardcode vị trí.
3. **Gắn "Mở cửa hàng" vào dialogue** — tái dùng `ListShopItemsResponse` có sẵn (Phase 5), KHÔNG
   packet mới, KHÔNG sửa `BuyItemUseCase`/`SellItemUseCase` (vẫn toàn cục, không ràng buộc vị trí
   thật — xem "Chưa kiểm tra khoảng cách" dưới).
4. **Module RIÊNG `feature-dialogue`** (không gộp vào feature-quest dù SCRIPTING.md liệt "nội dung
   quest (điều kiện, thưởng, dialogue)" chung 1 dòng) — dialogue có DB access + script execution
   thật, khác NPC hiển thị thuần (server-world, không Clean Arch). `server-features/README.md`
   chưa liệt kê module này trước đó (khác feature-shop đã có opcode dành sẵn) nhưng ranh giới
   nghiệp vụ đủ rõ để tách riêng — quyết định cùng chủ dự án, không tự ý thêm module.

## Bug hạ tầng thật bắt được TRƯỚC khi viết use case (không đoán trước)

`ScriptSandbox.eval(script, hostApi)` (engine-scripting, Phase 4) trả về `Value` SAU KHI `Context`
đã đóng (try-with-resources đóng context trước khi hàm return). Viết test thật xác nhận: gọi
`Value.getMember(...)`/`getArrayElement(...)` trên kết quả đó ném `IllegalStateException` ngay —
chỉ scalar (`asDouble`/`asString`/`asInt`) sống sót qua việc đóng context. Dialogue cần trả về
object có cấu trúc (text + list option), không phải scalar, nên **thêm overload**
`ScriptSandbox.eval(script, hostApi, extractor)` — `extractor: Function<Value, T>` chạy TRONG lúc
`Context` còn mở, kết quả trích ra (kiểu `T` bất kỳ do caller chọn, ở đây là `DialogueNode`) mới
"sống sót" qua việc đóng context. Đây là thay đổi CHUNG cho `engine-scripting` (dùng được cho bất
kỳ script nào cần trả cấu trúc, không riêng dialogue) — xem `ScriptSandboxTest` 2 test mới
(`shouldRejectMemberAccess_onPlainEvalResult_afterContextClosed` +
`shouldExtractStructuredResult_whenExtractorRunsBeforeContextCloses`).

## `GetDialogueNodeUseCase` — cách hoạt động

- Tra `npc_template.dialogue_script_ref` theo `npcCode` qua `NpcTemplatePort`.
- Load script từ classpath resource (cache theo `scriptRef` trong `ConcurrentHashMap` — script
  không đổi lúc chạy, tránh đọc lại resource mỗi lần người chơi nói chuyện với NPC).
- `eval(source, Map.of(), root -> extractNode(root, nodeId))` — `extractNode` đọc `root.getMember(nodeId)`
  rồi `text_key`/`options[].label_key`/`options[].action`, trả `null` nếu `nodeId` không tồn tại
  (bọc lại thành `Optional.empty()` ở `execute()`).
- **KHÔNG giữ state giữa các lần gọi** — mỗi lần "nói chuyện"/"chọn lựa chọn" đều re-eval TOÀN BỘ
  script từ đầu rồi trích đúng node cần dùng. Khác quest (lát cắt 3 sau) sẽ cần state thật
  (`progress_json`) xuyên nhiều lần gọi — dialogue không cần vì không có gì phải nhớ giữa các lượt.

## `DialogueFacade.selectOption` — server-authoritative

`optionIndex` là VỊ TRÍ trong node hiện tại, KHÔNG phải chuỗi `action` — server tự
`getDialogueNodeUseCase.execute(npcCode, currentNodeId)` lại để tra action THẬT theo index, không
tin action client tự khai (Điều 24, giống cách EnhanceItemCommand không tin tỉ lệ/chi phí từ client).
3 action đặc biệt: `DialogueOption.ACTION_OPEN_SHOP`/`ACTION_END` (hằng số), còn lại coi là
`nodeId` kế tiếp (điều hướng tự do trong script, không giới hạn số node).

## Chưa kiểm tra khoảng cách thật (đứng gần NPC)

Client gửi thẳng `npc_code` (đã biết từ `NpcSpawnedEvent`) — server KHÔNG tra vị trí NPC thật qua
ECS vì `server-world` chỉ đọc/ghi trên tick thread (đọc xuyên thread từ Netty EventLoop/Virtual
Thread vỡ ranh giới đó, chưa có cơ chế snapshot vị trí an toàn xuyên thread). Chấp nhận được ở lát
cắt này: dialogue không có giá trị kinh tế (đọc text sớm không phải khai thác), mở shop qua
dialogue chỉ là lối tắt UX (shop đã toàn cục từ Phase 5, không đổi gì về authorization thật). Nếu
sau này cần ràng buộc thật, cần thêm cơ chế đọc snapshot vị trí NPC an toàn xuyên thread trước.

## Cứng

✅ Nội dung dialogue là SCRIPT (Điều 27, SCRIPTING.md §2) — KHÔNG hardcode Java, KHÔNG config thay thế
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`DialogueNode`, `DialogueOption`)
✅ Module khác gọi qua `DialogueFacade`, không đọc bảng `npc_template`/chạy `ScriptSandbox` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL hay GraalJS

## Test

`domain/` test không mock. `application/`/`api/` test với Fake `NpcTemplatePort` + script TEST
riêng (`test_fixture.js`/`test_fixture_shop.js`, KHÔNG phải `town_elder.js` sản phẩm). Postgres
thật (`server-database/.../dialogue/DialogueIntegrationTest`) verify ĐÚNG script sản phẩm
(`town_elder.js`) qua migration V10 thật. Socket thật (`GatewayServerIntegrationTest`) verify nhánh
NPC không tồn tại (Fake port luôn rỗng) — nghiệp vụ node/nhánh/mở-shop đã phủ ở tầng dưới.
