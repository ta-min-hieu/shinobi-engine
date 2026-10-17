# GAME-DESIGN-DOCUMENT.md

**Thiết kế game — game này LÀ GÌ**

| | |
|---|---|
| Version | 0.3.0 — **re-theme tu tiên** theo [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) (✅ Accepted 2026-09-01) |
| Status | Draft — cần duyệt trước khi khoá scope MVP |
| Liên quan | [GAMEPLAY.md](../05-server/GAMEPLAY.md) — server implement thế nào |

> Các tài liệu 00–12 nói **xây dựng thế nào**. Tài liệu này nói **xây dựng cái gì**.
> Mọi hệ thống gameplay phải map ngược về một mục ở đây trước khi có Component/System ([GAMEPLAY.md §1](../05-server/GAMEPLAY.md)).
> ⚠️ **Cơ chế trong bản 0.3.0 giữ nguyên 100% so với 0.2.0** — chỉ đổi bối cảnh/tên gọi
> ([ADR-038 D5](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)). Tên code/DB/opcode không đổi.

---

## 1. Thể loại & Cảm hứng

2D **tu tiên MMORPG side-view** — tường thuật kiểu truyện tiên hiệp *phàm nhân nghịch tập*
(dòng *Phàm Nhân Tu Tiên*), khung cơ chế kế thừa dòng game side-view hành động
(Ninja School Online, Ngôi Sao Trắng — nguồn gốc lịch sử của cơ chế, xem ADR-033/034).

⚠️ **Cảm hứng ≠ sao chép** (Điều 5). Không sao chép IP, tên riêng, cốt truyện, asset. Thế
giới/nhân vật là **nguyên bản**; thuật ngữ quy ước thể loại (Luyện Khí, Trúc Cơ, linh căn, bí
cảnh...) dùng chung toàn dòng tiên hiệp, không thuộc IP nào. Chỉ vay mượn **cơ chế + fantasy**
đặc trưng:

- Phàm nhân xuất phát thấp (tạp dịch ngoại môn) → từng bước thành tiên — tiến trình là cốt lõi
- Side-view, khinh công nhảy nhiều tầng địa hình (cơ chế [ADR-033](../01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md) giữ nguyên)
- Combat real-time (chọn mục tiêu + bấm đánh — [ADR-034](../01-architecture/adr/ADR-034-Manual-Attack-And-Experience.md))
- Linh căn nguyên tố thay class cố định
- Độ kiếp chuyển thế mở rộng vòng đời
- Bang hội, PvP, thời trang, linh thú, sự kiện mùa

---

## 2. Game Pillars

Mọi tính năng phải phục vụ ≥1 pillar. Không → loại khỏi scope.

1. **Tiến trình đọc được** — người chơi luôn biết bước tiếp theo để mạnh hơn. Cảnh giới tu luyện
   (§5) là hiện thân trực tiếp: ai cũng biết sau Luyện Khí là Trúc Cơ.
2. **Chơi cùng nhau có ý nghĩa** — bang/PvP/event tạo lý do chơi cùng người khác.
3. **Chiều sâu không phụ thuộc grind thuần** — build đa dạng > số giờ cày.
4. **Server Authority tuyệt đối** — Điều 15.

---

## 3. Thế giới

**Đại lục Huyền Thương** (tên đề xuất — ADR-038 D6). Trời đất có **Linh Khí**; kẻ có **linh căn**
hấp thu được Linh Khí để tu luyện. Sau **Thiên Địa Đại Kiếp**, linh khí đại lục thất thường, các
tông môn phải mở rộng thu nhận cả phàm nhân có linh căn mỏng — người chơi là một phàm nhân như
thế, vào **Thanh Hư Tông** (tên đề xuất) làm **đệ tử tạp dịch ngoại môn**: điểm khởi đầu.

### Bản đồ

| Khu vực | Vai trò |
|---|---|
| **Thanh Hư Tông** | Hub trung tâm, safe zone tuyệt đối (NPC trưởng lão, cửa hàng, nhiệm vụ) |
| **5 Linh Mạch** (theo nguyên tố) | Luyện cấp theo hệ linh căn, yêu thú khó dần theo cảnh giới |
| **Hoang Vực** | PvP mở, tài nguyên hiếm (linh thảo, khoáng thạch). _Trạng thái code 2026-09-24: vùng chưa được dựng ⇒ PvP tạm cấm mọi nơi trừ Arena (chủ dự án quyết; xem STORY.md §7)._ |
| **Bí Cảnh** | Instance theo party + Hard Mode + Yêu Vương (World Boss) |

---

## 4. Năm linh căn (thay "class")

Thức tỉnh linh căn ở **Lv10** (Luyện Khí tầng 5). Hỏa/Thủy/Thổ là **ngũ hành linh căn** phổ biến;
**Phong/Lôi là dị linh căn** — hiếm, được NPC trầm trồ (flavor, KHÔNG mạnh hơn — cân bằng như nhau).

| Linh căn | Nguyên tố | Thiên hướng | Khắc | Bị khắc |
|---|---|---|---|---|
| Hỏa | Lửa | Sát thương liên tục, bùng nổ | Phong | Thủy |
| Thủy | Nước | Khống chế, hồi phục | Hỏa | Thổ |
| Phong (dị) | Gió | Tốc độ, bạo kích | Thổ | Hỏa |
| Lôi (dị) | Sấm | Đơn mục tiêu, xuyên giáp | — | — |
| Thổ | Đất | Phòng thủ, khiên | Thủy | Phong |

⚠️ Hệ số khắc chế ±15% là **số tham khảo, phải cân bằng qua playtest** (Phase 4+). Lôi trung lập
để cân bằng meta. Enum code `FIRE/WATER/WIND/THUNDER/EARTH` giữ nguyên (ADR-038 D3).

Đổi linh căn 1 lần miễn phí trước Lv30, sau đó tốn **Tẩy Căn Đan** (code: Faction Change Stone).

---

## 5. Nhân vật & Tiến trình

### Chỉ số

Cơ bản: `STR · AGI · INT · VIT · Linh Lực` (code `chi` — giữ nguyên)
Dẫn xuất (⚠️ **server tính** — [GAMEPLAY.md §2](../05-server/GAMEPLAY.md)): `ATK, SKILL_DMG, DEF, RES, HIT, DODGE, CRIT, CRIT_DMG, ATK_SPEED, MOVE_SPEED`

`DEF` (giáp, chống vật lý) và `RES` (kháng phép) tách riêng — kiểu AD/AP của LMHT/Liên Quân: đòn
đánh thường (STR/AGI) đi qua `DEF`, pháp thuật linh căn (INT) đi qua `RES`. Build "thể tu" (tank
giáp) khác "pháp tu kháng phép".

**Công thức derive** (đặt ở `CombatConstants`, KHÔNG hardcode rải rác — [CODING-STANDARDS.md §18](../02-coding-standards/CODING-STANDARDS.md)). ⚠️ Hệ số dưới đây là **placeholder — chưa cân bằng, chỉnh qua playtest**:

| Chỉ số | Công thức |
|---|---|
| `ATK` (sức mạnh vật lý) | `STR × 2 + AGI × 1` |
| `SKILL_DMG` (sức mạnh phép) | `INT × 2.5` |
| `DEF` (giáp) | `VIT × 1.5 + STR × 0.5` |
| `RES` (kháng phép) | `VIT × 1.0 + INT × 0.5` |
| `HIT%` | `90 + AGI × 0.1`, kẹp 50–100% |
| `DODGE%` | `AGI × 0.15`, kẹp 0–30% (tránh né 100%) |
| `CRIT%` | `AGI × 0.1`, kẹp 0–50% |
| `CRIT_DMG` | 150% cố định ở MVP (pháp khí cộng thêm — Post-MVP) |
| `ATK_SPEED` | base + bonus nhỏ theo `AGI` |
| `MOVE_SPEED` | hằng số server-world riêng (`WorldConstants`, chưa gắn theo stat nhân vật — Phase 3) |

### Lực Chiến (Combat Power)

Chỉ số **tổng hợp để hiển thị UI/xếp hạng** — **KHÔNG BAO GIỜ dùng để tính damage thật** (damage
luôn tính từ chỉ số riêng ở §6, Điều 15 — giá trị 1 chiều, chỉ đọc). Đặt ở `CombatConstants`, hệ
số placeholder:

```
LucChien = HP × 1 + Chi × 0.5
         + (ATK + SKILL_DMG) × 3.5
         + (DEF + RES) × 3
         + CRIT% × 100 × 2
         + DODGE% × 100 × 1.5
         + HIT% × 100 × 0.5
         + ATK_SPEED_bonus% × 100 × 1.5
         + Level × 50
```

### Cảnh giới & Độ Kiếp Chuyển Thế

Cơ chế level giữ nguyên (`level` 1–100, EXP — code không đổi); **cảnh giới là cách trình bày**
([ADR-038 D2](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)):

**6 đại cảnh giới, mỗi cảnh giới 9 tầng** (chuẩn thể loại — chủ dự án chỉnh 2026-09-01), gom
Sơ kỳ (tầng 1–3) / Trung kỳ (4–6) / Hậu kỳ (7–9):

| Level | Cảnh giới | Tầng | Mốc mở khoá |
|---|---|---|---|
| 1–18 | **Luyện Khí** | 1–9 (2 lv/tầng) | Lv10 thức tỉnh linh căn (tầng 5) · Lv15 công pháp (tầng 8) |
| 19–36 | **Trúc Cơ** | 1–9 | Lv20 linh thú (tầng 1) · Lv25 bang hội (tầng 4) · Lv30 PvP (tầng 6) |
| 37–54 | **Tử Phủ** | 1–9 | Lv50 Thần Thông (tầng 7) |
| 55–72 | **Kim Đan** | 1–9 | — |
| 73–90 | **Nguyên Anh** | 1–9 | — |
| 91–100 | **Hoá Thần** | 1–9 (1 lv/tầng), Lv100 = **Viên Mãn** | Lv100 Độ Kiếp |

Hiển thị client-only: 5 cảnh giới đầu `tầng = ceil((lv − start + 1) / 2)`; Hoá Thần 1 lv/tầng —
cảnh giới cuối lên tầng nhanh, cao trào trước Độ Kiếp. Nâng trần level KHÔNG có kế hoạch (rebirth
thay thế); nếu có thì thang mở tiếp: Luyện Hư → Hợp Thể → Đại Thừa.

- **Độ Kiếp Chuyển Thế** (Lv100, code: Rebirth): độ kiếp phi thăng, chuyển thế trùng tu từ Luyện
  Khí tầng 1, giữ pháp khí/danh hiệu, nhận **Đạo Vận** (code: Origin Point) vĩnh viễn. Tối đa 5
  lần ở MVP (Feature Flag mở rộng).
- Mục đích: kéo dài vòng đời không cần nâng trần level vô hạn (tránh power creep).

### Công pháp (Skill)

- Mỗi linh căn: 3 pháp thuật chủ động + 2 tâm pháp bị động + 1 **Thần Thông** (Lv50 — Tử Phủ
  tầng 7, CD 60–120s, tốn toàn bộ Linh Lực)
- Điểm công pháp từ cảnh giới + nhiệm vụ — **không mua bằng tiền thật**

### Pháp khí (Trang bị)

Slot: Vũ khí · Giáp · Mũ · Găng · Giày · Phụ kiện ×2 (code/DB `item_template` giữ nguyên)

- **Tôi luyện** (cường hoá) +0→+15, tỉ lệ giảm dần, có **Hộ Linh Phù** (Protect Scroll) chống rớt
  cấp từ +10 (chống frustration). Hiệu ứng chỉ số theo [ADR-036](../01-architecture/adr/ADR-036-Equipment-Stats-And-Enhancement-Effect.md).
- **Linh Ngọc** khảm nạm (Post-MVP): bonus phụ, có **pity system**
- **Bộ pháp khí** 4/6/8 món: khuyến khích build đa dạng, không 1 món OP tuyệt đối

### Linh thú & Phi kiếm

- Linh thú (Pet): buff/damage phụ, level riêng, tiến hoá
- Phi kiếm/linh cầm (Mount): **chỉ tốc độ + thẩm mỹ, KHÔNG cộng chỉ số** (pay-for-cosmetic sạch)

---

## 6. Combat

Real-time side-view — "đấu pháp" tốc độ cao, khinh công nhảy né đòn. Chọn mục tiêu bằng chuột rồi
**BẤM** đánh thường (phím Z hoặc nút Đánh) — không auto-target
([ADR-034](../01-architecture/adr/ADR-034-Manual-Attack-And-Experience.md), chủ dự án quyết
2026-08-09); yêu thú/Yêu Vương vẫn tự đánh. Pháp thuật chủ động bằng phím 1–6 / chạm.

| Loại | Nội dung |
|---|---|
| PvE | Yêu thú thường; Bí Cảnh party (2–5); Yêu Vương (World Boss) theo lịch |
| PvP | Luận kiếm 1v1 (Arena, MMR/season); Chiến trường 5v5 (objective); Tranh đoạt Linh Mạch (guild war) |

⚠️ Toàn bộ combat **server tính** (Điều 15). Vi phạm = bug nghiêm trọng.

### Công thức damage

Chỉ số + hệ số derive ở [§5](#5-nhân-vật--tiến-trình). Pipeline 1 đòn đánh/pháp thuật, theo thứ tự:

1. **Né**: `roll(clamp(attacker.HIT − defender.DODGE, 50%, 100%))` — trượt → 0 damage, dừng ở đây.
2. **Damage thô**: `attacker.ATK` (đòn vật lý) hoặc `attacker.SKILL_DMG` (pháp thuật linh căn) × hệ số riêng của skill + bonus cố định (định nghĩa ở `skill_template`, không hardcode — GAMEPLAY.md §4).
3. **Bạo kích**: `roll(attacker.CRIT%)` — trúng → nhân thêm `attacker.CRIT_DMG`.
4. **Giảm giáp/kháng** — công thức kiểu LMHT/Liên Quân, diminishing returns (giáp càng cao, hiệu quả biên càng giảm; xuyên giáp bù lại):
   ```
   effectiveDEF = max(0, DEF × (1 − %xuyên_giáp) − xuyên_giáp_thẳng)
   mitigation   = 100 / (100 + effectiveDEF)
   ```
   (damage phép tính y hệt, thay `DEF` bằng `RES`)
5. **Khắc chế linh căn** (§4): nhân thêm `1.15` (khắc) / `0.85` (bị khắc) / `1.0` (trung lập).

```
finalDamage = rawDamage × mitigation × elementMultiplier
```
(`rawDamage` ở bước 2 đã nhân `CRIT_DMG` nếu bạo kích ở bước 3.)

⚠️ Toàn bộ hệ số trên là **placeholder — chưa cân bằng qua playtest**. Không phải quyết định cuối
cùng, nhưng đủ cụ thể để code `CombatSystem` ([GAMEPLAY.md §3](../05-server/GAMEPLAY.md)).

### Luật biên thật — trận tay đôi bắt buộc của chuỗi quest (chốt 2026-10-01)

**Số đo dẫn tới luật.** Playtest 2026-09-17 (`gameProbe`, nhân vật Cấp 30, bộ Trúc Cơ: công 82 ·
thủ 65 · máu 206): `wasteland_beast` (Lv29, 280/44/32) trên giấy là trận thắng — người chơi cần
**5** nhát, quái cần **8** ⇒ biên giấy **+3**. Ba trận 1v1 thật từ máu đầy: **1 thắng, 2 chết**.
Biên giấy bỏ sót hai số hạng:

- **Nhát phủ đầu** — quái ra 1–2 nhát trong lúc người chơi đi tới/đứng vào thế đánh, kể cả với
  `aggro_radius_meters = 3` (V46/V47 đo hai lần độc lập). Luật lấy cận trên: **2**.
- **Một lần né** — người chơi trúng ~91% (bước 1 ở trên); mỗi lần hụt là trả trọn một nhát của quái.
  Luật dự trữ: **1**.

**Luật** (chủ dự án chốt 2026-10-01, thay luật "biên ≥ 2" cũ):

```
biên giấy = (số nhát quái cần để hạ người chơi) − (số nhát người chơi cần để hạ quái)
biên thật = biên giấy − 2 (phủ đầu) − 1 (dự trữ một lần né)
Trận tay đôi BẮT BUỘC của chuỗi quest phải có  biên thật ≥ +2   (tức biên giấy ≥ +5)
```

- Sát thương mỗi nhát tính bằng ATK trung bình, không bạo kích, theo công thức giảm giáp ở trên;
  hai bên chung nhịp 1 đòn/giây; **hoà nghĩa là chết**.
- Người chơi đo theo **trang bị mốc** mà lớp cân bằng của chính trận đó dùng (bộ Tạp Dịch / Ngoại
  Môn / Trúc Cơ).
- Máu đầu vào là máu lúc **tới nơi** sau thuế đường đi của các loài KHÁC (nếu trận đó có mô hình
  đường đi), **không** trừ nhát phủ đầu của chính đối thủ — số hạng −2 đã gánh phần đó.
- Mã: `DuelMargin` (`server-world`, test) + các lớp `*BalanceTest`.

**Áp luật lên mọi trận đang có lớp cân bằng** (2026-10-01):

| Trận (loài) | Cấp | Trang bị mốc | Biên giấy trước | Biên thật trước | Sau V50 |
|---|---|---|---|---|---|
| `wandering_bandit` (quest 2) | 4 | Tạp Dịch | 4 | +1 | 🔴 chưa chỉnh |
| `archive_warden` (quest 3) | 5 | Tạp Dịch | 2 | −1 | 🔴 chưa chỉnh |
| `trial_construct` (quest 4, đóng arc 1 nửa đầu) | 8 | Tạp Dịch | 3 | 0 | 🔴 chưa chỉnh |
| `vein_raider` (quest 10) | 10 | Tạp Dịch | 2 | −1 | 🔴 chưa chỉnh |
| `raider_chieftain` (quest 12) | 15 | Ngoại Môn | 2 | −1 | 🔴 chưa chỉnh |
| `blood_sand_acolyte` (quest 13–15) | 22 | Ngoại Môn | 2 | −1 | 🔴 chưa chỉnh |
| `blood_sand_ringleader` (quest 13–15) | 22 | Ngoại Môn | 2 | −1 | 🔴 chưa chỉnh |
| `wasteland_beast` (quãng quest 16–20) | 29 | Trúc Cơ | 3 | 0 | ✅ 280/44/32 → **289/36/32**: biên giấy 5, biên thật **+2** |
| `foundation_warden` (quest 20, **đóng arc 2**) | 33 | Trúc Cơ | 3 | 0 | ✅ 300/46/35 → **300/37/35**: biên giấy 5, biên thật **+2** |

**Vì sao bảy trận đầu chưa chỉnh.** Quét toàn bộ không gian chỉ số cho từng loài: mọi bộ số đạt
luật đều phá một luật đang khoá — tay không phải thua thạch vệ/cỗ khảo hạch (bộ Tạp Dịch chỉ hơn
tay không +7/+7/+18, quá hẹp để vừa "phải sắm đồ" vừa "thắng chắc"); bộ Tạp Dịch phải thua đầu
lĩnh đoạt mạch; bậc thang loài (tán tu < thạch vệ < cỗ khảo hạch; `vein_raider` cùng máu với cỗ
khảo hạch; đầu lĩnh < tế đồ < đàn chủ < yêu thú); và trận THUA đo thật của V46 (đổi công tán tu làm
nó lật thành thắng). Đây là câu hỏi **thiết kế** (trang bị mốc / bậc thang), không phải một con
số — chờ chủ dự án. Các ô `*ViolatesTheRealMarginRule` khoá đúng con số vi phạm hiện tại.

**Hai điểm của V50 cần biết.** (1) Máu yêu thú TĂNG 280 → 289 — phần thêm duy nhất, để bộ Ngoại
Môn vẫn không thắng (dưới 289 thì nó cần 6 nhát và thắng, tức quest mở lát cắt thôi cần trao bộ
Trúc Cơ). (2) Không cặp chỉ số nào vừa đạt luật vừa giữ bộ Ngoại Môn **thua hẳn**; mức tốt nhất
là **hoà 7–7 = chết**, biên thật −2. Phép so trong `FoundationTrialBalanceTest` vì vậy đổi từ
"âm" sang "không dương" (✅ chủ dự án duyệt cả hai điểm 2026-10-01). Số đo 1 thắng/2 chết ở trên là của V48; V50 đã kiểm sống — xem mục ngay dưới.

#### Kiểm sống V50 trên DB vứt đi (2026-10-01)

**Cách đo.** DB dev `kage` KHÔNG chạy V50 (vẫn ở V49 — chủ dự án chưa duyệt V50, Flyway không lùi
được). Thay vào đó: mạng docker riêng `kage-v50check` gồm Postgres 17 + Redis trắng, server-gateway
build từ cây có V50 chạy trong container `eclipse-temurin:25-jre`, cổng host 7790 (cổng 7777 viết
cứng trong `GatewayBootstrap`, nên tách bằng ánh xạ cổng của docker chứ không bằng biến môi trường).
Flyway chạy V1→V50 trên DB trắng; xác nhận `wasteland_beast 289/36/32`, `foundation_warden 300/37/35`.
Nhân vật Cấp 30 hệ HOẢ, chỉ số nền 10/10/10/10, mặc trọn 6 món `foundation_*` bằng SQL — vào world
**máu 206/206**, đánh **62** lên yêu thú / **61** lên cỗ khảo hạch, ăn **22**/nhát từ cả hai, đúng
từng điểm với `FoundationTrialBalanceTest`. `gameProbe --attackTarget=nearest
--attackIntervalMillis=1100 --observeSeconds=20` (ngắn hơn `respawn_seconds` 30), xuất phát TRONG
trạm nghỉ ngay trước quái (x=169 → đi tới 176; x=195 → đi tới 201), quãng đi ≤7 m, mỗi lượt từ máu đầy.

| Lượt | Đối thủ | Nhát quái ra trước nhát đầu của ta | Ta hụt | Quái hụt | Máu thấp nhất | Kết quả |
|---|---|---|---|---|---|---|
| 1 | `wasteland_beast` @175 | 2 | 0 | 2 | 96/206 | thắng |
| 2 | `wasteland_beast` | 1 | 1 | 1 | 74/206 | thắng |
| 3 | `wasteland_beast` | 1 | 1 | 1 | 74/206 | thắng |
| 4 | `wasteland_beast` | **3** | 0 | 0 | 52/206 | thắng |
| 5 | `wasteland_beast` | 1 | **2** | 0 | **30/206** | thắng |
| 1 | `foundation_warden` @201 | 2 | 0 | 0 | 52/206 | thắng |
| 2 | `foundation_warden` | 1 | 0 | 1 | 96/206 | thắng |
| 3 | `foundation_warden` | 2 | 0 | 0 | 52/206 | thắng |
| 4 | `foundation_warden` | 2 | 1 | 0 | **30/206** | thắng |
| 5 | `foundation_warden` | 1 | 1 | 0 | 74/206 | thắng |

**Kết luận: PASS — 10/10 thắng** (V48: yêu thú 1 thắng 2 chết; cỗ khảo hạch 2 thắng sít + 1 chết).
Trường hợp xấu nhất (lượt 5 yêu thú, lượt 4 cỗ khảo hạch) là đúng tổ hợp luật dự trữ — tổng
"phủ đầu + hụt" = 3 — và còn **30 máu = đúng 1 nhát + 8**, tức quái đánh trúng 8 trong 10 nhát nó cần: còn thiếu đúng **2
nhát**, khớp `biên thật = +2` của `DuelMargin`. Ta trúng 50/56 nhát (~89%, khớp ~91%); bạo kích 2 lần.

**Ba chỗ mô hình và thực đo lệch nhau (không lật kết luận):**
1. **Phủ đầu có lúc là 3** (lượt 4 yêu thú), vượt cận trên 2 của luật. Nguyên nhân thấy được trong
   log: ở 5/10 lượt, nhát đầu của ta đến **~1,15–1,3 s sau** lúc "đi bộ xong" thay vì ngay lập tức
   (nghi lệnh đánh đầu tiên bị bỏ vì chưa vào tầm/đang cooldown — chưa xác minh nhân quả, có thể là
   đặc tính của `gameProbe`, xem bẫy nhịp đánh). Lượt đó vẫn thắng vì không hụt nhát nào; nếu 3 phủ
   đầu trùng 2 lần hụt thì chỉ còn dư 0 nhát (8 máu) — vẫn sống, nhưng sát nút.
2. **Nhịp không chung**: quái đánh đều 1,0 s; ta (probe gửi 1100 ms) ra nhát mỗi ~1,1–1,25 s. Mô hình
   giả định chung nhịp 1 đòn/giây ⇒ lạc quan cho người chơi ~10–20% số nhát trao đổi. Client thật
   chưa đo nhịp này.
3. **Quái cũng hụt** (5/70 đòn, ~7%) — mô hình không tính, có lợi cho người chơi, bù một phần cho (1)–(2).

📌 **Nhân quả của (1)–(2) đã xác minh 2026-10-01 — phần lớn là lỗi CÔNG CỤ đo.** Server bỏ IM LẶNG lệnh
đánh khi mục tiêu còn ngoài tầm 2 m (`CombatSystem.tryAttackNow`) và CỐ Ý không giữ lại lệnh đó; lúc
probe "đi bộ xong" con quái thường còn đang lao tới (> 2 m), nên lệnh đầu bị bỏ, rồi probe chờ trọn một
nhịp 1100 ms (+ tới 200 ms chặn đọc) mới gửi lại ⇒ đúng dải 1,15–1,3 s. Probe đã sửa (`AttackCadence`:
thử lại mỗi 100 ms tới khi server xác nhận nhát đầu, sau đó giữ nhịp tính từ nhát đầu). Ô
`CombatSystemTest.shouldDropAttackRequestSilently_whenTargetStillOutOfRange_andNotReplayItWhenTargetCloses`
khoá hành vi server. **Không nâng giả định phủ đầu lên 3** — đo lại bên dưới xác nhận. Phát hiện phụ: cooldown thực là **61 tick** (~1,017 s), không phải 60 — trừ `1f/60f`
dạng float 60 lần còn dư ~2,8e-7 — áp cho cả hai bên nên tỉ lệ 1v1 không đổi. ✅ **Đã sửa 2026-10-01** (chủ dự án duyệt):
`COOLDOWN_READY_EPSILON_SECONDS` = nửa tick cho cả đòn thường lẫn kỹ năng ⇒ đúng 60 tick (ô
`CombatSystemTest.shouldAttackEverySixtyTicks_despiteFloatCooldownResidue`); số đo bảng trên là trước bản sửa. Client thật dính cùng cơ
chế bỏ lệnh ngoài tầm (giữ phím lặp 1 s) — hướng xử lý chờ chủ dự án.

#### Đo lại V50 bằng probe đã sửa (2026-10-01, DB dev đã ở V50)

Cùng nhân vật/đồ/xuất phát/cờ như trên (`--attackIntervalMillis=1100 --observeSeconds=20`), probe có
`AttackCadence`. Sát thương khớp từng điểm (62/61 ra, 22 vào, máu 206/206). **10/10 thắng.**

| Đối thủ | Phủ đầu (5 lượt) | Ta hụt | Quái hụt | Máu thấp nhất |
|---|---|---|---|---|
| `wasteland_beast` | 2 · 1 · 1 · 1 · 1 | 3 · 1 · 1 · 0 · 0 | 3 · 1 · 1 · 1 · 0 | 74 · 96 · 96 · 118 · 96 |
| `foundation_warden` | 1 · 1 · 1 · 1 · 1 | 0 · 0 · 0 · 0 · 1 | 0 · 0 · 0 · 0 · 0 | 96 · 96 · 96 · 96 · 74 |

- Trễ nhát đầu sau "đi bộ xong": 15–315 ms (cũ: 5/10 lượt 1150–1282 ms). Ở các lượt trễ 100–300 ms
  nhát của ta tới ~30 ms sau nhát đầu của quái ⇒ đó là nhát phủ đầu KHÔNG tránh được (quái vào tầm trước).
- Nhịp của ta 1098–1118 ms (cũ 1100–1301); quái ~1018 ms (61 tick) ⇒ ta chậm hơn ~8%, không còn 10–20%.
- Lượt duy nhất phủ đầu 2: một nhát rơi lúc ta còn ĐANG ĐI và nhát đó hụt. Đã dựng lại từng tick bằng
  System thật (`MonsterOpeningHitRangeReplayTest`): phía server quái ra đòn ở **1,994 m ≤ 2** — đúng tầm.
  Probe thấy 2,32 m vì `MoveResponse` gửi lúc drain lệnh (trước Movement, trễ tới 4 tick), vị trí quái
  chỉ phát mỗi 3 tick (AoI 20 Hz), và quái lệch trục y ~0,9 m. **Không có bug server**; đây là thuế tiếp
  cận thật. Đừng chấm "đánh ngoài tầm" từ log probe — sai số ~0,35 m.
- **Kết luận: giả định "phủ đầu ≤ 2" của `DuelMargin` ĐỨNG; bảng 10 lượt ở trên là bi quan giả do lỗi
  nhịp của probe.** Lượt tệ nhất mới 74/206 (cũ 30/206).

Phạm vi: chỉ đo trận 1v1 từ máu ĐẦY, đi tới từ trạm nghỉ sát bên. Thuế đường đi trên trục chính
(nhiều quái liên tiếp) không nằm trong phép đo này. Số cân bằng KHÔNG đổi. ✅ **Chủ dự án DUYỆT V50 2026-10-01** (cả hai điểm: máu yêu thú 280 → 289 và nới ô test thành "không dương").

---

## 7. Xã hội

| Hệ thống | Mô tả |
|---|---|
| Bang hội (Guild) | Từ Lv25 (Trúc Cơ); cấp bang, kho, nhiệm vụ tuần, tranh đoạt Linh Mạch |
| Bạn bè | Danh sách, trạng thái online, mời party/trade |
| Thư | Gửi item/linh thạch, phí chống spam gold-selling |
| Chat | Thế giới/Bang/Party/Riêng/Khu vực; rate-limit + filter |
| Đấu giá | Giao dịch liên instance, thuế (gold sink) |
| Xếp hạng | Cảnh giới/PvP/boss damage/bang hội/**Lực Chiến** (§5); cập nhật định kỳ |

---

## 8. Quest & Nội dung

Điều 27 — **không hardcode**, qua Script Sandbox ([SCRIPTING.md](../06-plugin/SCRIPTING.md)):

- **Cốt truyện chính** Lv1–100: 6 arc theo 6 cảnh giới — khung + lore ở **[STORY.md](STORY.md)**
  (SSOT tường thuật; mọi quest phải map về một arc ở đó).
  🔶 **TODO-OPUS**: viết chi tiết STORY.md §3–§6 rồi mới viết quest ([ADR-038 D9](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)).
- **Nhiệm vụ ngày** (tạp dịch tông môn — gánh nước, luyện đan, tuần sơn) — nguồn resource ổn định
- **Nhiệm vụ bang** — hợp tác
- **Sự kiện mùa** — Feature Flag bật/tắt không deploy lại

### Kiểm sống TOÀN CHUỖI quest arc 1–2 từ nhân vật Cấp 1 mới tinh (2026-10-03)

**Cách đo.** Server `installDist` thật trên DB dev (V51), nhân vật `QuestRun` TẠO MỚI qua đúng
`CreateCharacterRequest` (probe thêm `--createCharacter=true`): Cấp 1, 100 máu, 0 vàng, túi rỗng, không
trang bị. Đi hết 20 quest bằng `gameProbe` (hội thoại/nhận/lĩnh thưởng/đánh/đi bộ/mua bán đều qua dây
thật — probe thêm `--sellItems/--buyItems` và `--attackCode/--retargetOnKill`). **Không nhảy bước nào
bằng SQL** — cấp, vàng, đồ đều kiếm thật. Quy ước duy nhất: "nghỉ ở trạm" = thoát ra vào lại tại đúng
trạm đó (máu không lưu ⇒ đầy; cùng kết quả với đứng yên ~30 s, chỉ nhanh hơn). Mọi trận dưới đây đều
từ máu đầy, đi tới từ trạm/chỗ an toàn gần nhất, trừ khi ghi "nối máu". Nhịp đánh 1100 ms.

| # | Quest | Cấp lúc làm | Nhận / Xong / Lĩnh | Trận bắt buộc — đo thật |
|---|---|---|---|---|
| 1 | `kill_training_dummies` | 1 | ✅/✅/✅ +500, kunai×5 → Cấp 3 | 3/3 (mộc nhân không đánh trả) |
| 2 | `night_watch_bandit` | 3, tay không | ✅/✅/✅ +800, bùa hộ cường hoá → Cấp 5 | 1/1 thắng, còn 28/100 |
| 3 | `clear_old_archive` | 5 | ✅/✅/✅ +1200, Mảnh Ngọc Cổ → Cấp 7 | tay không 1 thắng (nhờ thạch vệ hụt 2) 1 **chết**; 6/7 món Tạp Dịch **2/2 thắng**, còn 38/118 |
| 4 | `awakening_trial` | 7, đủ Tạp Dịch | ✅/✅/✅ +1800 → Cấp 10 | đứng ở −43: **0/2 — chết cả hai** (thạch vệ @−33 đánh kèm); đứng ở −46: 1/1 thắng, còn 66/122 |
| 5 | `spirit_vein_raiders` | 10 | ✅/✅/✅ +2800 → Cấp 13 | đứng ở 45: **chết** (tán tu @34 đánh kèm); ở 50: thắng còn **4**/122; vòng lặp tại 49 nối máu: 3/3 thắng (66 · 54 · 42) |
| 6 | `first_cultivation_manual` | 13 | ✅/✅/✅ +3300, bộ Ngoại Môn → Cấp 15 | 2/2 thắng |
| 7 | `break_the_raider_band` | 15, Ngoại Môn | ✅/✅/✅ +3700 → Cấp 18 | đầu lĩnh 1/1 thắng, còn 54/174 (ta hụt 2) |
| 8 | `report_to_outer_sect` | 18 | ✅/✅/✅ +200, đan×2 | — |
| 9 | `recover_outer_sect_tallies` | 18 | ✅/✅/✅ +1000 | nộp khi có 1 thẻ: không lấy, không xong (đúng luật); 2 thẻ: lấy đúng 2 |
| 10 | `missing_disciple_trail` | 18 | ✅/✅/✅ +1300 → Cấp 19 | tán tu hạ TRƯỚC khi hỏi Chu Hoành KHÔNG tính (đúng script); sau khi hỏi 2/2 |
| 11 | `abandoned_cave` | 19 | ✅/✅/✅ +1600 → Cấp 20 | thám tử 1/1, huyết phù rơi 100%, nộp đúng 1 |
| 12 | `confront_the_steward` | 20 | ✅/✅/✅ +2000 → Cấp 21 | quản sự (Cấp 12) chết sau 2 nhát |
| 13 | `dead_vein_intruders` | 21 | ✅/✅/✅ +3600 → Cấp 23 | tế đồ 3/3 thắng (59 · 59 · 59 /174) |
| 14 | `blood_sand_trail` | 23 | ✅/✅/✅ +4000 → Cấp 25 | 2/2 thắng, nối máu (59 · 59) |
| 15 | `scatter_the_blood_sand_band` | 25 | ✅/✅/✅ +4400 → Cấp 27 | đàn chủ 1/1, còn 36/174 |
| 16 | `wasteland_edge_patrol` | 27 | ✅/✅/⚠️ lần 1 **INVENTORY_FULL**; bán đồ cũ rồi ✅ +4400, bộ Trúc Cơ → **Cấp 29** | đàn chủ 1/1, còn 36/174 |
| 17 | `rank_board_dispute` | 29→30 | ✅/✅/✅ +4600 → Cấp 32 | Ngoại Môn ở Cấp 29: 1 **chết** (yêu thú còn 19), 1 bỏ dở 30 vs 44; cày 6 đàn chủ (6/6 thắng) lên Cấp 30; Trúc Cơ: 4/4 |
| 18 | `foundation_pill_run` | 32 | ✅/✅/✅ +4800 → Cấp 33 | 2/2; nộp lấy đúng 3 đan (túi có 24) |
| 19 | `outer_sect_proving` | 33 | ✅/✅/✅ +5000 → Cấp 35 | 3/3 |
| 20 | `foundation_breakthrough` | 36 | ✅/✅/✅ +5400 → **Cấp 38** | cỗ khảo hạch 4/4 thắng (56 · 78 · 100 · 122 /210) |

**Kết luận: chuỗi 20 quest chạy THÔNG từ Cấp 1 tới CLAIMED quest cuối**, không quest nào kẹt cứng,
không tiên quyết vòng, không vật phẩm quest nào không rơi/không nộp được, mọi phần thưởng khớp script
từng con số. Yêu thú với bộ Trúc Cơ **9/9 thắng** (máu thấp nhất 56/210), cỗ khảo hạch 4/4 — khớp
lần đo V50. Thuế đường đi đo lại: tông môn → trạm 107 mất 79 máu (5 nhát), → trạm 141 mất 125 máu (7 nhát).

**Bug code đã sửa:** `ExperienceChangedEvent.leveled_up` sau lĩnh thưởng quest LUÔN `false` (6/6 lần lên
cấp nhờ thưởng quest trong lượt đo — 1→3, 3→5, 8→10 mở chọn hệ phái, 13→15, …) ⇒ client không bao giờ
hiện "lên cấp" cho đúng những cú lên cấp lớn nhất. Sửa: `GrantExperienceUseCase.syncAfterExternalGrant`
so cấp suy từ `exp` với cột `level` đang lưu; kiểm sống lại: lĩnh quest 1 ra `level=3 leveledUp=true`.

**Chỗ vướng thật (thiết kế/nội dung — KHÔNG tự sửa, chờ chủ dự án):**
1. **Vàng cho bộ Tạp Dịch.** Nhân vật mới 0 vàng, quest không thưởng vàng, quái không rơi vàng. Lớp
   cân bằng quest 3–5 giả định bộ Tạp Dịch (700 vàng), mà đường duy nhất tới đó ở Cấp 5 là **bán bùa
   hộ cường hoá thưởng quest 2 (500)** + đồ rơi (tổng 644 — thiếu 56 cho món thứ 7). Không bán bùa thì
   phải cày mộc nhân (~9 vàng/con theo bảng rơi). Tay không vs thạch vệ: 1 thắng 1 chết.
2. **Hai trận bắt buộc kéo thêm con thứ hai qua leash.** Người chơi đi từ phía tông môn gặp
   `trial_construct` (@−47) ở khoảng −41…−43 — vẫn trong leash 10 m của `archive_warden` @−33 vừa bị
   đánh động lúc đi ngang ⇒ 2/2 chết; lùi sâu tới −46 thì thắng thoải mái. `vein_raider` @47 y hệt với
   `wandering_bandit` @34 (đứng 45 chết, 50 thắng). Luật giãn spawn V29 (`|A−B| > aggroA+aggroB`)
   không tính vế **leash** của con bị đánh động khi đi ngang. *(✅ 2026-10-17: với hai trận tay đôi này, luật nay tính cả leash lẫn tuần tra, V56 dời hai điểm spawn — README dòng 64(b); áp cho cả trục thì còn 16 cặp vi phạm, chờ chủ dự án.)*
3. **Cấp 29 cầm bộ Cấp 30.** Quest 16 trao bộ Trúc Cơ (`required_level` 30) nhưng để người chơi ở
   Cấp 29 (40 990 EXP; Cấp 30 = 43 500), mà quest 17–20 cân bằng theo đúng bộ đó. Bộ Ngoại Môn đo
   được 1 chết + 1 sát nút ⇒ phải cày ~2 500 EXP ở chỗ khác (6 đàn chủ, ~5 phút) — không có quest/NPC
   nào nói điều này.
4. **Túi 20 ô tính cả 7 món đang mặc** ⇒ người giữ lại bộ cũ bị từ chối lĩnh thưởng 6 món
   (`INVENTORY_FULL`; quest giữ `COMPLETED`, không mất gì). Kèm: `outer_sect_tally` vẫn rơi 100% từ tán tu
   sau khi quest 9 xong và **không bán được** (cuối lượt còn 3 thẻ rác). *(✅ 2026-10-17: bán được, không mua được — V55.)*
5. **Im lặng khi chưa đủ điều kiện**: nộp thiếu thẻ bài, hạ tán tu trước khi hỏi Chu Hoành — đúng
   luật script nhưng người chơi không nhận một dòng phản hồi nào. *(✅ 2026-10-17: NPC nhắc khi thiếu đồ — README dòng 65(c).)*
6. **Lên cấp không cộng chỉ số**: máu 100 từ Cấp 1 tới Cấp 5 tay không, sát thương chỉ đổi theo đồ ⇒
   cấp chỉ là cổng mặc đồ. Ghi lại để xác nhận đây là chủ đích. *(✅ 2026-10-17: +2 máu tối đa mỗi cấp — README dòng 65(d).)*

Nhân vật thử đã xoá khỏi DB dev sau khi đo (account `questrun1003`, nhân vật 8007/8008, cùng quest/túi/
audit của chúng); bảng `idempotency_key` không gắn nhân vật nên còn lại vài khoá UUID của lượt đo.

---

## 9. Core Loop

```
Nhận mục tiêu → Đánh yêu thú/Bí Cảnh/PvP → Tu Vi + Pháp khí + Linh Thạch
→ Tôi luyện pháp khí/công pháp/đột phá cảnh giới → Mạnh hơn → Nội dung khó hơn
→ (quay lại, đồng thời tương tác bang/bạn/đấu giá)
```

Mục tiêu phiên chơi: **15–25 phút** cho một chuỗi hoàn chỉnh (hợp cả desktop lẫn mobile).

---

## 10. Kinh tế & Monetization

⚠️ **Mô hình kinh doanh CHƯA CHỐT** — cần ADR nếu quyết định thu phí. Nguyên tắc nếu có:

- ❌ **Không Pay-to-Win**: không bán ATK/DEF thô bằng tiền thật
- ✅ **Pay-for-Cosmetic/Convenience**: thời trang, phi kiếm, mở túi, rút ngắn chờ
- ✅ **Gold Sink bắt buộc**: tôi luyện, khảm nạm, phí đấu giá/thư — chống lạm phát
- Mọi thay đổi economy qua Data-Driven Config, không sửa code (Điều 27)

⚠️ Theo dõi `kage.gold.total` (linh thạch — tên metric giữ nguyên) để phát hiện lạm phát/duplicate ([OBSERVABILITY.md §3.5](../07-infrastructure/OBSERVABILITY.md)).

---

## 11. MVP Scope

Chống scope creep (Điều 9). MVP = **hết [Phase 6](../11-process/ROADMAP.md)**.

**Có trong MVP:**
- 1 linh căn đầy đủ (đề xuất: **Hỏa**) — verify toàn pipeline trước khi nhân ra 4 hệ
- Lv1–30 (Luyện Khí → Trúc Cơ), không Độ Kiếp
- PvE cơ bản (yêu thú ngoài trời — **không Bí Cảnh**, xem dưới)
- Pháp khí + tôi luyện (không Linh Ngọc)
- Chat + Bạn bè + Thư cơ bản
- 1 tông môn hub + 1 Linh Mạch luyện cấp

**Post-MVP:** PvP xếp hạng, Chiến trường, Tranh đoạt Linh Mạch · Bang hội đầy đủ · 4 linh căn còn
lại · Đấu giá, Xếp hạng toàn cục · Phi kiếm, Linh thú tiến hoá, Festival · Trade · **Bí Cảnh**
(Dungeon instance, ROADMAP xếp Phase 8 — mục này trước đây liệt vào MVP nhầm, sửa lại 2026-07-23
để khớp ROADMAP, xác nhận cùng chủ dự án).

> **Ghi chú trạng thái thật (2026-09-01):** code đã vượt xa MVP giấy này (Phase 0-9 đóng, đang
> Phase 10) — mục §11 giữ nguyên làm mốc lịch sử scope; danh sách "Post-MVP" phần lớn đã xây xong.

---

## 12. ⚠️ Open Questions — chốt trước khi code

| # | Câu hỏi | Chặn | ADR |
|---|---|---|---|
| 1 | ~~Công thức damage/derived stats cụ thể~~ → xem §5–6 (placeholder, playtest sau) | Phase 4 | — |
| 2 | Desktop-first hay mobile-first | Input, UI, launcher | ADR-023 |
| 3 | ~~Single-realm hay multi-realm~~ → single-realm + `realm_id` | Schema, scaling | ✅ [ADR-010](../01-architecture/adr/ADR-010-Realm-Model.md) |
| 4 | i18n: chỉ tiếng Việt hay có tiếng Anh ở MVP | UI | — |
| 5 | **Tên thương hiệu mới theo theme tu tiên** ("Project Kage" đã lệch — package root `dev.kage` vẫn giữ) | Branding, tên repo | [ADR-012](../01-architecture/adr/ADR-012-Package-Root.md) + [ADR-038 D6](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) |
| 6 | Mô hình kinh doanh | §10 | — |
| 7 | ~~Viewport~~ → FitViewport | Phase 1 | ✅ ADR-020 |
| 8 | Kích thước AoI (thấy được ai) | Phase 3 | — (đo Phase 3) |
| 9 | ~~Tên riêng "Huyền Thương"/"Thanh Hư Tông"~~ → chủ dự án duyệt dùng luôn (2026-09-01) | — | ✅ [ADR-038 D6](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) |

Mỗi câu trả lời → cập nhật tài liệu này hoặc tạo ADR.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 linh căn nguyên tố thay class cố định (Phong/Lôi = dị linh căn) | Accepted |
| 2 | Độ Kiếp Chuyển Thế (rebirth) thay nâng trần level vô hạn | Accepted |
| 3 | Phi kiếm/cosmetic **không** cộng chỉ số chiến đấu | Accepted |
| 4 | MVP: **1 linh căn (Hỏa)** trước, nhân ra sau | Accepted |
| 5 | Không Pay-to-Win | Accepted (nếu có monetization) |
| 6 | `DEF`/`RES` tách riêng (vật lý/phép, kiểu AD/AP LMHT) — không dùng 1 DEF chung | Accepted |
| 7 | Giảm giáp/kháng theo công thức diminishing returns kiểu LMHT (`100/(100+DEF)`) + xuyên giáp | Accepted (placeholder, playtest) |
| 8 | **Lực Chiến** (Combat Power) — chỉ hiển thị/xếp hạng, KHÔNG dùng tính damage thật | Accepted |
| 9 | Hệ số cân bằng (derive stat, damage, Lực Chiến) là số tham khảo, chờ playtest | ⏳ Phase 4+ |
| 10 | **Re-theme tu tiên: chỉ đổi tường thuật/tên hiển thị, KHÔNG đổi cơ chế/protocol/DB** | ✅ [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) Accepted 2026-09-01 |
| 11 | Cảnh giới = cách trình bày Lv1–100 (6 cảnh giới × 9 tầng, có Tử Phủ), không phải hệ thống mới | ✅ ADR-038 D2 |
| 12 | Open questions còn lại §12 | ⏳ |
| 13 | **Luật biên thật** cho trận tay đôi bắt buộc: biên giấy − 2 phủ đầu − 1 né ≥ +2 (§6) | ✅ Chủ dự án chốt 2026-10-01; 7/9 trận còn vi phạm, chờ quyết định thiết kế |

## ADR cần tạo

Xem §12 — còn ADR-023 (mobile input). ADR-038 ✅ Accepted 2026-09-01.

## Tài liệu liên quan

- [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) — quyết định re-theme + 3 đợt thi hành
- [GAMEPLAY.md](../05-server/GAMEPLAY.md) — server implement
- [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ (đã cập nhật cột tu tiên)
- [ROADMAP.md](../11-process/ROADMAP.md) — nội dung theo Phase
- [DATABASE.md](../05-server/DATABASE.md) — bảng dữ liệu
- [ADR Index](../01-architecture/adr/README.md)

## Việc cần làm tiếp theo

1. ✅ [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) Accepted 2026-09-01 — GDD 0.3.0 này là Đợt 1 của nó.
2. ✅ [STORY.md](STORY.md) v0.2.0 viết đầy đủ (2026-09-01). 🔶 **TODO-OPUS** — Đợt 2: i18n client + tên item/quái/NPC/skill trong DB seed + quest bám STORY §8.
3. 🔶 **TODO-OPUS** — Đợt 3: audit art ninja → tu tiên (15 nhân vật, 3 yêu thú, 17 VFX).
4. Playtest cân bằng hệ số §4/§5-6 — TOÀN BỘ hệ số hiện tại là placeholder (không đổi so với 0.2.0).
