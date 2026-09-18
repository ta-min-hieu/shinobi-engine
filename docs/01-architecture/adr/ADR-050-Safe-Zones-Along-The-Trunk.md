# ADR-050: Trạm tiếp ứng dọc trục — vùng an toàn thôi chỉ hồi máu, và bắt đầu CHẶN aggro

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-18) — chủ dự án duyệt **trọn D1–D8** và chọn **D6 phương án (a)**: mốc nhìn thấy được dựng từ `npc_spawn` đã có, 0 opcode, không đổi protocol. Trước đó chủ dự án đã chọn HƯỚNG (c) "vùng an toàn dọc trục" (2026-09-17) trong ba hướng của playtest Lv28–36 |
| Date | 2026-09-17 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (**Revisit Trigger của nó vừa kích hoạt đúng từng chữ**) · [ADR-049](ADR-049-Monster-Retaliation.md) (đường nhắm mục tiêu THỨ HAI — ADR này phải xử lý cả nó) · [ADR-039](ADR-039-Per-Species-AI-Parameters.md) (`aggroRadius`/`leashRadius` theo loài) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (trục chơi MỘT chiều — gốc của vấn đề) · [STORY §7](../../13-game-design/STORY.md) |

---

## Context

### Playtest 2026-09-17 đo được: lát cắt Lv28–36 KHÔNG đi bộ tới nơi được

Lát cắt cuối của v1 (quest 16–20, V48) nằm ở `x = 148…201`. Đo bằng `gameProbe`, nhân vật Cấp 30–31
mặc trọn bộ Trúc Cơ (máu tối đa **206**):

| Chặng đi bộ | Máu lúc tới nơi |
|---|---|
| `0 → 150` | **51/206** |
| `0 → 142` | **83/206** |
| `0 → 145` (hai lần) | **93/206** và **132/206** |

Không con nào trong số đó là quái của quest. Đó là **thuế đường đi**: trục có **11 điểm spawn** giữa
làng và `x = 175`, mỗi con đánh đúng một nhát khi người chơi đi ngang (13–27 sát thương ở quãng này).

Rồi trận chính bắt đầu ở mức máu đó. Đo 3 trận 1v1 với `wasteland_beast` (Lv29) **từ máu ĐẦY**:
**1 thắng, 2 chết** — dù số học đúng thiết kế (người chơi 62 sát thương/nhát, cần 5 nhát; quái 27,
cần 8). Biên +3 nhát trên giấy bị **1–2 nhát mở màn** (quái đánh trước trong lúc mình còn đang đi
tới) và **1 lần né** ăn sạch.

### Vì sao không nghỉ được: tầm với của quái PHỦ KÍN trục

Tầm với thật của một con = `patrol_radius (3) + aggro_radius (3 hoặc 6)`. Trải ra trên trục:

| Vùng phủ | Của con |
|---|---|
| `[87, 99]` · `[114, 126]` · `[128, 140]` | `raider_chieftain` · 2× `blood_sand_acolyte` |
| `[142, 154]` · `[152, 170]` | `blood_sand_ringleader` · `stone_ogre` (aggro 6) |
| `[169, 181]` · `[182, 194]` · `[195, 207]` | 2× `wasteland_beast` · `foundation_warden` |

Khe trống rộng nhất từ `x = 126` trở ra là **2 mét** — hẹp hơn chính tầm đánh (`ATTACK_RANGE_METERS = 2`).
Nghĩa là **nửa cuối trục không tồn tại chỗ đứng hồi máu**, và hồi máu ngoài chiến đấu (ADR-046 D2:
8 giây đứng yên ⇒ 3%/giây) — nguồn hồi duy nhất không tốn vật phẩm — **không dùng được ở đúng chỗ
cần nó nhất**. Tôi đã chết một lần vì tin vào khe trống tính theo *điểm spawn*: thủ lĩnh hồi sinh
rồi **đi tuần tới 152,10** và giết người đang đứng nghỉ ở 154.

### Đây KHÔNG sửa được bằng dữ liệu

Ba cách thuần dữ liệu đều hỏng:

1. **Đặt vùng an toàn vào khe trống** — hết khe từ `x = 126`. Đó là số đo, không phải ý kiến.
2. **Giãn điểm spawn cho có khe** — trục đã nới **bốn lần** trong hai tháng (`[-55, 210]`); nới lần
   năm là né vấn đề, và ROADMAP đã ghi hệ thống **zone** là việc của arc 3+.
3. **Hạ chỉ số hai loài mới** — chữa trận đấu, không chữa thuế đường đi (113–155 máu mất trước khi
   trận bắt đầu).

Và một vùng an toàn đặt ở đó **cũng không cứu được**, vì: **vùng an toàn hiện KHÔNG chặn aggro.**
`AiSystem` không có một dòng nào nhắc tới zone; nó chỉ hồi máu lúc bước vào. Đứng trong vùng mà bị
đánh thì vẫn chết.

### Điều trớ trêu: tường thuật đã hứa điều ngược lại từ lâu

[STORY §7](../../13-game-design/STORY.md) dòng đầu bảng ánh xạ:

> | Hub an toàn (Thanh Hư Tông) | Trận pháp hộ sơn ngàn năm — **trong tông không thể động thủ** |

"Trong tông không thể động thủ" chính là luật chặn aggro, đã là SSOT tường thuật từ ADR-038. Code
chưa bao giờ thi hành nó — vùng tông môn an toàn *nhờ hình học* (`SafeZoneIntegrationTest` khoá
"không quái nào với tới được bên trong"), không nhờ luật. Ở làng, hình học đủ. Ở Hoang Vực thì không.

### ADR-046 đã báo trước đúng tình huống này

Revisit Trigger của ADR-046, nguyên văn:

> Khi thêm **vùng an toàn thứ hai** có luật khác (cấm PvP nhưng không hồi máu): lúc đó `safe_zone`
> cần cột `heals BOOLEAN`, đừng đoán trước bây giờ.

Điều kiện đó vừa xảy ra. ADR này là bản mở lại, không phải phát sinh ngoài kế hoạch.

---

## Decision

### D1 — Chặn aggro là luật của MỌI vùng an toàn, không phải cột dữ liệu

`AiSystem.findNearestCharacterInRange` bỏ qua nhân vật đang đứng trong bất kỳ vùng an toàn nào.

- **Vì sao không thêm cột `blocks_aggro`**: cả hai vùng hiện có (tông môn và trạm tiếp ứng) đều muốn
  luật này, và tường thuật (§7) phát biểu nó cho chính cái hub. Thêm cột để khai một giá trị duy nhất
  là đoán trước luật thứ hai — đúng cái mà ADR-046 đã cấm và đã đúng.
- **Chi phí: MỘT lần đọc component.** Mọi nhân vật đã mang `TriggerZoneMembershipComponent`
  (`Set<String> occupiedZoneIds`) từ ADR-046, và vòng quét aggro vốn đã duyệt qua từng nhân vật.
  Không đổi constructor `AiSystem`, không thêm plumbing, không đụng `WorldContentCatalog`.
- ⚠️ **Trễ một tick**: `TriggerZoneSystem` chạy ở vị trí 324, `AiSystem` ở 310 ⇒ AI đọc membership của
  tick TRƯỚC (16,7 ms ở 60Hz, tức ≤ 0,08 m ở tốc độ chạy 5 m/s). Chấp nhận được, nhưng phải ghi ra
  đây vì nó là loại chi tiết mà sáu tháng sau không ai đoán lại được.

### D2 — Quái đang đuổi mà mục tiêu bước vào vùng thì BỎ CUỘC, về điểm spawn

Hook ở `AiSystem.tickChase`, cùng nhánh đang xét `leashRadiusMeters`: mục tiêu ở trong vùng ⇒
`AiState.RETURN`.

Thiếu D2 thì D1 vô dụng: con quái đã khoá mục tiêu trước khi mình bước vào vẫn đuổi tới tận nơi và
đánh chết mình trong vùng "an toàn". Đây chính là cách tôi chết ở `x = 154`.

### D3 — Đánh ra từ trong vùng thì MẤT quyền được che, trong 8 giây

Nếu không, trạm tiếp ứng thành chòi bắn tỉa: đứng trong vùng đánh con quái ngoài vùng, nó đánh trả
(ADR-049 `Retaliation.onAttacked` — **đường nhắm mục tiêu THỨ HAI**, không đi qua vòng quét aggro),
D2 bắt nó quay về, mình đánh tiếp. Thắng mọi trận mà không mất một giọt máu.

Luật: lá chắn của D1/D2 chỉ có hiệu lực khi `CombatRecencyComponent.secondsSinceAttackDealt >=
REGEN_DELAY_SECONDS` (8 giây) — đồng hồ đếm từ lần cuối người chơi **TỰ RA ĐÒN**. Nó phát biểu một
câu dễ hiểu cho người chơi: *trạm che cho người đang nghỉ, không che cho người đang đánh.*

⚠️ **Bản đầu của D3 dùng `secondsSinceCombat` và đã SAI — sửa 2026-09-18 sau khi đo.** Đồng hồ đó
reset cho **cả bên đánh lẫn bên chịu** (`CombatSystem` gọi `markInCombat(attacker)` *và*
`markInCombat(target)`, cố ý từ ADR-046 D2: "đứng cạnh một con quái đang vung trượt vẫn là đang đánh
nhau"). Hệ quả: người **đang bị đuổi** không bao giờ đạt ngưỡng, nên D3 vô hiệu hoá chính D2 ở đúng
tình huống D2 sinh ra để giải — chạy vào trạm khi đang bị đánh. Ô test
`shouldBreakOffTheChase_whenTheTargetStepsIntoASafeZone` bắt được điều này, và phép thử nới biên vùng
rộng tới mức người chơi không thể ở ngoài vẫn đỏ ⇒ loại hẳn nguyên nhân vị trí.

Cách sửa (chủ dự án chọn): **đồng hồ THỨ HAI** `secondsSinceAttackDealt`, chỉ reset ở nhánh người ra
đòn — `CombatSystem` (đòn thường) và `SkillSystem` (chiêu gây damage; buff/hồi máu cố ý không tính).
`BossAiSystem` không đụng vì nó chỉ đánh dấu bên chịu đòn. `HealthRegenSystem` cộng `deltaTime` cho
đồng hồ mới **trước mọi nhánh thoát sớm**, nếu không người đầy máu sẽ không bao giờ lấy lại lá chắn.
Giá phải trả: **một field mới** — đắt hơn bản đầu đúng một float, và đó là giá của việc D2 chạy thật.

### D4 — Trạm tiếp ứng KHÔNG hồi đầy máu: thêm cột `heals BOOLEAN NOT NULL DEFAULT TRUE`

`sect_grounds` giữ `heals = true` (bước vào ⇒ đầy máu ngay, ADR-046 D3 không đổi).
Sáu trạm mới khai `heals = false`.

- **Vì sao**: hồi đầy tức thì mỗi 30 mét biến mọi trận thành vô hại — bước ra đánh, bước vào đầy máu,
  lặp lại. Thứ người chơi thiếu không phải nút hồi máu, mà là **chỗ đứng yên 8 giây**. Để D1+D2 mở
  khoá đúng cơ chế hồi máu đã có (3%/giây ⇒ 206 máu mất ~23 giây) là trả lại **chi phí thời gian**,
  thứ làm cho việc nghỉ là một quyết định chứ không phải một cái nút.
- Đây đúng là cột mà ADR-046 Revisit Trigger đã đặt tên trước.

### D5 — Sáu trạm (V49), và luật đặt trạm

| `code` | Biên X | Phục vụ |
|---|---|---|
| `waypost_spirit_vein` | `[80, 86]` | quãng 66–93 (thám tử / đầu lĩnh) |
| `waypost_east_ridge` | `[104, 110]` | quãng 93–120 |
| `waypost_dead_vein` | `[124.5, 129.5]` | giữa hai tế đồ |
| `waypost_ringleader_ridge` | `[138.5, 143.5]` | trước đàn chủ |
| `waypost_wasteland_gate` | `[165.5, 170.5]` | **trước hai yêu nhân Lv29** |
| `waypost_trial_approach` | `[192.5, 197.5]` | **trước khảo hạch Trúc Cơ Lv33** |

Trục Y `[-10, 10]` như `sect_grounds` (ADR-033 khoá y=0; hình chữ nhật để không phải đổi hình dạng
khi bản đồ có tầng).

**Luật đặt trạm — thay bất biến cũ**: `SafeZoneIntegrationTest` hiện khoá *"không quái nào với tới
được bên trong vùng"*. Bất biến đó **không thoả được** ở đuôi trục (khe rộng nhất 2m) và từ D1 nó
cũng không còn cần: vùng an toàn nhờ LUẬT, không nhờ khoảng cách. Thay bằng bất biến mới, vẫn kiểm
được bằng máy:

> **Không vùng nào được chồng lên dải tuần tra của một điểm spawn CÓ THỂ GÂY SỰ**
> (`spawn ± patrol_radius`, chỉ xét loài `aggro_radius_meters > 0`).
>
> ⚠️ Vế "có thể gây sự" thêm 2026-09-18 sau khi đo: bản đầu viết "bất kỳ điểm spawn nào" và
> đỏ ngay ở hàng seed CŨ — `blood_sand_enforcer` đứng ở `x = -8` với tuần tra 0, tức dải
> `[-8, -8]` nằm gọn trong sân tông môn `[-15, 5]`. Đó là nội dung cố ý (STORY §6: hộ pháp cải
> trang làm chấp sự, đứng trên hành lang arc 1 — ADR-049 cho hắn `aggro = 0` đúng vì thế).
> Mối nguy thật là con CÓ THỂ nhắm người, nên bất biến gác đúng chỗ đó.

Lý do: quái *đứng trong* vùng an toàn là cảnh vô lý, và D1/D2 không đụng tới quái nên không có gì
đẩy nó ra. Sáu biên trên đều nằm trong các cửa sổ trống-tuần-tra `(123,131) (137,145) (164,172)
(191,198)` và hai khe trống hoàn toàn `(75,87) (99,114)`.

### D6 — Người chơi phải NHÌN THẤY trạm: ✅ chọn phương án (a)

Hôm nay **protocol không chở vùng an toàn, client không biết chúng tồn tại** (grep `shared/*.proto`
và `client/` ra 0 kết quả). Với `sect_grounds` thì không sao — nó là cái làng, nhìn là biết. Một
trạm giữa Hoang Vực mà vô hình thì bằng không có.

| | Phương án | Giá |
|---|---|---|
| ✅ **a** *(ĐÃ CHỌN 2026-09-18)* | **Mốc nhìn thấy được, dựng từ dữ liệu ĐÃ có**: thêm `npc_spawn`/vật thể trang trí tại tâm mỗi trạm | 0 opcode, 0 đổi protocol. Dùng đúng đường `npc_spawn` đang chạy |
| **b** | Opcode mới liệt kê vùng lúc vào world + client vẽ biên | Đổi protocol (ADR-035 có tiền lệ "chỉ THÊM"), client phải vẽ hình học lần đầu |
| **c** | Hoãn — v1 chỉ có cơ chế, nói bằng lời thoại NPC | Rẻ nhất, nhưng người chơi phải học bằng cách chết |

### D7 — Hàng rào test

- `AiSystem`: **chưa có ô test nào** khoá việc chọn mục tiêu aggro (tìm bằng nội dung: chỉ ba lớp
  balance chạm `AiState.CHASE`). Ba luật D1/D2/D3 ship kèm test của chính chúng ở `WorldFacadeTest`,
  cùng khuôn hai ô ADR-049 đang có.
- `SafeZoneIntegrationTest`: `hasSize(1)` → 7; đổi bất biến theo D5.
- `SafeZoneTest` + 7 chặng plumbing của cột `heals`: `PostgresSafeZoneAdapter` (SELECT + mapper) →
  `SafeZone` → `WorldGeometryFacade` → `WorldContentLoader.toSafeZoneDefinition` →
  `SafeZoneCatalogSnapshot.SafeZoneDefinition` → `WorldFacade.toNamedZones` → `NamedZone`.
- `WorldFacadeTest.shouldHealToFullImmediately_whenCharacterWalksIntoASafeZone` giữ nguyên ý nghĩa
  (vùng đó khai `heals = true`), thêm ô đối chứng cho `heals = false`.

### D8 — NGOÀI phạm vi, cố ý

Cấm PvP trong vùng (STORY §7 hứa, chưa có PvP thật trên trục) · vùng cho dungeon/arena · dịch chuyển
nhanh · đổi chỉ số hai loài V48 (nếu sau khi có trạm mà trận vẫn 1 thắng/2 chết thì đó là ADR khác,
và lúc đó đã có số đo sạch để nói chuyện).

---

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Hạ atk `wasteland_beast` 44→38, `foundation_warden` 46→40 | Chữa trận đấu, không chữa **thuế đường đi** 113–155 máu. Và lát cắt sau lại tái diễn — đây là vấn đề hình học trục, không phải vấn đề chỉ số |
| Nâng máu bộ Trúc Cơ +106 → +150 | Như trên, và còn kéo theo lạm phát chỉ số sang arc 3 (Lv37+ đã phải giải đúng bài toán này) |
| Giãn điểm spawn cho có khe nghỉ | Trục đã nới bốn lần; trục dài thêm làm thuế đường đi TĂNG chứ không giảm |
| Dịch chuyển nhanh giữa các mốc | Giải được thuế đường đi, nhưng là hệ thống mới (UI + protocol + luật chống lạm dụng) và nó **xoá** luôn cảm giác "đi xa dần khỏi tông môn" mà STORY §6 dựng cho arc 2 |
| Cho uống đan dược thoải mái | Đã có (`minor_healing_pill`, 50 máu/viên) và người chơi vẫn chết — 3 viên mới bằng một lần nghỉ, và túi chỉ 20 ô |

---

## Consequences

**Được**

- Nửa cuối trục có chỗ nghỉ ⇒ nguồn hồi máu số một của game (ADR-046 D2) dùng được ở đúng chỗ cần.
- Luật "trong tông không thể động thủ" của STORY §7 **lần đầu thành code**, sau một năm chỉ nằm trong
  tài liệu.
- Mở đường cho cấm PvP (cùng bảng, cùng cơ chế) mà không phải dựng lại gì.

**Mất / rủi ro**

- `AiSystem` lần đầu biết tới một khái niệm không thuộc AI. Giữ rẻ bằng cách đọc component có sẵn,
  nhưng đây là một sợi dây mới giữa hai hệ.
- D3 là luật **người chơi phải học mà không được dạy** cho tới khi D6 có câu trả lời.
- Sáu vùng ⇒ `TriggerZoneSystem` xét 7 vùng × N nhân vật mỗi tick. Ở N=2000 là 14.000 phép
  `Aabb.contains` mỗi tick — nằm trong nhóm "mô phỏng ~3% CPU" mà JFR đã đo, không phải nhóm mạng 90%.
- Gói 408: trạm khai `heals = false` **không phát gói nào lúc bước vào** (đúng ý ADR-042); hồi dần
  vẫn bị trần 1 gói/giây/entity như cũ.

---

## Revisit Trigger

- Khi có vùng muốn **cấm PvP nhưng không chặn aggro quái** ⇒ lúc đó mới tách `blocks_aggro` khỏi D1.
- Khi hệ thống **zone** của arc 3+ ra đời: trạm tiếp ứng có thể trở thành điểm dịch chuyển, và D5
  (luật đặt trạm) phải được xét lại theo bản đồ nhiều vùng.
- Nếu sau khi có trạm mà trận Lv29 vẫn thua ≥1/3 lượt đo ⇒ mở lại chuyện chỉ số hai loài V48.

---

## Technical Decisions

- Chặn aggro là **luật**, không phải cột — vùng an toàn nào cũng chặn.
- Hồi máu là **cột** (`heals`) — vì ở đây thật sự có hai luật khác nhau.
- Lá chắn hết hiệu lực khi đang đánh nhau, đo bằng **đồng hồ đã có** (`REGEN_DELAY_SECONDS`).
- Bất biến hình học đổi từ "quái không với tới được" sang "vùng không chồng dải tuần tra".

## ADR cần tạo

Không — trừ khi chọn D6 phương án (b), lúc đó opcode mới đi theo tiền lệ ADR-035 và cần một dòng
trong ADR này chứ không cần ADR riêng.

## Tài liệu liên quan

Phải cập nhật cùng PR thi hành: [GAMEPLAY.md](../../05-server/GAMEPLAY.md) (bảng 4 nguồn hồi máu) ·
[AI.md §3](../../03-engine/AI.md) (máy trạng thái FSM có nhánh mới) ·
[DATABASE.md](../../05-server/DATABASE.md) (⚠️ **hiện KHÔNG có mục nào cho `safe_zone`** — V35 ship
bảng mà tài liệu DB chưa bao giờ ghi; trả nợ luôn) · [STORY §7](../../13-game-design/STORY.md)
(thêm dòng "Trạm tiếp ứng") · [GLOSSARY](../../GLOSSARY.md) (thêm thuật ngữ, §4 luật 2) ·
[ROADMAP](../../11-process/ROADMAP.md) (mục v1 còn ghi trục là `[-55, 170]`, thật ra `[-55, 210]`).

## Việc cần làm tiếp theo

1. ✅ Chủ dự án duyệt D1–D8 và chọn D6(a) — 2026-09-18.
2. Thi hành: V49 (cột + 6 hàng) → 7 chặng plumbing → D1/D2/D3 trong `AiSystem` → test → 6 tài liệu.
3. **Playtest lại đúng lát cắt đã đo hôm nay** (`0 → 177`, rồi trận Lv29) và so với hai bảng số ở
   mục Context. Đó là thước đo duy nhất nói được ADR này có giải quyết vấn đề thật hay không.
