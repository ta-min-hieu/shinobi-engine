# ART-AUDIT-TU-TIEN.md

**Rà toàn bộ asset thời ninja — cái nào giữ, cái nào sửa, cái nào làm lại**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft — **chờ chủ dự án quyết từng dòng ở §5** |
| Ngày đo | 2026-09-01 |
| Liên quan | [ADR-038 D7.3](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) (Đợt 3) · [ART-QUALITY-STANDARD](ART-QUALITY-STANDARD.md) (thước đo) · [ART-PIPELINE](ART-PIPELINE.md) (cách làm) · [STORY.md §7](../13-game-design/STORY.md) (lore từng thực thể) |

> Đây là bước ĐẦU của Đợt 3, cố ý tách khỏi bước làm: sinh model bằng Rodin tốn hạn mức và
> **không hoàn lại được**, nên phải biết chính xác cần sinh bao nhiêu món trước khi bấm.
> Mọi phán xét dưới đây dựa trên ảnh chụp/render THẬT của asset đang nằm trong repo, không phải
> trên tên file.

---

## 1. Kết luận một dòng

**Đợt 3 nhỏ hơn nhiều so với cách ADR-038 D7.3 phác.** Đếm thật: **33/37 nhóm asset dùng lại
nguyên vẹn**. Chỉ 4 món phải động tới, và trong đó chỉ **1 món** là "làm mới" thật sự.

Lý do: đề tài ninja gần như chỉ nằm ở **đúng một chi tiết hình ảnh** — chiếc **băng trán có tấm
kim loại**. Mọi thứ khác (thân chibi, khăn quàng, áo nâu, hiệu ứng nguyên tố, cọc gỗ, quái đá)
vốn đã trung tính về đề tài.

---

## 2. VFX — giữ 100%

| Nhóm | Số lượng | Phán xét |
|---|---|---|
| `vfx_*` (17 họ hiệu ứng, 25 vùng atlas) | 17 | ✅ **GIỮ NGUYÊN** |

Kiểm chứng: `vfx_fireball/impact_00.png` là một khối lục giác đỏ viền đen trừu tượng. Toàn bộ VFX
đều là **hình khối nguyên tố** (lửa/nước/gió/sét/đất) — chúng không mang dấu hiệu đề tài nào cả.
Lửa của ninja và lửa của tu sĩ là cùng một ngọn lửa.

Đây là phần tiết kiệm lớn nhất của Đợt 3: 17 họ hiệu ứng, 0 việc.

---

## 3. Icon vật phẩm — giữ 16/18, sửa 2

| Icon | Hiện là gì | Phán xét |
|---|---|---|
| `icon_genin_headband` | **Băng trán có TẤM KIM LOẠI gắn giữa** | 🔴 **PHẢI SỬA** — dấu hiệu ninja rõ nhất trong toàn bộ kho asset, và cũng là chi tiết gần IP người khác nhất (Điều 5). Thay bằng **mũ vải / khăn buộc tóc của đệ tử ngoại môn** |
| `icon_steel_katana` | Katana Nhật (lưỡi cong, tsuba tròn, chuôi quấn) | 🟡 **NÊN SỬA** — tên hiển thị đã là "Trường Kiếm Thép". Nắn lưỡi thẳng + đổi hộ thủ là đủ; không cần vẽ lại từ đầu |
| `icon_novice_headband` | Dải vải trơn có đuôi, **không có tấm kim loại** | ✅ GIỮ — đọc đúng "Khăn Đầu Tạp Dịch" |
| `icon_kunai` | Dao thẳng lưỡi xám cán đỏ | ✅ GIỮ — đọc đúng "Phi Đao" |
| `icon_chakra_shard` | Tinh thể xanh lam nhiều mảnh | ✅ GIỮ — đọc đúng "Mảnh Yêu Đan" |
| `icon_novice_vest` | Áo vạt chéo vải thô | ✅ GIỮ — đọc đúng "Áo Vải Tạp Dịch" |
| 12 icon còn lại (giày, găng, vòng, dây, giáp, sắt vụn, bùa, kiếm gỗ) | Đồ vật thường | ✅ GIỮ — không món nào mang dấu hiệu đề tài |

---

## 4. Model 3D — giữ 3 quái, sửa 15 nhân vật, làm mới 1 NPC

Đo bằng `./gradlew :client:client-core:runWorldPreview` (ảnh `1-idle-va-fallback.png`).

| Model | Hiện là gì | Phán xét |
|---|---|---|
| `mob_training-dummy` | Cọc gỗ nâu đầu đỏ | ✅ **GIỮ** — cọc luyện công là hình ảnh chung của mọi môn phái, kể cả tu tiên. Khớp thẳng STORY.md §7 "bãi mộc nhân" |
| `mob_wandering-bandit` | Người áo choàng nâu, tóc nâu | ✅ **GIỮ** — đọc đúng "tán tu cường đạo" (STORY.md §7) |
| `mob_stone-ogre` | Quái đá xám phủ rêu xanh | ✅ **GIỮ** — đọc đúng "thạch quái hộ mạch", thậm chí hợp lore Linh Triều hơn cả tên cũ |
| `char_town-elder` | **Khối hộp xám-xanh, đầu nhỏ, không mặt** | 🔴 **LÀM MỚI** — xem §4.1 |
| 15 `char_<hệ>-b1/b2/b3` | Chibi anime đạt chuẩn, **nhưng đội băng trán kim loại** | 🟡 **SỬA 1 CHI TIẾT** — xem §4.2 |

### 4.1. `char_town-elder` — món tệ nhất, và cũng là món quan trọng nhất

Nó **chưa bao giờ đạt** [ART-QUALITY-STANDARD §2](ART-QUALITY-STANDARD.md): không có mặt (trượt
Q2), không có tóc nhận diện (Q3), một vùng màu (Q4), thân là hộp trần (Điều cấm §3 của chuẩn).
Đây là **nợ có từ trước**, không phải do đổi đề tài sinh ra.

Nhưng nó vừa trở nên quan trọng hơn hẳn: NPC này giờ là **trưởng lão Chu Hoành** — người dẫn dắt
trọn arc 1, người chơi gặp ĐẦU TIÊN, và cái chết của ông là cao trào arc 5 (STORY.md §5/§6).
Một khối hộp xám không gánh nổi vai đó.

**Đề xuất**: đây là món DUY NHẤT xứng đáng tiêu hạn mức Rodin trong Đợt 3. Prompt bám STORY.md §5:
lão giả tóc bạc búi, râu dài, đạo bào xám bạc, lưng hơi còng, tay cầm chổi (chi tiết mở màn arc 1).

### 4.2. 15 nhân vật — bỏ băng trán, giữ nguyên phần còn lại

Đối chiếu ảnh chuẩn đã duyệt `assets/raw/reference/pilot_text3d_hoa_v2_actions.png`:

| Chi tiết | Đọc ra gì | Giữ hay bỏ |
|---|---|---|
| Thân chibi 2.5 đầu, mặt anime mắt to | Đạt đúng thước đo đã duyệt | ✅ Giữ — **đây là phần đắt nhất, và nó trung tính về đề tài** |
| Tóc đen dựng | Trung tính | ✅ Giữ |
| **Băng trán có tấm kim loại** | **Ninja, không chối được** | 🔴 **BỎ** |
| Khăn quàng đỏ | Trung tính, thậm chí rất hợp tu tiên | ✅ Giữ |
| Áo/quần nâu, giày | Đọc được như đệ tử tạp dịch | ✅ Giữ |

### 4.3. ĐÃ ĐO: bỏ băng trán chỉ tốn một phép đổi màu texture

Câu hỏi "băng trán rời hay dính liền" quyết định chi phí cả Đợt 3, nên đã **đo thẳng trên file
`.glb`** (đọc chunk JSON của glTF — không cần Blender, không cần Rodin):

| Đo được | Con số |
|---|---|
| Cấu trúc **CẢ 15** nhân vật | ĐÚNG 1 mesh thân **có texture** + N mesh phụ kiện **material phẳng** |
| Node của `char_hoa-b1` | 11 xương rig + thân `Hoa_D_med` + **3 node vũ khí rời** (`hoa_KunaiBlade/Grip/Ring`) |
| Texture thân | **1 ảnh duy nhất** 1024×1024 (`texture_diffuse`) |
| Vật liệu vũ khí | `*_flat` — **KHÔNG dùng texture**, chỉ `baseColorFactor` xám-xanh |
| Pixel xám SÁNG (tấm kim loại) trong texture | **7.861 px = 0,75%** |
| Pixel xám TỐI (vải/tóc) | 592.730 px = 56,5% |

Hai điều quan trọng suy ra:

1. **Băng trán DÍNH LIỀN mesh thân** — không xoá được bằng cách bỏ một node.
2. **Nhưng nó là một dải màu TÁCH BẠCH trong texture**, và vì vũ khí dùng material phẳng không
   texture, **mảng xám sáng 0,75% đó không thể là thứ gì khác ngoài tấm kim loại trên trán**.
   Đổi màu nó không đụng tới lưỡi dao, tóc, hay quần áo.

⇒ **Cách rẻ nhất là ĐỔI MÀU TEXTURE, không phải sinh lại model**: thay dải xám sáng bằng màu vải
của chính chiếc băng đó, trên 15 texture. **0 lượt Rodin, 0 phẫu thuật mesh, không cần Blender.**

⚠️ **Còn một điều chưa kiểm và phải kiểm bằng MẮT trước khi chốt**: hình khối lồi của tấm kim loại
vẫn còn trên mesh. Ở cỡ trong game (nhân vật cao ~100px) nó nhiều khả năng đọc thành nút buộc vải,
nhưng "nhiều khả năng" không phải bằng chứng — đổi màu 1 texture rồi chụp bằng
`runWorldPreview` là biết ngay, và đó là việc rẻ.

⚠️ `char_town-elder.glb` **không có texture nào cả** (đọc file báo thiếu hẳn mảng `images`) — khớp
đúng với ảnh render: nó là khối hộp phẳng thật, không phải model bị mất texture. Xác nhận §4.1.

---

## 5. Bảng quyết định — chủ dự án tick

| # | Việc | Chi phí ĐO ĐƯỢC | Cần quyết |
|---|---|---|---|
| ~~1~~ | ~~Băng trán rời hay dính?~~ | ✅ **Đã đo 2026-09-01** — dính mesh nhưng tách bạch trong texture (§4.3) | Xong |
| 2 | Bỏ băng trán khỏi 15 nhân vật bằng **đổi màu texture** | **0 hạn mức Rodin**, script chạy hàng loạt | ⬜ Đồng ý hướng này? (rẻ nhất, nhưng giữ lại khối lồi trên trán) |
| 2b | *Hoặc* sinh lại 15 nhân vật cho sạch hẳn khối lồi | **15 lượt Rodin** + 15 vòng nghiệm thu | ⬜ Chỉ chọn nếu #2 nhìn không đạt |
| 3 | Làm mới `char_town-elder` thành Chu Hoành | 1 lượt Rodin + 1 vòng nghiệm thu | ⬜ Đồng ý tiêu hạn mức? |
| 4 | Sửa `icon_genin_headband` → mũ vải ngoại môn | 1 icon, dựng tay | ⬜ |
| 5 | Sửa `icon_steel_katana` → trường kiếm thẳng | 1 icon, dựng tay | ⬜ Hay chấp nhận đao cong? |
| 6 | 17 VFX + 16 icon + 3 quái | **0** | Không cần quyết — giữ nguyên |

**Đề xuất của tôi**: làm #2 trước (rẻ, đảo ngược được vì texture nằm trong `.glb` và pipeline dựng
lại được từ nguồn), chụp `runWorldPreview` xem khối lồi có lộ không, rồi mới quyết có cần #2b.
Không bấm lượt Rodin nào cho tới lúc đó — trừ #3, vốn phải sinh lại bất kể đề tài.

⚠️ **Bẫy hạn mức Rodin đã dính một lần** (2026-08-30, art 2 loài quái): hết hạn mức giữa chừng thì
phiên art đứt đoạn và phải chờ. Chốt danh sách TRƯỚC khi bấm lượt đầu tiên.

⚠️ **Blender phải đang chạy kèm addon** thì công cụ MCP mới nối được — kiểm 2026-09-01: chưa chạy.
Xem [BLENDER-SESSION-GUIDE](BLENDER-SESSION-GUIDE.md).

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | VFX/icon/quái trung tính về đề tài — Đợt 3 KHÔNG đụng tới | 🟡 đề xuất |
| 2 | 15 nhân vật SỬA bằng đổi màu texture, không sinh lại | 🟡 đề xuất — **phép đo §4.3 đã chứng minh là làm được** |
| 3 | `char_town-elder` làm mới, lý do là chất lượng chứ không phải đề tài | 🟡 đề xuất |
| 4 | Không bấm lượt Rodin nào trước khi chốt xong bảng §5 | 🟡 đề xuất |
| 5 | Đo cấu trúc `.glb` bằng cách đọc chunk JSON, không cần mở Blender | ✅ đã dùng thật — nên dùng lại mỗi khi cần biết model có gì bên trong |

## ADR cần tạo

Không. Đợt 3 nằm trong phạm vi [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) đã Accepted; đây là kế hoạch thi hành, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [ART-QUALITY-STANDARD.md](ART-QUALITY-STANDARD.md) — thước đo ĐẠT/KHÔNG ĐẠT (§1 đã sửa 2026-09-01: đề tài là tu tiên, benchmark NSO chỉ còn đo độ hoàn thiện)
- [ART-PIPELINE.md](ART-PIPELINE.md) — đường xuất asset
- [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) — thao tác + bảng lỗi
- [STORY.md §7](../13-game-design/STORY.md) — lore của từng thực thể

## Việc cần làm tiếp theo

1. ✅ Phép đo quyết định (§4.3) đã chạy xong 2026-09-01 — không cần Blender, không tốn hạn mức.
2. Chủ dự án tick bảng §5: **#2 là ô quan trọng nhất** (đổi màu texture, rẻ) — cộng #3, #4, #5.
3. Sau khi tick #2: đổi màu 15 texture → `runWorldPreview` → nhìn khối lồi trên trán có lộ không.
4. Bật Blender + addon **chỉ khi** đã chốt #3 (hoặc #2b) — trước đó không cần.
