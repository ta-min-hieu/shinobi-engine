# ADR-046: Hồi máu — ba nguồn, một đường ống báo về client

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-07) — chủ dự án chọn **cả ba hướng (a) + (b) + (c)** ở dòng 17 bảng "🔴 ĐANG CHỜ CHỦ DỰ ÁN", rồi Accept ADR này **nguyên trạng** (giữ nguyên mọi con số đề xuất ở D2/D3/D5). |
| Date | 2026-09-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (tiền lệ "chỉ THÊM opcode") · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (máu tối đa không còn là hằng số) · [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md) (cùng họ: server đúng, client không được báo) · [ADR-039](ADR-039-Per-Species-AI-Parameters.md) (`leashRadiusMeters` — chỗ móc của D4) · [ADR-021](ADR-021-Box2D-vs-AABB.md) / PHYSICS.md §7 (`TriggerZoneSystem` dựng sẵn chờ consumer) |

---

## Context

### Triệu chứng đo được, không phải suy đoán

Cả **arc 1 không có một nguồn hồi máu nào**. `grep -rn "regen\|heal" server/server-world/src/main`
ra 0 kết quả; thứ duy nhất hồi máu trong game là kỹ năng `thuy_hoi_phuc`, mà nó đòi **đã chọn hệ
Thuỷ**, tức từ **Lv10** trở lên — đúng cái mốc arc 1 KẾT THÚC.

Hệ quả đã đo trong playtest thật (2026-09-06):

| Bằng chứng | Số |
|---|---|
| Quest 3 bắt hạ **2** thạch vệ; hạ xong con ĐẦU còn | **38/118** |
| Đường đi tới chỗ đánh (trước V32, phía đông) tốn | **65 máu** |
| Đường đi phía tây (sau V32) tốn | **40 máu** |
| Nhân vật chết trong ~15 phút playtest | **3 lần**, cả ba vì không có cách hồi máu |

Hiện người chơi chỉ có hai cách lấy lại máu: **chết rồi hồi sinh**, hoặc **thoát ra vào lại** (HP
không được lưu — đã quan sát 38/118 lúc thoát, 118/118 lúc vào lại). Cả hai là lỗ hổng, không phải
thiết kế.

### Khoảng trống thứ hai, ít ai để ý: client KHÔNG có đường nhận HP

Javadoc của `CombatHudController` nói thẳng:

> Server không gửi gói đồng bộ HP/Khí/hồi chiêu nào (GAMEPLAY.md §3) — HP/Khí bắt đầu ở mức đầy và
> chỉ được SỬA LẠI bằng số thật mỗi khi có `AttackResultEvent`/`SkillResultEvent` nhắm vào mình.

⚠️ **Điều này bác bỏ dòng đánh giá cũ của tôi ở docs/README.md** ("(a) rẻ nhất, KHÔNG đổi
protocol"). Máu hồi mà không có gói nào mang tin đi thì vạch máu trên màn hình **đứng im cho tới
đòn đánh kế tiếp** — đúng họ bug mà ADR-043 đã phải sửa một lần. **Cả ba hướng (a)(b)(c) đều cần
protocol**, nên chúng gộp vào MỘT ADR thay vì ba.

### Khoảng trống thứ ba: quái không bao giờ lành lại

`AiSystem.tickReturn` đưa quái về đúng điểm spawn rồi `IDLE`, **không đụng `HealthComponent`**.
Nghĩa là hôm nay đã có thể đánh một con, chạy ra khỏi `leashRadiusMeters`, quay lại đánh tiếp con
máu thấp — vô hạn. Chưa ai khai thác vì chưa ai chạy nổi, nhưng cho người chơi hồi máu mà không cho
quái lành lại là **biến lỗ hổng ngủ đông đó thành chiến thuật tối ưu của mọi trận**.

---

## Decision

### D1 — Một event duy nhất mang HP thay đổi, dùng chung cho cả ba nguồn

```protobuf
// opcode_registry.proto — dải Combat (400-499)
OPCODE_HEALTH_CHANGED_EVENT = 408;

// combat.proto
message HealthChangedEvent {
  int64 entity_id = 1;   // DƯƠNG = nhân vật, ÂM = quái — cùng quy ước combat.proto
  int32 current_hp = 2;
  int32 max_hp = 3;
}
```

Gửi cho **mọi người đang thấy entity đó trong AoI**, y hệt `AttackResultEvent`.

**Không có field "vì sao"**: vạch máu không quan tâm lý do, và số "+N" client tự lấy được bằng hiệu
với giá trị đang giữ. Thêm enum lý do là mở rộng bề mặt protocol để đổi lấy một thứ client tự tính
được.

**Dải Combat chứ không phải Character (200–299)**: event này nói về **một entity bất kỳ trong
world** (gồm cả quái, ID âm), không phải về nhân vật của chính mình như `GOLD_CHANGED_EVENT` 209.

**D1b.** `protocolVersion` **KHÔNG tăng** — chỉ THÊM (NETWORK.md §6), đúng tiền lệ ADR-035/ADR-045.

### D2 — (a) Hồi máu ngoài chiến đấu: `HealthRegenSystem` ở `server-world`

- Component mới `CombatRecencyComponent { float secondsSinceCombat; float healAccumulator; }` —
  chỉ dữ liệu (Điều 43).
- `CombatSystem`/`SkillSystem` đặt `secondsSinceCombat = 0` cho **cả bên đánh lẫn bên chịu** mỗi
  khi có damage thật.
- System cộng dồn `deltaTime`; khi vượt `REGEN_DELAY_SECONDS` thì cộng
  `REGEN_FRACTION_PER_SECOND × max` vào `healAccumulator`, đổ phần nguyên vào `current`.
- **Chỉ áp cho NHÂN VẬT** (`CharacterIdComponent`). Quái đi đường D4 — con quái đang đuổi theo
  người chơi mà chưa đánh trúng ai thì "ngoài chiến đấu" theo nghĩa đen, và sẽ vừa chạy vừa hồi
  máu. Hai loại entity, hai cơ chế đúng với chúng.
- Không hồi khi đã chết (`isDead()`), không vượt `max`.
- **Chỉ phát event khi `current` THỰC SỰ đổi**, và tối đa **1 lần/giây** mỗi entity
  (`REGEN_EMIT_INTERVAL_SECONDS`) — không phải 60 lần/giây. Với ADR-042 đang muốn GIẢM số gói, con
  số này là trần cứng: người đầy máu phát **0** gói.

Hằng số đề xuất (đặt ở `CombatConstants`, không phải magic number):

| Hằng số | Giá trị | Nghĩa với máu 118 |
|---|---|---|
| `REGEN_DELAY_SECONDS` | 8 | 8 giây không đánh/không bị đánh thì bắt đầu hồi |
| `REGEN_FRACTION_PER_SECOND` | 0,03 | ~3,5 máu/giây ⇒ 38→118 mất **~23 giây** |
| `REGEN_EMIT_INTERVAL_SECONDS` | 1 | ≤1 gói/giây/người, và chỉ khi đang thiếu máu |

### D3 — (c) Vùng an toàn tông môn: `TriggerZoneSystem` có consumer THẬT đầu tiên

- Bảng mới `safe_zone` (V35): `code`, `min_x`, `min_y`, `max_x`, `max_y` — **nội dung ở DB, không
  phải hằng số Java** (Điều 27, cùng đường ADR-037/ADR-040 đã đi cho quái và NPC).
- Nạp vào `WorldContentCatalog` như `monsterCatalog`/`npcCatalog`, đổ vào
  `TriggerZoneSystem.setZones(...)`; nhân vật được gắn `TriggerZoneMembershipComponent` lúc attach.
- **Bước VÀO vùng ⇒ `current = max` ngay**, phát `HealthChangedEvent`. Không hồi dần: chủ dự án
  chọn "về tông môn hồi đầy".
- Một hàng seed: vùng quanh trưởng lão Chu Hoành (x = −5). Đề xuất `[-15, 5] × [-10, 10]`.
- Bảng cố ý tên `safe_zone` chứ không `heal_zone`: cùng vùng đó sau này là chỗ **cấm PvP** (STORY
  §7 — tông môn là đất an toàn). ADR này KHÔNG thi hành phần PvP.

### D4 — Quái lành lại khi về tới điểm spawn (đóng lỗ hổng ở Context)

`AiSystem.tickReturn`, đúng nhánh "đã về tới nơi" (nơi đã chốt `position.x = ai.spawnX`):
`health.current = health.max` + phát `HealthChangedEvent` cho người đang quan sát.

Đây là luật MMO tiêu chuẩn, và nó khớp chính xác với D2: người chơi chạy đi 8 giây để hồi máu thì
con quái cũng vừa về nhà và đầy máu — **trận đấu reset**, không ai được lợi.

### D5 — (b) Đan dược: `UseItemCommand`/`UseItemResponse`

```protobuf
OPCODE_USE_ITEM_COMMAND  = 512;
OPCODE_USE_ITEM_RESPONSE = 513;

message UseItemCommand  { int64 inventory_item_id = 1; }
message UseItemResponse { bool success = 1; string error_key = 2; int32 remaining_quantity = 3; }
```

- `item_template` thêm **hai cột** (V36): `use_effect VARCHAR(32)` (NULL = không dùng được) và
  `use_value INT NOT NULL DEFAULT 0`. Enum Java `ItemUseEffect { HEAL_HP }` — mở rộng sau
  (`RESTORE_CHI`, buff) không đổi schema.
- `UseItemUseCase` ở `feature-inventory`: kiểm quyền sở hữu (characterId lấy từ **session**), kiểm
  `use_effect` khác NULL, giảm `quantity` 1 trong **transaction + audit log** (server/CLAUDE.md),
  rồi gọi `WorldFacade.enqueueHealCharacter(characterId, amount)`.
- ⚠️ **`UseItemResponse` KHÔNG mang HP** — `inbox` của world là bất đồng bộ, đọc entity ngay sau
  khi enqueue sẽ ra số CŨ (bẫy đã trả giá một lần ở [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md)). Máu mới về client bằng `HealthChangedEvent` ở D1 —
  đó chính là lý do D1 tồn tại trước D5 trong ADR này.
- Hai dòng nội dung (V36): `minor_healing_pill` (Tiểu Hồi Nguyên Đan, hồi **50** máu, stack 20, mua
  **80** vàng / bán 20) — bán ở Tàng Bảo Các. Một món là đủ cho arc 1; bậc cao hơn là nội dung của
  arc 2.
- Món này **xoá dòng ghi chú ở V21 §3** ("CỐ Ý KHÔNG thêm thuốc hồi máu: server CHƯA có
  UseItemUseCase nào") — điều kiện của ghi chú đó vừa hết hiệu lực.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| Mượn `SkillResultEvent` với `EFFECT_KIND_HEAL` cho hồi máu | 0 opcode mới | Phải bịa `skill_code` rỗng cho một thứ không phải skill; client đang dùng `skill_code` để chọn VFX/animation ⇒ mọi nhịp hồi máu sẽ phát hiệu ứng chiêu thức | Không |
| Nhét HP vào `MoveResponse` (gói client vốn đã nhận đều) | 0 opcode mới | Ai đứng yên thì không bao giờ biết mình đã hồi máu — đúng lỗi ADR-041 vừa sửa; và trộn hai trách nhiệm vào một gói | Không |
| **Event `HealthChangedEvent` riêng (D1)** | Nói đúng thứ đang xảy ra; một đường ống cho cả ba nguồn + D4; additive thuần | Thêm 1 opcode phải nuôi | **Đề xuất** |
| Hồi máu bằng cách LƯU HP rồi hồi khi đăng nhập lại | Rẻ | Biến "thoát ra vào lại" thành cơ chế hồi máu chính thức — tệ hơn hiện trạng | Không |
| Chỉ làm (b) đan dược, bỏ (a)(c) | Đúng chất tu tiên nhất | Người chơi hết vàng/hết đan là kẹt cứng; và arc 1 chưa có nguồn vàng ổn định | Không |
| Cho quái hồi máu ngoài chiến đấu như người chơi (thay D4) | Một cơ chế cho cả hai | Quái đang ĐUỔI mà chưa đánh trúng ai vẫn tính là "ngoài chiến đấu" ⇒ vừa đuổi vừa hồi máu | Không |

---

## Consequences

**Tích cực:** đóng dòng 17 — việc CHẶN duy nhất còn lại. Mở đường cho arc 2 (STORY §6), nơi mọi
chuỗi quest đều có nhiều hơn một trận. `TriggerZoneSystem` (dựng từ 2026-08-01, chưa có consumer)
lần đầu chạy thật. Đóng luôn lỗ hổng "đánh rồi chạy" của quái mà chưa ai báo cáo.

**Đánh đổi:**
- Thêm 3 opcode (408, 511, 512) phải nuôi. Với ADR-042 (đang muốn GIẢM số gói): 408 có trần
  ≤1 gói/giây/entity **và chỉ khi đang thiếu máu** — ở trạng thái thường (mọi người đầy máu) nó
  phát **0** gói, nên không đụng tới bài toán 59.760 gói/giây.
- Cân bằng mọi trận đấu đã đo trước 2026-09-07 **đổi nghĩa**: "thắng còn 38/118" giờ là "thắng còn
  38/118 rồi hồi lại trong 23 giây". `OldArchiveDuelBalanceTest` và các test cân bằng khác vẫn đo
  ĐÚNG một trận đơn lẻ (chúng không cho thời gian trôi ngoài chiến đấu), nên **không test nào phải
  sửa số** — nhưng ý nghĩa gameplay của chúng phải đọc lại theo ADR này.
- Đổi DB schema (2 migration) ⇒ đúng §4, chính là lý do ADR này tồn tại.

**Module bị ảnh hưởng:** `shared-protocol` (3 opcode + 3 message), `server-world`
(`HealthRegenSystem`, `CombatRecencyComponent`, `AiSystem`, `WorldFacade`, `WorldOutboundSink`,
`WorldContentCatalog`), `server-database` (V35 `safe_zone`, V36 cột + seed đan dược),
`feature-inventory` (`UseItemUseCase`), `server-gateway` (2 handler + 1 sink method),
`client-core` (`CombatHudController`, `InventoryPanel` nút "Dùng", định tuyến opcode).

---

## Revisit Trigger

- Khi có **hệ phái/trang bị đổi tốc độ hồi máu** (chỉ số "hồi phục"): lúc đó
  `REGEN_FRACTION_PER_SECOND` không còn là hằng số mà là chỉ số dẫn xuất, phải đi cùng đường
  `EquipmentBonusSnapshot`.
- Khi thêm **vùng an toàn thứ hai** có luật khác (cấm PvP nhưng không hồi máu): lúc đó `safe_zone`
  cần cột `heals BOOLEAN`, đừng đoán trước bây giờ.
- Khi số gói của 408 vượt ~5% tổng lưu lượng đo được: gộp vào ADR-042.

---

## Technical Decisions

- Một event cho cả ba nguồn hồi máu + reset của quái, **không** ba đường riêng.
- `UseItemResponse` KHÔNG mang HP — world là bất đồng bộ, HP về bằng 408.
- Người chơi hồi dần (D2), quái hồi đầy khi về nhà (D4): hai cơ chế vì hai loại entity khác nhau.
- Vùng an toàn nằm ở **DB**, không phải hằng số Java (Điều 27).

## ADR cần tạo

Không.

## Tài liệu liên quan

- [GAMEPLAY.md §3](../../05-server/GAMEPLAY.md) — pipeline damage, nơi `secondsSinceCombat` reset.
- [PHYSICS.md §7](../../03-engine/PHYSICS.md) — `TriggerZoneSystem`, vùng an toàn.
- [NETWORK.md §4/§6](../../05-server/NETWORK.md) — dải opcode + luật additive.
- [ADR-042](ADR-042-Outbound-Packet-Batching.md) — vì sao phải chốt trần số gói cho 408.

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án Accept nguyên trạng 2026-09-07.**
2. ✅ **Đợt 1 xong cùng ngày**: D1 (opcode 408) + D2 (`HealthRegenSystem` + `CombatRecencyComponent`)
   + D3 (V35 `safe_zone`, module mới **`feature-world`**, `TriggerZoneSystem` có consumer thật) +
   D4 (`AiSystem.healToFullOnArrival`).
3. ✅ **Đợt 2 xong cùng ngày**: D5 — opcode 512/513, V36 (2 cột + `minor_healing_pill`),
   `UseItemUseCase`, nút "Dùng" trong túi đồ, i18n vi/en, **icon `icon_minor_healing_pill` trả nợ
   art trong ngày** (ASSET-LOG 2026-09-07).
4. ⚠️ **Một quyết định KHÔNG có trong bản Proposed, phát sinh lúc thi hành**: bảng `safe_zone` phải
   có một feature module làm chủ (§4 — mọi bảng đi qua `api/` Facade), mà không module nào đang có
   hợp với "hình học bản đồ". Chủ dự án chọn **module mới `feature-world`** thay vì nhét vào
   `feature-monster` hay quay lại hằng số Java. Module đó là chỗ ở tương lai của bệ nhảy
   (`DEMO_PLATFORMS`) và dải nền đất — thứ đang hardcode phía CLIENT và là nguồn của bug quái lơ
   lửng 2026-09-06.
5. ⚠️ **`ItemCatalogEntry` thêm 2 field (11..12)** ngoài 3 opcode đã liệt ở D1/D5 — vẫn additive,
   `protocolVersion` KHÔNG tăng. Không có chúng, nút "Dùng" phải bày ra với MỌI món rồi chờ server
   từ chối, và ô chi tiết không nói được "hồi 50 máu".
6. ✅ **PLAYTEST SỐNG XONG cùng ngày** — `gameProbe` qua socket TLS thật, server dev + Postgres
   thật. **Cả bốn nguồn đều thấy trên dây**, mỗi cái một con số:

   | Cái gì | Bằng chứng trên dây |
   |---|---|
   | **D2** hồi ngoài chiến đấu | Đòn cuối t=7674ms → gói 408 ĐẦU TIÊN t=**16775**ms (9,1s sau — khớp 8s chờ + nhịp phát). Rồi 16775 · 17908 · 19041 · 20175 · 21291 · 22424: **đúng 1 gói/~1,13s**, HP 50→54→58→62→66→70→74 = **+4 máu mỗi nhịp** (3% × 118 = 3,54/giây × 1,13s = 4,0 ✔) |
   | **D3** vùng an toàn | Đòn cuối t=6144ms (67/118) → t=**9192**ms một gói duy nhất **hp=118/118**. Nhảy thẳng 67→118 chỉ **3,0 giây** sau đòn cuối, tức NẰM TRONG cửa sổ chờ 8s ⇒ regen không thể giải thích được; đúng lúc đi bộ về cắt qua biên đông x=5 |
   | **D4** quái lành lại | `408 entity=-7 hp=120/120` — `archive_warden` sau khi bỏ cuộc và về tới điểm spawn |
   | **D5** đan dược | `dùng minor_healing_pill (itemId=53): success=true errorKey='' cònLại=1`, kèm `408 entity=3 hp=6→56` = **đúng +50** |

   ⚠️ **Quan sát cân bằng, không phải lỗi**: trong ba lượt probe, nhân vật vẫn **chết 3 lần** —
   nhưng cả ba đều là *đứng yên giữa tầm aggro của quái đã hồi sinh*, không phải "không có cách hồi
   máu". Đây là loại chết khác hẳn ba lần chết ngày 2026-09-06.
7. ✅ **Đo lại QUEST 3 với hồi máu bật, cùng ngày** — và trận đấu tự nó KHÔNG đổi một chút nào:
   4 nhát × 32 hạ thạch vệ 120 máu, nhận lại 4 × 20 ⇒ **còn đúng 38/118**, khớp TỪNG SỐ với phép đo
   2026-09-06. Cái đổi là chuyện xảy ra SAU đó: gói 408 đầu tiên ở **t=16599ms** (9,1s sau đòn cuối),
   rồi 38→42→46→…→78 trong **10,2 giây**; ngoại suy từ nhịp đo được thì 38→118 mất **~23 giây**,
   đúng con số D2 hứa.
   ⚠️ **Một căng thẳng cân bằng MỚI, đo được chứ không phải đoán**: `monster_spawn.respawn_seconds`
   của thạch vệ là **20 giây**, mà hồi đầy máu mất **~23 giây** — hồi máu CHẬM HƠN hồi sinh của
   quái. Đứng nguyên tại chỗ vừa đánh thì con vừa chết quay lại lúc người chơi mới ~78/118 (probe
   chết đúng kiểu đó ở t=27532ms). Người chơi thật phải **lùi ra khỏi tầm đuổi rồi mới đứng chờ** —
   chuyện bình thường của thể loại, nhưng nay nó là một luật CÓ THẬT của bản đồ này. Quest 3 giờ
   **hoàn thành được mà không cần chết**, khác hẳn hôm qua.
8. ✅ **Đã nhìn nút "Dùng" bằng MẮT** (`runHudPreview`, ảnh `.debug-orientation/hud-inventory.png`):
   nhãn nằm gọn trong nền nút (không tràn như lỗi `QuestLogPanel` từng mắc), hàng 5 nút vẫn vừa
   560px, dòng **"Hồi 50 máu"** hiện đúng từ `ItemCatalogEntry.use_value`, và icon
   `icon_minor_healing_pill` đọc được ở cỡ ô túi.
9. 🔴 **CÒN LẠI**: chưa bấm nút "Dùng" bằng CHUỘT trong client nối server thật — nửa dây đã kiểm
   bằng `gameProbe`, nửa vẽ đã kiểm bằng `runHudPreview`, nhưng hai nửa chưa gặp nhau trên một
   màn hình.
