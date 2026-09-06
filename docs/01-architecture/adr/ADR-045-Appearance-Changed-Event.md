# ADR-045: Event "ngoại hình vừa đổi" — người ĐANG nhìn thấy phải thấy model mới ngay

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-06) — chủ dự án chọn **phương án C** (thêm event riêng) rồi Accept ngay trong phiên; đã thi hành cùng ngày. |
| Date | 2026-09-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) (nguồn của `faction`/`costume_tier` trong `CharacterSpawnedEvent`) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (cùng hình dạng: **chỉ THÊM** opcode để lấp khoảng trống đồng bộ) · [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md) (cùng họ bug: server đúng, client không được báo) |

---

## Context

### Triệu chứng

Nhân vật đủ Lv10 gửi `SelectFactionRequest` (opcode 206) đổi hệ phái giữa phiên chơi. Model của họ
**phải** đổi theo (ADR-031: model glTF suy ra từ `Faction` + `CostumeTier`). Thực tế:

| Ai nhìn | Thấy gì |
|---|---|
| Người chơi đi tới **sau** lúc đổi | ✅ model MỚI (đã vá 2026-08-29) |
| Người chơi **đang** nhìn thấy nhân vật đó lúc đổi | ❌ model CŨ, cho tới khi nhân vật rời rồi vào lại tầm nhìn của họ |

### Vì sao nửa sau chưa vá được

Nửa trước đã vá bằng `ConnectedCharacterRegistry.updateAppearance` — nó sửa **cache ngoại hình** nên
mọi `CharacterSpawnedEvent` phát về **sau** đều mang hệ phái mới.

Nhưng người **đang** quan sát thì đã nhận `CharacterSpawnedEvent` từ trước, và **client cố ý bỏ qua
spawn lặp**:

```java
// RemoteCharacterPresenter.onSpawned
if (entities.containsKey(event.getCharacterId())) {
    return; // đã có — bỏ qua an toàn thay vì tạo entity trùng.
}
```

Thế bất biến "spawn = tạo mới" đó là thứ đang chặn entity trùng — nó không phải lỗi. Nghĩa là
**server có phát lại `CharacterSpawnedEvent` cũng vô ích**: khoảng trống nằm ở chỗ giao thức **không
có** cách nói "nhân vật này vẫn ở đó, chỉ ngoại hình đổi".

---

## Decision

**D1.** Thêm **một** event server→client, dải Movement/World (300–399) — cùng chỗ với
spawn/despawn/respawn của nhân vật vì nó nói về **một nhân vật khác trong world**, không phải về
tài khoản/nhân vật của chính mình (dải 200–299):

```protobuf
// opcode_registry.proto
OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT = 307;

// movement.proto
message CharacterAppearanceChangedEvent {
  int64 character_id = 1;
  Faction faction = 2;
  CostumeTier costume_tier = 3;
}
```

**D2.** `protocolVersion` **KHÔNG tăng** — chỉ THÊM opcode + message, không đổi/xoá gì đang chạy
(NETWORK.md §6), đúng tiền lệ ADR-035.

**D3.** **`server-world` KHÔNG biết khái niệm "ngoại hình".** Lệnh đi qua hàng đợi world chỉ mang
`characterId`; world giải ra **tập người đang quan sát** (`AreaOfInterestTracker.observersOf`) rồi
gọi `WorldOutboundSink.onCharacterAppearanceChanged(observerId, changedCharacterId)`.
`ConnectedCharacterRegistry` (tầng gateway) mới là chỗ tra `faction`/`costume_tier` — đúng ranh giới
đã ghi trong javadoc của `appearanceByCharacterId`: "world chỉ biết vị trí và chỉ số".

**D4.** **Chính người đổi KHÔNG nhận event này** — chỉ những người KHÁC đang quan sát. Model của
chính mình đã đổi từ trước qua `LocalCharacterAppearance.applyFaction`, gọi khi nhận
`SelectFactionResponse` (opcode 207) mà server vừa xác nhận. Gửi thêm cho họ vừa là gói thừa (đi
ngược ADR-042) vừa dựng lên **đường quyền lực thứ hai** cho cùng một việc. Đây là chỗ ADR này khác
`handleAreaChatMessage` — ở đó người gửi *phải* nhận lại tin của mình vì không có đường nào khác.

**D5.** Client **thay model tại chỗ**, giữ nguyên entity/vị trí/trạng thái động tác — không despawn,
không dựng lại entity, nên thế bất biến "spawn = tạo mới" ở D-context **vẫn nguyên vẹn**.

**D6.** Event đi qua **hàng đợi world** (`WorldFacade.enqueue...`), không gửi thẳng từ luồng auth:
`AreaOfInterestTracker` **không thread-safe ngoài tick thread** — đây đúng lý do
`AreaChatMessageCommand` tồn tại.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| A. Server gửi **despawn + spawn** cho mọi observer | Không đổi protocol; ít code nhất | Nhân vật **biến mất rồi hiện lại** — nháy một khung hình trên màn hình mọi người xung quanh; và mọi trạng thái client gắn với entity (nội suy vị trí, cờ chết) bị reset vô cớ | Không |
| B. Client cập nhật model khi nhận **spawn lặp** | Không đổi protocol | Phá thế bất biến "spawn = tạo mới" — chính thứ đang chặn entity trùng. Nới nó ra là mở đường cho một lớp lỗi khó tìm, để đổi lấy một tình huống hiếm | Không |
| **C. Event "ngoại hình vừa đổi" riêng** | Nói đúng thứ đang xảy ra; không nháy hình; không phá thế bất biến nào; additive thuần | Đổi protocol ⇒ phải có ADR (chính file này); thêm 1 opcode | **Chủ dự án chọn** |
| D. Để nguyên | 0 công | Đổi hệ phái — một trong số ít lựa chọn nhân vật của game — trông như không có tác dụng với đúng những người đang đứng cạnh | Không |

---

## Consequences

**Tích cực:** đóng dòng #8 của bảng "🔴 ĐANG CHỜ CHỦ DỰ ÁN"; đường ống này dùng lại được **nguyên
vẹn** cho mọi thứ đổi ngoại hình sau này (đổi `CostumeTier` khi thay giáp — thứ ADR-031 đã dự trù
mà chưa có ai phát).

**Đánh đổi:** thêm một opcode phải nuôi. Mỗi nguồn đổi ngoại hình mới sau này phải nhớ gọi — cùng
loại nợ mà ADR-043 đã ghi nhận cho `bonus_hp`.

**Module bị ảnh hưởng:** `shared-protocol` (1 opcode + 1 message), `server-world` (1 `WorldCommand`
+ 1 method trên `WorldOutboundSink`), `server-gateway` (1 handler nối vào `handleSelectFactionBlocking`
+ 1 method trên `ConnectedCharacterRegistry`), `client-core` (`RemoteCharacterPresenter` +
`WorldModelRenderer` + định tuyến opcode ở `WorldScreen`). **Không đổi DB.**

---

## Revisit Trigger

Khi có **nguồn thứ ba** đổi ngoại hình (ví dụ ngoại hình theo trang bị đang mặc, đổi mỗi lần
equip/unequip): lúc đó tần suất event tăng hẳn và phải cân lại với ADR-042 (đang muốn GIẢM số gói) —
có thể gộp vào một `CharacterStateChangedEvent` chung.

---

## Technical Decisions

- Ngoại hình **không** rơi xuống `server-world`; world chỉ trả lời câu hỏi "ai đang nhìn thấy ai".
- Chính người đổi KHÔNG nhận event (đường của họ đã có: opcode 207 → `LocalCharacterAppearance`).
- Không tăng `protocolVersion` (chỉ THÊM).

## ADR cần tạo

Không.

## Tài liệu liên quan

- [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) — vì sao `Faction` + `CostumeTier` là thứ quyết định model.
- [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) — tiền lệ "chỉ THÊM opcode, không tăng `protocolVersion`".
- [NETWORK.md §4](../../05-server/NETWORK.md) — bảng dải opcode.

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án Accept 2026-09-06.**
2. ✅ **Đã thi hành cùng ngày** — 1 opcode + 1 message, `WorldCommand.AppearanceChangedCommand`,
   `WorldOutboundSink.onCharacterAppearanceChanged`, `WorldModelRenderer.replaceCharacterModel`.
3. ✅ **Kiểm chứng đường DÂY bằng hai socket thật**:
   `GatewayServerIntegrationTest.observerShouldGetNewFaction_whenAnotherCharacterChangesItInPlace`
   — A đứng nhìn, B lên Lv10 rồi đổi sang Thuỷ, A nhận đúng opcode 307 mang `FACTION_THUY`.
4. ✅ **ĐÃ KIỂM CHỨNG SỐNG nửa CLIENT cùng ngày** (ROADMAP mục 62a): hai client song song, A đứng
   nhìn, B (Lv10) bấm **H** chọn **Thuỷ** ⇒ trên màn hình A, model của B **đổi tại chỗ**, không
   biến mất rồi hiện lại, không đổi vị trí. `character.faction` = `THUY` trong DB.
