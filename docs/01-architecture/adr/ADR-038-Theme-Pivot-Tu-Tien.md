# ADR-038: Đổi bối cảnh game từ "ninja học viện" sang "tu tiên" (Phàm Nhân Tu Tiên-style)

| | |
|---|---|
| Status | 🟢 **Accepted** (2026-09-01, chủ dự án duyệt qua chat: "duyệt hết") — gồm cả bố cục [STORY.md](../../13-game-design/STORY.md) §1–§8, thang 6 cảnh giới × 9 tầng (có Tử Phủ), và 2 tên riêng đề xuất "Huyền Thương"/"Thanh Hư Tông" |
| Date | 2026-09-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD](../../13-game-design/GAME-DESIGN-DOCUMENT.md) (tài liệu bị ảnh hưởng chính) · [ADR-012](ADR-012-Package-Root.md) (package root `dev.kage` KHÔNG đổi) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (cơ chế di chuyển GIỮ NGUYÊN, chỉ đổi cách gọi) · [GLOSSARY.md](../../GLOSSARY.md) · Điều 5 Hiến pháp (cảm hứng ≠ sao chép) |

---

## Context

Dự án khởi đầu theo thể loại **2D ninja MMORPG kiểu Ninja School Online** (GDD v0.2.0). Ngày
2026-09-01, chủ dự án quyết định đổi hướng: bối cảnh không còn hấp dẫn, muốn chuyển sang thể loại
**tu tiên / tiên hiệp** — cảm hứng từ dòng truyện *Phàm Nhân Tu Tiên* ("phàm nhân bắt đầu từ tạp
dịch ngoại môn, từng bước tu luyện thành tiên").

Điểm mấu chốt: sau 10 phase, **toàn bộ cơ chế đã xây là theme-agnostic**. Side-view + nhảy, combat
real-time, 5 hệ nguyên tố, cường hoá, bang hội, bí cảnh... đều dùng được nguyên vẹn cho tu tiên —
thứ cần đổi là **lớp vỏ tường thuật**: tên gọi, lore, cách trình bày tiến trình.

Ràng buộc từ Hiến pháp Điều 5: cảm hứng ≠ sao chép. Không dùng tên riêng, địa danh, nhân vật của
*Phàm Nhân Tu Tiên* hay bất kỳ IP nào. Các thuật ngữ **quy ước thể loại** (Luyện Khí, Trúc Cơ,
Kim Đan, linh căn, linh thạch, bí cảnh...) dùng chung toàn dòng tiên hiệp, không thuộc IP nào —
dùng được.

## Decision

Đổi bối cảnh sang tu tiên theo nguyên tắc **"re-theme, không re-build"**: chỉ đổi tường thuật +
tên hiển thị; **KHÔNG đổi cơ chế, KHÔNG đổi protocol, KHÔNG đổi schema DB, KHÔNG đổi tên
class/package/opcode đang chạy** ở bước này. Cụ thể (D1–D9):

### D1 — Thế giới & khởi đầu

- Đại lục **Huyền Thương** (tên đề xuất, chủ dự án đổi được). Sức mạnh là **Linh Khí** trời đất.
- Người chơi là **phàm nhân** được **Thanh Hư Tông** (tên đề xuất) thu nhận làm **đệ tử tạp dịch
  ngoại môn** — đúng fantasy "phàm nhân nghịch tập" của thể loại.
- Tông môn = hub/safe zone (thay Học Viện Ninja, cùng vai trò).

### D2 — Cảnh giới thay cách trình bày Level (cơ chế Lv1–100 giữ nguyên)

Cột `level` trong DB, EXP, mọi công thức **không đổi**. Chỉ đổi cách **hiển thị**. Thang cảnh giới
theo chuẩn thể loại (chủ dự án chỉnh 2026-09-01): **6 đại cảnh giới, mỗi cảnh giới 9 tầng**, gom
Sơ kỳ (tầng 1–3) / Trung kỳ (4–6) / Hậu kỳ (7–9):

| Level | Cảnh giới | Tầng | Mốc mở khoá (giữ nguyên cơ chế cũ) |
|---|---|---|---|
| 1–18 | **Luyện Khí** | 1–9 (2 lv/tầng) | Lv10 = thức tỉnh linh căn, chọn hệ (tầng 5) · Lv15 công pháp (tầng 8) |
| 19–36 | **Trúc Cơ** | 1–9 | Lv20 linh thú (tầng 1) · Lv25 bang hội (tầng 4) · Lv30 PvP (tầng 6) |
| 37–54 | **Tử Phủ** | 1–9 | Lv50 Thần Thông (Tuyệt Kỹ cũ — tầng 7) |
| 55–72 | **Kim Đan** | 1–9 | — |
| 73–90 | **Nguyên Anh** | 1–9 | — |
| 91–100 | **Hoá Thần** | 1–9 (1 lv/tầng), Lv100 = **Viên Mãn** | Lv100 Độ Kiếp |
| Chuyển Sinh | **Độ Kiếp Chuyển Thế** — độ kiếp, chuyển thế trùng tu, giữ **Đạo Vận** (Điểm Nguồn cũ) | — | Cơ chế rebirth giữ nguyên |

Công thức hiển thị (client-only, không đụng server): 5 cảnh giới đầu mỗi cảnh 18 level, `tầng =
ceil((lv − start + 1) / 2)`; Hoá Thần 1 lv/tầng — cảnh giới cuối lên tầng nhanh, tạo cảm giác cao
trào trước Độ Kiếp. Nếu sau này nâng trần level (hiện KHÔNG có kế hoạch — rebirth thay thế), thang
mở tiếp theo quy ước thể loại: Luyện Hư → Hợp Thể → Đại Thừa.

### D3 — Linh căn thay hệ phái (giữ nguyên 5 nguyên tố hiện có)

5 hệ **Hỏa/Thủy/Phong/Lôi/Thổ giữ nguyên** — trình bày là **linh căn**; Phong và Lôi là
**dị linh căn** (hiếm, đúng chất thể loại — dị linh căn là quy ước tiên hiệp). Khắc chế ±15%,
chọn hệ Lv10, đổi hệ bằng **Tẩy Căn Đan** (Đá Chuyển Hệ cũ) — tất cả cơ chế giữ nguyên.
Enum/code `FIRE/WATER/WIND/THUNDER/EARTH` không đổi.

### D4 — Bảng đổi tên hiển thị (display-only; tên code/DB giữ nguyên)

| Cũ (ninja) | Mới (tu tiên) | Code/DB giữ |
|---|---|---|
| Khí (resource) | **Linh Lực** | `current_chi` |
| EXP | **Tu Vi** | `experience` |
| Vàng | **Linh Thạch** | `gold` |
| Skill / Tuyệt Kỹ | **Công pháp / Thần Thông** | `skill_template` |
| Trang bị / cường hoá | **Pháp khí / tôi luyện** | `item_template`, `enhance_level` |
| Pet / Mount | **Linh Thú / Phi Kiếm** (mount = phi kiếm, vẫn cosmetic-only) | — |
| Quái / World Boss | **Yêu thú / Yêu Vương** | `monster_template` |
| Lãnh Địa Hệ Phái | **Linh Mạch** (ngũ hành) | — |
| Vùng Tranh Chấp | **Hoang Vực** | — |
| Bí Cảnh | **Bí Cảnh** (đã chuẩn tu tiên, giữ) | — |
| Chuyển Sinh / Điểm Nguồn | **Độ Kiếp Chuyển Thế / Đạo Vận** | `rebirth_count` |

### D5 — Cơ chế GIỮ NGUYÊN 100%

Di chuyển trái/phải + nhảy ([ADR-033](ADR-033-Ninja-School-Style-Movement.md) — flavor mới:
**khinh công**), đánh thường bấm mới ra đòn (ADR-034), công thức damage/derive stats (GDD §5–6),
cường hoá % (ADR-036), quái từ DB (ADR-037), toàn bộ 158 opcode, mọi bảng DB. **Không có
migration, không tăng `protocolVersion`, không đổi module.**

### D6 — Những gì KHÔNG quyết ở ADR này (chủ dự án quyết riêng)

1. **Tên game/thương hiệu** — "Project Kage"/`shinobi-engine` giờ lệch theme. Package root
   `dev.kage` **vẫn giữ** bất kể tên mới ([ADR-012](ADR-012-Package-Root.md) đã lường trước).
   Tên repo đổi hay không là việc riêng, không chặn gì.
2. Tên riêng "Huyền Thương"/"Thanh Hư Tông" — đề xuất, đổi lúc nào cũng được trước Open Beta.

### D7 — Thứ tự thi hành (3 đợt, đợt sau cần đợt trước)

1. **Đợt 1 — Tài liệu** (ADR này + GDD v0.3.0 + GLOSSARY + STORY.md): ✅ **XONG 2026-09-01**.
2. **Đợt 2 — Chuỗi hiển thị**: i18n client + tên item/quái/NPC/skill + script quest/hội thoại.
   ✅ **XONG 2026-09-01** — chi tiết ở D10 dưới đây.
3. **Đợt 3 — Art**: model 15 nhân vật + 3 quái + 17 VFX đang là phong cách ninja. Đánh giá
   từng asset: giữ (đa số VFX nguyên tố dùng lại được) / sửa (trang phục) / làm mới (quái →
   yêu thú). 🔶 **TODO-OPUS** (tốn Rodin quota + acceptance loop, xem [ART-PIPELINE.md](../../14-art/ART-PIPELINE.md)).

### D8 — Tài liệu cần quét lại (74 chỗ nhắc "ninja" trong 17 file docs)

GDD + GLOSSARY làm ở Đợt 1. Còn lại — GAMEPLAY.md, UI.md, CLIENT.md, RENDERING.md, PHYSICS.md,
ROADMAP.md, ART-*.md, KAGE-CONSTITUTION.md (1 chỗ), CLAUDE.md gốc — đa số là nhắc tên thể loại
trong ví dụ/lịch sử, sửa dần không chặn gì. 🔶 **TODO-OPUS** (quét `ninja|Ninja|Học Viện|Kagesekai`
toàn cây docs). **ADR cũ (031, 033, 034...) là hồ sơ lịch sử — KHÔNG sửa.**

### D10 — Đợt 2 đã thi hành thế nào (2026-09-01, bổ sung sau khi Accept)

**Phát hiện làm Đợt 2 nhẹ hơn dự kiến: DB KHÔNG chứa chữ hiển thị nào.** Mọi bảng seed
(`item_template`, `monster_template`, `npc_template`) chỉ giữ cột `name_key` trỏ sang bundle i18n —
xác minh bằng `grep name_key` trên toàn bộ 24 migration. Vì vậy **Đợt 2 không cần migration nào**,
đúng như D5 đã đòi; toàn bộ tên hiển thị đổi trong hai file `.properties`. Câu "tên item/quái/NPC
trong DB seed" ở D7.2 dựa trên một giả định sai về nơi chữ được lưu.

Đã sửa:

| Vùng | Nội dung |
|---|---|
| `strings_vi/en.properties` | ~90 giá trị: 5 chiêu, 3 loài quái, 20 vật phẩm, 7 ô trang bị, vàng→**linh thạch**, cường hoá→**tôi luyện**, hầm ngục→**bí cảnh**, cửa hàng→**Tàng Bảo Các**, đấu tay đôi→**Luận Kiếm**, hệ phái→**linh căn**, LÊN CẤP→**ĐỘT PHÁ**. **Không khoá nào bị đổi tên** (D7.2) |
| `town_elder.js` | Trưởng lão **Chu Hoành**, nội dung arc 1; thêm 3 node (tông môn → cảnh giới → thân thế trưởng lão). Bốn lựa chọn của node `start` giữ nguyên thứ tự vì `option_index` là VỊ TRÍ |
| `kill_training_dummies.js` | Khai `// STORY: arc-1 §6` theo STORY.md §8; ba con số cân bằng KHÔNG đổi |
| `CultivationRealm` (mới) | Ánh xạ cấp→cảnh giới của D2, thuần tuý, 20 test; dùng ở màn chọn nhân vật + điều kiện mặc pháp khí |
| `npc.town_elder.name` | Khoá đã nằm trong DB từ V10 nhưng **chưa từng có dòng i18n** — tham chiếu treo, nay đã viết |

**Hai bẫy bố cục đo được bằng ảnh chụp, không đoán:**

1. Tên chiêu "Cuồng Phong Quyết" (17 ký tự) làm nở nhãn tên chiêu (không có bề rộng cố định) và
   đẩy cả cụm nút góc phải — rút còn "Cuồng Phong". Trần thực tế ~14 ký tự, đã ghi vào bundle.
2. Dòng chọn nhân vật là nút rộng cố định **340px** và vốn ĐÃ sát trần từ trước ("(Cấp 100)" trên
   tên 16 ký tự ≈ 330px); thêm cảnh giới thì vượt. Nới lên 440px.

**Hai test mới gác nội dung** (cả hai chạy không cần Docker — chính là vấn đề chúng vá):
`CultivationRealmTest` ghim bảng ánh xạ D2 theo từng mốc GDD, và `TownElderDialogueTreeTest` chạy
**script sản phẩm thật** để bắt node cụt / action trỏ vào hư không / lộ nội dung arc sau. Trước đó
script sản phẩm chỉ được kiểm bởi `DialogueIntegrationTest` vốn cần Postgres, tức là im lặng biến
mất mỗi khi máy không bật Docker.

### D11 — Playtest thật (2026-09-01, sau khi bật Docker)

Chơi thật trên server + Postgres + Redis thật. **1099/1099 test xanh** (lần đầu tính cả 197 test
Postgres, gồm `DialogueIntegrationTest` chạy đúng `town_elder.js` sản phẩm). Ảnh chụp từng bước.

| Đã NHÌN THẤY chạy | Bằng chứng |
|---|---|
| Cảnh giới ở màn chọn nhân vật | "Kagemaru — Hỏa (**Luyện Khí 1**)" |
| Điều kiện dùng pháp khí theo cảnh giới | "Trường Kiếm Thép +7 · **Cần Luyện Khí tầng 3**" (cấp 5 ⇒ tầng 3 ✓) |
| Hội thoại Chu Hoành + trọn 3 node arc 1 | Lời chào, "Đây là Thanh Hư Tông…", "Tu luyện có thứ tự: Luyện Khí, rồi Trúc Cơ…", "Ta kẹt ở Kim Đan bốn mươi năm…" |
| Nhận nhiệm vụ qua hội thoại | HUD "**Đập Mộc Nhân — Đang làm**" |
| Giết quái → rơi đồ → tên vật phẩm mới | Chat "**Nhận được Phi Đao x1.**" |
| Túi đồ | "Túi đồ **41.100 linh thạch**", "Hộ Linh Phù x3", ô "**Pháp khí / Đạo bào / Hộ thủ**" |
| Cửa hàng | "**Tàng Bảo Các  Linh thạch: 41.100**", trọn bộ "Tạp Dịch" |
| Tên quái / chiêu / gợi ý phím | "Mộc nhân luyện công", "Hỏa Cầu Thuật", "B mua bán… K luận kiếm" |

**Playtest bắt được một lỗi tràn do chính đợt này gây ra**: dòng cửa hàng ghép
`"<tên> — Mua: X  Bán: Y"` trong một nhãn KHÔNG xuống dòng, nên "Khăn Buộc Tóc Tạp Dịch" (22 ký tự)
nuốt luôn giá bán ("… Bá…"). Rút ba tên dài nhất xuống ≤17 ký tự — mốc **đo được** từ
"Băng Tay Tạp Dịch" vốn hiện đủ — và ghi trần đó vào ngay bundle. Đã chụp lại xác nhận hết tràn.

**Chưa chứng minh bằng chơi tay**: đủ 3 mạng quái để nhiệm vụ chuyển "Hoàn thành" rồi lĩnh thưởng.
Đánh nhau bằng input giả tuột mục tiêu và chết giữa chừng (giới hạn đã biết của công cụ playtest,
không phải lỗi game) — nhưng chuỗi nhận → đếm → hoàn thành → lĩnh thưởng thì `QuestIntegrationTest`
đã kiểm trọn trên Postgres thật với đúng script sản phẩm, và hai chuỗi chữ của bước lĩnh thưởng
vốn KHÔNG bị đợt này đụng tới.

### D9 — Cốt truyện có tài liệu SSOT riêng: [STORY.md](../../13-game-design/STORY.md)

Cốt truyện KHÔNG nằm rải trong GDD — tách thành **STORY.md** (bố cục §1–§8 đã đề xuất, chờ chủ dự
án duyệt): tiền đề "phàm nhân nghịch tập", vũ trụ quan, lịch sử Thiên Địa Đại Kiếp (bí mật trục
chính), thế lực, nhân vật, **6 arc theo 6 cảnh giới**, bảng ánh xạ lore ↔ hệ thống game, quy tắc
viết quest. Từ Đợt 2: mọi quest/NPC/tên vùng phải map về một mục của STORY.md — không bám được thì
không viết. Chi tiết §3–§6 🔶 **TODO-OPUS** (viết creative dài, sau khi duyệt bố cục).

## Alternatives Considered

1. **Giữ nguyên theme ninja** — bị loại: chủ dự án đã quyết đổi; theme là lý do làm game, không
   phải chỗ tiết kiệm.
2. **Đổi 5 hệ thành ngũ hành chuẩn (Kim/Mộc/Thủy/Hỏa/Thổ)** — "đúng sách" tu tiên hơn, nhưng
   Phong→Kim, Lôi→Mộc kéo theo: đổi enum + DB seed + **2 bộ art faction đầy đủ** + VFX + i18n +
   cân bằng lại khắc chế. Chi phí lớn cho khác biệt thẩm mỹ nhỏ; dị linh căn Phong/Lôi lại là
   điểm nhấn riêng. **Loại** (revisit được nếu chủ dự án muốn — xem Revisit Trigger).
3. **Re-build cả cơ chế theo tu tiên "đúng chất"** (tu luyện offline, đột phá có tỉ lệ thất bại,
   đấu pháp turn-based...) — đập bỏ 10 phase đã xong + toàn bộ playtest. Vi phạm tinh thần Điều 9
   (scope creep). **Loại**; cơ chế mới nếu muốn thì từng cái một, ADR riêng.
4. **Chỉ đổi tên trong docs, không đổi chuỗi hiển thị/art** — game chạy vẫn là ninja, docs nói tu
   tiên → mâu thuẫn tài liệu-code, vi phạm Điều 1 (SSOT). **Loại**; phải có Đợt 2-3, chỉ là làm sau.

## Consequences

**Được**: giữ 100% code/protocol/DB/test (1067 test không đổi); pivot theme bằng chi phí tài liệu
+ chuỗi + art thay vì chi phí kỹ thuật; fantasy "phàm nhân nghịch tập" khớp sẵn pillar #1
(tiến trình đọc được — cảnh giới còn đọc được hơn level trần).

**Mất/rủi ro**: tên code kiểu `NinjaAcademy`/`chi` (nếu có) sẽ lệch tường thuật — chấp nhận, đổi
tên code là refactor thuần túy làm sau nếu ngứa mắt, không bao giờ là điều kiện chặn. Art ninja
đã làm (15 nhân vật) có thể phải sửa trang phục — số phận từng asset quyết ở Đợt 3, chưa vứt gì
lúc này. Tên "Kage" trên repo/branding lệch theme cho tới khi chủ dự án đặt tên mới (D6).

## Revisit Trigger

- Chủ dự án muốn ngũ hành chuẩn Kim/Mộc (Alternative 2) → mở lại D3, cần ADR amend + kế hoạch art.
- Muốn cơ chế tu tiên thật (đột phá có tỉ lệ, luyện đan, động phủ...) → ADR mới từng cơ chế.
- Đặt tên thương hiệu mới → cập nhật D6, KHÔNG đụng package root (ADR-012).

## Việc cần làm tiếp theo

1. ✅ Accepted 2026-09-01 (chủ dự án duyệt trọn D1–D9 + bố cục STORY.md + 2 tên riêng).
2. ✅ Đợt 1 đã soạn kèm: [GDD v0.3.0](../../13-game-design/GAME-DESIGN-DOCUMENT.md) + [GLOSSARY](../../GLOSSARY.md) + khung [STORY.md](../../13-game-design/STORY.md).
3. ✅ STORY.md §1–§8 viết đầy đủ (v0.2.0, 2026-09-01) — nội dung chờ chủ dự án duyệt.
4. ✅ Đợt 2 xong 2026-09-01 — xem D10.
5. ✅ Playtest thật xong 2026-09-01 — xem D11. 1099/1099 test xanh (gồm 197 test Postgres).
6. ✅ **Đợt 3 — phần RÀ SOÁT xong 2026-09-01**: [ART-AUDIT-TU-TIEN.md](../../14-art/ART-AUDIT-TU-TIEN.md).
   Kết quả ngược hẳn với dự đoán của D7.3: **33/37 nhóm asset dùng lại nguyên vẹn** (toàn bộ 17 VFX,
   16/18 icon, cả 3 quái). Đề tài ninja gần như chỉ nằm ở MỘT chi tiết — băng trán có tấm kim loại.
   Và phép đo cấu trúc `.glb` cho thấy nó là **dải màu tách bạch trong texture**, gỡ được bằng đổi
   màu chứ không phải sinh lại model ⇒ **0 lượt Rodin cho 15 nhân vật**. Cũng đã gỡ bẫy: thước đo nghiệm thu cũ đòi nhân vật "vẫn nhận ra là
   ninja", nó sẽ đánh trượt mọi asset tu tiên đúng đề tài.
7. ✅ **Đợt 3 — phần THI HÀNH xong 2026-09-03.** Chủ dự án đã tick đủ **7/7** dòng bảng §5. Kết quả:
   băng trán ninja gỡ khỏi `char_hoa-b1` (2026-09-01 — và hoá ra **chỉ 1/15** nhân vật từng đội nó);
   `icon_genin_headband` băng trán hitai-ate → **mũ vải Ngoại Môn**; `icon_steel_katana` katana →
   **trường kiếm thẳng + tua kiếm**; `char_town-elder` **dựng lại thành trưởng lão Chu Hoành**
   (43 part, LOD 7 622/7 622/4 998, thay khối hộp không mặt vốn chưa bao giờ đạt
   ART-QUALITY-STANDARD §2 — nợ có từ 2026-08-01, không do pivot sinh ra); giáp kim loại của
   `char_tho-b3` **GIỮ**. Ngân sách tam giác 57/57. Mã vật phẩm và `asset_key` **không đổi một chữ**
   (D5).

   **Tổng cộng Đợt 3 tiêu 0 lượt Rodin.** Chủ dự án ĐÃ đồng ý chi cho ông lão, nhưng khoá dùng thử
   trả `API_INSUFFICIENT_FUNDS` — API từ chối trước khi sinh nên không mất credit. Ông lão đi đường
   `kage_mesh_kit` (offline, tất định); đổi sang bản Rodin sau này tốn đúng một lệnh. ⛔ **Việc duy
   nhất còn treo của Đợt 3: một khoá Hyper3D trả tiền**, và nó KHÔNG chặn gì — bản đang chạy dùng
   được.

   Bài học đắt nhất, đáng nhớ cho mọi asset nhân vật sau này: **ảnh preview Blender không thay được
   `./gradlew :client:client-core:runWorldPreview`**. Ở yaw 0 trong Blender mặt ông lão đẹp; qua bộ
   dựng thật của client (camera chúc xuống theo ADR-031) thì vòm tóc che kín mặt — đúng lỗi Q2 mà
   cả lần dựng lại sinh ra để sửa. Chi tiết: [ASSET-LOG.md](../../../assets/raw/ASSET-LOG.md) mục
   2026-09-03.
8. ✅ D8 xong phần thật sự lệch (2026-09-01): `docs/README` (dòng mô tả dự án), `ARCHITECTURE` (sơ
   đồ), `KAGE-CONSTITUTION Điều 5`, `AI.md`, `RENDERING.md`, `RESOURCE.md` (ví dụ đặt tên),
   `ART-QUALITY-STANDARD`. **Phần CÒN LẠI cố ý GIỮ**: link tới `ADR-033-Ninja-School-Style-Movement.md`
   (tên file thật), các bản ghi playtest cũ trong ROADMAP nhắc "bộ Hạ Nhẫn" (số đo lịch sử — sửa là
   làm sai hồ sơ), và `BLENDER-SESSION-GUIDE` (bản ghi lệnh đã chạy thật). Con số "74 chỗ" ở D8 gộp
   cả ba nhóm đó nên nghe lớn hơn việc thật rất nhiều.
9. 💡 Đề xuất (CHƯA làm, cần chủ dự án gật): quest thứ hai của arc 1 — "đêm trực gặp tán tu cường
   đạo" (STORY.md §6 arc 1 cao trào). Chặn kỹ thuật có thật: `feature-quest/CLAUDE.md` quy định
   **quest thứ hai theo dõi giết quái thì BẮT BUỘC tổng quát hoá `ProcessMonsterKilledUseCase.TRACKED_QUEST_CODE`
   trước** (hiện hardcode đúng một mã), và cần thêm dòng `quest_template` ⇒ một migration. Cả hai
   vượt khỏi phạm vi "chỉ đổi tường thuật" của ADR này.
