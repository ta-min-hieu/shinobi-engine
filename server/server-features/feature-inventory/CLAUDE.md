# CLAUDE.md — feature-inventory

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-inventory/**`.

## Cấu trúc (Clean Arch)

```
api/            InventoryFacade · EquipmentUseCases (gộp tham số, tránh vượt 4 — Checkstyle)
application/    AddItemUseCase · GrantLootUseCase · EquipItemUseCase · UnequipItemUseCase
                ComputeEquipmentBonusUseCase (đọc thuần, tổng bonusAtk từ mọi item đang mặc)
domain/         Inventory (aggregate) · Equipment (aggregate RIÊNG) · ItemStack (VO)
                DropTableEntry (VO) · ItemTemplate (VO, equipSlot + bonusAtk) · EquipmentBonus (VO)
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
- ⚠️ **Chưa nối protocol/gateway** cho cường hoá (opcode, `EnhanceItemCommand`) — lát cắt này CHỈ có
  domain + application + adapter + test (Postgres + fake port), theo đúng thứ tự đã dùng cho "mặc
  đồ" (nền tảng trước, protocol/gateway sau). Opcode tiếp theo trong dải Inventory/Equipment
  (500-599, `inventory.proto`) là 505+.

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
