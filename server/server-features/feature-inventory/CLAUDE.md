# CLAUDE.md — feature-inventory

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-inventory/**`.

## Cấu trúc (Clean Arch)

```
api/            InventoryFacade · EquipmentUseCases (gộp tham số, tránh vượt 4 — Checkstyle)
application/    AddItemUseCase · GrantLootUseCase · EquipItemUseCase · UnequipItemUseCase
                ComputeEquipmentBonusUseCase (đọc thuần, tổng 4 chỉ số × hệ số cường hoá từng món)
domain/         Inventory (aggregate) · Equipment (aggregate RIÊNG) · ItemStack (VO)
                DropTableEntry (VO) · ItemTemplate (VO, equipSlot + 4 chỉ số + requiredLevel)
                EquipmentBonus (VO, 4 chỉ số) · ItemCatalogEntry (VO, 10 cột item_template)
                InventoryPort · EquipmentPort · DropTablePort · ItemTemplatePort
                Transaction · TransactionRunner · CharacterInventoryNotFoundException
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — không có
`infrastructure/` trong module này, cùng lý do đã ghi ở feature-account/CLAUDE.md: adapter thật
(`PostgresInventoryAdapter`, `PostgresDropTableAdapter`) nằm ở **`server-database`** (ArchUnit chặn
JDBC theo package `dev.kage.server.database`, không theo Gradle module).

## Quyết định MVP ghi lại ở đây (không có trong docs gốc)

- **Mở sớm hơn ROADMAP dự kiến** (Phase 5) — quyết định của chủ dự án cho lát cắt "rơi đồ thật vào
  túi đồ" ở Phase 4 (quái chết → rơi đồ thật, không chỉ hiển thị suông).
- **`drop_table.entries_json` → normalize thành bảng `drop_table_entry`**: DATABASE.md ghi
  `drop_table | id, entries_json` (JSON blob), nhưng dự án CHƯA có thư viện JSON nào
  (Jackson/Gson) trong TECHNOLOGY-STACK.md — thêm mới là việc cần dừng hỏi (CLAUDE.md §4). Bảng
  quan hệ (`drop_table_entry`: 1 dòng = 1 entry) đạt cùng mục đích "config, không code" mà không
  cần dependency mới, tận dụng Postgres có sẵn. Không phải quyết định kiến trúc (không đổi ADR
  nào) — chỉ là cách biểu diễn dữ liệu.
- **`Inventory` không phải 1 dòng DB riêng** — không có bảng `inventory`, chỉ có `inventory_item`.
  `PostgresInventoryAdapter.findByCharacter` LUÔN trả `Optional.of(...)` (never empty) — lắp từ
  các dòng `inventory_item` hiện có (có thể rỗng) + `DEFAULT_CAPACITY` hằng số (chưa đọc từ đâu
  khác, placeholder như `MOVE_SPEED_MPS`).
- **`save()` = xoá hết + ghi lại toàn bộ** (không diff) — đơn giản nhất cho quy mô túi đồ nhỏ MVP
  (Điều 9). Tối ưu incremental save nếu đo thấy cần.
- **Không stack item trùng loại** — `AddItemUseCase`/`GrantLootUseCase` luôn thêm dòng mới, không
  gộp `quantity` vào stack đã có cùng `item_template_id`. `ItemStack.plus()` (template) tồn tại
  cho tương lai nhưng CHƯA dùng ở lát cắt này.
- **Túi đầy → item bị "mất"** (không rơi xuống đất) ở `GrantLootUseCase` — chưa có ground-item/mail
  hệ thống để trả lại đồ khi túi đầy. Ghi rõ, không giấu — không phải hành vi cuối cùng.

### Phase 5 lát cắt đầu (2026-07-22): "mặc đồ" — sửa nền tảng ID + Equipment aggregate mới

- **`ItemStack` giờ có `id` THẬT** (`inventory_item.id` qua `RETURNING id`) — trước đó KHÔNG có
  định danh ổn định nào (chỉ `slot_index`, tính lại mỗi lần save). Phát hiện khi làm "mặc đồ": cần
  tham chiếu ĐÚNG 1 item cụ thể (`equipment_slot.inventory_item_id`), dừng lại hỏi chủ dự án theo
  CLAUDE.md §4 (phát hiện vấn đề thiết kế → mô tả + đề xuất, không tự sửa) — chủ dự án chọn "sửa
  nền tảng" thay vì né tránh bằng cách gộp equip vào 1 cột trên `inventory_item`.
- **`PostgresInventoryAdapter.save()` đổi từ "xoá hết ghi lại" sang UPSERT thật** (insert/update/
  delete theo `ItemStack.id`) — `ItemStack.isPersisted()` (`id != 0`) phân biệt item mới (INSERT,
  `RETURNING id`) với item đã có (UPDATE tại chỗ, giữ nguyên id). Xoá thật chỉ xảy ra với id đã tồn
  tại DB nhưng không còn trong `inventory.items()` — CHƯA có UseCase nào thật sự xoá item (chưa
  cần test nhánh đó kỹ, chỉ có ở mức domain).
- **`ItemStack.newItem(itemTemplateId, quantity, enhanceLevel)`** — factory cho item CHƯA lưu
  (`id = ItemStack.NEW_ITEM_ID = 0`), dùng thay constructor trực tiếp khi tạo item mới (rơi đồ,
  nhặt đồ). Constructor 4-tham số đầy đủ (`id` thật) dùng khi tái tạo từ DB hoặc biết ID sẵn.
- **`Equipment` là aggregate RIÊNG với `Inventory`** (không phải field trên `ItemStack`) — đúng
  DATABASE.md §2.2 (bảng `equipment_slot` riêng, `inventory_item_id` FK). Chỉ giữ tham chiếu ID,
  KHÔNG copy dữ liệu item. `EquipItemUseCase` điều phối 3 Port (Inventory đọc item thuộc về ai +
  itemTemplateId, ItemTemplate đọc đúng slot, Equipment ghi) — validate "đúng slot" nằm ở
  UseCase (cần `ItemTemplatePort`), KHÔNG nằm ở `Equipment.equip()` (domain thuần, không biết
  item_template).
- **`ItemTemplate` (VO) chỉ mang `equipSlot`** — chưa mang `nameKey`/`maxStack` dù DATABASE.md liệt
  đủ cột, vì chưa UseCase nào cần đọc lại 2 trường đó (YAGNI, thêm khi có nhu cầu thật).
- **`PostgresEquipmentAdapter.save()` VẪN "xoá hết ghi lại"** (khác `PostgresInventoryAdapter`) —
  AN TOÀN ở đây vì không bảng nào tham chiếu ngược `equipment_slot` theo ID (bảng còn không có cột
  `id`, PK là `(character_id, slot_type)`), tối đa 7 dòng/nhân vật.
- ✅ **Đã nối protocol/gateway** (cùng ngày, lát cắt tiếp theo): `EquipItemCommand`/
  `UnequipItemCommand`/`*Response` (opcode 501-504, `inventory.proto`) + `GatewayChannelHandler`
  handler thật (Virtual Thread, giống `handleSelectCharacterCommand`) + verify qua socket thật
  (`GatewayServerIntegrationTest`). Điều kiện tiên quyết đã sửa: `InventoryPort.save()` giờ TRẢ VỀ
  `Inventory` đã lưu (id thật) thay vì `void` — `GrantLootUseCase` dùng giá trị này để trả ID thật
  về client qua `ItemGrantedEvent.item_id` (field mới, không đổi field cũ).

### Phase 5 lát cắt 3 (cùng ngày): trang bị thật sự cộng chỉ số chiến đấu

- **`ItemTemplate.bonusAtk`** (chỉ ATK ở lát cắt này — GDD §5 còn SKILL_DMG/DEF/RES/..., thêm khi
  có item thật cần, tránh field không ai dùng) + **`EquipmentBonus`** VO (tổng cộng dồn, LUÔN tính
  lại từ đầu qua `ComputeEquipmentBonusUseCase`, không cộng dồn delta — tránh trôi số nếu equip/gỡ
  đồ nhiều lần hoặc gọi sai thứ tự).
- **`ComputeEquipmentBonusUseCase`** đọc thuần: duyệt `Equipment.slots()` → tra từng item trong
  `Inventory` → tra `ItemTemplate` → cộng dồn `bonusAtk`. Item/template lệch (hiếm, dữ liệu không
  nhất quán) → bỏ qua an toàn, không ném lỗi (không được để 1 dòng lệch làm hỏng toàn bộ tính chỉ số).
- **`GatewayChannelHandler` là nơi NỐI feature-inventory ↔ server-world** — server-world không tự
  biết feature-inventory tồn tại (Điều 23, module boundary). Gọi `computeEquipmentBonus` +
  `WorldFacade.applyEquipmentBonus` ở 3 chỗ: lúc chọn nhân vật (attach — nếu không, mặc đồ từ
  phiên trước sẽ "biến mất" lúc đăng nhập lại), lúc mặc đồ thành công, lúc gỡ đồ thành công.
- **`WorldFacade` giữ `CharacterBaseStats` gốc** (map `baseStatsByCharacterId`, song song
  `entitiesByCharacterId`) — `applyEquipmentBonus` LUÔN derive lại từ nền + cộng bonus mới, không
  cộng dồn vào giá trị ATK hiện có (tránh drift nếu bị gọi nhiều lần/không đúng thứ tự).
- Không đổi `WorldFacade.attachCharacter`'s signature (vẫn 4 tham số) để tránh phá ~15 chỗ gọi đã
  có trong `WorldFacadeTest` — bonus áp dụng bằng lệnh RIÊNG (`applyEquipmentBonus`) ngay sau attach,
  không gộp vào tham số attach.

### Phase 5 lát cắt 4 (2026-07-23): "cường hoá" — nền tảng gold + audit_log + EnhanceItemUseCase

- **`GoldPort`/`AuditPort` mới, định nghĩa trong `feature-inventory/domain`** dù gold vật lý nằm ở
  `character_stat` (thuộc feature-character) và `audit_log` về nguyên tắc dùng chung nhiều feature
  (trade, shop). Lý do: `EnhanceItemUseCase` cần trừ gold + đổi `enhance_level` + ghi audit NGUYÊN
  TỬ trong 1 transaction — `CharacterFacade` không nhận `Transaction` chia sẻ giữa 2 feature (mỗi
  feature tự định nghĩa `Transaction`/`TransactionRunner` riêng, xem feature-account/feature-character
  CLAUDE.md). Cùng tinh thần "domain không import domain feature khác": adapter thật
  (`PostgresGoldAdapter`, `PostgresAuditAdapter`) nằm ở `server-database` — module hạ tầng dùng
  chung, được phép đụng `character_stat`/`audit_log` mà KHÔNG vi phạm "feature đọc bảng DB feature
  khác" (rule đó áp cho application/domain layer, không áp cho adapter layer ở `server-database`).
  Feature khác cần audit (trade Phase 7+) sẽ khai báo `AuditPort` RIÊNG của nó, không tái dùng
  interface này — như `TransactionRunner` đã lặp lại ở mỗi feature.
- **`GoldPort.deductGold` là 1 UPDATE có điều kiện** (`WHERE gold >= amount`), KHÔNG đọc-rồi-ghi —
  tránh race giữa 2 request cường hoá đồng thời của cùng 1 nhân vật (dù hiện tại mỗi nhân vật chỉ
  xử lý tuần tự qua 1 Virtual Thread ở gateway, làm đúng ngay từ đầu rẻ hơn sửa sau).
- **`character_stat.gold`** (không phải `character.gold`) — character_stat vốn tách riêng vì tần
  suất ghi/đọc cao (lý do gốc ở V2), gold cũng đổi liên tục (cường hoá, sau này là shop) nên cùng
  lý do đó. Migration V7.
- **`EnhancementTable`** (domain, static, giống `LootRoller`) — bảng tỉ lệ thành công/chi phí gold
  theo cấp, ĐỀ XUẤT của AI được chủ dự án duyệt (GDD chưa có số cụ thể, chỉ nói "giảm dần") — xem
  GAMEPLAY.md §7.1 cho bảng đầy đủ + rationale duyệt.
- **Chưa có "Bảo Vệ"** (item/flag chống rớt cấp từ +10, GDD nhắc tới) — quyết định CHỦ ĐỘNG của chủ
  dự án để lát cắt sau, KHÔNG phải thiếu sót. `EnhanceResult` đã chừa sẵn `FAILED_NO_DROP` (dưới
  +10, thất bại không mất gì ngoài gold) và `FAILED_DROPPED` (từ +10, thất bại rớt 1 cấp thật) để
  thêm Bảo Vệ sau không cần đổi enum.
- **Random cường hoá dùng `ThreadLocalRandom` trực tiếp trong `EnhanceItemUseCase`** (không qua
  domain riêng như `LootRoller`) — chỉ 1 lần roll thành/bại mỗi lệnh gọi, không đủ phức tạp để tách
  class riêng (khác `LootRoller` phải roll NHIỀU entry độc lập).
- **`Inventory.replaceItem(ItemStack)`** (mới) — thay 1 item đã tồn tại (cùng `id`) bằng bản ghi
  mới, dùng khi đổi `enhanceLevel`. Ném `IllegalStateException` nếu `id` không tồn tại (lỗi lập
  trình, không phải luồng bình thường — khác `findItem` trả `Optional`).
- **`InventoryFacade` giờ có 4 tham số constructor** (`addItemUseCase`, `grantLootUseCase`,
  `equipmentUseCases`, `enhanceItemUseCase`) — CHẠM giới hạn 4 của Checkstyle `ParameterNumber`,
  KHÔNG gộp thêm được nữa. UseCase tiếp theo (`feature-shop` cùng Phase 5) phải gộp vào 1 record
  kiểu `EquipmentUseCases`, không thêm tham số thứ 5 trực tiếp.
- ⚠️ **Lát cắt này (4) chỉ có domain + application + adapter + test** (Postgres + fake port) — chưa
  nối protocol/gateway, theo đúng thứ tự đã dùng cho "mặc đồ" (nền tảng trước, protocol/gateway sau).

### Phase 5 lát cắt 5 (2026-07-23, cùng ngày): nối protocol/gateway cho cường hoá

- **`EnhanceItemCommand`/`EnhanceItemResponse`** (opcode 505-506, `inventory.proto`) — response
  dùng **enum `EnhanceOutcome` riêng (6 giá trị)**, KHÔNG tái dùng pattern `bool success` +
  `error_key` của Equip/Unequip. Lý do: cường hoá có 3 kết quả "lệnh chạy đúng nhưng khác ý nghĩa
  gameplay" (`SUCCESS`/`FAILED_NO_DROP`/`FAILED_DROPPED`) mà client PHẢI phân biệt được (đặc biệt
  `FAILED_DROPPED` cần cảnh báo "mất 1 cấp!" khác hẳn `FAILED_NO_DROP`) — 1 bool không đủ diễn tả,
  3 kết quả lỗi còn lại (`ITEM_NOT_FOUND`/`MAX_LEVEL`/`INSUFFICIENT_GOLD`) gộp chung enum luôn cho
  gọn thay vì tách response riêng.
- **`GatewayChannelHandler.handlers` đổi từ `Map.of` sang `Map.ofEntries`** — thêm opcode thứ 11 thì
  vượt quá 10 overload cố định của `Map.of(...)` (JDK chỉ có bản 0..10 cặp), `Map.ofEntries` không
  giới hạn số cặp, cùng ngữ nghĩa bất biến.
- **`ENHANCE_OUTCOME_MAPPING`** (bảng tra `InventoryFacade.EnhanceResult` → `EnhanceOutcome`) — cùng
  pattern `SLOT_MAPPING` đã dùng cho equip (tránh switch 6 nhánh chạm PMD CyclomaticComplexity).
  Không có `toErrorKey`/`sendError` cho lỗi nghiệp vụ ở đây — tất cả 6 kết quả đi qua
  `EnhanceItemResponse.outcome`, kể cả các nhánh trước đây equip coi là "lỗi 400/401 gửi qua
  `sendError`" cho slot sai (khác equip vì cường hoá không có khái niệm "slot" để validate sớm).
- **Test real-socket** (`GatewayServerIntegrationTest`) chỉ verify nhánh `ITEM_NOT_FOUND` (Fake
  `InventoryPort` trong test này luôn trả túi rỗng) — nhánh thành công/thất bại/rớt cấp đã phủ ở
  tầng dưới (`EnhanceItemUseCaseTest` với Fake Port, `EnhanceItemUseCaseIntegrationTest` với
  Postgres thật) — cùng cách chia tầng test đã dùng cho equip/unequip.

### Phase 5 lát cắt 7 (2026-07-23): "Bảo Vệ" chống rớt cấp

3 quyết định chốt cùng chủ dự án qua `AskUserQuestion` (GDD chỉ nói "có Bảo Vệ" không có cơ chế cụ
thể — CLAUDE.md §4): (1) mua ở shop (item tiêu hao mới, migration V9), không rơi từ quái; (2)
client chủ động gửi cờ `use_protection` mỗi lần cường hoá, không tự động dùng; (3) chỉ tiêu khi
THẤT BẠI THẬT (server roll trước, mới biết có cần tiêu hay không) — thành công hoặc thất bại dưới
+10 không đụng Bảo Vệ dù cờ có bật.

- **`ItemTemplatePort.findIdByCode(String code)`** (mới, tổng quát — không chỉ dành riêng Bảo Vệ) —
  tra `item_template.id` theo khoá nghiệp vụ ổn định thay vì hardcode ID số (ID phụ thuộc thứ tự
  seed, dễ vỡ khi thêm/xoá seed data). `EnhancementTable.PROTECTION_CHARM_CODE` giữ hằng số code.
- **`Inventory.findByTemplate(itemTemplateId)`** (mới) — tìm 1 stack theo template thay vì theo id
  thật (khác `findItem`), dùng để tìm Bảo Vệ trong túi mà không biết trước `ItemStack.id`.
  **`Inventory.consumeOne(itemId)`** (mới) — tiêu 1 đơn vị, xoá hẳn dòng nếu về 0, cùng hợp đồng lỗi
  với `replaceItem` (`IllegalStateException` nếu `itemId` không tồn tại — lỗi lập trình, gọi sau
  `findByTemplate`/`findItem` nên luôn phải tồn tại).
- **`EnhanceItemUseCase` giờ cần 4 Port** (`InventoryPort`/`GoldPort`/`AuditPort`/`ItemTemplatePort`,
  thêm `ItemTemplatePort` để tra Bảo Vệ) — CHẠM giới hạn 4 tham số Checkstyle, bundle vào
  `EnhancePorts` record (mới, cùng tinh thần `ShopPorts`/`EquipmentUseCases`).
- **Thứ tự trong transaction**: roll kết quả TRƯỚC (ngoài transaction, không đổi gì) → trong
  transaction: trừ gold (rẻ, no-op nếu không đủ) → nếu thất bại+≥+10+`useProtection=true`, tìm Bảo
  Vệ qua `findByTemplate` → nếu có, tiêu bằng `consumeOne` + giữ nguyên cấp
  (`FAILED_PROTECTED`); nếu không có, rớt cấp thật (`FAILED_DROPPED`) như cũ.
- **`EnhanceResult` thêm `FAILED_PROTECTED`** (7 giá trị) — không đổi nghĩa 6 giá trị cũ, chỉ thêm
  nhánh mới. Protocol: `EnhanceItemCommand.use_protection` (field mới, additive) +
  `EnhanceOutcome.ENHANCE_OUTCOME_FAILED_PROTECTED` (giá trị enum mới, additive) — không đổi field/
  giá trị cũ nào (shared/CLAUDE.md).
- **Migration V9**: seed `item_template` mới (`enhance_protection_charm`, không mang được — `equip_slot`
  NULL) + 1 dòng `shop_item` (buy 3000/sell 500, PLACEHOLDER cùng tinh thần V8).
- Mọi nơi implement `ItemTemplatePort` bằng lambda (functional interface cũ, giờ có 2 method) phải
  đổi sang anonymous class — vỡ compile ở `ComputeEquipmentBonusUseCaseTest` +
  3 file fake ở `server-gateway/src/test` (`GatewayPipelineTest`/`GatewayServerIntegrationTest`/
  `ConnectedCharacterRegistryTest`), đã sửa hết cùng lát cắt này.

### ADR-025 retrofit (2026-08-02): `EnhanceItemCommand` giờ có `idempotency_key`

Audit hệ thống (rà toàn bộ Command có side-effect theo ADR-025) phát hiện `EnhanceItemUseCase` trừ
gold + đổi `enhance_level` thật (cùng lớp rủi ro `BuyItemCommand`) nhưng CHƯA enforce
`idempotency_key` — client tự retry sau khi mất kết nối trước response sẽ bị trừ gold + roll cường
hoá 2 lần. Vá theo ĐÚNG pattern đã dùng cho `BuyItemUseCase`/`ClaimMailUseCase`: thêm
`IdempotencyPort`/`IdempotencyKeyReusedException` ở `domain/` (bản sao ý tưởng, không phụ thuộc
Gradle vào feature-shop), `EnhancePorts` giờ 5 Port (thêm `idempotencyPort`), ghi key TRƯỚC trừ gold
trong CÙNG transaction — vi phạm UNIQUE → rollback, trả `EnhanceResult.DUPLICATE_REQUEST` (giá trị
enum mới, `EnhanceOutcome.ENHANCE_OUTCOME_DUPLICATE_REQUEST = 8` ở protocol, additive). `EquipItemCommand`/
`UnequipItemCommand` KHÔNG cần field này — thuần đổi trạng thái (map slot→itemId), retry không có
hiệu ứng cộng dồn (xem `EquipItemUseCase`/`UnequipItemUseCase`, không gold/item nào bị chạm).

## Cứng

✅ Quy tắc nghiệp vụ ở `domain/` (túi đầy = `Inventory.add()`, không phải UseCase)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`ItemStack`, `DropTableEntry`)
✅ Random rơi đồ (`GrantLootUseCase`/roll) — chỉ sinh ở server (ENGINE.md §10)
✅ Module khác gọi qua `InventoryFacade`, không đọc bảng `inventory_item`/`drop_table*` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL

## Test
`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito).

### ADR-036 (Accepted 2026-08-30): trang bị có 4 chỉ số, cường hoá có tác dụng thật

Ba lỗ cùng chạm `item_template`, vá cùng một lượt — đọc ADR-036 cho lập luận đầy đủ, đây chỉ là
những gì ảnh hưởng tới cách viết code trong module này:

- **`ItemTemplate` giờ 7 thành phần** (`bonusDef`/`bonusRes`/`bonusHp`/`requiredLevel` mới,
  migration **V22**) — javadoc YAGNI cũ ("chỉ có bonusAtk, thêm khi có item thật cần") đã HẾT
  hiệu lực: V21 chính là "item thật cần" đó. `EquipmentBonus` cũng 4 trường.
- **`EnhancementTable.enhancedBonus(base, level)`** là chỗ DUY NHẤT biết công thức cường hoá
  (`base × (1 + 0,10 × level)`). `ComputeEquipmentBonusUseCase` gọi nó cho **TỪNG món** rồi mới
  cộng lại — cộng tổng trước rồi nhân là SAI, mỗi món có cấp cường hoá riêng.
- **`EquipItemUseCase.execute` nhận thêm `characterLevel`** thay vì có `CharacterPort` riêng: cấp
  nhân vật thuộc `feature-character`, `GatewayChannelHandler` đọc từ DB rồi truyền vào — cùng
  khuôn với `computeEquipmentBonus` → `WorldFacade`. Con số đó KHÔNG BAO GIỜ đến từ client.
- **`server-world` không nhận `EquipmentBonus`** (Điều 23 — nó không được biết module này tồn
  tại): gateway dịch sang `dev.kage.server.world.EquipmentBonusSnapshot`. Cũng vì
  `applyEquipmentBonus(characterId, 4 số rời)` là 5 tham số, vượt Checkstyle `ParameterNumber`.
- **Khoá lỗi là `error.equip_level_too_low`, KHÔNG phải `error.level_too_low`** — khoá sau đã
  thuộc về luồng chọn hệ phái ("chưa đủ cấp 10"). ADR bản Proposed ghi sai tên, đã ghi chú lại
  trong chính ADR.


### ADR-046 D5 (Accepted 2026-09-07): vật phẩm DÙNG ĐƯỢC đầu tiên — đan dược hồi máu

- **`ItemTemplate` giờ 9 thành phần** (`useEffect`/`useValue` mới, migration **V36**).
  `useEffect == null` = món KHÔNG dùng được, tức trạng thái của MỌI hàng trước đó. Enum
  `ItemUseEffect` chỉ có `HEAL_HP` và **cố ý không có `NONE`**: "không dùng được" không phải một
  hiệu ứng, và enum có nhánh NONE luôn kết thúc bằng việc quên kiểm nó ở đâu đó.
- **Một món KHÔNG được vừa mặc được vừa dùng được** — chặn ở compact constructor VÀ ở `CHECK` của
  V36. Dùng xong thì món biến mất, mà nó đang nằm trên người thì đó là một ô trang bị rỗng không ai
  giải thích được.
- **`InventoryFacade` vẫn ĐÚNG 4 tham số**: `UseItemUseCase` vào chung `ItemActionUseCases` với
  `EnhanceItemUseCase` ("thao tác lên MỘT món đang trong túi"), đúng như ghi chú của lát cắt 4 đã
  dặn trước — không thêm tham số thứ 5.
- **`UseItemUseCase` chỉ làm NỬA việc**: kiểm sở hữu + trừ một cái + audit, rồi TRẢ VỀ hiệu ứng.
  Cộng máu là của `server-world`; chỗ nối hai nửa là `server-gateway`. Module này vẫn không biết
  `WorldFacade` tồn tại.
- **KHÔNG kiểm "đang đầy máu thì đừng uống"** — máu sống trong ECS và đường tới đó bất đồng bộ, nên
  hỏi rồi mới trừ là một cuộc đua. Client tự chặn nút (nó có sẵn số máu trong HUD); ép gói qua thì
  mất một viên đan của CHÍNH người chơi, không khai thác được gì.
- **Không có `idempotencyKey`** (khác `EnhanceItemUseCase`): lệnh này không trừ gold, và một lần
  retry chỉ tốn thêm một viên đan. Thêm bảng idempotency cho mỗi lần uống thuốc là chi phí ghi DB
  cho mọi trận đánh.
- **`ItemCatalogEntry` giờ 12 thành phần** (`useEffect`/`useValue`, field 11..12 của proto) — không
  có chúng, client phải bày nút "Dùng" với MỌI món rồi chờ server từ chối, và ô chi tiết không nói
  được "hồi 50 máu". ⚠️ Cột mới phải nối vào **CẢ HAI** đường đọc của `PostgresItemTemplateAdapter`
  (`findById` cho gameplay, `listCatalog` cho client) — có test Postgres khoá cả hai.


### Khoá túi đồ trong DB — đọc-sửa-lưu nay nằm TRỌN trong một transaction (2026-09-25)

- **Lỗi thật**: `AddItemUseCase`/`GrantLootUseCase`/`UseItemUseCase`/`EnhanceItemUseCase` đọc túi
  bằng `findByCharacter` NGOÀI transaction rồi `save()` — mà `save()` xoá mọi dòng không có trong
  bản đã đọc. Một lần mua hàng/nhận thư/giao dịch commit chen giữa hai bước bị XOÁ THẬT (tiền đã
  trừ). `ReentrantLock` của `InventoryFacade` không che được: cửa hàng/thư/quest/giao dịch không đi
  qua facade. Song song với nó, mọi đường "đếm ô rồi INSERT" đẩy được túi lên 21 ô.
- **Cách vá**: `InventoryPort.findByCharacterForUpdate(tx, characterId)` — adapter Postgres lấy
  `CharacterInventoryLock` (khoá tư vấn theo nhân vật) rồi mới đọc. Bốn use case trên đọc bằng hàm
  này, là lời gọi ĐẦU TIÊN trong transaction, trước cả `idempotency_key` và vàng. Cường hoá nay
  roll SAU khi trừ vàng, trong transaction (phân phối không đổi).
- **Luật thứ tự khoá chung của cả server** (cửa hàng, thư, quest, giao dịch cũng theo):
  [DATABASE.md §5.1](../../../docs/05-server/DATABASE.md). Thêm use case ghi túi đồ ⇒ đọc bằng
  `findByCharacterForUpdate`, KHÔNG bằng `findByCharacter`.
- `findByCharacter` vẫn dùng cho đường CHỈ ĐỌC (liệt kê túi, tính chỉ số trang bị) và cho bản mặc
  định của fake ở test.

### Món đang chào trong giao dịch không được cường hoá (quyết định chủ dự án 2026-09-29)

- **Lỗi thật**: `trade_offer_item` không lưu `enhance_level` — người đưa cường hoá món SAU KHI người
  kia đã xác nhận, giao dịch thi hành với món khác giá trị với thứ đã được đồng ý.
- **`TradeOfferPort`** (domain, mới) — `EnhancePorts` giờ 6 Port. `EnhanceItemUseCase` hỏi nó NGAY SAU
  khi tìm thấy món (sau khoá túi đồ, TRƯỚC `idempotency_key` và vàng) ⇒ `EnhanceResult.ITEM_IN_TRADE`:
  không trừ vàng, không đổi cấp, không audit, khoá idempotency CHƯA tiêu (huỷ giao dịch xong thử lại
  bằng chính khoá đó vẫn chạy). Adapter `PostgresInventoryTradeOfferAdapter` (server-database) đọc
  `trade_offer_item` — cùng cách feature-trade đọc `inventory_item` qua port riêng, không phụ thuộc
  Gradle vào feature-trade.
- **Protocol KHÔNG đổi**: gateway gửi `ErrorResponse` `error.item_in_trade` (409) thay cho
  `EnhanceItemResponse`; client khớp theo `sequenceId` của lệnh đang treo
  (`InventoryCoordinator#onEnhanceRejected`).
- **`InventoryFacade.enhanceItem`** đổi switch sang bảng tra (nhánh thứ 9 chạm trần
  `CyclomaticComplexity`); `InventoryFacadeEnhanceResultTest` khoá bảng phủ hết enum thay cho compiler.
- Tuần tự hoá với đặt lời chào: xem feature-trade/CLAUDE.md và DATABASE.md §5.1.

### ADR-061 (Accepted 2026-10-18, lô 16): kho đồ tông môn

- **Bảng RIÊNG `storage_item`** (V63) — không cột vị trí trong `inventory_item`, nên mọi câu đếm/khoá túi
  hiện có không đổi. `StoragePort` (domain) + `PostgresStorageAdapter` (server-database, dùng chung
  `JdbcTransaction` với túi đồ).
- **`StorageUseCase`** nằm trong `ItemActionUseCases` (thành phần thứ ba; ctor 2 tham số cũ = kho
  `unavailable()` cho test) — `InventoryFacade` vẫn đúng 4 tham số.
- Khoá: túi đồ (`findByCharacterForUpdate`) TRƯỚC, rồi `storage_item ... FOR UPDATE`. Gộp chồng trong kho
  cùng luật `Inventory.add` (`StoredItem#canMerge`). Món đang mặc / đang chào giao dịch không gửi được.
- Đứng gần `senior_sister` là việc của gateway (`SectStorageCommands` + `NpcProximity`), module này không
  biết vị trí. Không idempotency key (chỉ dời đồ của chính mình — cùng lý do `UseItemUseCase`).

### ADR-064 (Accepted 2026-10-18, lô 16): Linh Ngọc khảm nạm

- `GemPort` + `GemPolicy` (domain), `SocketGemUseCase` (thành phần thứ tư của `ItemActionUseCases`),
  adapter `PostgresGemAdapter`. Random tiêm qua `RandomGenerator` (test cố định được kết quả).
- `ComputeEquipmentBonusUseCase` có ctor 4 tham số kèm `GemPort` — ngọc cộng PHẲNG, không nhân cường hoá.
- `InventoryPort#isSocketed`: đường dời đồ bằng xoá/tạo dòng (kho) phải từ chối món đã khảm; thư và phường
  thị chặn thẳng trong SQL của adapter (`NOT EXISTS item_socket`). Giao dịch dời nguyên dòng nên giữ ngọc.

