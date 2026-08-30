# ADR-036: Trang bị phải có chỉ số thật — 4 cột `item_template` + hiệu lực của cường hoá

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-08-29) — chờ chủ dự án quyết. AI đo + soạn, KHÔNG tự thi hành |
| Date | 2026-08-29 |
| Deciders | Chủ dự án quyết; AI đo + soạn |
| Related | [GAMEPLAY.md §2, §7, §7.1](../../05-server/GAMEPLAY.md) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (catalog vật phẩm gửi qua mạng) · [NETWORK.md §6](../../05-server/NETWORK.md) (luật tương thích ngược) · [DATABASE.md](../../05-server/DATABASE.md) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi schema DB + protocol phải dừng hỏi) |

---

## Context

Ngày 2026-08-29 migration **V21** thêm 15 `item_template` để lấp 6/7 ô trang bị vốn chưa có gì để
mặc. Trong lúc làm, ba khoảng trống lộ ra — cả ba đều **đọc từ code và kiểm bằng chơi thật**, không
suy đoán.

### Khoảng trống 1 — `item_template` chỉ có đúng MỘT cột chỉ số

Đọc thẳng schema trên Postgres dev (sau V21):

```
item_template: id · code · name_key · max_stack · equip_slot · bonus_atk
```

`bonus_atk` là cột chỉ số **duy nhất**. `EquipmentBonus` (feature-inventory/domain) vì vậy cũng chỉ
có một trường, và javadoc của nó nói thẳng lý do — đúng tinh thần YAGNI lúc viết:

> *"Chỉ có `bonusAtk` ở lát cắt này — `ItemTemplate` cũng chỉ mang đúng field này, thêm chỉ số khác
> (SKILL_DMG/DEF/RES...) khi có item thật cần, không suy đoán trước (YAGNI)."*

**Nay đã có item thật cần.** V21 phải chọn giữa hai cái sai: cho áo giáp/mũ/găng/giày/trang sức
`bonus_atk = 0` (6/7 ô trang bị hoàn toàn vô nghĩa, mặc hay không như nhau), hoặc cho chúng cộng
**ATK** (một cái áo giáp làm người chơi đánh mạnh hơn — vô lý với bất kỳ ai chơi game). V21 chọn cái
thứ hai kèm ghi chú, vì cái thứ nhất biến 14/15 món mới thành nội dung chết.

Trong khi đó công thức damage **đã có sẵn chỗ cho DEF/RES**: `CombatConstants.deriveCombatStats`
tính đủ `atk / skillDmg / def / res / hit% / dodge% / crit%` và `resolveAttack` dùng DEF thật,
`resolveSkillAttack` dùng RES thật. Chỉ là **không có đường nào để trang bị chạm tới DEF/RES/HP** —
`GatewayChannelHandler` chỉ chuyển đúng một con số sang world:

```java
channelFacade.applyEquipmentBonus(characterId, equipmentBonus.bonusAtk());
```

### Khoảng trống 2 — cường hoá KHÔNG làm gì cả

`ComputeEquipmentBonusUseCase` cộng `template.bonusAtk()` của từng món đang mặc. Nó **không đọc
`item.enhanceLevel()`**. Grep toàn server: `enhanceLevel` chỉ xuất hiện ở đường ghi/đọc DB và ở
packet gửi cho client — **không một công thức chỉ số nào đọc nó**.

Kiểm bằng chơi thật (2026-08-29, server thật + Postgres thật): cường hoá `steel_katana` từ **+0 lên
+7**, `audit_log` ghi đủ 9 lượt, trừ đúng **8.900 vàng**. Chỉ số nhân vật: **không đổi gì**. Cái
"+7" chỉ là một nhãn trong tên hiển thị.

Nghĩa là tính năng cường hoá — trọn một `EnhanceItemUseCase`, một bảng tỉ lệ, một bùa Bảo Vệ mua
bằng vàng, một enum 8 outcome, một transaction có audit — hiện là **một cỗ máy đốt vàng không có
đầu ra**. Tài liệu cũng không cứu được: [GAMEPLAY.md §7.1](../../05-server/GAMEPLAY.md) ghi rất kỹ
tỉ lệ và chi phí từng cấp, nhưng **không có một dòng nào nói +1 thì món đồ mạnh lên bao nhiêu**.

### Khoảng trống 3 — điều kiện mặc đồ được ghi trong tài liệu nhưng không tồn tại

[GAMEPLAY.md §7](../../05-server/GAMEPLAY.md) mô tả:

> | `EquipItemUseCase` | Kiểm tra **level/faction yêu cầu** → tính lại chỉ số dẫn xuất (§2) |

`item_template` không có cột `required_level` lẫn `required_faction`, nên phép kiểm đó **không thể
tồn tại** và thật sự không tồn tại trong code. Hệ quả thấy ngay sau V21: một nhân vật **Cấp 1** đủ
vàng là mặc được trọn bộ "Hạ Nhẫn" (+49 ATK so với ATK gốc ~30).

### Vì sao gộp cả ba vào một ADR

Cả ba đều chạm đúng **một bảng** (`item_template`), đúng **một use case** đọc bảng đó
(`ComputeEquipmentBonusUseCase`), và đúng **một message** mang bảng đó ra client
(`ItemCatalogEntry`, ADR-035). Tách ba ADR sẽ là ba lần đổi cùng một schema, ba lần migration, ba
lần đụng protocol — trong khi câu hỏi thực chất chỉ có một: *trang bị trong game này có ý nghĩa gì?*

---

## Decision

> ⚠️ **Chưa được duyệt.** Mục này là ĐỀ XUẤT để chủ dự án quyết, không phải việc đã chốt.

**D1 — Thêm 4 cột vào `item_template`** (migration mới, chỉ THÊM cột có `DEFAULT`, không đổi cột
đang chạy, không đụng dữ liệu cũ):

| Cột | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `bonus_def` | `integer NOT NULL` | `0` | cộng thẳng vào DEF (chống đòn thường) |
| `bonus_res` | `integer NOT NULL` | `0` | cộng thẳng vào RES (chống skill) |
| `bonus_hp` | `integer NOT NULL` | `0` | cộng vào máu tối đa |
| `required_level` | `integer NOT NULL` | `1` | cấp tối thiểu để MẶC được |

Không thêm `required_faction` ở bước này: 15 món của V21 đều là đồ chung, và một cột chỉ có giá trị
NULL là một cột chưa cần. Thêm khi có bộ đồ riêng hệ phái đầu tiên.

**D2 — `EquipmentBonus` mang 4 trường thay vì 1**, `applyEquipmentBonus` chuyển cả 4 sang
`server-world` (đổi `WorldCommand.ApplyEquipmentBonusCommand` — API nội bộ, không phải protocol).

**D3 — Cường hoá cộng theo PHẦN TRĂM chỉ số gốc của chính món đó**, không cộng số tuyệt đối:

```
bonus_hiệu_dụng = bonus_gốc × (1 + ENHANCE_STEP × enhance_level)
```

với `ENHANCE_STEP = 0,10` (mỗi cấp +10% chỉ số gốc của món; +15 ⇒ ×2,5). Chọn phần trăm vì nó tự
chia tỉ lệ theo bậc đồ: cùng một công thức, +10 trên kiếm gỗ vẫn là đồ tập sự, +10 trên Katana Thép
mới đáng 20.000 vàng ở cấp cuối. Số tuyệt đối thì cường hoá món rẻ nhất luôn là nước đi tối ưu.

`ENHANCE_STEP` đặt cạnh `EnhancementTable` (feature-inventory/domain) — cùng chỗ với tỉ lệ và chi
phí, để một lần cân bằng chỉ phải mở một file.

**D4 — `EquipItemUseCase` kiểm `required_level`**, trả `error.level_too_low` (khoá i18n mới, đúng
khuôn ba `error_key` Equip/Unequip đang có — xem `InventoryController.KNOWN_ERROR_KEYS` phía client).

**D5 — Protocol: THÊM 4 field vào `ItemCatalogEntry`** (`inventory.proto`), số field mới `7..10`,
KHÔNG đụng field `1..6` đang chạy, **KHÔNG tăng `protocolVersion`** — đúng luật additive của
[NETWORK.md §6](../../05-server/NETWORK.md) và đúng tiền lệ ADR-035. Client cũ đọc bản mới vẫn chạy
(bỏ qua field lạ); client mới đọc server cũ nhận `0` (mặc định proto3) — hiển thị đúng cái nó biết.

Không thêm opcode nào. Không đổi message nào đang chạy.

---

## Alternatives Considered

| Phương án | Vì sao KHÔNG chọn |
|---|---|
| **A. Giữ nguyên `bonus_atk`, mọi món đều cộng ATK** (hiện trạng V21) | Rẻ nhất, và là hiện trạng. Nhưng nó biến 6/7 ô trang bị thành ô trang trí và dạy người chơi rằng giáp là vũ khí. Càng thêm nội dung càng khó gỡ về sau — mọi con số cân bằng đã lỡ đặt theo ATK. |
| **B. Một cột `stats_json`** thay vì 4 cột | Mềm dẻo, thêm chỉ số sau không cần migration. Nhưng dự án **chưa có thư viện JSON nào** trong TECHNOLOGY-STACK — thêm Jackson/Gson là một quyết định riêng, lớn hơn hẳn quyết định này. `drop_table` đã từng đứng trước đúng ngã ba này và chọn bảng quan hệ (feature-inventory/CLAUDE.md); đi ngược lại đó ở cùng một module là tự mâu thuẫn. |
| **C. Bảng `item_stat` riêng (1 dòng = 1 chỉ số)** | Tổng quát nhất, không bao giờ phải migration nữa. Nhưng thêm một lượt JOIN cho một bảng chỉ có **4 loại chỉ số** và ~20 dòng item — trả giá phức tạp cho một sự tổng quát chưa ai cần (Điều 9 và chính lập luận YAGNI đã dùng cho `bonus_atk`). |
| **D. Cường hoá cộng SỐ TUYỆT ĐỐI mỗi cấp** (vd +2 ATK/cấp) | Đơn giản hơn phần trăm. Nhưng làm cường hoá món rẻ nhất luôn là lựa chọn tối ưu về mặt vàng/điểm chỉ số, và bậc đồ mất hết ý nghĩa ở mức cường hoá cao. |
| **E. Để cường hoá tiếp tục không có tác dụng, chỉ là nhãn khoe** | Trung thực với hiện trạng và không tốn gì. Nhưng người chơi đang **mất vàng thật** cho một nút không làm gì — đó là một cái bẫy, không phải một tính năng. Nếu chọn phương án này thì phải **gỡ nút Cường hoá khỏi client**, không thể để nguyên. |

---

## Consequences

**Được:**

- 6/7 ô trang bị có lý do tồn tại; V21 và mọi đợt nội dung sau đặt được số liệu đúng chỗ.
- Cường hoá có đầu ra thật ⇒ vòng "giết quái → bán đồ → cường hoá → đánh khoẻ hơn" khép kín. Đây là
  vòng lặp cốt lõi duy nhất còn đứt của game.
- `EquipItemUseCase` khớp lại với tài liệu mô tả nó (khoảng trống 3), thay vì tài liệu tiếp tục mô
  tả một phép kiểm không tồn tại.
- Cân bằng gom về đúng hai chỗ: số liệu từng món trong migration, hệ số cường hoá trong
  `EnhancementTable`.

**Mất / phải chấp nhận:**

- **Đổi schema DB** (§4) — cần chủ dự án duyệt. Chỉ THÊM cột có `DEFAULT`, dữ liệu cũ không phải
  backfill.
- **Đổi protocol** (§4) — additive, không tăng `protocolVersion`, nhưng vẫn là đổi.
- `HP tối đa` hiện là hằng số `CombatConstants.CHARACTER_MAX_HP = 100` (placeholder, GDD chưa chốt
  công thức HP). Cho `bonus_hp` chạy nghĩa là **đụng vào chỗ đó** — hoặc chấp nhận `maxHp = 100 +
  bonus_hp` như một placeholder mới, hoặc tách `bonus_hp` ra khỏi đợt này và chỉ làm DEF/RES.
  **Đây là câu hỏi phụ cần chủ dự án trả lời cùng.**
- Số liệu 15 món của V21 sẽ phải **đặt lại** (đang dồn hết vào ATK) — một migration chỉnh số, không
  phải viết lại.
- `ENHANCE_STEP = 0,10` là **đề xuất khởi điểm chưa qua playtest**, đúng như bảng tỉ lệ §7.1 từng là.

**Không đổi:** không thêm opcode, không đổi message đang chạy, không đổi `EnhanceItemUseCase`
(nó vẫn chỉ đổi `enhance_level` — cách đọc con số đó mới là phần thay đổi), không đụng ADR nào đang
Accepted.

---

## Revisit Trigger

- Khi có bộ đồ **riêng cho từng hệ phái** đầu tiên ⇒ mở lại để thêm `required_faction`.
- Khi GDD chốt **công thức HP tối đa** thật ⇒ mở lại phần `bonus_hp`.
- Khi số chỉ số vượt **6** ⇒ đo lại phương án B/C: 4 cột thì rẻ, 10 cột thì bảng bắt đầu loãng.
- Khi playtest cho thấy `ENHANCE_STEP` làm chênh lệch giữa người cường hoá và người không cường hoá
  quá lớn ⇒ chỉnh hệ số, không cần mở lại ADR.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 4 cột thẳng trên `item_template`, không JSON, không bảng phụ | Proposed |
| 2 | Cường hoá cộng theo **phần trăm** chỉ số gốc của chính món đó | Proposed |
| 3 | Thêm field vào `ItemCatalogEntry`, KHÔNG tăng `protocolVersion` | Proposed |
| 4 | Chưa thêm `required_faction` — chờ bộ đồ hệ phái đầu tiên | Proposed |
| 5 | `bonus_hp` phụ thuộc câu hỏi HP tối đa, có thể tách khỏi đợt này | Chờ chủ dự án |

## ADR cần tạo

Không. ADR này tự đứng được; không sinh ra quyết định kiến trúc nào khác.

## Tài liệu liên quan

- [GAMEPLAY.md §2, §7, §7.1](../../05-server/GAMEPLAY.md) — chỉ số dẫn xuất, cường hoá, bảng tỉ lệ
- [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) — `ItemCatalogEntry` sinh ra ở đây
- [NETWORK.md §6](../../05-server/NETWORK.md) — luật thêm field không phá tương thích
- [DATABASE.md](../../05-server/DATABASE.md) — quy ước migration
- `server/server-features/feature-inventory/CLAUDE.md` — lập luận YAGNI gốc của `bonus_atk`

## Việc cần làm tiếp theo

1. **Chủ dự án quyết**: duyệt/bác D1-D5, và trả lời câu hỏi phụ về `bonus_hp` vs
   `CHARACTER_MAX_HP` placeholder.
2. Nếu duyệt: migration thêm 4 cột → mở rộng `EquipmentBonus`/`ApplyEquipmentBonusCommand` →
   `ComputeEquipmentBonusUseCase` đọc `enhanceLevel` → `EquipItemUseCase` kiểm `required_level` →
   thêm 4 field `ItemCatalogEntry` → migration chỉnh lại số liệu 15 món V21 → client hiện chỉ số
   trong ô chi tiết túi đồ.
3. Nếu bác D3 (chọn phương án E): **gỡ nút Cường hoá khỏi `InventoryPanel`** — để nguyên một nút
   đốt vàng không tác dụng là tệ hơn cả hai lựa chọn.
