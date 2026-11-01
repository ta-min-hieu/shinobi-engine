# ADR-130: Nội dung arc 3 — Nội Môn (Tử Phủ, Lv37–54)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-31) — chủ dự án chọn "Nội dung arc 3 (Tử Phủ)" trong lượt hỏi lô 24 (AskUserQuestion; mô tả kèm: theo STORY.md §6). Chi tiết (số quest, loài, chỉ số, vị trí, lời thoại) do AI soạn bám STORY §6/§8 — mọi con số là ĐỀ XUẤT CÂN BẰNG. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [STORY.md](../../13-game-design/STORY.md) §6 arc 3 · [ADR-126](ADR-126-Heavenly-Tribulation.md) (cấp 36 là trần — phải độ kiếp mới vào arc 3) · V48 (khuôn nhịp đóng arc 2) |

---

## Decision

**D1 — Nội dung là DB + script, không đổi Java** (Điều 27): migration V109, 5 script quest, 5 nhánh hội thoại của Chu
Hoành, chuỗi vi/en. Không đổi protocol, không đổi schema.

**D2 — Năm quest chính tuyến**, nối sau `foundation_breakthrough`, mỗi quest "hạ quái rồi báo NPC" (cùng khuôn arc 2):

| # | Mã | Nhịp STORY §6 | Mục tiêu | EXP |
|---|---|---|---|---|
| 21 | `inner_sect_examination` | khảo hạch nội môn | 1 Trắc Nội Môn Thạch Nhân → Chu Hoành; **trao bộ Nội Môn** | 9.000 |
| 22 | `forbidden_archive_glow` | twist (1): ngọc cổ phát sáng, "Thiên Môn" lần đầu | 2 thạch vệ Tàng Thư → Chu Hoành | 9.000 |
| 23 | `leaked_examination` | twist (2): nội gián cấp trưởng lão (không danh tính) | 3 tế đồ trà trộn → Chu Hoành | 11.000 |
| 24 | `inner_sect_grand_contest` | đại bỉ võ: vượt Bạch Kính Chi | Bạch Kính Chi → Tô Vân Nhi | 13.000 |
| 25 | `repel_the_altar_master` | cao trào: đẩy lui đàn chủ Huyết Sa | đàn chủ → Chu Hoành (một loài: khuôn `QuestFuzzSpec`) | 16.000 |

**D3 — Bốn loài + bộ "Nội Môn" (Lv37)**, chỉ số CHỌN TỪ luật biên thật (`DuelMargin`, khoá ở `InnerSectBalanceTest`);
người chơi nền 10/10/10/10 đã qua 2 kiếp: bộ Trúc Cơ Cấp 37 = 88/71/338, bộ Nội Môn Cấp 42 = 106/86/392.

| Loài | Cấp | Máu/công/thủ | Biên thật |
|---|---|---|---|
| `inner_trial_construct` | 37 | 360/52/40 | bộ Trúc Cơ +3 |
| `blood_sand_infiltrator` | 42 | 400/60/46 | bộ Trúc Cơ 0 (chặn) · bộ Nội Môn +4 |
| `bach_kinh_chi` | 46 | 450/66/50 | bộ Nội Môn +2 |
| `blood_sand_altar_master` | 52 | 480/68/52 | bộ Trúc Cơ −2 (chặn) · bộ Nội Môn +2 |

Bộ Nội Môn: công 70 · thủ 60 · kháng 48 · máu 150 (6 món), không bán ở Tàng Bảo Các (cùng lý do V45/V48).

**D4 — Hình học**: đường chính đi tiếp về đông, bước 16 m như V57: 232 · trạm tiếp ứng 240 (vùng an toàn
`waypost_inner_sect_gate` + NPC trạm) · 248 · 264 · 280 · 296. `DemoPlatforms.GROUND_MAX_X` 225 → 305 cùng lượt.

**D5 — Art**: 4 model quái sinh từ mesh kit (`tools/blender/build_monster_arc_three.py`, kế thừa spec của dòng tương
ứng, chỉ đổi bảng màu/phụ kiện); 6 icon bộ Nội Môn (tím Tử Phủ, thân tối chi tiết bạc — đảo bố cục so với bộ Trúc Cơ).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 quest arc 3 + 4 loài + bộ Nội Môn Lv37, chỉ số theo luật biên thật; trục đông tới 296, nền đất 305 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [STORY.md](../../13-game-design/STORY.md) §6, §7

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31: V109, 5 script quest, `town_elder.js` (+5 nhánh, `TownElderDialogueTreeTest` ghim lại),
   i18n vi/en, `InnerSectBalanceTest`, cập nhật các test hình học (số spawn, số vùng an toàn, nền đất).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V109): Kagemaru (Cấp 42, bộ Trúc Cơ) nhận quest 21 qua hội
   thoại (`0 → 19 → 0`, năm nhánh mới đứng đúng vị trí 19–23) ⇒ đi tới @230, thấy `inner_trial_construct` 360/360 ⇒ hạ
   trong 5 nhát, mất 87/479 máu ⇒ báo Chu Hoành ⇒ lĩnh: +9.000 EXP (Cấp 42 → 44) + đủ 6 món bộ Nội Môn ⇒ quest 22 mở và
   nhận được. **Lỗi thật lộ ra khi kiểm**: `WorldConstants.MAIN_WORLD_MAX_X` vẫn là 210 từ dòng 67(c) trong khi V57
   nới nền client lên 225 và dời cỗ khảo hạch Trúc Cơ sang @216 — người chơi bị chặn cách trận đóng arc 2 sáu mét. Đã
   nới biên server lên 305 (= nền đất mới), ghim ở `MainWorldBoundsTest`. **Bổ sung 2026-11-01 — đi TRỌN quest 22–25 bằng probe** (Kagemaru mặc bộ Nội Môn): 2 thạch vệ → lĩnh → nhận 23;
   3 tế đồ trà trộn (mỗi con 35 sát thương/nhát lên người chơi) → lĩnh → nhận 24; Bạch Kính Chi 450 máu hạ trong 6 nhát,
   mất 7 × 39 = 273 máu → báo sư tỷ → lĩnh → nhận 25; đàn chủ 480 máu hạ, còn 316 máu → lĩnh. Kết thúc ở **đúng Cấp 54**
   (trần Thiên Kiếp kế tiếp) — chuỗi arc 3 đưa người chơi tới đúng mốc độ kiếp.
3. ⚠️ Chủ dự án xem: toàn bộ con số + lời thoại do AI soạn; 4 model quái là bản đầu chưa qua `runWorldPreview`; EXP
   5 quest (58.000) không nhắm "lĩnh xong đứng đúng Cấp 54" như arc 2 — trần Thiên Kiếp ở 54 đã giữ cấp.
