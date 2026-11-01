# ADR-138: Bày trí động phủ hiện hình 3D

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Bày trí động phủ hiện hình" trong lượt hỏi lô 25 (AskUserQuestion). Cách làm (NPC động, vị trí, hình dáng) do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-129](ADR-129-Cave-Dwelling.md) (động phủ, D3 bày trí) · [ADR-131](ADR-131-Blacksmith-And-Merchant-NPCs.md) (NPC động `placeNpc`) · [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Decision

**D1 — Mỗi món đã bày là một NPC ĐỘNG `cave_<mã>` trong World động phủ.** `CaveCommands` gọi `WorldFacade.placeNpc` cho
mọi món chủ đã bày khi một người vào động phủ (chủ hoặc khách được mời), và cho đúng món vừa bày nếu chủ đang đứng trong
động phủ của mình. NPC là thực thể của World nên khách vào cùng World cũng thấy; `placeNpc` bỏ qua NPC đã đứng đúng chỗ
nên gọi lặp vô hại. **Không gói mới, không schema mới**.

**D2 — Chỗ đứng theo thứ tự danh mục** (`sort_order`): Linh Tùng x=−6, Ngọc Đăng −3, Vân Bình Phong +3, Liên Trì +6;
y=0,5 (lùi về sau lối đi). Chỗ vào động phủ là x=0.

**D3 — Model**: bốn đồ vật dựng bằng `tools/blender/build_cave_decorations.py` trên rig của `kage_mesh_kit` với clip
`idle` (client luôn chạy `idle` cho NPC — model không rig sẽ lỗi lúc nạp). Khoá asset `char_cave-<mã>` theo
`NpcModelKey`. Tùng và sen đung đưa nhẹ (xương `spine`), đèn và bình phong đứng yên.

**D4 — Đồ vật không nói chuyện**: client (`DialogueCoordinator.talkTo`) bỏ qua mã bắt đầu `cave_` — không gửi lệnh để
rồi nhận lỗi.

## Kiểm chứng

- `runWorldPreview` khung `9-bay-tri-dong-phu` (đối chứng trực trạm) — xem ASSET-LOG 2026-11-01.
- **Kiểm sống 2026-11-01** (gameProbe): Kagemaru `cave:enter` ⇒ `NPC_SPAWNED cave_spirit_pine x=-6`; `cave:decorate/
  jade_lantern` ⇒ −2.000 linh thạch, sản lượng 66→72/giờ, `NPC_SPAWNED cave_jade_lantern x=-3` ngay trong lượt; Benkei
  được mời rồi `cave:visit/Kagemaru` ⇒ vào World động phủ, thấy CẢ HAI món của chủ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Món bày trí = NPC động trong World động phủ, không đổi protocol/DB | Accepted |
| 2 | Vị trí cố định theo thứ tự danh mục | Accepted |
| 3 | Model đồ vật đi đường model NPC (rig kit + `idle`) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-129](ADR-129-Cave-Dwelling.md) · [ART-PIPELINE §2.8](../../14-art/ART-PIPELINE.md) · `assets/raw/ASSET-LOG.md`

## Việc cần làm tiếp theo

- Cho chủ tự chọn chỗ đặt (kéo thả) — cần lệnh + cột vị trí, chờ chủ dự án.
