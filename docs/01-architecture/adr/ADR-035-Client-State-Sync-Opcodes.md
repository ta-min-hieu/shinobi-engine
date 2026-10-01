# ADR-035: Bốn khoảng trống đồng bộ trạng thái — túi đồ, catalog vật phẩm, vàng, nhật ký nhiệm vụ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-27) — chủ dự án duyệt **nguyên đề xuất**: cả 7 opcode, catalog theo phương án **A′**. Đề xuất viết 2026-08-25 |
| Date | 2026-08-25 |
| Deciders | Chủ dự án quyết (2026-08-27); AI đo + soạn |
| Related | [NETWORK.md §4, §6](../../05-server/NETWORK.md) (dải opcode + luật tương thích ngược) · [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) (giao asset theo tier) · [ADR-025](ADR-025-Idempotency.md) · [ADR-034](ADR-034-Manual-Attack-And-Experience.md) (tiền lệ "server đẩy chỉ số của chính mình") · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi protocol phải dừng hỏi) |

---

## Context

### Số liệu đo thật ngày 2026-08-25

Đếm trực tiếp trên `opcode_registry.proto` và mã nguồn client (`client/*/src/main/java`), không lấy
lại số từ tài liệu:

| Đo cái gì | Kết quả |
|---|---|
| File `.proto` trong `shared-protocol` | **18** |
| Opcode đã cấp phát (không tính `OPCODE_UNSPECIFIED`) | **150** |
| Opcode client thật sự tham chiếu | **40** (~27%) |
| Opcode dải Inventory (500-599) client dùng | **0 / 7** |
| Opcode nào mang số vàng của nhân vật | **0** — grep cả 18 file |

> Ghi chú trung thực: bản giao việc nêu "~151 opcode, client dùng ~36". Con số 151 gồm cả
> `OPCODE_UNSPECIFIED` (protobuf bắt buộc, không phải packet thật) nên số cấp phát thật là **150**;
> và client tham chiếu **40** chứ không phải 36 — client dùng hằng số sinh ra có hậu tố `_VALUE`
> (`OPCODE_PING_VALUE`), grep thiếu hậu tố này sẽ đếm hụt. Bốn khoảng trống bên dưới thì **đúng
> nguyên vẹn**, đã kiểm chứng lại từng cái.

### Khoảng trống 1 — không có opcode liệt kê túi đồ

Dải Inventory 500-599 cấp phát đúng 7 số: `ITEM_GRANTED_EVENT` (500), Equip (501/502), Unequip
(503/504), Enhance (505/506). **Không có `LIST_INVENTORY`.**

Nghĩa là client chỉ biết về vật phẩm qua đúng một đường: `ItemGrantedEvent` bắn lúc *vừa nhặt được*.
Đó là một luồng sự kiện, không phải một trạng thái. Hệ quả gameplay:

- **Đăng nhập lại là mất sạch nội dung túi phía client**, dù `inventory_item` trong DB còn nguyên.
- Không dựng được giao diện túi đồ, và **không dựng được cả giao diện trang bị**: `EquipItemCommand`
  cần `item_id` là `inventory_item.id` THẬT, mà client chỉ có được ID đó nếu nó tình cờ còn giữ
  `ItemGrantedEvent` từ phiên này. Sau relog thì không có cách nào lấy lại.
- Bán đồ (`SellItemCommand`, cũng cần `item_id`) chịu đúng ràng buộc đó.

### Khoảng trống 2 — không có catalog vật phẩm

`ItemGrantedEvent.item_template_id` và `ShopListingView.item_template_id` chỉ mang **số**. Client
không có bảng nào tra ra tên/icon/loại slot. Chính comment trong `.proto` đã thừa nhận, hai lần:

> `inventory.proto:14` — *"item_template_id (không phải code) — client CHƯA có catalog item để tra
> tên/icon"*
> `shop.proto:13` — *"client chưa có catalog để tra tên/icon ở lát cắt này"*

Đây là lý do giao diện cửa hàng không dựng được kể cả khi đã có `ListShopItemsResponse`: có giá
tiền nhưng không có tên món hàng. Client hiện xử lý bằng một lời xin lỗi thẳng thắn — trong
`DialogueCoordinator`, nhận `LIST_SHOP_ITEMS_RESPONSE` thì đóng hội thoại và hiện
`dialogue.notice.shop_unavailable`.

**Phát hiện phụ, quan trọng cho thiết kế catalog** — soi `item_template` (V4/V5/V6) thì bảng chỉ có
6 cột: `id`, `code`, `name_key`, `max_stack`, `equip_slot`, `bonus_atk`. **Không có cột icon**, và
`name_key` là *khoá i18n* chứ không phải tên hiển thị. Tức là dữ liệu catalog tự nhiên tách làm hai
nửa, và nửa hiển thị vốn đã thuộc về client:

| Nửa | Gồm | Hiện ở đâu | Đổi khi nào |
|---|---|---|---|
| **Hiển thị** | tên (qua `name_key`), icon | Client — bundle i18n `strings_vi/en.properties` + atlas theo [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) | Khi làm nội dung/mỹ thuật |
| **Cấu trúc** | `id`→`code`, `max_stack`, `equip_slot`, `bonus_atk` | Server — `item_template` | Khi **cân bằng game** |

Nên thứ duy nhất bắt buộc phải đi qua dây là **ánh xạ `id` → `code`** cộng vài số nhỏ. Không phải
tên, không phải icon. Điều này thu hẹp câu hỏi đánh đổi rất nhiều (§Decision-2).

Hiện trạng: bundle i18n có **0** khoá `item.*` — nửa "tên" thật sự còn trống.

> **Đính chính 2026-08-27** (bản viết 2026-08-25 ghi sai): 3 icon vật phẩm **đã render và đã vào
> atlas** ở cả 3 tier — `assets/exported/atlases/{low,med,high}/main.atlas` có sẵn 3 vùng
> `icon_kunai`, `icon_wooden_sword`, `icon_enhance_protection_charm`. Chỉ còn nợ khoá i18n.

**Mâu thuẫn tham chiếu đang tồn tại** (cần chốt luôn trong ADR này): 4 message tham chiếu vật phẩm
bằng `item_template_id` kiểu `int64` (`ItemGrantedEvent`, `ShopListingView`, `BuyItemCommand`,
`TradeItemView`, `MailAttachment`), nhưng `ItemRewardView` trong `quest.proto` lại dùng
`item_template_code` kiểu `string`. Catalog phải tra được **cả hai chiều**, nếu không thì phần
thưởng nhiệm vụ vẫn hiện ra dưới dạng chuỗi thô.

### Khoảng trống 3 — không opcode nào mang số vàng

Grep `gold|currency|money|balance` trên cả 18 file `.proto` ra đúng 3 nhóm kết quả, **không nhóm
nào là số dư của chính người chơi**:

- Enum kết quả: `BUY_OUTCOME_INSUFFICIENT_GOLD`, `ENHANCE_OUTCOME_INSUFFICIENT_GOLD`
- Vàng đính kèm thư: `MailView.gold_amount`
- Vàng trong lời chào giao dịch: `SetTradeOfferCommand.gold_amount`, `TradeStatusView.my_gold_offer`

Server thì biết rõ: `character_stat.gold BIGINT NOT NULL DEFAULT 0` có từ migration **V7**. Đây là
đúng khuôn "server nhớ đủ, client mù" của khoảng trống 1. Hệ quả gameplay: cửa hàng không thể hiện
"bạn có bao nhiêu tiền", và **người chơi chỉ phát hiện mình nghèo sau khi bấm mua và nhận
`INSUFFICIENT_GOLD`** — phản hồi sau hành động, đúng loại im lặng mà [ADR-034](ADR-034-Manual-Attack-And-Experience.md)
vừa phải đi sửa cho combat.

### Khoảng trống 4 — không có opcode liệt kê nhiệm vụ đang làm

Dải Quest 600-699 cấp 600-609, không có `LIST_QUESTS`. Nặng hơn ba cái trên vì nó làm **hỏng hẳn
một vòng gameplay đã đóng**:

- `QuestStatusChangedEvent` chỉ phát khi quest **vừa chuyển COMPLETED** (cố ý, theo GAMEPLAY.md §9.3
  — tránh quá tải World tick). Nó không phải ảnh chụp trạng thái.
- `AcceptQuestResponse` **không mang `quest_code`**. JavaDoc của `QuestCoordinator` nói rõ nguồn duy
  nhất của `quest_code` phía client là chuỗi `action="accept_quest:<code>"` đọc được từ lựa chọn hội
  thoại ngay lúc bấm.
- Mà `ClaimQuestRewardCommand` **bắt buộc có `quest_code`**.

Ghép lại: **nhận nhiệm vụ → thoát game → đăng nhập lại → không lĩnh thưởng được nữa**, vĩnh viễn,
vì client không còn đường nào biết mã nhiệm vụ. Server vẫn giữ tiến độ; chỉ là không ai hỏi được nó.

### Vì sao cần ADR

Cả bốn đều là *thêm opcode* — nằm trong danh sách 🛑 của [/CLAUDE.md §4](../../../CLAUDE.md) ("Đổi
protocol"). ADR này **chỉ đề xuất**; không file `.proto` nào bị sửa cùng lượt viết tài liệu này.

---

## Decision (đề xuất)

### Decision-1 — Liệt kê túi đồ: thêm 2 opcode ở dải Inventory

Dải 500-599 đang dùng tới 506, cấp tiếp **507/508** (tuần tự trong dải, không tái dùng số —
[NETWORK.md §4](../../05-server/NETWORK.md)).

```proto
// inventory.proto — ĐỀ XUẤT, chưa ghi vào file thật

// Command: Client -> Server (OPCODE_LIST_INVENTORY_REQUEST = 507). Không field — characterId lấy
// từ session (SECURITY.md §4, chống IDOR), cùng khuôn ListCharactersRequest.
message ListInventoryRequest {
}

// 1 dòng túi đồ. item_id là inventory_item.id THẬT — đây chính là thứ EquipItemCommand và
// SellItemCommand cần, và là lý do gốc phải có packet này.
message InventoryItemView {
  int64 item_id = 1;
  int64 item_template_id = 2;
  int32 quantity = 3;
  int32 enhance_level = 4;
  int32 slot_index = 5;
}

// Response: Server -> Client (OPCODE_LIST_INVENTORY_RESPONSE = 508). Ảnh chụp TOÀN BỘ túi tại thời
// điểm hỏi. `capacity` là hằng số phía code (không phải cột DB — xem V4), gửi kèm để client vẽ đủ ô
// trống mà không phải tự đoán.
message ListInventoryResponse {
  repeated InventoryItemView items = 1;
  int32 capacity = 2;
  repeated EquippedSlotView equipped = 3; // slot nào đang mặc item_id nào
}

message EquippedSlotView {
  EquipmentSlot slot = 1;
  int64 item_id = 2;
}
```

Gộp trang bị vào cùng response thay vì thêm `LIST_EQUIPMENT` riêng: hai thứ này luôn được mở cùng
lúc trên một màn hình, và tách ra thì có cửa sổ thời gian mà client thấy item vừa nằm trong túi vừa
đang mặc.

**Không phân trang.** Sức chứa túi là hằng số nhỏ phía code; phân trang là độ phức tạp trả cho một
vấn đề chưa tồn tại (Điều 9). Ghi vào Revisit Trigger.

### Decision-2 — Catalog vật phẩm: **gửi qua mạng, có version để client cache** (phương án A′)

Đây là câu hỏi thiết kế thật của ADR này. So sánh thẳng ở §Alternatives; kết luận đề xuất:

Cấp **509/510** ở dải Inventory (catalog là dữ liệu vật phẩm, cùng domain):

```proto
// inventory.proto — ĐỀ XUẤT

// 1 dòng catalog. KHÔNG mang tên hiển thị và KHÔNG mang icon: tên tra qua name_key trong bundle
// i18n phía client, icon tra qua code trong atlas (ADR-028). Server chỉ gửi thứ server sở hữu.
// Mang CẢ id lẫn code vì protocol hiện tham chiếu vật phẩm bằng cả hai kiểu (ItemGrantedEvent dùng
// id, ItemRewardView của quest.proto dùng code) — catalog phải tra được cả hai chiều.
message ItemCatalogEntry {
  int64 id = 1;
  string code = 2;
  string name_key = 3;
  int32 max_stack = 4;
  EquipmentSlot equip_slot = 5; // UNSPECIFIED = không mặc được (khớp equip_slot NULL trong DB)
  int32 bonus_atk = 6;
}

// Command: Client -> Server (OPCODE_GET_ITEM_CATALOG_REQUEST = 509). Client gửi version nó đang
// cache (rỗng nếu chưa có gì). Server so, khớp thì trả về rỗng và client dùng lại bản cũ.
message GetItemCatalogRequest {
  string known_version = 1;
}

// Response: Server -> Client (OPCODE_ITEM_CATALOG_RESPONSE = 510).
// unchanged=true  -> entries RỖNG, client giữ nguyên cache.
// unchanged=false -> entries là TOÀN BỘ catalog mới (thay hẳn, không cộng dồn).
message ItemCatalogResponse {
  bool unchanged = 1;
  string version = 2; // hash nội dung catalog; server tính, client chỉ lưu và gửi lại
  repeated ItemCatalogEntry entries = 3;
}
```

Điểm mấu chốt: **server là bên quyết định cache còn hợp lệ hay không**, không phải client. Nhờ vậy
lấy được gần hết lợi ích tiết kiệm băng thông của phương án "đóng gói tĩnh" mà **không** rước theo
rủi ro lệch phiên bản của nó.

Đo dung lượng thật: hôm nay `item_template` có **3 dòng** (`kunai` V4, `wooden_sword` V5,
`enhance_protection_charm` V9) → payload cỡ vài trăm byte. Ước lượng ~30 byte/dòng thì 500 vật phẩm
≈ **15 KB**, 2000 vật phẩm ≈ **60 KB**, và chỉ trả giá đó khi catalog thật sự đổi.

### Decision-3 — Vàng: thêm 1 event ở dải Character, theo đúng khuôn ADR-034

Vàng nằm ở `character_stat` (V7), thuộc domain Character → dải 200-299, đang dùng tới 208, cấp
**209**.

Có tiền lệ khớp đến từng chi tiết: `ExperienceChangedEvent` (208, [ADR-034](ADR-034-Manual-Attack-And-Experience.md))
cũng là "một chỉ số vô hướng của CHÍNH người chơi, server đẩy xuống". Đề xuất sao chép nguyên khuôn
đó thay vì phát minh khuôn mới:

```proto
// character.proto — ĐỀ XUẤT

// Event: Server -> Client (OPCODE_GOLD_CHANGED_EVENT = 209). Vàng của CHÍNH người chơi vừa đổi.
// Không có character_id: luôn là chính mình, cùng quy ước ExperienceChangedEvent (ADR-034).
//
// Gửi NGAY sau khi vào world (để cửa hàng có số ngay, không phải chờ giao dịch đầu tiên) và mỗi lần
// vàng đổi: mua/bán, cường hoá, nhận thư, giao dịch, thưởng nhiệm vụ.
//
// `gold` là số dư TUYỆT ĐỐI sau thay đổi, không phải delta — mất một gói giữa chừng thì gói kế tự
// sửa lại, không tích luỹ sai lệch.
message GoldChangedEvent {
  int64 gold = 1;
  int64 delta = 2;  // chênh lệch của chính lần này (âm = tiêu); thuần để client hiện "-500"
  string reason_key = 3; // vd "gold.reason.buy_item" — i18n key, KHÔNG phải câu tiếng Việt cứng
}
```

Chọn **event đẩy** thay vì **request/response hỏi-đáp** vì vàng đổi do nhiều nguyên nhân ngoài tầm
kiểm soát của client (nhận thư, thưởng nhiệm vụ, đối phương xác nhận giao dịch). Kiểu hỏi-đáp buộc
client phải poll, và poll thì hoặc tốn tick hoặc hiển thị số cũ.

### Decision-4 — Nhật ký nhiệm vụ: thêm 2 opcode ở dải Quest

Dải 600-699 đang dùng tới 609, cấp **610/611**.

```proto
// quest.proto — ĐỀ XUẤT

// Command: Client -> Server (OPCODE_LIST_QUESTS_REQUEST = 610). Không field — characterId từ session.
message ListQuestsRequest {
}

// 1 dòng nhật ký. quest_code là thứ ClaimQuestRewardCommand bắt buộc phải có, và hiện client chỉ
// lấy được nó từ chuỗi action của hội thoại lúc nhận — đây chính là gói gỡ bế tắc đó.
// KHÔNG mang progress_json: nó là chuỗi đục do script tự quản (quest.proto dòng 10), Java không đọc
// được và client không hiển thị được. Giữ nguyên ranh giới đó.
message QuestLogEntry {
  string quest_code = 1;
  QuestStatus status = 2;
}

// Response: Server -> Client (OPCODE_LIST_QUESTS_RESPONSE = 611). Gồm cả quest đã CLAIMED để client
// biết không mời lại — lọc phía server thì client không phân biệt được "chưa từng nhận" với "đã trả".
message ListQuestsResponse {
  repeated QuestLogEntry quests = 1;
}
```

### Tổng hợp opcode đề xuất

| Opcode | Số | Dải | Vì sao dải này |
|---|---|---|---|
| `OPCODE_LIST_INVENTORY_REQUEST` | 507 | Inventory 500-599 | Nội dung túi đồ |
| `OPCODE_LIST_INVENTORY_RESPONSE` | 508 | Inventory | — |
| `OPCODE_GET_ITEM_CATALOG_REQUEST` | 509 | Inventory | Catalog là dữ liệu vật phẩm |
| `OPCODE_ITEM_CATALOG_RESPONSE` | 510 | Inventory | — |
| `OPCODE_GOLD_CHANGED_EVENT` | 209 | Character 200-299 | `gold` nằm ở `character_stat` (V7); liền sau `EXPERIENCE_CHANGED_EVENT` = 208 |
| `OPCODE_LIST_QUESTS_REQUEST` | 610 | Quest 600-699 | — |
| `OPCODE_LIST_QUESTS_RESPONSE` | 611 | Quest | — |

**7 opcode mới. 150 → 157.** Tất cả đều là số kế tiếp còn trống trong dải đúng của domain, không số
nào tái sử dụng.

### Backward compatibility

**Không phá.** Cụ thể theo bảng luật [NETWORK.md §6](../../05-server/NETWORK.md):

- **Chỉ THÊM** opcode và message mới. Không đổi số field, không đổi ý nghĩa field, không xoá field
  nào đang dùng, không đánh `deprecated` cái nào.
- `ItemGrantedEvent`, `ShopListingView`, `ItemRewardView` giữ **nguyên vẹn** — kể cả sự chênh
  id-vs-code đã nêu. ADR này vá bằng cách cho catalog mang **cả** `id` lẫn `code`, chứ không sửa
  message đang chạy. Thống nhất kiểu tham chiếu (nếu chủ dự án muốn) là một ADR khác, có chi phí
  khác hẳn.
- **Không đổi schema DB.** Cả 7 opcode đều đọc từ cột đã có: `inventory_item`, `equipment_slot`
  (V4/V5), `item_template` (V4/V5/V6), `character_stat.gold` (V7), bảng quest (Phase 6).
- `protocolVersion` **không cần tăng**: header và cách encode chung không đổi.
- Client cũ bỏ qua opcode lạ theo đúng đường xử lý sẵn có; server cũ gặp `LIST_INVENTORY_REQUEST` sẽ
  trả `OPCODE_ERROR` bình thường.

---

## Alternatives Considered

### Câu hỏi chính — catalog vật phẩm giao thế nào

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| **A. Gửi toàn bộ catalog qua mạng lúc đăng nhập** | Không bao giờ lệch phiên bản — client thấy đúng thứ server đang dùng; cân bằng lại game là mọi người nhận số mới ở lần đăng nhập kế; không thêm hạ tầng nào | Trả lại toàn bộ payload mỗi lần đăng nhập dù catalog không đổi | Gần đúng, thiếu đúng phần cache |
| **A′. A + `version`/hash do server phán quyết** ✅ | Giữ trọn tính đúng đắn của A; lần đăng nhập thứ hai trở đi gần như miễn phí (`unchanged=true`, vài chục byte); **server** là bên quyết cache còn hợp lệ nên client không thể tự tin sai | Thêm một trường hash và một nhánh cache phía client | **Đề xuất chọn** |
| **B. Đóng gói catalog thành tài sản tĩnh trong bản cài, đồng bộ bằng version/hash** | Đăng nhập không tốn byte nào; tái dùng được `asset-manifest.json` + sha256 đã thiết kế ([ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md), [RESOURCE.md §9](../../04-client/RESOURCE.md)) | Lợi ích mua được ~15 KB — đo thật thì catalog hôm nay chỉ 3 dòng; đổi lại rước về **lệch phiên bản trong dữ liệu cân bằng**: file trong máy nói `bonus_atk=10`, DB nói `12`, client hiện số sai mà không ai báo lỗi; mỗi lần chỉnh cân bằng thành một lần phát hành asset | **Không chọn** — xem lập luận bên dưới |
| **C. Tra từng vật phẩm khi cần** | Chỉ tải thứ thật sự nhìn thấy; chịu được catalog rất lớn | Màn túi đồ cần *tất cả* các dòng cùng lúc → N+1 vòng gửi nhận đúng vào lúc người chơi đang chờ khung hình; ô túi hiện ra rồi mới điền tên dần | **Không chọn làm đường chính** — nhưng giữ làm đường lùi cho trường hợp không chặn được số lượng (xem dưới) |
| **D. Nhét tên/icon thẳng vào `ItemGrantedEvent` và `ShopListingView`** | Không cần catalog | Lặp cùng một chuỗi trong mọi gói; và phải **đổi message đang chạy** → đụng thẳng luật "chỉ được thêm field" | **Không chọn** |

**Vì sao B bị loại, nói thẳng.** B và [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) *trông* giống
nhau — cùng là "đóng gói vào bản cài, đồng bộ bằng hash" — nhưng hồ sơ rủi ro ngược nhau:

| | Asset mỹ thuật (ADR-028) | Catalog vật phẩm |
|---|---|---|
| Dung lượng | Megabyte | **Kilobyte** |
| Tần suất đổi | Thấp (xong là xong) | **Cao — mỗi lần cân bằng lại game** |
| Lệch phiên bản thì sao | Hiện icon cũ. Xấu, thấy ngay | **Hiện chỉ số sai. Im lặng, không ai thấy** |

ADR-028 đánh đổi như vậy là đúng vì nó tiết kiệm megabyte và hỏng thì lộ ngay. Catalog thì tiết
kiệm kilobyte và hỏng thì im — đúng loại bug [NETWORK.md §6](../../05-server/NETWORK.md) gọi là
*"tệ nhất"*. Và Closed Beta chính là giai đoạn số cân bằng đổi nhiều nhất. Theo thứ tự ưu tiên
Correctness → … → Performance ([/CLAUDE.md §8](../../../CLAUDE.md)), đổi tính đúng đắn lấy 15 KB là
đi ngược chiều.

A′ lấy được phần tốt của B (lần thứ hai trở đi gần như không tốn gì) mà bỏ được phần xấu, vì quyền
phán quyết cache nằm ở server.

**Ranh giới của A′** — nó dựa trên giả định *catalog đủ nhỏ để gửi trọn gói*. Hôm nay 3 dòng, giả
định thoải mái đúng. Nếu sau này catalog vượt cỡ vài nghìn dòng thì đường lùi **không phải** quay
sang B, mà là C có chọn lọc: A′ cho phần thường gặp + tra lẻ cho phần đuôi dài. Ghi vào Revisit
Trigger.

### Các lựa chọn phụ

| Lựa chọn | Vì sao không chọn |
|---|---|
| Không thêm `LIST_INVENTORY`, để client tự dựng lại túi bằng cách ghi nhớ `ItemGrantedEvent` | Client tự suy trạng thái gameplay từ luồng sự kiện = client thành nguồn chân lý thứ hai, sai với Server Authority ([/CLAUDE.md §5](../../../CLAUDE.md)); và vẫn không sống nổi qua relog |
| Nhồi số vàng vào `SelectCharacterResponse` | Chỉ đúng đúng một lần lúc vào world; vàng đổi liên tục sau đó. Và phải thêm field vào message đang chạy trong khi một event mới không đụng gì |
| Nhồi số vàng vào `ListInventoryResponse` | Buộc phải mở túi mới biết mình có bao nhiêu tiền; cửa hàng cần số này khi túi đang đóng |
| Gửi `delta` thay vì số dư tuyệt đối trong `GoldChangedEvent` | Mất một gói là lệch vĩnh viễn, và không tự sửa được. Gửi cả hai: tuyệt đối để đúng, delta để hiện hiệu ứng |
| Cho `QuestLogEntry` mang `progress_json` | Chuỗi đục do script tự quản (`quest.proto` dòng 10); Java không đọc được, client không hiển thị được. Phá ranh giới script/Java để đổi lấy con số không |
| Gộp cả 4 khoảng trống vào một gói "SyncStateResponse" duy nhất | Buộc 4 domain vào chung một message; sau này sửa nhiệm vụ lại phải đụng vào gói của túi đồ. Trái với chia dải theo domain ở [NETWORK.md §4](../../05-server/NETWORK.md) |
| Thêm `ITEM_REMOVED_EVENT` cùng lượt | Có lý, nhưng chưa cần: sau mỗi hành động làm đổi túi, client hỏi lại `LIST_INVENTORY`. Thêm khi đo thấy việc hỏi lại thật sự tốn. Số 511 vẫn để trống sẵn |

---

## Consequences

**Tích cực**

- Bốn màn hình đang bị chặn cứng mở được đường: túi đồ, trang bị, cửa hàng, nhật ký nhiệm vụ.
- **Sửa một lỗi mất tiến độ thật**: nhận nhiệm vụ rồi đăng nhập lại thì hiện không lĩnh thưởng được
  nữa (Khoảng trống 4). Đây là bug làm mất công người chơi, không phải thiếu tiện nghi.
- Người chơi thấy số vàng **trước** khi bấm mua, thay vì học bằng cách bị từ chối.
- Client tăng từ 40 lên 47 opcode có ích trên 157 — vẫn còn xa server, nhưng đây đúng 7 cái đang
  chặn nhóm giao diện lớn nhất còn lại.
- Không đụng schema DB, không đụng message đang chạy, không tăng `protocolVersion`.

**Tiêu cực / chi phí phải trả**

- Bảy opcode nghĩa là bảy handler hai đầu + test round-trip cho mỗi message
  ([shared/CLAUDE.md](../../../shared/CLAUDE.md) yêu cầu round-trip test).
- `GoldChangedEvent` phải được bắn từ **mọi** đường làm đổi vàng: mua, bán, cường hoá, nhận thư,
  giao dịch, thưởng nhiệm vụ. **Bỏ sót một đường là hiện số cũ** — cần một điểm phát duy nhất, không
  rắc lời gọi ở từng use-case.
- Client phải giữ cache catalog qua các phiên (đĩa) và xử lý nhánh `unchanged`; nhánh này chỉ chạy
  từ lần đăng nhập thứ hai nên **dễ mục nếu không có test riêng**.
- Nợ nội dung lộ ra ngay khi có giao diện: bundle i18n cần khoá `item.*` (hiện **0**). Icon thì
  **không** nợ — đã có sẵn trong atlas cả 3 tier (xem đính chính ở §Context). Có catalog mà thiếu
  khoá i18n thì túi đồ hiện ô có icon nhưng không tên.
- Sự chênh id-vs-code trong protocol **vẫn còn** sau ADR này — được vá chứ chưa được sửa.
- Một `SELECT` túi đồ mỗi lần mở màn hình. Chạy trên Virtual Thread như các truy vấn khác, không
  đụng tick thread; chưa đo, và ở mức tần suất này chưa có lý do phải đo.

## Revisit Trigger

Xem lại nếu:

1. **Catalog vượt ~2000 dòng** hoặc payload lần đầu vượt ~200 KB → chuyển sang A′ + tra lẻ phần đuôi
   (KHÔNG chuyển sang B, xem lập luận ở Alternatives).
2. **Sức chứa túi tăng đủ lớn để `ListInventoryResponse` thành gói nặng** → lúc đó mới bàn phân trang.
3. **Đo thấy việc hỏi lại toàn bộ túi sau mỗi hành động tốn thật** → thêm event tăng dần
   (`ITEM_REMOVED_EVENT`, số 511 đang để trống).
4. **Chủ dự án quyết thống nhất tham chiếu vật phẩm về một kiểu duy nhất** (id hoặc code) → ADR
   riêng; catalog hai chiều của ADR này sẽ thành thừa một nửa.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thêm `LIST_INVENTORY` 507/508, gồm cả trang bị trong cùng response | ✅ Chốt 2026-08-27 |
| 2 | Catalog vật phẩm theo **A′**: gửi qua mạng + `version` do server phán quyết | ✅ Chốt 2026-08-27 — chủ dự án chọn A′, không chọn B |
| 3 | Catalog mang **cả** `id` lẫn `code`, KHÔNG mang tên hiển thị và icon | ✅ Chốt 2026-08-27 — hệ quả của việc `name_key`/icon vốn đã ở phía client |
| 4 | `GOLD_CHANGED_EVENT` = 209, dải Character, khuôn `ExperienceChangedEvent` | ✅ Chốt 2026-08-27 |
| 5 | Vàng gửi số dư **tuyệt đối** kèm delta, không gửi riêng delta | ✅ Chốt 2026-08-27 |
| 6 | `LIST_QUESTS` 610/611, KHÔNG mang `progress_json` | ✅ Chốt 2026-08-27 |
| 7 | Không đổi message đang chạy, không tăng `protocolVersion` | ✅ Chốt 2026-08-27 — hệ quả bắt buộc của NETWORK.md §6 |
| 8 | Thống nhất id-vs-code trong toàn protocol | ⏳ **Mở** — cố ý để ngoài phạm vi ADR này |

## ADR cần tạo

- **Thống nhất tham chiếu vật phẩm (`item_template_id` vs `item_template_code`)** — chỉ khi chủ dự
  án muốn dọn thật. ADR đó phải sửa message đang chạy nên chi phí và luật tương thích ngược khác hẳn
  ADR này. Technical Decision #8.
- Không ADR nào khác. Bảy opcode ở trên nằm gọn trong khuôn đã có.

## Tài liệu liên quan

- [NETWORK.md §4, §6](../../05-server/NETWORK.md) — dải opcode và luật tương thích ngược; §4 cần bổ
  sung nếu ADR này được duyệt
- [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) — giao asset theo tier; ADR này **cố ý không**
  mở rộng cơ chế đó sang catalog, lý do ở Alternatives
- [ADR-034](ADR-034-Manual-Attack-And-Experience.md) — tiền lệ `ExperienceChangedEvent`, khuôn gốc
  của `GoldChangedEvent`
- [shared/CLAUDE.md](../../../shared/CLAUDE.md) — quy trình thêm packet mới (4 bước, có round-trip test)
- [GAMEPLAY.md §9.3](../../05-server/GAMEPLAY.md) — vì sao `QuestStatusChangedEvent` chỉ bắn lúc
  COMPLETED
- [DATABASE.md §2.2](../../05-server/DATABASE.md) — `item_template`, `inventory_item`, `equipment_slot`

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án đã quyết 2026-08-27**: duyệt nguyên đề xuất, catalog theo **A′**. Không còn quyết
   định nào treo trong ADR này (ngoài #8 vốn cố ý để ngoài phạm vi).
2. ~~⏳~~ ✅ 2026-10-02: xong `783131f` (2026-08-28) — opcode 209/507–510/610/611 trong `opcode_registry.proto`, round-trip ở `InventoryPacketsTest`/`QuestPacketsTest`/`CharacterPacketsTest`. Sau khi Accepted: ghi 7 opcode vào `opcode_registry.proto`, thêm message vào `inventory.proto`
   / `character.proto` / `quest.proto`, kèm round-trip test mỗi message.
3. ~~⏳~~ ✅ 2026-10-02: xong `783131f` — NETWORK.md §4 nay ghi "Quest / NPC / Dialogue". Cập nhật bảng [NETWORK.md §4](../../05-server/NETWORK.md) — hiện ghi dải 600-699 là "Quest",
   trong khi `.proto` ghi "Quest / NPC / Dialogue". Lệch nhỏ, sửa luôn cùng lượt.
4. ~~⏳~~ ✅ 2026-10-02: xong — `GoldLedger` là điểm duy nhất đổi vàng + phát event (`server-database/.../GoldLedger.java`). Tìm **một** điểm phát duy nhất cho `GoldChangedEvent` trước khi viết handler — rắc lời gọi ở
   từng use-case là cách chắc chắn sẽ sót đường.
5. ~~⏳~~ ✅ 2026-10-02: xong `74c9d56` (2026-08-29) — khoá `item.*` có trong `strings_vi/en.properties`. Nợ nội dung chặn giao diện, làm song song được: thêm khoá `item.*` vào
   `strings_vi/en.properties` (hiện 0 khoá). ~~Render icon~~ — kiểm lại 2026-08-27 thì icon **đã có**
   trong atlas cả 3 tier, không phải việc phải làm.
6. ⏳ Sau khi có 4 màn hình này, đo lại độ phủ opcode client — con số 40/150 hôm nay là mốc so sánh.
