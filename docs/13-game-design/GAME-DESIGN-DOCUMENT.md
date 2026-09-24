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
