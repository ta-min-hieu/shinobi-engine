# ADR-043: Máu tối đa mới phải theo gió về client ngay khi đổi trang bị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-05) — chủ dự án duyệt, **chọn cách (a)** ở D5 (tính lại bằng `WorldFacade.characterMaxHp`, không chờ tick). |
| Date | 2026-09-05 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stat-Bonuses.md) (nguồn của `bonus_hp`) · [ADR-041](ADR-041-Spawn-Position-In-Enter-World.md) (cùng hình dạng: field additive vá một khoảng trống đồng bộ) · [NETWORK.md §6](../../05-server/NETWORK.md) (luật additive) |

---

## Context

### Triệu chứng đo được khi chơi thật (2026-09-05)

Mặc trọn bộ "Tạp Dịch" (`novice_*`, tổng **+18 máu**) trong lúc đang ở trong world:

- Thanh máu trên HUD vẫn ghi **`100 / 100`**.
- Chỉ sau khi **chết và hồi sinh** nó mới thành **`118 / 118`**.
- Đăng nhập lại cũng ra `118 / 118` ngay.

### Server KHÔNG sai — đây là khoảng trống GIAO THỨC

Đã đọc hết đường đi: `GatewayChannelHandler.handleEquipItemBlocking` gọi
`WorldFacade.applyEquipmentBonus(...)` **ngay sau khi mặc thành công**, và `applyMaxHpBonus` cập
nhật `HealthComponent.max` đúng trong tick đó. **Chỉ số thật đúng ngay lập tức** — chỉ có client là
không biết.

Lý do: **không gói nào chở máu tối đa của CHÍNH MÌNH ngoài hai chỗ**:

| Message | Khi nào tới |
|---|---|
| `SelectCharacterResponse.max_hp` (character.proto 3..4) | lúc vào world |
| `CharacterRespawnedEvent.max_hp` (movement.proto) | lúc hồi sinh |

`EquipItemResponse` / `UnequipItemResponse` chỉ có `success` + `error_key`; `EnhanceItemResponse`
chỉ có `outcome`. **Cả ba đều đổi được `bonus_hp`** (cường hoá nhân hệ số lên `bonus_hp` theo
ADR-036 D2/D3) mà không cái nào nói lại con số mới.

### Vì sao đáng sửa chứ không phải chuyện nhỏ

Đây **đúng là con bug ADR-036 từng sửa một nửa**: lần đó triệu chứng là `139 / 100` (mẫu số kẹt ở
hằng số 100 của client) và được vá bằng cách thêm `max_hp` vào `SelectCharacterResponse`. Nhưng chỉ
vá **thời điểm VÀO WORLD**; thời điểm **ĐỔI TRANG BỊ** vẫn hở nguyên.

Hậu quả trực tiếp lên gameplay: người chơi **không thấy đồ mình vừa mua có tác dụng**, nên không
biết lúc nào đủ sức đánh con quái đang giết mình — chính tình huống đã xảy ra khi playtest quest
`night_watch_bandit`.

---

## Decision

**D1.** Thêm field **additive** vào ba message đã có, KHÔNG thêm opcode mới:

```protobuf
message EquipItemResponse   { bool success = 1; string error_key = 2; int32 current_hp = 3; int32 max_hp = 4; }
message UnequipItemResponse { bool success = 1; string error_key = 2; int32 current_hp = 3; int32 max_hp = 4; }
message EnhanceItemResponse { EnhanceOutcome outcome = 1;             int32 current_hp = 2; int32 max_hp = 3; }
```

**D2.** `protocolVersion` **KHÔNG tăng** — chỉ thêm field, không đổi/xoá field nào (NETWORK.md §6),
đúng tiền lệ ADR-036 và ADR-041.

**D3.** `max_hp <= 0` ⇒ client **giữ nguyên giá trị đang có**, không đoán — đúng khuôn
`CombatHudController.onEnterWorld`/`onRespawn` đã dùng cho server cũ chưa gửi field.

**D4.** Chỉ gửi khi thao tác THÀNH CÔNG (`success=true`, hoặc outcome đổi thực sự `enhance_level`);
thất bại thì cả hai bằng 0.

**D5.** ⚠️ **VẤN ĐỀ THẬT PHẢI GIẢI TRONG LÚC THI HÀNH**: `applyEquipmentBonus` **đẩy lệnh vào
`inbox`** của vòng tick, nên lúc gateway dựng response thì `HealthComponent` **CHƯA được cập nhật**.
Đọc thẳng entity ngay sau khi gọi sẽ trả về số CŨ — tức vá xong vẫn sai, chỉ sai tinh vi hơn. Hai
hướng — **chủ dự án chọn (a)**:

- **(a)** Tính lại bằng `WorldFacade.characterMaxHp(bonus)` — hàm `static`, là **nguồn chân lý duy
  nhất** của công thức này và gateway ĐÃ dùng đúng nó cho `SelectCharacterResponse`. Rẻ, không phải
  chờ tick. **Khuyến nghị.**
- **(b)** Cho `applyEquipmentBonus` trả về `CompletableFuture` rồi chờ — đúng hơn về nguyên tắc
  nhưng thêm một điểm chặn vào đường lệnh, và vẫn phải đợi tick.

**D6.** Máu HIỆN TẠI cũng phải gửi kèm, không chỉ máu tối đa: `applyMaxHpBonus` **dịch máu hiện tại
theo độ chênh** (mặc đồ +HP không phải nút hồi máu, gỡ đồ không được giết người chơi), nên tử số
cũng đổi.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| **A. Thêm field vào 3 response đã có** (đề xuất) | Additive thuần, không opcode mới, không tăng `protocolVersion`, đúng tiền lệ ADR-036/041; số tới đúng lúc người chơi đang nhìn | 3 message phải nhớ gửi; thêm chỗ thứ 4 đổi `bonus_hp` sau này là lại quên | **Đề xuất** |
| B. Một `StatsChangedEvent` chung, server chủ động đẩy | Một chỗ duy nhất, mọi nguồn đổi chỉ số đều qua đó | **Opcode MỚI** + phải quyết định đẩy khi nào (mọi tick? ngưỡng?) — đắt hơn hẳn vấn đề đang có, và đi ngược ADR-042 (đang muốn GIẢM số gói) | Không — Điều 9 |
| C. Client tự cộng `bonus_hp` từ catalog vật phẩm | Không đổi protocol | **Bản sao thứ hai của công thức HP** ở phía client, lệch trong im lặng ngay lần GDD chốt công thức thật; và client tính lại hệ số cường hoá = đoán gameplay (Điều 15) | Không |
| D. Để nguyên | 0 công | Người chơi không thấy đồ vừa mua có tác dụng cho tới khi chết một lần | Không |

---

## Consequences

**Tích cực:** đóng nốt nửa sau của bug ADR-036; người chơi thấy ngay tác dụng của đồ vừa mặc/cường hoá.

**Đánh đổi:** mỗi chỗ mới đổi được `bonus_hp` sau này phải nhớ gửi kèm hai field này. Nếu số chỗ đó
vượt **bốn**, xem lại phương án B.

**Module bị ảnh hưởng:** `shared-protocol` (3 field), `server-gateway` (3 handler), `client-core`
(`InventoryCoordinator` → `CombatHudController`). **Không đổi DB, không đổi `server-world`.**

---

## Revisit Trigger

Khi GDD chốt công thức HP thật (thay `CHARACTER_MAX_HP = 100` placeholder), hoặc khi có nguồn thứ tư
đổi `bonus_hp` — lúc đó cân lại A vs B.

---

## Technical Decisions

- Server ĐÃ đúng từ đầu; đây thuần là khoảng trống đồng bộ client, không phải lỗi logic.
- Không tăng `protocolVersion` (additive).
- **D5 là cái bẫy thật**: `inbox` bất đồng bộ ⇒ đọc entity ngay sau khi gọi sẽ ra số cũ.

## ADR cần tạo

Không.

## Tài liệu liên quan

- [ADR-036](ADR-036-Equipment-Stat-Bonuses.md) — nguồn của `bonus_hp` và của nửa trước bản vá.
- [NETWORK.md §6](../../05-server/NETWORK.md) — luật thêm field additive.

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án duyệt 2026-09-05, chọn cách (a).**
2. ✅ **ĐÃ THI HÀNH cùng ngày**: 6 field additive → 3 handler gateway → `InventoryCoordinator`
   → `CombatHudController`.
3. ✅ **ĐÃ PLAYTEST SỐNG**: mặc/gỡ đồ giữa world, thanh máu đổi **ngay**, không cần chết.
