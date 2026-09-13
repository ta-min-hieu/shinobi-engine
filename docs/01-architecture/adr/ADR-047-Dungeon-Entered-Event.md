# ADR-047: Người bị kéo vào hầm ngục theo nhóm phải được báo

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-13) — chủ dự án chọn **phương án B**, thi hành ngay cùng ngày: opcode **1504** `DungeonEnteredEvent`, `protocolVersion` KHÔNG tăng. `@Disabled` đã gỡ và test có thêm khẳng định THỨ TỰ (D3). ⚠️ Mục **"Phát hiện phụ"** (khe thứ tự của người GỬI) đã dựng thử và **BỊ TEST BÁC** — xem cuối mục đó. |
| Date | 2026-09-11 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-026](ADR-026-Dungeon-Instance-Model.md) (instance hầm ngục = World riêng cùng process) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (cùng họ: server đúng, client không được báo ⇒ thêm một event đẩy) · [ADR-042](ADR-042-Outbound-Packet-Batching.md) (đang muốn GIẢM số gói) · [NETWORK.md §3, §5](../../05-server/NETWORK.md) (Command/Response/Event, khớp `sequenceId`) · [GAMEPLAY.md §6](../../05-server/GAMEPLAY.md) ("mỗi party 1 instance riêng") |

---

## Context

### Triệu chứng

Nợ ghi từ bàn giao 2026-08-30, chưa ai sửa. Một thành viên nhóm (A) gửi `EnterDungeonCommand` (1500).
`DungeonInstanceRegistry.enterDungeon` dời **mọi** thành viên đang online — kể cả B, người không bấm gì —
sang World hầm ngục. Nhưng `GatewayChannelHandler.handleEnterDungeonBlocking` chỉ trả
`EnterDungeonResponse` (1501) cho **người gửi**:

| Ai | Server làm gì | Client nhận gì |
|---|---|---|
| A (người bấm) | detach world chính → attach hầm ngục ở `(0,0)` | ✅ `EnterDungeonResponse(OK)` ⇒ dải chữ HUD, dọn thực thể cũ, xin lại vị trí |
| B (bị kéo theo) | **y hệt A** | ❌ **không một gói nào thuộc dải Dungeon** — chỉ có spawn/move/đòn đánh của World mới đổ về |

**Đã tái hiện qua socket thật**: `GatewayServerIntegrationTest.pulledPartyMemberShouldBeToldTheyEnteredDungeon_overRealSockets`
— B nhận `MonsterSpawnedEvent("sample_boss")` (bằng chứng server đã dời B, world chính không có boss) mà
không nhận gói nào trong dải 1500–1599. Test đang `@Disabled("Chờ ADR-047 …")`.

Lần chạy đỏ 2026-09-11 (chưa gắn `@Disabled`), đúng thứ tự B nhận:

```
208 209            ExperienceChanged, GoldChanged   — trạng thái ban đầu lúc vào world
303 402 402 600    CharacterSpawned(A), 2× MonsterSpawned, NpcSpawned — nhìn thấy làng
304                CharacterDespawned(A)             — A rời world chính
303 402            CharacterSpawned(A), MonsterSpawned(sample_boss) — B ĐÃ ở trong hầm ngục
404… 401…          MonsterMoved của boss, AttackResult — trận đánh đã bắt đầu
```

Không có một opcode 15xx nào. Nhìn từ màn hình của B: A biến mất rồi hiện lại, một con boss mọc ra
giữa làng và bắt đầu đánh.

### Hậu quả thật với người chơi B

Đọc từ code client (`DungeonCoordinator`, `WorldScreen`, các `*Presenter`) — **chưa** kiểm chứng bằng
client thật, chỉ bằng đường dây:

1. **Không biết mình đang ở trong hầm ngục.** `DungeonController.inDungeon` = `false` ⇒ không có dải chữ
   thường trực "đang ở trong hầm ngục / thoát bằng khung Nhóm", không có dòng báo trong nhật ký chat.
   Nút Rời hầm ngục thì *có* (khung Nhóm luôn bày cả hai nút) nhưng B không có lý do gì để bấm.
2. **Làng vẫn nằm nguyên trên màn hình.** `AreaOfInterestTracker.onDetach` chỉ báo despawn cho
   *người khác*; người rời không nhận despawn của thứ gì. Với A, `DungeonCoordinator#setOnWorldChanged`
   dọn bù; với B, lời gọi đó **không bao giờ chạy** ⇒ hình nộm, trưởng lão, tán tu, người chơi khác của
   làng đứng đông cứng giữa hầm ngục, lẫn với boss và A. Bấm vào chúng: lệnh đi vào World hầm ngục, nơi
   id đó không tồn tại.
3. **Vị trí sai tới lần di chuyển đầu tiên.** Server đặt B ở `(0,0)` của instance; client B vẫn tin B
   đứng ở toạ độ làng. `MoveResponse` chỉ là ack của `MoveCommand`, nên B đứng yên thì không bao giờ
   được sửa. Boss spawn **trong tầm aggro của điểm vào** (xem javadoc `ArenaInstanceRegistry`) ⇒ boss lao
   vào đánh một B mà trên màn hình của chính B đang đứng ở chỗ khác.
4. **Có thể trùng id với boss** *(suy từ code, chưa thấy tận mắt)*: mỗi `WorldFacade` cấp id quái/NPC
   bằng bộ đếm riêng bắt đầu từ `-1` (`nextMonsterOrNpcId`), world chính spawn quái trước NPC ⇒ quái ở
   điểm spawn đầu tiên của làng là `-1`, và boss của một hầm ngục mới cũng là `-1`. Nếu con `-1` của
   làng đang nằm trong tầm nhìn của B lúc bị kéo, `WorldModelRenderer.addMonsterOrNpc(-1, …)` thấy id đã
   có và **giữ model cũ** ⇒ B thấy con quái làng đứng ở chỗ boss, mang máu của boss.

### Các đường rời hầm ngục khác — đã kiểm, KHÔNG có cùng lỗi

| Đường | Ai đổi World | Có cần gói báo? | Gác bằng test |
|---|---|---|---|
| `ExitDungeonCommand` | chỉ người gửi | Không — người gửi đã nhận `ExitDungeonResponse` | `partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets` (có sẵn) |
| Mất kết nối bên trong | người mất kết nối (không còn ai để báo) | Không — người ở lại không đổi World, họ nhận `CharacterDespawnedEvent` bình thường | `remainingMemberShouldSeeDespawnAndStillExit_whenPartyMemberDisconnectsInsideDungeon_overRealSockets` (mới) |
| Rời nhóm / giải tán nhóm khi đang ở trong | không ai | Không — `exitDungeon` tra theo nhân vật, người rời nhóm vẫn tự thoát được | `memberShouldStillExitDungeon_afterLeavingPartyInside_overRealSockets` (mới) |
| Boss chết | không ai (không có tự đẩy ra) | Không | `partyShouldFightBossInDungeon_overRealSockets` (có sẵn) |
| Server tắt | mọi người | Đã có `ServerShuttingDownEvent` | — |

⇒ Khoảng trống **chỉ** nằm ở chiều **vào**, và **chỉ** với người bị kéo theo.

### Đã hỏi "có đường nào sẵn chưa" — không có đường nào sạch

- `dungeon.proto` (dải 1500–1599) chỉ có đúng 2 cặp Command/Response, **không có một `*Event` nào**.
- Không message nào khác mang nghĩa "bạn vừa đổi World": `CharacterRespawnedEvent` có vị trí nhưng nghĩa
  là "bạn vừa hồi sinh" (client đặt lại máu đầy); `SelectCharacterResponse` là response của một request
  khác. Mượn chúng là đổi nghĩa message đang chạy.
- **Chưa từng có tiền lệ đẩy Response không ai hỏi**: `grep 'RESPONSE_VALUE, 0,'` trong
  `server-gateway/src/main` = 0 kết quả. Mọi gói `sequenceId = 0` hiện nay đều là `*Event`.
- **Có tiền lệ gần nhất, và nó chọn event**: Arena gặp đúng bài toán này (người thách đấu không gửi lệnh
  gì lúc đối thủ chấp nhận) và giải bằng `DuelStartedEvent` đẩy cho **cả hai** người
  (`handleAcceptDuelBlocking`, `arena.proto`).

---

## Decision

✅ **Chủ dự án chọn phương án B (2026-09-13).** Mọi mục D1–D4 dưới đây là quyết định đã chốt và
đã thi hành.

**Phương án B** — thêm **một** event đẩy trong dải Dungeon, cùng khuôn `DuelStartedEvent`:

```protobuf
// opcode_registry.proto
OPCODE_DUNGEON_ENTERED_EVENT = 1504;

// dungeon.proto
// Đẩy cho thành viên bị kéo vào hầm ngục theo nhóm — người GỬI EnterDungeonCommand đã có
// EnterDungeonResponse nên KHÔNG nhận event này (cùng tinh thần ADR-045 D4).
message DungeonEnteredEvent {
  string dungeon_code = 1;
  int64 initiator_character_id = 2; // ai bấm — để client nói được "X đã đưa nhóm vào hầm ngục"
}
```

**D1.** Chỉ **người bị kéo theo** nhận event; người gửi giữ nguyên `EnterDungeonResponse`. Không có
đường quyền lực thứ hai cho người gửi.

**D2.** `protocolVersion` **KHÔNG tăng** — chỉ THÊM opcode + message (NETWORK.md §6, tiền lệ ADR-035/045).

**D3. Thứ tự là một phần của quyết định, không phải chi tiết thi hành.** Client dọn sạch thực thể khi
nhận lời báo (đường `onWorldChanged` có sẵn). Nếu lời báo tới **sau** spawn của World mới, cú dọn xoá
luôn boss và đồng đội vừa nhận — và server sẽ **không** phát lại (tập "đang thấy" phía server đã có họ)
⇒ B không bao giờ thấy boss. Vì vậy event phải được **ghi vào kênh của B TRƯỚC khi** `attachCharacter`
của B được xếp vào hộp thư World hầm ngục (ghi từ luồng auth rồi mới enqueue ⇒ happens-before ⇒ hàng
đợi task của EventLoop giữ đúng thứ tự). Test tái hiện ở trên đã được viết để chấp nhận cả hai thứ tự
(nó chỉ hỏi "có tới không"); lúc thi hành phải thêm một khẳng định thứ tự.

**D4.** Client: `DungeonCoordinator.dispatch` nhận opcode 1504 và đi **đúng** đường `settleEnter(OK)` hiện
có (ghi `inDungeon`, dải chữ HUD, dọn thực thể, `resendDirection` để xin lại vị trí) — chỉ khác dòng báo
trong chat (khoá i18n mới, ví dụ `dungeon.notice.pulled_in`).

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A.** Đẩy `EnterDungeonResponse(OK)` với `sequenceId = 0` cho người bị kéo | 0 opcode mới; client hiện tại **chạy đúng ngay** không sửa dòng nào (`DungeonCoordinator` định tuyến theo opcode, không nhìn `sequenceId`) | **Đổi ngữ nghĩa một message đang chạy**: Response theo NETWORK.md §3/§5 là *câu trả lời cho một Command*, khớp bằng `sequenceId`. Đẩy nó không ai hỏi là tiền lệ đầu tiên của cả dự án, và `OK` sẽ mang hai nghĩa ("lệnh của bạn thành công" / "người khác đưa bạn vào"). Client nào khớp theo `sequenceId` (`FactionCoordinator` đã làm vậy cho opcode 207) sẽ hiểu sai. Vẫn cần ADR — chỉ rẻ hơn về code, không rẻ hơn về quy trình | Không khuyến nghị — rẻ hôm nay, đắt khi có client khớp `sequenceId` thật |
| **B.** Event riêng `DungeonEnteredEvent` (1504) | Nói đúng thứ đang xảy ra; additive thuần; đúng tiền lệ Arena (`DuelStartedEvent`) và ADR-045; mang được `initiator_character_id` | 1 opcode + 1 message phải nuôi; client phải thêm 1 nhánh định tuyến + 1 khoá i18n | **Khuyến nghị** |
| **C.** Event chung "bạn vừa đổi World" (dải 300–399) cho MỌI cú đổi World (hầm ngục, đấu trường, đổi khu vực), mang loại instance + vị trí spawn | Giải luôn hai khoảng trống còn lại của `DungeonController` (thực thể đọng, vị trí) cho CẢ người gửi lẫn người bị kéo; bỏ được mẹo `resendDirection` | Phạm vi lớn hơn hẳn nợ đang xử lý; đụng Arena + `MainWorldChannelRegistry`; ngữ nghĩa trùng một phần với `EnterDungeonResponse`/`DuelStartedEvent` đang chạy | Không cho lần này — ghi thành Revisit Trigger |
| **D.** Server tự gửi despawn mọi thứ người rời đang thấy (`AreaOfInterestTracker.onDetach` báo cả cho chính họ) | **Không đổi protocol** | Chỉ vá hậu quả #2 (thực thể đọng); #1 (không biết mình ở đâu) và #3 (vị trí) còn nguyên. Thêm gói (ngược ADR-042). Hai World khác luồng tick ⇒ despawn của world cũ có thể tới SAU spawn của world mới, và với id trùng `-1` (hậu quả #4) nó sẽ **xoá nhầm boss** | Không — vá một nửa và mở ra lỗi thứ tự mới |
| **E.** Bỏ "kéo theo cả nhóm": `EnterDungeonCommand` chỉ đưa **người gửi** vào instance của nhóm mình; ai muốn vào thì tự bấm | **Không đổi protocol**; mỗi người vào đều tự nhận `EnterDungeonResponse` ⇒ lỗi biến mất từ gốc; code server GỌN hơn | **Đổi luật chơi** (GAMEPLAY.md §6 vẫn đúng — "mỗi party 1 instance" — nhưng cảm giác "cả nhóm cùng vào" mất); `PARTY_MEMBER_OFFLINE` mất nghĩa; boss đã spawn và có thể đánh người vào trước khi đồng đội kịp bấm | Phương án hợp lệ duy nhất **không cần** đổi protocol — nhưng là quyết định thiết kế game, chỉ chủ dự án chọn được |
| **F.** Để nguyên | 0 công | Mọi lần vào hầm ngục theo nhóm, mọi người trừ một đều chơi trên một màn hình sai | Không |

---

## Consequences

**Tích cực (nếu chọn B):** đóng nợ 2026-08-30; `inDungeon()` của client thôi là "chỉ đúng cho người vừa
bấm"; dựng xong chỗ để hầm ngục về sau (nhiều hầm ngục, phần thưởng) có kênh báo có sẵn.

**Đánh đổi:** thêm 1 opcode. Mỗi nguồn "đưa người khác vào instance" mới sau này phải nhớ phát event
này — cùng loại nợ ADR-043/045 đã ghi.

**Module bị ảnh hưởng (phương án B):** `shared-protocol` (1 opcode + 1 message), `server-gateway`
(`DungeonInstanceRegistry.enterDungeon` phải báo cho gateway *ai* bị kéo theo và báo **trước** khi
attach — D3; `handleEnterDungeonBlocking` gửi event qua `ConnectedCharacterRegistry.sendToCharacterIfOnline`),
`client-core` (`DungeonCoordinator` + 1 khoá i18n vi/en). **Không đổi DB. Không đụng `server-world`.**

**Chi phí ước tính:** nhỏ — cùng cỡ ADR-045. Test tái hiện đã có sẵn, chỉ cần bỏ `@Disabled` + thêm
khẳng định thứ tự (D3).

### Phát hiện phụ — người GỬI cũng có một khe thứ tự (không đổi protocol, chưa sửa)

`DungeonCoordinator` dựa vào giả định "response tới **trước** entity của World mới". Giả định đó
**không được bảo đảm**: `enterDungeon` dời từng thành viên theo thứ tự `listMembers` (trưởng nhóm trước),
và **sau khi đã attach người gửi** vẫn còn đọc DB (`loadStats`, `findById`, có thể `loadPosition`) cho
những thành viên còn lại, rồi mới trả response. Nếu vòng `sync` 20Hz của World hầm ngục chạy lọt vào
khoảng đó, boss/đồng đội tới trước response ⇒ cú dọn của client xoá họ ⇒ người gửi không thấy boss.
Test socket không bắt được vì fake DB trả ngay lập tức; với Postgres thật, cửa sổ là vài ms mỗi thành
viên so với chu kỳ 50ms của `sync`.

> ⛔ **BẢN SỬA ĐỀ XUẤT Ở TRÊN LÀ SAI — đã dựng thử 2026-09-13 và bị chính test bác.**
>
> Câu *"xác định outcome xong thì gửi response, rồi mới dời người"* nghe hợp lý nhưng hỏng: tách
> `enterDungeon` thành `decideEnter` + `commitEnter` rồi gửi response ở giữa làm **HAI test có sẵn đỏ
> ngay**:
>
> | Test | Mong đợi | Nhận được |
> |---|---|---|
> | `partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets` | lệnh vào THỨ HAI (của B, người vừa bị kéo) trả `ALREADY_IN_DUNGEON` | `OK` |
> | `memberShouldStillExitDungeon_afterLeavingPartyInside_overRealSockets` | lệnh RA ngay sau đó trả `OK` | `NOT_IN_DUNGEON` |
>
> **Nguyên nhân chung, và nó là một luật chứ không phải một lỗi vặt:** `Response` không chỉ là lời
> đáp cho lệnh vừa rồi, nó còn là **mốc happens-before cho lệnh TIẾP THEO của client**. Client đọc
> response xong là gửi lệnh kế tiếp ngay; lệnh đó chạy trên `authExecutor` và có thể vượt lên trước
> phần `commit` còn đang chạy, nên nó đọc phải trạng thái CŨ. Trả lời sớm = nói dối về một trạng thái
> chưa tồn tại.
>
> ⇒ **Giữ nguyên thứ tự "dời người xong mới trả lời".** Khe response-vs-spawn của người GỬI vẫn còn
> (nợ cũ, chưa chặn gì, chưa ai thấy nó xảy ra thật). Đóng nó phải bằng cách KHÁC — ví dụ để client
> tự xin lại trạng thái sau khi đổi World thay vì dọn mù, hoặc lối vào chung của phương án C. Cần
> một lượt thiết kế riêng, không phải một cú đảo hai dòng.

---

## Revisit Trigger

- Có **nguồn thứ hai** đưa người chơi sang World khác mà họ không tự bấm (ví dụ dịch chuyển theo sự
  kiện, bị GM kéo) ⇒ cân lại phương án C (một event "đổi World" chung) thay vì mỗi nguồn một event.
- ADR-042 đi vào thi hành và gộp các event nhỏ ⇒ xem event này có gộp được không.

---

## Technical Decisions

- Nợ có thật và đã tái hiện qua socket thật; chỉ chiều **vào** + người **bị kéo theo** bị ảnh hưởng.
- Không đường nào sẵn có nói được "bạn vừa bị đưa vào hầm ngục" mà không đổi nghĩa một message đang chạy.
- Thứ tự "báo trước, attach sau" là ràng buộc bắt buộc của mọi phương án có dọn màn hình phía client.

## ADR cần tạo

Không (phương án C, nếu có ngày chọn, sẽ cần ADR riêng).

## Tài liệu liên quan

- [ADR-026](ADR-026-Dungeon-Instance-Model.md) — vì sao hầm ngục là một World riêng.
- [ADR-045](ADR-045-Appearance-Changed-Event.md) — khuôn "thêm một event đẩy, `protocolVersion` không tăng".
- [NETWORK.md §3, §5](../../05-server/NETWORK.md) — Response khớp theo `sequenceId`, Event thì không.
- `client/client-core/.../world/DungeonController.java` — javadoc liệt kê ba khoảng trống của `dungeon.proto`.

## Việc cần làm tiếp theo

1. ✅ **XONG** — chủ dự án chọn **B** (2026-09-13).
2. ✅ **XONG cùng ngày** — `@Disabled` đã gỡ; test có thêm khẳng định thứ tự (D3, `beforeBoss`);
   `notifyPulledIn` chạy NGAY TRƯỚC `attachCharacter` của chính người bị kéo.
   ⛔ Mục **"Phát hiện phụ"** thì KHÔNG xong: bản sửa đề xuất đã dựng thử và bị hai test bác (xem
   khung cảnh báo ở cuối mục đó). Khe của người gửi vẫn mở, và nay đã biết vì sao cách đó không đi
   được.
3. ⏳ **CÒN LẠI**: kiểm chứng sống bằng **hai client thật** (công thức ở bàn giao 2026-08-29): A bấm
   vào, màn hình B phải đổi sang hầm ngục + hiện dải chữ "Người trong nhóm vừa đưa cả nhóm vào bí
   cảnh". Test socket đã gác đường dây; cái còn thiếu là con mắt người.
