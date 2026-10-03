# ADR-051: Một event "bạn vừa đổi World" chung cho mọi cú đổi World

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-24) — chủ dự án chọn **phương án A** (event chung 308) cùng ngày. **Đã thi hành cùng ngày** — xem "Việc cần làm tiếp theo" mục 2. |
| Date | 2026-09-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-047](ADR-047-Dungeon-Entered-Event.md) (phương án C + Revisit Trigger mà ADR này thi hành) · [ADR-026](ADR-026-Dungeon-Instance-Model.md) (hầm ngục = World riêng) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (khuôn "thêm event đẩy, `protocolVersion` không tăng") · [ADR-042](ADR-042-Outbound-Packet-Batching.md) (đang muốn GIẢM số gói) · [NETWORK.md §3, §5, §6](../../05-server/NETWORK.md) |

---

## Context

### Điều kiện mở lại của ADR-047 đã xảy ra

ADR-047 chọn event riêng `DungeonEnteredEvent` (1504) cho người bị kéo vào hầm ngục, và ghi Revisit
Trigger: *"Có **nguồn thứ hai** đưa người chơi sang World khác mà họ không tự bấm ⇒ cân lại phương án C
(một event 'đổi World' chung) thay vì mỗi nguồn một event."*

Ngày 2026-09-24 chủ dự án quyết: **chết trong hầm ngục ⇒ tự đưa về world chính** (đúng đường
`exitDungeon`: vị trí đã lưu lúc vào, chỉ số nền + bonus trang bị, sống và đầy máu như luật hồi sinh
của world chính). Đó chính là nguồn thứ hai. Người chơi không bấm gì, nên không có Response nào để
bám vào, và `dungeon.proto` không có event nào cho chiều RA.

### Nếu chỉ dời nhân vật phía server mà không báo

Client chỉ dọn thực thể của world cũ và đặt `inDungeon = false` khi nhận `ExitDungeonResponse` (trả lời
lệnh của chính nó) hoặc `DungeonEnteredEvent` (`DungeonCoordinator.settleExit` / `settlePulledIn`).
Không có gói nào thì:

1. Boss và đồng đội đứng ĐỌNG trên màn hình giữa làng (`AreaOfInterestTracker.onDetach` chỉ báo despawn
   cho người KHÁC, không cho chính người rời đi — hai khoảng trống còn mở ghi ở javadoc
   `DungeonController`).
2. Dải chữ "đang ở trong hầm ngục" và `inDungeon` vẫn bật; nút rời hầm ngục trả `NOT_IN_DUNGEON`.
3. Vị trí phía client sai tới lần di chuyển đầu tiên (`MoveResponse` chỉ là ack của `MoveCommand`).

Tệ hơn trạng thái hôm nay (nằm 0 máu nhưng client ít ra còn nhất quán).

### Khoảng trống này KHÔNG riêng hầm ngục

Đọc code (chưa kiểm bằng client thật): `DuelCoordinator` khi nhận `DuelStartedEvent`/`DuelResultEvent`
chỉ dọn trạng thái HUD trận đấu, **không** dọn thực thể của World cũ và không xin lại vị trí — trong
khi `ArenaInstanceRegistry` detach khỏi world chính rồi attach vào World đấu trường (và ngược lại lúc
chốt trận). Tức hai khoảng trống "thực thể đọng" + "vị trí sai" của `DungeonController` có mặt ở MỌI cú
đổi World, và mỗi nguồn đang (hoặc sẽ) tự vá theo cách riêng.

Thêm một khe thứ tự đã biết (ADR-047 "Phát hiện phụ"): người GỬI `EnterDungeonCommand` dọn màn hình khi
nhận Response, nhưng Response có thể tới SAU spawn của World mới ⇒ cú dọn xoá mất boss. Bản sửa "trả
lời sớm" đã bị chính test bác (Response là mốc happens-before cho lệnh kế tiếp). ADR-047 ghi: đóng nó
phải bằng *"lối vào chung của phương án C"*.

### Đã hỏi "có đường nào sẵn chưa"

- `CharacterRespawnedEvent` (305) có vị trí + máu, nhưng NGHĨA là "bạn vừa hồi sinh tại chỗ". Dùng nó để
  báo đổi World bắt client **suy ra** một nghĩa mà gói không nói — cùng họ lý do ADR-047 bác phương án
  A (một message mang hai nghĩa).
- Đẩy `ExitDungeonResponse` với `sequenceId = 0`: đúng phương án A mà ADR-047 đã bác.
- Không message nào khác mang nghĩa "bạn đang ở World khác".

---

## Decision

**Đề xuất:** thêm **một** event đẩy `WorldChangedEvent` (opcode **308**, dải 300–399 của di chuyển/
World), phát cho **đúng người bị đổi World**, **mọi** cú đổi World, ghi vào kênh **TRƯỚC** khi
`attachCharacter` vào World mới được xếp hàng (luật D3 của ADR-047). Client dọn màn hình và đặt lại vị
trí **chỉ** theo event này.

```protobuf
// opcode_registry.proto
OPCODE_WORLD_CHANGED_EVENT = 308;

// world.proto (hoặc movement.proto — nơi đang chứa 300–307)
enum WorldKind {
  WORLD_KIND_UNSPECIFIED = 0;
  WORLD_KIND_MAIN = 1;
  WORLD_KIND_DUNGEON = 2;
  WORLD_KIND_ARENA = 3;
}

enum WorldChangeReason {
  WORLD_CHANGE_REASON_UNSPECIFIED = 0;
  WORLD_CHANGE_REASON_REQUESTED = 1;      // chính người này bấm (vào/ra hầm ngục, chấp nhận đấu)
  WORLD_CHANGE_REASON_PULLED_BY_PARTY = 2; // bị bạn cùng nhóm kéo vào hầm ngục
  WORLD_CHANGE_REASON_DEFEATED = 3;       // chết trong hầm ngục ⇒ đưa về world chính
  WORLD_CHANGE_REASON_DUEL_ENDED = 4;     // trận đấu chốt xong ⇒ về world chính
}

message WorldChangedEvent {
  WorldKind world_kind = 1;
  WorldChangeReason reason = 2;
  float x = 3;              // vị trí spawn trong World mới — client đặt thẳng, bỏ mẹo resendDirection
  float y = 4;
  int32 current_hp = 5;     // có giá trị khi attach mới đặt lại máu (DEFEATED: đầy máu)
  int32 max_hp = 6;
}
```

**D1. Một nguồn sự thật cho việc dọn màn hình.** Client (`WorldScreen` + một coordinator nhỏ) dọn thực
thể, đặt vị trí, cập nhật máu và cờ "đang ở hầm ngục/đấu trường" **chỉ** khi nhận event này. Các
Response (`EnterDungeonResponse`, `ExitDungeonResponse`, `AcceptDuelResponse`…) lui về đúng nghĩa của
chúng: báo kết quả lệnh + dòng chữ. Khe thứ tự của người GỬI (ADR-047 "Phát hiện phụ") đóng theo: event
luôn đi trước spawn, dù Response tới lúc nào.

**D2. Thứ tự là quyết định, không phải chi tiết** (thừa kế ADR-047 D3): event ghi vào kênh TRƯỚC khi
`attachCharacter` của chính người đó được enqueue vào World mới, trên cùng luồng ghi (happens-before).
Detach khỏi World cũ đi trước event.

**D3. `protocolVersion` KHÔNG tăng** — chỉ THÊM opcode + message (NETWORK.md §6, tiền lệ ADR-035/045/047).

**D4. `DungeonEnteredEvent` (1504) giữ nguyên trên dây** để không phá client cũ đang chạy, nhưng client
mới thôi dùng nó để dọn màn hình (chỉ còn dòng báo "X đã đưa nhóm vào bí cảnh" — thông tin này
`reason = PULLED_BY_PARTY` cũng mang được, nên có thể ngừng phát 1504 ở một ADR sau, khi không còn
client cũ).

**D5. Chết trong hầm ngục** (quyết định của chủ dự án 2026-09-24) đi qua event này với
`reason = DEFEATED`: `DungeonOutboundSink` nhận `onCharacterDefeated` trên tick thread của hầm ngục ⇒
đẩy sang một executor Virtual Thread (KHÔNG đọc DB trên tick thread) ⇒ gọi đường `exitDungeon` /
`commitExit` có sẵn ⇒ phát event (x, y = vị trí lưu lúc vào; máu đầy) ⇒ attach world chính. Instance tự
dừng khi người cuối cùng rời (đã có).

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. `WorldChangedEvent` chung (308)** — ADR này | Một đường dọn duy nhất cho hầm ngục VÀO/RA/CHẾT + đấu trường VÀO/RA; đóng luôn khe thứ tự của người gửi (ADR-047) và hai khoảng trống "thực thể đọng"/"vị trí sai" của `DungeonController`; bỏ được mẹo `resendDirection`; nguồn đổi World thứ N sau này (dịch chuyển theo sự kiện, GM kéo) chỉ cần phát đúng event này | Phạm vi lớn nhất: đụng `DungeonInstanceRegistry`, `ArenaInstanceRegistry`, client `DungeonCoordinator` + `DuelCoordinator` + `WorldScreen`; 1 opcode + 3 kiểu (message + 2 enum) phải nuôi; trùng một phần nghĩa với 1504/`DuelStartedEvent` trong giai đoạn chuyển tiếp (D4) | **Khuyến nghị** — đúng Revisit Trigger ADR-047 đã đặt trước |
| **B. `DungeonExitedEvent` riêng (1505)** | Nhỏ nhất có tên đúng nghĩa; cùng khuôn 1504 | Đi ngược khuyến nghị Revisit Trigger của ADR-047 ("thay vì mỗi nguồn một event"); không đóng khe của người gửi, không chạm đấu trường; nguồn tiếp theo lại cần event tiếp theo | Không — rẻ hôm nay, nợ nhân lên theo số nguồn |
| **C. Mượn `CharacterRespawnedEvent` (305)** | 0 thay đổi protocol; ~1 file gateway + 1 file client | Client phải SUY RA "đổi World" từ một gói nói "hồi sinh" — cùng loại lỗi khiến ADR-047 bác phương án A; chỉ phủ đúng ca chết, không phủ ra/vào khác; nếu mai kia world chính có hồi sinh tại chỗ thì hai nghĩa va nhau | Không khuyến nghị; hợp lệ nếu chủ dự án muốn tránh đổi protocol bằng mọi giá |
| **D. Để nguyên** (chết trong hầm ngục nằm 0 máu tới khi tự thoát) | 0 công | Đi ngược quyết định của chủ dự án 2026-09-24 | Không |

---

## Consequences

**Tích cực:**
- Quyết định "chết trong hầm ngục ⇒ về world chính" thi hành được mà client vẫn nhất quán.
- Đóng khe thứ tự response-vs-spawn của người gửi (nợ ADR-047 chưa có đường sửa).
- Đấu trường có cùng cách dọn màn hình như hầm ngục thay vì không dọn gì.

**Đánh đổi phải chấp nhận:**
- Thêm 1 opcode (ngược hướng ADR-042 muốn giảm số gói — nhưng mỗi cú đổi World chỉ 1 gói, không phải
  gói theo tick).
- Giai đoạn chuyển tiếp có hai gói cùng báo một việc cho người bị kéo vào hầm ngục (1504 + 308) — D4.
- Mỗi nguồn đổi World mới phải nhớ phát event, và phát ĐÚNG THỨ TỰ (D2). Nên gom vào một chỗ phía
  gateway (ví dụ một hàm `moveToWorld(characterId, targetWorld, entry, reason)` dùng chung cho cả hai
  registry) để thứ tự không phải nhớ lại ở từng nơi.

**Module bị ảnh hưởng:** *(tra [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md))*
- `shared-protocol` — 1 opcode, 1 message, 2 enum.
- `server-gateway` — `DungeonInstanceRegistry` (vào/ra/chết), `ArenaInstanceRegistry` (vào/chốt trận),
  `DungeonOutboundSink` (nhận `onCharacterDefeated`), executor Virtual Thread cho đường chết.
- `client-core` — `DungeonCoordinator`, `DuelCoordinator`, `WorldScreen` (đường dọn dùng chung), khoá
  i18n cho dòng báo "bạn đã gục trong bí cảnh và được đưa về".
- **Không đổi DB. Không đụng `server-world`** (World vẫn không biết khái niệm "hầm ngục").

**Chi phí ước tính:** vừa — lớn hơn ADR-047 (hai registry + hai coordinator client), nhỏ hơn một lát
cắt tính năng. Test socket hầm ngục/đấu trường có sẵn làm hàng rào; cần thêm khẳng định THỨ TỰ (event
trước spawn) cho cả người gửi lẫn người bị đưa đi.

---

## Revisit Trigger

- ADR-042 (gộp gói) đi vào thi hành ⇒ xem event này có gộp chung khung với spawn đầu tiên của World mới
  được không.
- Có World thứ tư (khu vực/bản đồ mới người chơi đi bộ sang) ⇒ thêm giá trị `WorldKind`, không thêm event.
- Không còn client nào dùng 1504 để dọn màn hình ⇒ ngừng phát 1504 (ADR riêng, D4).

---

## Technical Decisions

- Nguồn thứ hai của Revisit Trigger ADR-047 đã xảy ra (chết trong hầm ngục, quyết định 2026-09-24).
- Khoảng trống "không có gói đổi World" có ở cả đấu trường (đọc code, chưa kiểm bằng client thật).
- Thứ tự "báo trước, attach sau" (ADR-047 D3) là ràng buộc bắt buộc của mọi phương án có dọn màn hình.

## ADR cần tạo

Không (ngừng phát 1504 — D4 — sẽ cần ADR riêng khi tới lúc).

## Tài liệu liên quan

- [ADR-047](ADR-047-Dungeon-Entered-Event.md) — phương án C, "Phát hiện phụ", Revisit Trigger.
- [NETWORK.md §3, §5, §6](../../05-server/NETWORK.md) — Command/Response/Event, khớp `sequenceId`, versioning.
- `client/client-core/.../world/DungeonController.java` — javadoc liệt kê các khoảng trống của `dungeon.proto`.
- `server/server-gateway/.../DungeonInstanceRegistry.java`, `ArenaInstanceRegistry.java` — hai nơi đổi World hôm nay.

## Việc cần làm tiếp theo

1. ✅ **XONG** — chủ dự án chọn **A** (2026-09-24).
2. ✅ **ĐÃ THI HÀNH 2026-09-24** theo D1–D5, không lệch Decision:
   - `shared-protocol`: opcode **308** + `WorldChangedEvent` + `WorldKind` + `WorldChangeReason` trong
     `movement.proto` (cạnh 300–307), `protocolVersion` KHÔNG tăng.
   - `server-gateway`: `WorldTransfer` là chỗ DUY NHẤT giữ thứ tự D2 (detach → event → attach), dùng
     chung cho `DungeonInstanceRegistry` (vào: `REQUESTED` cho người bấm, `PULLED_BY_PARTY` cho người bị
     kéo — 1504 vẫn phát, D4; ra: `REQUESTED`; gục: `DEFEATED`) và `ArenaInstanceRegistry` (vào trận:
     `REQUESTED` cho CẢ HAI người — người thách chính là người đã xin trận; chốt trận mọi đường — bỏ
     cuộc, bị hạ gục, đối thủ rớt mạng: `DUEL_ENDED`). D5: `DungeonOutboundSink.onCharacterDefeated`
     (tick thread) ⇒ `DungeonInstanceRegistry.handleCharacterDefeated` ⇒ executor Virtual Thread ⇒ đường
     `exitDungeon`/`commitExit` có sẵn. `current_hp = max_hp` = máu mà lệnh attach đặt; hầm ngục/đấu
     trường attach KHÔNG kèm bonus trang bị (giới hạn có sẵn) nên `max_hp` ở đó là máu nền.
     **Đã sửa 2026-09-29**: mọi đường attach của gateway (chọn nhân vật, vào/ra hầm ngục, vào/chốt
     trận) nay đi qua `CharacterLoadout` (chỉ số nền + bonus trang bị) ⇒ `max_hp` trong event là máu
     tối đa HIỆU LỰC, và thực thể trong hầm ngục/đấu trường mang đủ bonus. Test:
     `EquipmentAcrossWorldTransferTest`.
   - `client-core`: `WorldChangeCoordinator` là lối vào duy nhất; `WorldScreen` dọn thực thể + đặt vị
     trí/máu theo event; Response vào/ra hầm ngục và 1504 KHÔNG còn dọn màn hình; mẹo
     `resendDirection` đã xoá; dòng báo mới `dungeon.notice.defeated`.
   - Test: `WorldChangedEventOrderTest` khẳng định event tới TRƯỚC spawn đầu tiên cho người bấm vào,
     người bị kéo, cả hai người vào trận; và TRƯỚC spawn của world chính khi gục trong hầm ngục/chốt trận.
     Với hành vi cũ (lời báo gục rơi vào hàm rỗng), test gục-trong-hầm-ngục đỏ: nhân vật vẫn ở trong sau
     10 giây chờ.
3. Kiểm chứng sống bằng client thật: gục trong bí cảnh ⇒ màn hình về làng, không còn boss đọng, vạch máu đầy.
   - ⚠️ *(Ghi chú 2026-10-03)* Lượt kiểm sống nhánh **đấu trường** (2026-09-25, hai `gameProbe`, ba cách
     chốt trận — hạ gục / bỏ cuộc / rớt mạng — đều PASS) **chưa phủ đường client THẬT**: probe đánh
     đối thủ bằng cách gửi thẳng id nhân vật (`--attackTarget=<characterId>`), còn client desktop chỉ
     đặt mục tiêu là QUÁI (`CombatHudController.selectTarget` chỉ nhận id trong bảng quái; click vào
     người chơi đi sang `DuelCoordinator.onCharacterPicked` — chọn để thách đấu, không gửi
     `SetTargetCommand`). Tức là với client thật, **vào được trận nhưng không đánh được đối thủ**; kết
     luận PASS đúng cho thứ tự 308/spawn và đường chốt trận phía server, không cho trải nghiệm trận
     đấu. Hướng sửa (phía client, không đổi protocol) đang chờ chủ dự án — dòng Arena ở
     ["🔴 ĐANG CHỜ CHỦ DỰ ÁN"](../../README.md).
