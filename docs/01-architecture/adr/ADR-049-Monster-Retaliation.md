# ADR-049: Quái đánh trả khi bị đánh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-12) — chủ dự án duyệt trọn D1–D7. Thi hành ngay cùng ngày. |
| Date | 2026-09-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-039](ADR-039-Monster-Ai-Params-Per-Species.md) (**D4 cố ý làm cọc tập THỤ ĐỘNG — ràng buộc chi phối ADR này**) · [ADR-048](ADR-048-Boss-Into-Data-Tables.md) (boss vào bảng dữ liệu; chính nó lộ ra lỗ hổng) · [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) · [AI.md §3](../../05-server/AI.md) |

---

## Context

### Playtest quest 9 đo được: boss cao trào arc 2 KHÔNG đánh trả một nhát nào

Quest 9 (`confront_the_steward`, V42) là cao trào arc 2: người chơi đối chất chấp sự Hình Vân Sơn,
hắn trở mặt. Đo bằng `gameProbe` ngày 2026-09-12:

```
t=1946ms 3 -> -10 sátThương=33 ... máuCònLại=117
t=3130ms 3 -> -10 sátThương=33 ... máuCònLại=84
t=4146ms 3 -> -10 sátThương=33 ... máuCònLại=51
t=5163ms 3 -> -10 sátThương=33 ... máuCònLại=18
t=8230ms 3 -> -10 sátThương=33 ... máuCònLại=0 chết=true
Nhân vật: máu=118/118        ← KHÔNG mất một giọt nào
```

Năm nhát, tám giây, người chơi **đầy máu**. Không có `heavy_strike`, không có `enrage` — tức toàn
bộ script hai pha của hắn (`blood_sand_enforcer.js`: lộ nguyên hình dưới 55% máu, chiêu ×1.6/×2.0)
**chưa từng chạy một dòng nào**.

### Nguyên nhân: chuỗi ba mắt xích, và không mắt nào sai riêng lẻ

1. Hắn khai `aggro_radius_meters = 0` — CỐ Ý, vì hắn là chấp sự đứng trong sân tông môn; aggro > 0
   nghĩa là hắn đánh lén mọi người đi ngang (x=-8 nằm trên hành lang mà người chơi **arc 1** phải đi
   qua để tới thạch vệ @-20/-33 và sân khảo hạch @-47).
2. `AiSystem` chỉ đặt mục tiêu ở MỘT chỗ duy nhất — vòng quét aggro (`startChasing`). Không có
   đường nào khác đặt `TargetComponent.targetEntityId`.
3. `CombatSystem` chỉ ra đòn khi `targetEntityId != 0`, và `BossAiSystem` chỉ chạy khi
   `AiState == ATTACK`.

⇒ aggro 0 không chỉ nghĩa là "không chủ động gây hấn", nó nghĩa là **không bao giờ tự vệ**. Một con
quái aggro 0 là bao cát, kể cả khi nó là boss 150 máu của cao trào một arc.

### Vì sao không sửa được bằng dữ liệu

| Cách | Vì sao không |
|---|---|
| Cho boss aggro 2–3m | x=-8 nằm trên hành lang phía tây của **arc 1**. Trục chơi là MỘT chiều (y=0 khoá bởi ADR-033), nên "đi ngang" nghĩa là đi xuyên qua đúng toạ độ đó — người chơi Cấp ~8, ~100 máu sẽ ăn đòn của boss Cấp 12, atk 30. |
| Dời boss ra chỗ vắng | Không có chỗ vắng: mọi điểm trên trục đều là hành lang tới một nội dung khác. Phần đông vùng an toàn `[-15, 5]` thì bị bệ nhảy `[3,7]` và hai NPC (@-5, @2) chiếm. |
| Dựa vào vùng an toàn | **Vùng an toàn KHÔNG chặn aggro** — nó chỉ hồi máu lúc bước vào (ADR-046 D3). Chính playtest này đã chứng minh: thạch vệ đuổi vào trong `[-15, 5]` và đánh chết người chơi ở x=-12. |

### Ràng buộc phải giữ: ADR-039 D4

ADR-039 D4 làm `training_dummy` **thụ động có chủ đích** (`aggro_radius = 0`, `patrol_radius = 0`),
và ADR đó viết thẳng rằng `aggro_radius >= 0` cho phép **0** vì "0 là một giá trị có nghĩa". Playtest
sống của nó là *"đứng đè lên cọc 20 giây, 0 sát thương"*.

⇒ **"Cho mọi quái đánh trả" là phá D4.** Cọc luyện công mà đánh trả thì lại đúng con bug
"cọc luyện công đuổi đánh người chơi" đã sửa 2026-09-04.

---

## Decision

**D1. Thêm cột `retaliates BOOLEAN NOT NULL DEFAULT false`** trên `monster_template` — "con này bị
đánh thì có đánh trả không". Mặc định `false` nên **không một loài hiện có nào đổi hành vi**.

**D2. Chỉ `blood_sand_enforcer` khai `true`** ở đợt này. Các loài aggro > 0 không cần: chúng đã tự
đuổi theo người chơi trong tầm aggro rồi. Loài duy nhất cần là loài CỐ Ý aggro 0 mà vẫn phải biết
tự vệ.

**D3. Thuộc về `MonsterAiParams`, không phải một record riêng** — "con này hung dữ tới đâu" là câu
hỏi AI, đúng chỗ ADR-039 đã mở ra cho `aggro_radius`. Đi kèm nó qua cả bốn chặng dữ liệu đang có
(`MonsterTemplate` → `MonsterCatalogSnapshot` → `AiParamsComponent`), không thêm đường mới nào.

**D4. Cơ chế: khi một quái NHẬN sát thương từ người chơi mà nó chưa có mục tiêu và nó khai
`retaliates`, thì đặt mục tiêu là kẻ vừa đánh nó và chuyển sang `CHASE`.** Đây là thay đổi Java
DUY NHẤT, và nó nằm ở tầng cơ chế (Điều 27) — "đánh trả" là luật chơi chung, không phải nội dung của
riêng con nào. Nội dung vẫn là cột dữ liệu + script.

**D5. Áp cho CẢ đòn thường lẫn đòn kỹ năng.** Một con boss chỉ đánh trả khi bị đánh thường, còn
đứng im khi ăn kỹ năng, là một luật chơi không ai đoán được.

**D6. `leash` vẫn quyết định như cũ.** Quái đánh trả rồi vẫn bị kéo về điểm spawn khi ra ngoài leash
— không có ngoại lệ nào cho `retaliates`, nếu không "đánh rồi chạy" lại thành chiến thuật tối ưu
(đúng lỗ hổng ADR-046 D4 vừa bịt).

**D7. KHÔNG đổi protocol.** Client không cần biết con nào đánh trả; nó chỉ thấy `AttackResultEvent`
như mọi đòn khác. `protocolVersion` không tăng.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| **A. Cột `retaliates` + đặt mục tiêu khi dính damage** (khuyến nghị, chủ dự án chọn) | Boss đứng yên giữa tông môn mà vẫn là boss thật; không loài nào khác đổi hành vi; đúng nghĩa "chấp sự đứng yên tới khi bị đối chất" | Một cột schema + một thay đổi cơ chế ở AI | **Chọn** |
| B. Mọi quái đều đánh trả (không cột) | Không tốn schema | **Phá ADR-039 D4** — cọc luyện công đánh trả là đúng con bug đã sửa 2026-09-04 | Không |
| C. Cho boss aggro 2–3m | Chỉ sửa dữ liệu | Người chơi **arc 1** đi qua x=-8 ăn đòn boss Cấp 12. Trục chơi một chiều nên không né được | Không |
| D. Dời nội dung arc 1 ra khỏi hành lang | Chỉ sửa dữ liệu | Đụng lại bố cục 3 quest arc 1 đã playtest xong, và bản đồ chỉ có một trục nên chỗ dời rất hẹp | Không |
| E. Ship cao trào một chiều | Rẻ nhất hôm nay | Boss 150 máu đứng im chịu 5 nhát; script hai pha vừa viết nằm im | Không |

---

## Consequences

**Tích cực:** một loài có thể vừa "không gây sự với ai" vừa "không phải bao cát" — thứ mà trước
ADR này không khai được. Cao trào arc 2 chạy đúng script hai pha của nó. Mở đường cho mọi NPC-như-
quái về sau (kẻ gác cổng, đối thủ tỉ thí) mà không phải cho chúng aggro.

**Đánh đổi:** thêm 1 cột chỉ có nghĩa với một nhóm nhỏ loài; thêm một đường đặt `TargetComponent`
ngoài vòng quét aggro — từ nay `AiSystem` không còn là nơi DUY NHẤT quyết định mục tiêu của quái, và
đó là thứ phải nhớ khi đọc AI.md §3.

**Module bị ảnh hưởng:** `server-database` (migration + adapter), `feature-monster`
(`MonsterAiParams`), `server-world` (`MonsterCatalogSnapshot`, `AiParamsComponent`, `CombatSystem`/
`SkillSystem` đặt mục tiêu khi gây damage), `server-gateway` (`WorldContentLoader`).
**Không đụng protocol. Không đụng client.**

**Rủi ro đã biết:** đặt mục tiêu từ trong đường xử lý damage nghĩa là hai hệ thống cùng ghi
`TargetComponent` trong một tick. Cả hai đều chạy trên tick thread (một luồng chạm ECS tại một thời
điểm — SERVER.md §3), nên không có tranh chấp; nhưng thứ tự "đặt mục tiêu rồi mới tới lượt AiSystem"
phải được gác bằng test, không suy luận.

---

## Revisit Trigger

- Cần **mức độ thù ghét** (threat/aggro table) thay vì "kẻ đánh cuối cùng" — lúc đó `retaliates`
  một cờ boolean là không đủ.
- Có loài cần đánh trả **cả quái khác** (quái đánh nhau) ⇒ mở lại giả định "chỉ người chơi mới gây
  ra đánh trả".

## Technical Decisions

- "Đánh trả" là CƠ CHẾ (Java), "con nào đánh trả" là DỮ LIỆU (cột) — đúng ranh Điều 27.
- Mặc định `false` để không loài nào đang chạy đổi hành vi, giữ nguyên ADR-039 D4.

## Tài liệu liên quan

- [ADR-039](ADR-039-Monster-Ai-Params-Per-Species.md) D4 — lý do không thể cho mọi quái đánh trả.
- [ADR-048](ADR-048-Boss-Into-Data-Tables.md) — đợt đưa boss vào bảng, nơi lỗ hổng này lộ ra.
- [AI.md §3](../../05-server/AI.md) — FSM và chỗ mục tiêu được đặt.

## Việc cần làm tiếp theo

1. ~~Chủ dự án Accept hoặc từ chối~~ ✅ **Accepted 2026-09-12**, thi hành ngay cùng ngày.
2. Migration V43 (`retaliates` + bật cho `blood_sand_enforcer`), luồng dữ liệu bốn chặng, đặt mục
   tiêu khi dính damage ở cả hai đường (đòn thường + kỹ năng), test gác.
3. Playtest lại quest 9 — lần này phải thấy `heavy_strike` và `enrage` trong nhật ký.
