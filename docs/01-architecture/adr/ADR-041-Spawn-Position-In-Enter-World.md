# ADR-041: Server gửi vị trí lúc vào world — bỏ giả định `(0,0)` của client

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-09-05) — chờ chủ dự án duyệt D1–D4. AI **không** tự Accept. |
| Date | 2026-09-05 |
| Deciders | Chủ dự án quyết; AI phát hiện khi playtest + soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (tiền lệ THÊM field vào chính `SelectCharacterResponse`, không tăng `protocolVersion`) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (khoá di chuyển vào trục X) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (khuôn "lấp khoảng trống đồng bộ trạng thái client") · [ADR-040](ADR-040-NPC-Template-And-Placement.md) (NPC ở `(-5, 0)` — hàng đối chứng của phát hiện này) · [KAGE-CONSTITUTION Điều 24](../../00-governance/KAGE-CONSTITUTION.md) (Server Authority) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi protocol ⇒ dừng, viết ADR) |

---

## Context

### Triệu chứng, quan sát tận mắt trong playtest 2026-09-05

Đặt `character.pos_x` cho cùng một nhân vật lần lượt bằng **8**, **−3**, **−5** rồi đăng nhập lại
mỗi lần. Cả ba lần, khung hình vừa vào world **giống nhau từng pixel**: cùng dải cỏ, cùng khối
ô-liu bên phải, và trưởng lão Chu Hoành luôn nằm ở sát mép trái màn hình.

Bấm **một** phát phím `D` trong 250 ms thì khung hình **nhảy** một đoạn dài, và Chu Hoành hiện ra
ngay cạnh nhân vật — đúng chỗ `npc_spawn` ghi (`-5, 0`). Không phải lỗi dựng hình: mọi thứ vẽ đúng,
chỉ là camera đang đứng nhầm chỗ cho tới khi có gói `MoveResponse` đầu tiên.

Hệ quả kèm theo, cũng quan sát được: lúc đứng ở `x=8` **đè lên** một mộc nhân, bảng mục tiêu hiện
"Mộc nhân luyện công 50/50" và đánh trúng được nó, **nhưng trên màn hình không thấy con quái nào**.
Quái được vẽ ở toạ độ thế giới thật (`x=8`), còn camera thì ở `0`, nên nó nằm ngoài khung.

### Nguyên nhân gốc: một giả định đúng-lúc-viết đã mục mà không ai gỡ

`WorldScreen#spawnLocalPlayer()` tạo `new PositionComponent()` — mặc định `(0, 0, 0)`. Javadoc ngay
trên nó nói rõ vì sao, và nói thật:

> *"Spawn (0,0) khớp mặc định `server-world.WorldConstants.SPAWN_X/Y` — **chưa có persistence vị
> trí** (server-world follow-up), `MoveResponse` đầu tiên sẽ tự sửa nếu lệch."*

Lúc viết, câu đó **đúng**: chưa lưu vị trí thì mọi nhân vật vào world đều ở `(0,0)`, nên client đoán
`(0,0)` không bao giờ sai. Persistence vị trí sau đó đã có thật —
`GatewayChannelHandler#handleSelectCharacterBlocking` gọi `characterFacade.loadPosition(characterId)`
rồi `attachCharacter(characterId, spawn.x(), spawn.y(), ...)`. Tiền đề của giả định biến mất, nhưng
giả định thì ở lại. Cái van an toàn nó tự đặt ("`MoveResponse` đầu tiên sẽ tự sửa") vẫn chạy đúng —
đó chính là lý do lỗi sống lâu: nó **tự lành ngay khi người chơi bấm phím đi**, nên không ai bắt gặp
trừ khi đứng yên nhìn kỹ.

### Vì sao đây là vi phạm Server Authority, không chỉ là lỗi thẩm mỹ

Server **đang cầm** con số đúng, ngay trong cùng một hàm, cách chỗ dựng `SelectCharacterResponse`
đúng 5 dòng — rồi không gửi. `SelectCharacterResponse` hiện có `success`, `error_key`, `current_hp`,
`max_hp`; `sendInitialSelfState` gửi thêm kinh nghiệm và vàng. **Không gói nào mang vị trí.**

Nên client buộc phải *bịa* một mẩu trạng thái gameplay mà server sở hữu. Đây đúng khuôn lỗi mà
[ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) đã sửa cho **máu tối đa** (client tự
biết "100" ⇒ vạch máu `139/100`), và cùng khuôn với 4 khoảng trống mà
[ADR-035](ADR-035-Client-State-Sync-Opcodes.md) đã lấp. Đây là cái thứ năm cùng họ.

### Ai chịu ảnh hưởng thật

Mọi người chơi **đăng nhập lại** ở bất cứ đâu không phải `(0,0)` — tức gần như mọi người chơi thật,
vì vị trí được lưu lúc thoát. Trong Closed Beta, ấn tượng đầu tiên mỗi phiên là "thế giới sai chỗ,
quái vô hình", tự sửa sau phím đi đầu tiên. Không mất dữ liệu, không sai kết quả gameplay (server
vẫn phán quyết đúng), nhưng phá vỡ tin cậy vào những gì màn hình đang nói.

---

## Decision

**D1.** THÊM hai field vào `SelectCharacterResponse`: `float spawn_x = 5;`, `float spawn_y = 6;` —
server điền từ chính `PositionSnapshot` nó vừa dùng để `attachCharacter`, tức **một nguồn chân lý**
cho cả world lẫn client.

**D2.** Client nạp hai số đó vào `PositionComponent` của `localPlayer` **trước** khi frame đầu tiên
được vẽ, và xoá javadoc giả định `(0,0)`.

**D3.** THÊM, không đổi: field 1–4 giữ nguyên, `protocolVersion` **KHÔNG** tăng (luật additive
[NETWORK.md §6](../../04-network/NETWORK.md)) — đúng tiền lệ ADR-036 đã làm với `current_hp`/`max_hp`
trên cùng message này. Client cũ vẫn chạy y như hôm nay (vẫn sai chỗ tới lần đi đầu tiên); server cũ
gặp client mới thì hai field bằng `0` ⇒ hành vi bằng đúng hôm nay, không tệ hơn.

**D4.** KHÔNG đụng `z`/`grounded`. Trục dọc lúc vào world luôn là mặt đất (`z=0`, `grounded=true`)
theo [ADR-033](ADR-033-Ninja-School-Style-Movement.md); thêm hai field nữa chỉ để mang hằng số là
mở rộng bề mặt protocol không đổi lấy gì.

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. THÊM `spawn_x`/`spawn_y` vào `SelectCharacterResponse`** (chọn) | Server phát ra con số nó đã cầm sẵn; đúng Điều 24; additive nên không tăng `protocolVersion`; cùng khuôn ADR-036 trên **chính message này** | Bề mặt protocol rộng thêm 2 field | **Chọn.** Rẻ nhất, đúng nhất, có tiền lệ đã duyệt ngay tại chỗ |
| B. Client tự gửi một `MoveCommand` delta-0 ngay khi vào world để ép server trả `MoveResponse` | Không đụng protocol | Client bịa ra một input người chơi không hề bấm; đua với gói AoI đầu tiên; vẫn nháy sai một hoặc vài frame; thêm một lệnh giả vào bộ đếm rate-limit | Không chọn — chữa triệu chứng bằng cách nói dối server |
| C. Client hoãn vẽ world tới khi có `MoveResponse` đầu tiên | Không đụng protocol | `MoveResponse` **chỉ** đến khi người chơi bấm phím đi ⇒ ai đứng yên sẽ nhìn màn hình trống vô thời hạn. Tệ hơn hiện tại | Không chọn |
| D. Opcode `SELF_POSITION_SYNC` riêng | Tách bạch trách nhiệm | Thêm một opcode + một vòng gửi cho thứ chỉ dùng đúng một lần, trong khi đã có sẵn một message "vào world" đúng nghĩa | Không chọn — ADR-035 đã tiêu 7 opcode cho các khoảng trống *thật sự* cần opcode riêng; cái này không cần |

---

## Consequences

**Tích cực:**
- Vào world là thấy đúng chỗ mình đứng, ngay frame đầu, không cần bấm gì.
- Quái/NPC trong tầm AoI hiện ra ngay thay vì "vô hình cho tới khi đi một bước".
- Gỡ được một mẩu trạng thái gameplay do client tự bịa (Điều 24).
- Xoá một javadoc đang nói sai về hệ thống — thứ đắt hơn cả bản thân con bug.

**Đánh đổi phải chấp nhận:**
- `SelectCharacterResponse` phình từ 4 lên 6 field. Chấp nhận: đúng nơi cần, đúng luật additive.
- Không sửa gì cho **nhân vật khác** trong AoI — họ vẫn vào đúng chỗ vì `CharacterSpawnedEvent` vốn
  đã mang toạ độ. Chỉ CHÍNH mình là thiếu, vì server cố ý không gửi `CharacterSpawnedEvent` cho
  chính mình (đã ghi trong javadoc `WorldScreen`).

**Module bị ảnh hưởng:**
- `shared/shared-protocol` — `character.proto`.
- `server/server-gateway` — `GatewayChannelHandler#enterWorldResponse` (phải nhận thêm
  `PositionSnapshot`; hàm hiện là `static` một tham số).
- `client/client-core` — `WorldScreen#spawnLocalPlayer` + javadoc.

---

## Revisit Trigger

Khi thế giới có **nhiều world/instance** mà điểm vào không suy ra được từ `character.pos_x` (ví dụ
đăng nhập lại trong lúc đang ở dungeon/arena — hôm nay `DungeonInstanceRegistry`/`ArenaInstanceRegistry`
dùng hằng số điểm vào riêng). Lúc đó `spawn_x/spawn_y` phải đi kèm định danh world, và cần xem lại
ADR này.

---

## Technical Decisions

- Additive protocol, không tăng `protocolVersion` — theo NETWORK.md §6 và tiền lệ ADR-036.
- Nguồn chân lý là `PositionSnapshot` mà `handleSelectCharacterBlocking` đã nạp; **không** đọc DB
  lần hai (một lần đọc, hai chỗ dùng).
- Không mở rộng sang `z`/`grounded` (D4).

## ADR cần tạo

Không có. ADR này tự đóng phạm vi của nó.

## Tài liệu liên quan

- [NETWORK.md §6](../../04-network/NETWORK.md) — luật thêm field không tăng version.
- [SERVER.md §6](../../05-server/SERVER.md) — AoI và luật "không gửi event cho chính mình".
- [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) — cùng message, cùng khuôn sửa.

## Việc cần làm tiếp theo

1. **Chủ dự án duyệt D1–D4.** Câu hỏi cụ thể duy nhất: có muốn gộp luôn `z`/`grounded` (bác D4) để
   khỏi phải mở lại message này lần nữa không? Tôi khuyên **không** — hôm nay chúng luôn là hằng số.
2. Sau khi Accept: sửa proto → server → client, kèm test tái hiện (vào world ở `x≠0`, assert
   `PositionComponent` của `localPlayer` bằng đúng vị trí server, **trước** mọi input).
3. Playtest lại đúng kịch bản đã bắt được lỗi: đặt `pos_x` ba giá trị khác nhau, đăng nhập, chụp ảnh
   — ba khung hình phải KHÁC nhau.
