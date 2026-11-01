# ADR-139: Nội dung arc 4 — Hạ Sơn (Kim Đan, Lv55–72)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Nội dung arc 4 (Kim Đan)" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: STORY §6, Lv55–72). Chi tiết (số quest, loài, chỉ số, vị trí, lời thoại) do AI soạn bám STORY §6/§8 — mọi con số là ĐỀ XUẤT CÂN BẰNG. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [STORY.md](../../13-game-design/STORY.md) §6 arc 4, §4.4 · [ADR-130](ADR-130-Arc-Three-Inner-Sect.md) (khuôn arc 3) · [ADR-126](ADR-126-Heavenly-Tribulation.md) (cấp 54 là trần) · [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) (PvP Hoang Vực) |

---

## Decision

**D1 — Nội dung là DB + script, không đổi Java gameplay** (Điều 27): migration V113, 5 script quest, 5 nhánh hội thoại
của Chu Hoành, chuỗi vi/en. Không đổi protocol, không đổi schema. Java chỉ đổi hằng số biên world (D4).

**D2 — Năm quest chính tuyến**, nối sau `repel_the_altar_master`, mỗi quest "hạ quái rồi báo Chu Hoành" (khuôn arc 3):

| # | Mã | Nhịp STORY §6 | Mục tiêu | EXP |
|---|---|---|---|---|
| 26 | `descend_the_mountain` | mở arc: Kim Đan xuống núi luyện tâm | 1 tà tu Hoang Vực; **trao bộ Kim Đan** | 18.000 |
| 27 | `trace_the_blood_sand` | truy dấu Huyết Sa: đào mạch chết "tìm thứ gì đó" | 2 quật mạch đồ | 20.000 |
| 28 | `free_the_awakened_beast` | twist: cứu yêu tộc khai trí — nạn nhân Đại Kiếp | 1 cai ngục Huyết Sa | 22.000 |
| 29 | `quiet_the_frenzied_kin` | đánh nhau trước, hiểu nhau sau | 2 cuồng mạch yêu thú | 24.000 |
| 30 | `fell_the_ancient_colossus` | cao trào: liên thủ hạ cổ thạch quái | 1 cổ thạch quái hộ mạch | 30.000 |

Nhịp hé lộ (§8 quy tắc 2): KHÔNG nói thứ Huyết Sa tìm là vết nứt Thiên Môn (arc 5), KHÔNG danh tính nội gián. Bạch Kính
Chi và yêu tộc có mặt trong lời thoại; trận cuối vẫn là tay đôi theo luật biên (chưa có cơ chế NPC đánh cùng).

**D3 — Năm loài + bộ "Kim Đan" (Lv55)**, chỉ số CHỌN TỪ luật biên thật (`DuelMargin`, khoá ở `GoldenCoreBalanceTest`);
người chơi nền 10/10/10/10 đã qua 3 kiếp: bộ Nội Môn Cấp 55 = 109/89/448, bộ Kim Đan Cấp 60 = 131/107/508.

| Loài | Cấp | Máu/công/thủ | Biên thật |
|---|---|---|---|
| `wasteland_marauder` | 55 | 490/70/54 | bộ Nội Môn +3 |
| `blood_sand_digger` | 60 | 540/78/58 | bộ Nội Môn +1 (chặn) · bộ Kim Đan +4 |
| `blood_sand_beast_warden` | 64 | 570/82/60 | bộ Kim Đan +3 |
| `frenzied_vein_beast` | 67 | 600/84/62 | bộ Kim Đan +2 |
| `ancient_stone_colossus` | 70 | 640/88/64 | bộ Nội Môn −2 (chặn) · bộ Kim Đan +2 |

Bộ Kim Đan: công 92 · thủ 78 · kháng 64 · máu 200 (6 món), không bán ở Tàng Bảo Các.

**D4 — Hình học: Hoang Vực sâu, phía đông cổng nội môn**, bước 16 m: trạm 304 · 312 · 328 · 344 · 360 · trạm 368 · 376
· 392 · 408. Hai trạm tiếp ứng mới (vùng an toàn + người trực trạm). `DemoPlatforms.GROUND_MAX_X` và
`WorldConstants.MAIN_WORLD_MAX_X` cùng đi 305 → 417 (bài học V57: hai số này phải đi cùng lượt).

**D5 — Hoang Vực sâu là đất PvP tự do**: dải `pvp_zone` thứ hai `wasteland_deep` [307, 417] — STORY §6 arc 4 "Zone:
Hoang Vực (PvP mở)". Trạm 368 nằm trong dải nhưng vùng an toàn luôn thắng (ADR-137 D2). ⚠️ Nghĩa là quest arc 4 làm
giữa đất PvP — đúng lore, chủ dự án xem lại nếu thấy gắt.

**D6 — Art**: 5 model quái suy ra từ spec dòng dõi (`tools/blender/build_monster_arc_four.py`, chỉ đổi bảng màu/phụ
kiện); 6 icon bộ Kim Đan (vàng kim thân sáng chi tiết huyền thiết — đảo bố cục so với bộ Nội Môn; mũ chóp châu, hài mũi
cuộn, ngọc bội bán nguyệt, kiếm hộ thủ đĩa tròn).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 quest arc 4 + 5 loài + bộ Kim Đan Lv55, chỉ số theo luật biên thật; trục đông tới 408, biên 417 | Accepted |
| 2 | Hoang Vực sâu là dải PvP tự do | Accepted — chủ dự án xem lại |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [STORY.md](../../13-game-design/STORY.md) §6, §7 · `assets/raw/ASSET-LOG.md`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: V113, 5 script quest, `town_elder.js` (+5 nhánh, `TownElderDialogueTreeTest` ghim lại), i18n
   vi/en, `GoldenCoreBalanceTest`, cập nhật các test hình học (số spawn 30, vùng an toàn 10, nền đất 417).
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01** (DB dev lên V113): Kagemaru Cấp 54 độ kiếp lần ba (5 đợt sét, còn 2/522
   máu) ⇒ Cấp 56 ⇒ nhận quest 26 qua hội thoại (`0 → 24 → 0`, nhánh `descend` đứng đúng vị trí 24) ⇒ đứng trạm 304, tới
   tà tu @312 (490 máu) ⇒ hạ trong 5 nhát bằng bộ Nội Môn, mất 205/556 máu ⇒ báo Chu Hoành ⇒ COMPLETED; lĩnh lần đầu
   `INVENTORY_FULL` (túi dev 34/34 — server chặn đúng, đã dọn 6 món sang kho) ⇒ lĩnh: +18.000 EXP (Cấp 56 → 59) + đủ 6
   món Kim Đan ⇒ mặc cả bộ, nhận quest 27 ⇒ quật mạch đồ @328 (540 máu) hạ trong 6 nhát, mất ~250/613 máu. Dải PvP
   `wasteland_deep`: hai người @384/@385 ⇒ 2 đòn; cùng đứng trong trạm @368 ⇒ 0 đòn.
   **Trọn arc 2026-11-01 (lô 26, kiểm sống nợ)**: quest 27 thợ đào thứ hai ⇒ báo ⇒ +20.000; quest 28 Hộ Thú @360 hạ trong
   6 nhát, nhận 6 đòn × 38 (còn 396/629) ⇒ +22.000; quest 29 hai Cuồng Mạch Thú @376 (mỗi trận nhận 7–8 đòn, còn
   467/639) ⇒ +24.000; quest 30 Cổ Thạch Khổng Lồ @408 hạ trong 6 nhát, nhận 6 đòn × 41 ⇒ +30.000. ⚠️ Không phải phép đo
   biên: Kagemaru vào arc ở Cấp 60 (dư từ các lượt thử trước) và tới trận cuối đã Cấp 72 — trận ở cấp thiết kế vẫn chỉ có
   trên giấy (`GoldenCoreBalanceTest`). Một lượt chết do bẫy công cụ "mục tiêu chốt một lần" (probe chốt Hộ Thú ngoài tầm
   lúc Cuồng Mạch Thú chưa hồi sinh), không phải do cân bằng.
3. ⚠️ Chủ dự án xem: toàn bộ con số + lời thoại do AI soạn; NPC đồng minh (Bạch Kính Chi, yêu tộc) mới chỉ có trong lời
   thoại — muốn họ hiện hình/đánh cùng cần cơ chế mới.
