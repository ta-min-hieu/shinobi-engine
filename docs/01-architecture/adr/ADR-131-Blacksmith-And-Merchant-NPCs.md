# ADR-131: NPC thợ rèn + thương nhân du hành

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-31) — chủ dự án chọn "NPC thợ rèn + thương nhân" trong lượt hỏi lô 24 (AskUserQuestion; mô tả kèm: model dựng bằng kit có sẵn, đặt vào world, thợ rèn nhận lò luyện khí). Chi tiết do AI chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-120](ADR-120-Forging.md) (lò luyện khí — **D2 được thay** bởi ADR này) · [ADR-118](ADR-118-Traveling-Merchant.md) (thương nhân du hành) · [ADR-040](ADR-040-NPC-Template-And-Placement.md) (NPC tĩnh) |

---

## Decision

**D1 — Thợ rèn Luyện Khí Đường (`sect_blacksmith`)**, NPC TĨNH trong sân tông @-11 (vùng an toàn [-15, 5]), V110.
Lò luyện khí của ADR-120 **chuyển từ Chu Hoành sang thợ rèn**: `CraftingCommands.FORGE_NPC_CODE` = `sect_blacksmith`
(server kiểm đứng gần — `NpcProximity`); lò đan vẫn ở Chu Hoành. Hội thoại có action mới `open_forge` ⇒ gateway gửi
danh sách công thức FORGE (1815), client tự mở cửa sổ lò. `/luyenkhi` vẫn xem được danh sách ở bất cứ đâu.

**D2 — Thương nhân du hành (`traveling_merchant`)**, NPC ĐỘNG: chỉ có `npc_template`, không `npc_spawn`. Nhịp 1 phút
(`MerchantCommands.presenceJob`) đặt NPC cạnh trạm của lần ghé đang diễn ra (+2 m), ở MỌI kênh world chính (kênh mới
mở nhận ở nhịp kế), gỡ khi hết lần ghé. World có lệnh mới `PlaceNpcCommand`/`RemoveNpcCommand` (đặt lặp cùng chỗ vô
hại, đặt chỗ khác thì dời; gỡ ⇒ AoI gửi despawn qua `onNpcDetach`). Hội thoại có action `open_merchant` ⇒ gateway
gửi trạng thái + hàng (1025), client tự mở cửa sổ. Mua vẫn phải đứng gần trạm (ADR-118 D3).

**D3 — Model**: hai NPC dựng bằng mesh kit (`tools/blender/build_npc_lot24.py`), kế thừa spec người trực trạm (dáng
đứng, bao 4 phụ kiện, không áo choàng): thợ rèn áo muội than + tạp dề da + búa rèn (`stone_mace`); thương nhân áo màu
thương hội + đai vàng + hồ lô. Vô danh có chủ đích (không thêm tên riêng vào STORY §5).

**D4 — Giao thức, chỉ thêm**: không opcode mới (dùng lại 1815/1025 và gói NPC spawn/despawn có sẵn). Hai action
hội thoại mới. Schema: chỉ thêm dòng (V110).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thợ rèn tĩnh @-11 nhận lò luyện khí (thay ADR-120 D2); thương nhân là NPC động đứng cạnh trạm đang ghé, mọi kênh; action `open_forge`/`open_merchant` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [ADR-120](ADR-120-Forging.md) D2 (đã thay)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31: V110, `sect_blacksmith.js`/`traveling_merchant.js`, `DialogueOption.ACTION_OPEN_FORGE/
   ACTION_OPEN_MERCHANT`, `WorldFacade.placeNpc/removeNpc` (+ `DynamicNpcTest`), `MerchantCommands.presenceJob`,
   client mở cửa sổ khi response tới, 2 model + LOD.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V110; một lần ghé `sect_gate` tạo bằng SQL): thợ rèn hiện
   @-11; đứng @-10 nói chuyện ⇒ chọn "mở lò" ⇒ server trả 1815 (danh sách công thức FORGE); đứng @0 ⇒ `npc_too_far`
   (kiểm đứng gần có hiệu lực). Thương nhân được job đặt @-3 (= trạm -5 + 2) sau nhịp đầu ⇒ nói chuyện ⇒ "xem hàng" ⇒
   1025. **Lỗi thật lộ ra khi kiểm**: `NpcProximity` chỉ biết NPC tĩnh, nên NPC động luôn "quá xa". Vá: sổ NPC động
   dùng chung ở `MainWorldChannelRegistry` (`placeDynamicNpc`/`removeDynamicNpc`/`dynamicNpcs`), `NpcProximity`
   đọc cả sổ đó (test `shouldReachADynamicNpcOnlyWhileItIsPlaced`). Gỡ NPC khi hết lần ghé phủ bằng `DynamicNpcTest`,
   chưa kiểm sống.
3. ⚠️ Chủ dự án xem: vị trí @-11, lệch +2 m cạnh trạm; hai model chưa qua `runWorldPreview`.
