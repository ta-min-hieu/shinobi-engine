# ADR-048: Kéo boss vào bảng dữ liệu

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-09-12) — chủ dự án đã CHỌN HƯỚNG này khi được hỏi, nhưng **chưa Accept ADR**. AI không tự Accept; chưa thi hành một dòng nào. |
| Date | 2026-09-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) (**D7 hoãn boss, và tự đặt điều kiện mở lại**) · [ADR-016](ADR-016-Scripting-Engine.md) (GraalJS — hành vi là script) · [ADR-026](ADR-026-Dungeon-Instance-Model.md) (boss hiện chỉ sống trong hầm ngục) · [ADR-047](ADR-047-Dungeon-Entered-Event.md) (đang Proposed — chạm cùng vùng hầm ngục) · [GAMEPLAY.md §6](../../05-server/GAMEPLAY.md) · [DATABASE.md §2.2](../../05-server/DATABASE.md) |

---

## Context

### Điều kiện mở lại đã xảy ra, và chính ADR-037 viết ra nó

ADR-037 (Accepted 2026-08-30) kéo quái vào `monster_template`/`monster_spawn` nhưng **cố ý hoãn boss**
ở D7, kèm đúng một câu Revisit Trigger:

> **"Khi boss thứ hai xuất hiện ⇒ mở lại 'boss giữ hardcode'."**

Quest 9 — cao trào arc 2, *hộ pháp Huyết Sa cải trang làm chấp sự tông môn* ([STORY.md §6](../../13-game-design/STORY.md))
— **là boss thứ hai**. Nên đây không phải một đề xuất tự phát: nó là điều kiện do chính ADR trước đặt ra.

### Boss hôm nay hardcode ở BỐN chỗ rời rạc

| Chỗ | Nội dung |
|---|---|
| `BossConstants` | `BOSS_CODE = "sample_boss"` · `BOSS_EXP_REWARD = 200` · `BOSS_SCRIPT_RESOURCE = "/scripts/boss/sample_boss.js"` · `DECISION_INTERVAL_SECONDS = 0.5` · `HEAVY_STRIKE_COOLDOWN_SECONDS = 3` |
| `CombatConstants` | `BOSS_STATS` (ATK/DEF/RES/hit/dodge/crit) · `BOSS_MAX_HP = 320` |
| `WorldFacade` | `BOSS_SPAWN_X = 3` · `BOSS_SPAWN_Y = 0` · `spawnBossEntity()` dựng entity RIÊNG, `modelKey = ""` (client tự suy) |
| `BossAiSystem` | `bossScript = loadScript(BOSS_SCRIPT_RESOURCE)` — nạp **ĐÚNG MỘT** script lúc khởi tạo |

Ba chỗ đầu chỉ là số. **Chỗ thứ tư mới là thứ thật sự chặn**: hệ thống AI boss cầm một script duy
nhất cho cả đời tiến trình, nên boss thứ hai **không có đường nào để khai hành vi riêng**. Thêm một
`if (monsterCode.equals(...))` ở đó là đúng thứ Điều 27 sinh ra để tránh.

### So sánh với đường quái thường (đã đi qua ADR-037)

```java
// quái thường — đọc từ bảng
monster.modelKey  = loai.modelKey();
monster.expReward = loai.expReward();

// boss — đọc từ hằng số Java
monster.modelKey  = "";                              // client tự suy ⇒ hộp thay thế
monster.expReward = BossConstants.BOSS_EXP_REWARD;
entity.add(toComponent(CombatConstants.BOSS_STATS));
```

Hệ quả nhìn thấy được: boss là thực thể DUY NHẤT còn ra hộp thay thế trong client, vì nó không có
`model_key` để mà trỏ tới `.glb`.

### Tám quest gần nhất đã chứng minh đường bảng rẻ hơn hẳn

V28 → V30 → V33 → V37 → V38 → V39 → V40: **bảy migration liên tiếp thêm nội dung mà không sửa một
dòng Java nội dung nào**. Quest 9 đi đường hardcode sẽ là lần đầu phá lệ đó — và phá vào đúng chỗ
ADR-037 đã dặn phải mở lại.

---

## Decision

*(Đề xuất — chờ chủ dự án Accept. Chủ dự án đã chọn HƯỚNG khi được hỏi 2026-09-12.)*

**D1. Boss là một dòng `monster_template` như mọi loài khác**, phân biệt bằng cột mới `is_boss`.
Không dựng bảng `boss_template` riêng: boss dùng chung TOÀN BỘ cột đang có (`max_hp`, `atk`, `def`,
`res`, `exp_reward`, `model_key`, `level`, `drop_table_id`, tham số AI), chỉ khác ở hành vi.

**D2. Hành vi boss khai bằng `ai_script_ref`** (`VARCHAR(64)`, NULL = không có AI riêng, chạy như
quái thường). Đây là cột **đường dẫn script**, không phải cột chứa logic — đúng khuôn
`npc_template.dialogue_script_ref` và `quest_template.script_ref` đã dùng từ Phase 6.

**D3. `BossAiSystem` nạp script THEO `monsterCode`** (cache theo `scriptRef`, cùng khuôn
`QuestScriptRunner.scriptSourceCache`), thay cho một script cố định lúc khởi tạo. Đây là thay đổi
Java DUY NHẤT thuộc tầng cơ chế, và nó là điều kiện để mọi boss về sau chỉ tốn dữ liệu.

**D4. Hai tham số nhịp AI thành cột**: `ai_decision_interval_seconds` và `ai_skill_cooldown_seconds`
(mặc định giữ đúng số hiện tại 0,5 và 3,0). Lý do: boss thứ hai của STORY là kẻ *cải trang* — nhịp
ra chiêu của hắn phải khác con máy móc `sample_boss`, và đó là dữ liệu, không phải logic.

**D5. `sample_boss` CHUYỂN sang bảng trong cùng migration** — giữ nguyên từng con số hiện có
(HP 320, EXP 200, script cũ, 0,5s, 3,0s). Không đổi hành vi, và test Phase 8 hiện có là hàng rào:
nếu chúng đỏ thì việc chuyển đã làm sai, không phải chúng lỗi thời.

**D6. Vị trí spawn đi theo `monster_spawn` như mọi loài** — boss world chính nhờ đó không cần đường
riêng nào. `spawnBoss()` (lệnh của hầm ngục) vẫn giữ, nhưng nhận **mã boss** thay vì hardcode, để
hầm ngục chọn được boss của nó.

**D7. KHÔNG đổi protocol.** Client đã nhận `model_key` từ `MonsterSpawnedEvent` (ADR-037); boss có
`model_key` thật thì nó thôi ra hộp thay thế — đó là hệ quả, không phải tính năng mới.
`protocolVersion` không tăng.

**D8. Cắt cảnh KHÔNG thuộc ADR này.** Chủ dự án chọn hoãn: cao trào arc 2 kể bằng cây hội thoại đã
có (chấp sự nói chuyện rồi trở mặt). Cắt cảnh thật sẽ cần ADR riêng khi có người làm phần nhìn.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| **A. Kéo vào `monster_template` + `ai_script_ref`** (khuyến nghị) | Boss thứ ba trở đi chỉ tốn dữ liệu; boss hết ra hộp thay thế; đúng câu ADR-037 tự dặn | Đổi schema (3 cột); một thay đổi Java ở `BossAiSystem` | **Chọn** |
| **B. Bảng `boss_template` riêng** | Tách bạch "boss" khỏi "quái" | Nhân đôi 9 cột đang dùng chung, và mọi đường đọc quái phải học thêm một nguồn thứ hai. Boss KHÁC quái ở hành vi, không ở dữ liệu | Không |
| **C. Giữ hardcode, thêm `if` cho boss thứ hai** | Nhanh nhất hôm nay | Nhân đôi đúng phần ADR-037 dặn phải mở lại; boss thứ ba lại thêm một `if` nữa; vẫn phải tự viết đường spawn world chính | Không |
| **D. Script hoá cả chỉ số boss** | Nhất quán "script = nội dung" | ADR-037 đã bác đúng lập luận này cho quái: chỉ số là **dữ liệu bảng**, script hoá một hàng số là chọn công cụ nặng nhất cho việc nhẹ nhất | Không |

---

## Consequences

**Tích cực:** boss thứ ba trở đi chỉ tốn một dòng dữ liệu + một file script, đúng như tám quest gần
nhất; boss có `model_key` nên thôi là thực thể duy nhất còn vẽ bằng hộp; hầm ngục chọn được boss
riêng thay vì luôn là `sample_boss`.

**Đánh đổi:** 3 cột mới trên `monster_template` (2 trong đó chỉ có nghĩa với boss — NULL với quái
thường); `BossAiSystem` phải cache script theo ref thay vì giữ một chuỗi.

**Module bị ảnh hưởng:** `server-database` (migration + adapter đọc 3 cột), `feature-monster`
(`MonsterTemplate` thêm 3 field), `server-world` (`BossAiSystem` nạp theo ref; `spawnBossEntity`
hợp nhất với đường spawn thường; `BossConstants`/`CombatConstants.BOSS_STATS` teo lại còn mặc định).
**Không đụng protocol. Không đụng client.**

**Rủi ro đã biết:** `sample_boss` đang được ba test Phase 8 gác (spawn trong hầm ngục, đánh boss qua
socket thật). Chúng là hàng rào của lần chuyển này — phải xanh mà KHÔNG sửa kỳ vọng.

---

## Revisit Trigger

- Boss cần **nhiều pha có script riêng từng pha**, hoặc cần trạng thái sống lâu hơn một trận ⇒ lúc
  đó `ai_script_ref` một cột là không đủ.
- Có **loại AI thứ hai không phải boss** (quái bắn xa, quái triệu hồi) ⇒ cân nhắc đổi tên cột thành
  thứ chung hơn và bỏ `is_boss` đi.

## Technical Decisions

- Boss khác quái ở **hành vi**, không ở dữ liệu ⇒ dùng chung bảng, thêm cột đường dẫn script.
- Thay đổi Java chỉ ở tầng cơ chế (nạp script theo ref); nội dung boss nằm ở script + dữ liệu.
- `sample_boss` chuyển nguyên trạng, test Phase 8 là hàng rào chứng minh việc chuyển không đổi hành vi.

## ADR cần tạo

- **Cắt cảnh** (nếu sau này làm thật) — ADR riêng, không thuộc ADR này (D8).

## Tài liệu liên quan

- [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) — D7 và câu Revisit Trigger sinh ra ADR này.
- [ADR-016](ADR-016-Scripting-Engine.md) — sandbox GraalJS, nơi hành vi boss chạy.
- [GAMEPLAY.md §6](../../05-server/GAMEPLAY.md) · [DATABASE.md §2.2](../../05-server/DATABASE.md).

## Việc cần làm tiếp theo

1. **Chủ dự án Accept hoặc từ chối** — AI không tự Accept (luật dự án).
2. Nếu Accept: migration (3 cột + chuyển `sample_boss` + boss arc 2), `BossAiSystem` nạp theo ref,
   hợp nhất đường spawn, rồi quest 9 (script + thoại chấp sự trở mặt + i18n + test + playtest).
3. Quest 9 **không** chờ cắt cảnh (D8).
