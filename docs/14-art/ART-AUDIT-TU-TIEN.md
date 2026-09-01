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

⇒ Hướng đúng là **ĐỔI MÀU TEXTURE, không sinh lại model** — nhưng cách khoanh vùng phải chuẩn hơn
"bắt theo màu", xem §4.4.

### 4.4. ⚠️ SỬA LẠI §4.3: luật màu toàn cục KHÔNG tổng quát được (đo 2026-09-01)

Bản đầu của tài liệu này kết luận "dải xám sáng 0,75% chỉ có thể là tấm kim loại" và đề xuất đổi
màu theo luật đó cho cả 15 nhân vật. **Kết luận ấy đúng với Hỏa và sai với các hệ khác** — nó được
đo trên MỘT nhân vật rồi suy ra cho 15. Chạy thử (chế độ khô, không ghi file) mới lộ:

| Nhân vật | Pixel lọt luật màu toàn cục | Thực chất |
|---|---|---|
| `char_hoa-b1` | 7.861 | phần lớn là tấm kim loại ✓ |
| `char_thuy-b1` | **55.484** | ❌ chủ yếu là **áo xanh-trắng nhạt** của Thủy |
| `char_tho-b1` | 5.766 | lẫn tông đất |
| `char_phong-b1` | 13.869 | lẫn tông sáng |
| `char_loi-b1` | 1.437 | thiếu |

Siết ngưỡng cho "trung tính hơn" cũng không cứu được: Thổ còn **186**, Lôi còn **159** — tức là
ngưỡng chặt lại vứt mất chính tấm kim loại của hai hệ đó. **Không tồn tại một ngưỡng màu chung.**
Mỗi nhân vật do Rodin sinh độc lập nên có bảng màu riêng.

**Cách khoanh vùng ĐÚNG — và đã kiểm chứng: dùng TRỌNG SỐ XƯƠNG.** Mesh thân có `JOINTS_0`/
`WEIGHTS_0` và rig 11 xương, trong đó có xương `head`. Chỉ rasterise những tam giác có cả ba đỉnh
gắn chủ yếu vào `head` → ra đúng vùng đầu trong không gian UV. Trong vùng đó, thứ duy nhất mang màu
xám trung tính sáng là tấm kim loại (tóc đen, da hồng, khăn đỏ đều không phải). Kết quả nhất quán
hẳn:

| Nhân vật | Xám **trong vùng đầu** |
|---|---|
| Hỏa | 1.875 |
| Thủy | 942 |
| Thổ | 893 |
| Lôi | 599 |

Dải 599–1.875 thay cho dải 599–55.484: **phép khoanh vùng theo xương hoạt động**, luật màu thì không.

### 4.5. ⚠️ SỬA LẠI lần hai: KHÔNG được sửa file trong `assets/exported/`

`assets/exported/` nằm trong `.gitignore` (dòng 51). Model `.glb` ở đó là **sản phẩm build**, sinh
ra từ `assets/raw/blender/characters/*.blend` — và **texture được đóng gói BÊN TRONG file `.blend`**,
không có file ảnh rời nào để sửa.

Hai hệ quả, cả hai đều quan trọng:

1. **Sửa `.glb` xuất ra là công cốc**: lần xuất Blender kế tiếp ghi đè, băng trán quay lại. Nó cũng
   không có trong git nên không revert được bằng `git checkout` — bài học trả giá thật trong lúc
   soạn tài liệu này (may là phép ghi lại `.glb` chỉ THÊM ảnh mới vào cuối buffer chứ không xoá ảnh
   cũ, nên khôi phục được bằng cách trỏ `images[0].bufferView` về chỗ cũ; đã xác minh: đếm lại đúng
   7.861 px như bản gốc).
2. **Việc thật phải làm TRONG Blender**: chạy phép khoanh vùng §4.4 trên ảnh đóng gói của từng
   `.blend`, tô lại, rồi xuất lại 15 nhân vật × 3 tier.

**Vẫn là 0 lượt Rodin** — chỉ đổi chỗ làm, từ "sửa file xuất ra" sang "sửa nguồn rồi xuất lại".

### 4.6. Đã chạy thử TRỌN VÒNG trong Blender — chạy được, nhưng mask còn quá rộng

Ngày 2026-09-01 đã dựng và chạy hết vòng, **không cần addon MCP** (Blender chạy nền qua dòng lệnh,
đúng cách dự án vốn dùng): mở `.blend` → khoanh vùng → tô lại ảnh đóng gói → lưu ra `.blend` tạm →
`export_character_lods.py` → `runWorldPreview` → so ảnh trước/sau bằng máy.

**Chạy được thật**: 5.832 pixel tấm kim loại trong ảnh nguồn 2048², màu thay tự chọn ra
`(0.396, 0.31, 0.278)` — chính tông nâu của dải da chiếc băng. Ảnh diff cho thấy thay đổi rơi
**đúng vào dải băng trán**. Không tốn lượt Rodin nào.

**Nhưng chưa đủ chính xác để chạy hàng loạt.** Xương `head` bao **cả đầu** — tóc, mặt, tai — nên
mask phủ rộng hơn chiếc băng rất nhiều. Đo được: hộp bao vùng pixel thay đổi trên texture 1024 là
`(30,103)–(1006,912)`, tức gần trọn tấm; ảnh diff cũng có vài đốm ở mí mắt. Nói cách khác nó tô
đúng chiếc băng **và** vài chỗ xám trung tính khác trên đầu.

### 4.7. Đã thử BỐN cách siết mask — không cách nào tự động hoàn toàn mà chạy được

Sau khi phát hiện mask "cả đầu" quá rộng, đã thử lần lượt và **đo lại bằng ảnh mỗi lần**:

| Cách | Kết quả đo | Vì sao hỏng |
|---|---|---|
| Ngưỡng xám cố định (110–200) | vùng đổi trải gần trọn texture | Bỏ sót mặt **bắt sáng** của tấm (~240), tô lan chỗ khác |
| Lọc theo **cụm độ cao** (dải Z) | 5.832 → 1.864 px, hộp bao còn **20×79** | Gọn nhưng **cắt mất chính tấm** — tấm là hình chữ nhật CAO, không phải vành mỏng; chỉ tô được một sọc bên trái |
| **Mảng liên thông** + phủ hộp bao | tô được ~một nửa tấm | Hoạ tiết khắc và đinh tán chia tấm thành nhiều mảnh vụn |
| Nới trần độ sáng lên 250 | mảng lớn nhất **nhảy sang vùng khác** | Ở dải sáng đó, lòng trắng mắt/highlight lớn hơn cả tấm |

Ảnh phóng to vùng texture cho thấy rõ vì sao khó: tấm kim loại có **mặt bắt sáng gần trắng, vùng
trong bóng xám trung, đinh tán đen, và một hoạ tiết khắc tối ngang màu tóc** — bốn dải giá trị
khác nhau, mỗi dải lại trùng với một bộ phận khác trên đầu.

**Kết luận: đừng cố dò tự động.** Thiết kế đúng là **CHỈ ĐIỂM** — người mở texture trong UV editor
của Blender, đọc một toạ độ nằm trên tấm kim loại, truyền vào `--seed u,v`; từ đó phép loang theo
"giống kim loại" + phủ hộp bao làm nốt, và nó chắc chắn. Chi phí: **15 lần chỉ điểm cho 15 nhân
vật** — rẻ hơn nhiều so với một heuristic không đáng tin.

### 4.8. ✅ ĐÃ GIẢI XONG — và không cần mắt người

Bản trước của mục này kết luận "phải mở UV editor mới biết toạ độ". **Sai nốt.** Ba việc gỡ được
hết bằng máy:

1. **Xuất ảnh theo đúng hệ toạ độ của script** (`--dump`): mọi tranh cãi về quy ước lật trục V
   biến mất — toạ độ đọc trên ảnh đó dùng thẳng được.
2. **Hạt giống tự bám** sang pixel hợp lệ gần nhất. Lý do bốn lần chỉ điểm ĐÚNG vẫn bị từ chối:
   tấm kim loại có **hoạ tiết khắc ở chính giữa**, nên trọng tâm rơi vào chỗ tối; và mask (dựng
   bằng cách đóng dấu quanh từng đỉnh) có **lỗ rỗ** ở những đảo UV thưa đỉnh.
3. **Loang KHÔNG đòi hỏi mask**. Chính lỗ rỗ đó chặn đứt phép loang giữa tấm — lần đầu chỉ phủ
   được 1/8 tấm. Hạt giống đã neo đúng chỗ rồi, đi theo "giống kim loại" là đủ và không nhảy sang
   đảo khác.

Thêm hai chi tiết phải đúng thì màu mới đúng: lấy màu vải từ **một vành cách hộp bao 3–12 px**
(sát mép ra màu trắng của viền tấm; lấy sau khi nới hộp bao ra màu đen của tóc), và **ưu tiên màu
có sắc độ**.

**Kết quả trên `char_hoa-b1`, đã nhìn tận mắt**: tấm kim loại — cả đinh tán lẫn hoạ tiết khắc —
**biến mất hoàn toàn**, thay bằng vải đỏ trơn `(0.824, 0.11, 0.18)` lấy từ chính chiếc băng. Trong
game ở cỡ thật, nhân vật đội một dải khăn đỏ trơn; khối lồi còn lại trên mesh **không lộ**.

### 4.9. Tự động chọn hạt giống: đúng trên Hỏa B1, SAI trên Hỏa B2

Đã thử nốt một bước tự động hoá (`--auto`): coi tấm kim loại là vùng "giống kim loại" **loang ra
lớn nhất**. Trên `char_hoa-b1` nó đúng — 10.020 px, bỏ xa ứng viên kế tiếp (2.409, chính là lòng
trắng mắt mà cách đếm ở §4.7 đã chọn nhầm.)

**Nhưng trên `char_hoa-b2` nó sai**: chọn ra một vùng 48.384 px và lấy màu thay gần trắng
`(0.957, 0.882, 0.816)` — màu da, không phải vải băng. Tức là ở nhân vật đó có một mảng sáng nhạt
lớn hơn cả tấm kim loại.

**Đây là lần thứ bảy một luật chung bị chính phép đo bác bỏ, và nó đủ để rút ra kết luận:** 15
nhân vật do Rodin sinh độc lập, mỗi con một bảng màu và một cách tô bóng, nên **không có heuristic
chung nào đáng tin**. Đừng thử cái thứ tám.

**Cách chắc chắn, và rẻ**: mỗi nhân vật xuất một ảnh `--dump` (đúng hệ toạ độ của script), nhìn
một lần, đọc toạ độ tấm kim loại, chạy `--seed u,v`. **15 lần nhìn cho 15 nhân vật.** Phần còn lại
— loang, hộp bao, chọn màu vải, xuất lại 3 tier — đã tự động và đã kiểm chứng.

**Không có file nguồn nào bị sửa trong đợt thử này** — thao tác trên `.blend` tạm ngoài repo, và
model xuất ra đã được khôi phục, xác minh bằng cách đếm lại đúng **7.861** px xám như bản gốc.
Script để ở `tools/blender/strip_headband.py` kèm ghi chú trạng thái ngay đầu file.

⚠️ `char_town-elder.glb` **không có texture nào cả** (đọc file báo thiếu hẳn mảng `images`) — khớp
đúng với ảnh render: nó là khối hộp phẳng thật, không phải model bị mất texture. Xác nhận §4.1.

---

## 5. Bảng quyết định — chủ dự án tick

| # | Việc | Chi phí ĐO ĐƯỢC | Cần quyết |
|---|---|---|---|
| ~~1~~ | ~~Băng trán rời hay dính?~~ | ✅ **Đã đo 2026-09-01** — dính mesh nhưng tách bạch trong texture (§4.3) | Xong |
| 2 | Bỏ băng trán khỏi 15 nhân vật bằng `strip_headband.py --seed` | **0 hạn mức Rodin**. Công cụ ĐÃ CHẠY XONG và **đã kiểm chứng bằng mắt trên `char_hoa-b1`** (§4.8): tấm kim loại + đinh tán + hoạ tiết khắc biến mất, thay bằng vải đỏ trơn. Còn 14 nhân vật, mỗi con cần đúng **một lần nhìn** để đọc toạ độ hạt giống (§4.9) | ⬜ **Nhìn ảnh trước/sau rồi gật** — 15 file nguồn `.blend` sẽ bị sửa (revert được, chúng nằm trong git LFS) |
| 2b | *Hoặc* sinh lại 15 nhân vật cho sạch hẳn khối lồi | **15 lượt Rodin** + 15 vòng nghiệm thu | ⬜ Chỉ chọn nếu #2 nhìn không đạt |
| 3 | Làm mới `char_town-elder` thành Chu Hoành | 1 lượt Rodin + 1 vòng nghiệm thu | ⬜ Đồng ý tiêu hạn mức? |
| 4 | Sửa `icon_genin_headband` → mũ vải ngoại môn | 1 icon, dựng tay | ⬜ |
| 5 | Sửa `icon_steel_katana` → trường kiếm thẳng | 1 icon, dựng tay | ⬜ Hay chấp nhận đao cong? |
| 6 | 17 VFX + 16 icon + 3 quái | **0** | Không cần quyết — giữ nguyên |

**Đề xuất của tôi**: làm #2 trước — nó không tốn hạn mức, và phép khoanh vùng theo xương đã được
kiểm chứng trên 4/5 hệ (§4.4). Xuất lại xong thì chụp `runWorldPreview` xem khối lồi trên trán có
lộ ở cỡ trong game không, rồi mới quyết có cần #2b. Không bấm lượt Rodin nào cho tới lúc đó — trừ
#3, vốn phải sinh lại bất kể đề tài.

⚠️ **Cả #2 lẫn #3 đều cần Blender đang chạy kèm addon.** Kiểm 2026-09-01: chưa chạy, nên phần thi
hành của Đợt 3 dừng ở đây. Mọi thứ KHÔNG cần Blender trong tài liệu này thì đã làm xong.

⚠️ **Bẫy hạn mức Rodin đã dính một lần** (2026-08-30, art 2 loài quái): hết hạn mức giữa chừng thì
phiên art đứt đoạn và phải chờ. Chốt danh sách TRƯỚC khi bấm lượt đầu tiên.

⚠️ **Blender phải đang chạy kèm addon** thì công cụ MCP mới nối được — kiểm 2026-09-01: chưa chạy.
Xem [BLENDER-SESSION-GUIDE](BLENDER-SESSION-GUIDE.md).

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | VFX/icon/quái trung tính về đề tài — Đợt 3 KHÔNG đụng tới | 🟡 đề xuất |
| 2 | 15 nhân vật SỬA bằng tô lại texture, không sinh lại | 🟡 đề xuất |
| 2b | Khoanh vùng tấm kim loại bằng **trọng số xương**, KHÔNG bằng luật màu | ✅ **đã kiểm chứng** (§4.4) — luật màu đã thử và HỎNG trên Thủy/Thổ/Lôi |
| 2c | Trọn vòng Blender headless (tô lại → xuất lại → render → diff) chạy được, 0 lượt Rodin | ✅ **đã chạy thật** (§4.6) — còn phải siết mask từ "cả đầu" xuống "trán" |
| 3 | `char_town-elder` làm mới, lý do là chất lượng chứ không phải đề tài | 🟡 đề xuất |
| 4 | Không bấm lượt Rodin nào trước khi chốt xong bảng §5 | 🟡 đề xuất |
| 5 | Đo cấu trúc `.glb` bằng cách đọc chunk JSON, không cần mở Blender | ✅ đã dùng thật — nên dùng lại mỗi khi cần biết model có gì bên trong |
| 6 | Sửa art phải sửa **nguồn `.blend`**, không sửa `assets/exported/` (gitignore, là build artifact) | ✅ **đã trả giá một lần** (§4.5) |

## ADR cần tạo

Không. Đợt 3 nằm trong phạm vi [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) đã Accepted; đây là kế hoạch thi hành, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [ART-QUALITY-STANDARD.md](ART-QUALITY-STANDARD.md) — thước đo ĐẠT/KHÔNG ĐẠT (§1 đã sửa 2026-09-01: đề tài là tu tiên, benchmark NSO chỉ còn đo độ hoàn thiện)
- [ART-PIPELINE.md](ART-PIPELINE.md) — đường xuất asset
- [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) — thao tác + bảng lỗi
- [STORY.md §7](../13-game-design/STORY.md) — lore của từng thực thể

## Việc cần làm tiếp theo

1. ✅ Phép đo cấu trúc (§4.3) + phép khoanh vùng theo xương (§4.4) đã chạy xong 2026-09-01 — không cần Blender, không tốn hạn mức. Hai kết luận vội của bản đầu đã được sửa ở §4.4 và §4.5.
2. Chủ dự án tick bảng §5: **#2 là ô quan trọng nhất** — cộng #3, #4, #5.
3. ✅ Không cần addon MCP: Blender chạy **nền qua dòng lệnh** là đủ, trọn vòng đã chạy thật (§4.6).
4. Việc kỹ thuật còn lại của #2 (§4.7): **mở texture trong UV editor của Blender, đọc toạ độ `u,v` của tấm kim loại** cho 1 nhân vật để chốt quy ước trục, rồi lấy nốt 14 toạ độ còn lại và chạy hàng loạt 15 nhân vật × 3 tier + chụp nghiệm thu. Đây là việc **cần mắt người nhìn một lần**, không đoán mò được.
5. #3 (`char_town-elder` → Chu Hoành) là việc DUY NHẤT còn cần Rodin, nên cần chủ dự án gật trước.
