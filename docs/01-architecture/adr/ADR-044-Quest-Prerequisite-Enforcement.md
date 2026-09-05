# ADR-044: Thi hành `prerequisite_quest_id` — quest có thứ tự thì phải đi đúng thứ tự

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-05) — chủ dự án duyệt và trả lời cả hai câu hỏi: mốc là **`CLAIMED`**, và **KHÔNG** giấu lựa chọn hội thoại. |
| Date | 2026-09-05 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [STORY.md §6, §8](../../13-game-design/STORY.md) (thứ tự arc 1) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (tiền lệ thêm outcome/opcode cho nhật ký quest) · [feature-quest/CLAUDE.md](../../../server/server-features/feature-quest/CLAUDE.md) |

---

## Context

### Cột tồn tại từ V11, không ai đọc

`quest_template.prerequisite_quest_id` có từ **V11** (Phase 6, 2026-07-23). Suốt từ đó tới
2026-09-05 **không dòng dữ liệu nào dùng nó** và **không dòng code nào đọc nó** — `grep prerequisite`
trên cả `feature-quest` lẫn `server-database` chỉ ra đúng **một javadoc** ghi *"chưa đọc
`type`/`prerequisite_quest_id`, thêm khi có nhu cầu thật"* (YAGNI, đúng lúc viết).

**V28 (2026-09-05) là dòng đầu tiên dùng nó**: `night_watch_bandit` trỏ về `kill_training_dummies`.
Nhu cầu thật đã tới.

### Triệu chứng hiện tại

`AcceptQuestUseCase.execute` chỉ kiểm hai điều — quest có tồn tại không, và đã nhận chưa. Nên hôm
nay người chơi **nhận được quest "trực đêm" mà chưa từng đập một cái mộc nhân nào**, ngược hẳn thứ
tự [STORY.md §6](../../13-game-design/STORY.md): việc vặt mở màn trước, cao trào sau. Cây hội thoại
của Chu Hoành bày cả hai lựa chọn cùng lúc.

Không ai gặp lỗi này trước đây vì trước V28 **chỉ có đúng một quest trong toàn bộ dự án**.

### Vì sao không lặng lẽ vá

Từ chối một lệnh vì lý do MỚI nghĩa là client phải phân biệt được lý do đó. `AcceptQuestOutcome`
hiện có đúng ba giá trị (`OK`, `QUEST_NOT_FOUND`, `ALREADY_ACCEPTED`) — thêm giá trị thứ tư là **đổi
protocol**, và CLAUDE.md §4 bắt DỪNG + ADR. Gộp vào `QUEST_NOT_FOUND` thì người chơi đọc được câu
sai ("không có nhiệm vụ này") cho một tình huống hoàn toàn khác.

---

## Decision

**D1.** `QuestTemplate` (VO của `domain/`) mang thêm `OptionalLong prerequisiteQuestId` —
`OptionalLong` chứ không phải `Long` có thể null (Điều 11 cấm `null`) và không phải
`Optional<Long>` (khỏi boxing một giá trị đọc mỗi lần nhận quest).
`PostgresQuestTemplateAdapter` đọc cột đã có; **không migration nào**.

**D2.** `AcceptQuestUseCase` từ chối khi quest tiên quyết **chưa ở trạng thái `CLAIMED`**, trả
`AcceptResult.PREREQUISITE_NOT_MET`.

⚠️ **Chọn mốc `CLAIMED`, không phải `COMPLETED`** — quest 1 chưa lĩnh thưởng thì chuỗi kể chuyện
chưa đóng (người chơi chưa quay lại gặp Chu Hoành), và `CLAIMED` là trạng thái cuối duy nhất không
bao giờ lùi. Đây là **quyết định gameplay**; chủ dự án đã xác nhận `CLAIMED` (2026-09-05).

**D3.** Protocol: thêm `ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET = 4` vào `AcceptQuestOutcome`.
Chỉ THÊM giá trị enum, không đổi/xoá giá trị nào ⇒ `protocolVersion` **KHÔNG tăng** (NETWORK.md §6).

**D4.** Client: thêm khoá i18n `quest.notice.prerequisite_not_met` vi/en (cùng họ
`quest.notice.*` với các câu nhận quest khác) — *"Phải xong nhiệm vụ trước đã."* Client cũ nhận giá
trị lạ đã có sẵn nhánh "không rõ" (cùng khuôn `claimOutcomeMessageKey`), nên không vỡ.

**D5.** **KHÔNG giấu lựa chọn hội thoại.** NPC vẫn bày câu hỏi; server mới là nơi từ chối (Điều 15 —
server là nguồn chân lý, client không được tự quyết cái gì bày ra được). Lọc theo điều kiện ở phía
hội thoại là một tính năng riêng, tốn hơn nhiều, và cần ADR riêng nếu muốn.

**D6.** Quest **không có** quest tiên quyết (`prerequisite_quest_id IS NULL`) giữ nguyên hành vi cũ.

---

## Alternatives Considered

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| **A. Thi hành ở `AcceptQuestUseCase` + outcome mới** (ĐÃ CHỌN) | Một chỗ duy nhất, đúng tầng (nghiệp vụ nhận quest); dùng đúng cột DB đã có; người chơi đọc được câu ĐÚNG | Thêm một giá trị enum vào protocol; thêm một vòng tra DB mỗi lần nhận quest (hiếm, không phải đường nóng) | **Chọn** |
| B. Để nguyên, chỉ sửa comment V28 cho khỏi ngụ ý | 0 công, 0 rủi ro | Mọi quest có thứ tự kể chuyện đều dựa vào may mắn; STORY.md §8 sẽ mâu thuẫn với thứ đang chạy | Không — chủ dự án đã chọn thi hành |
| C. Ẩn lựa chọn hội thoại khi chưa đủ điều kiện | Người chơi không thấy thứ mình không dùng được | Client/dialogue phải biết trạng thái quest ⇒ phá ranh giới 2 module mà `feature-dialogue`/`feature-quest` cố ý giữ; và vẫn phải chặn ở server (Điều 15) nên là công VIỆC THÊM chứ không thay thế | Không bây giờ — có thể làm sau, trên nền A |
| D. Gộp vào `QUEST_NOT_FOUND` | Không đổi protocol | Nói dối người chơi: "không có nhiệm vụ này" cho một quest có thật | Không |

---

## Consequences

**Tích cực:** thứ tự kể chuyện của STORY.md được máy móc bảo đảm, không phụ thuộc việc người chơi
tình cờ bấm đúng thứ tự. Cột `prerequisite_quest_id` thôi là dữ liệu chết.

**Đánh đổi:**
- Một vòng tra DB thêm mỗi lần nhận quest (chỉ khi quest có `prerequisite_quest_id`).
- Chuỗi quest dài sẽ **không** tự kiểm bắc cầu: A→B→C chỉ kiểm B khi nhận C. Đủ dùng vì muốn có B
  thì đã phải qua A. Ghi rõ để không ai tưởng nó kiểm cả chuỗi.

**Module bị ảnh hưởng:** `shared-protocol` (1 giá trị enum), `feature-quest` (VO + use case),
`server-database` (1 câu SQL đọc thêm cột), `server-gateway` (1 nhánh ánh xạ outcome), `client-core`
(1 khoá i18n × 2 ngôn ngữ). **Không migration.**

---

## Câu hỏi cho chủ dự án — ĐÃ TRẢ LỜI (2026-09-05)

1. **Mốc là `CLAIMED`** (không phải `COMPLETED`) — chuỗi kể chuyện chỉ đóng khi người chơi quay lại
   gặp NPC lĩnh thưởng.
2. **KHÔNG giấu lựa chọn hội thoại** — NPC vẫn hỏi, server từ chối kèm lý do (Điều 15). Việc ẩn
   lựa chọn để sau, cần ADR riêng nếu làm.

---

## Revisit Trigger

Khi có chuỗi quest **dài hơn 2 bậc** và cần kiểm bắc cầu thật, hoặc khi cần điều kiện mở quest khác
ngoài "đã xong quest kia" (cấp độ, hệ phái, vật phẩm) — lúc đó cột đơn này không đủ và cần một mô
hình điều kiện riêng.

---

## Technical Decisions

- Không migration: cột đã có từ V11, chỉ là chưa ai đọc.
- Chỉ THÊM giá trị enum ⇒ `protocolVersion` không tăng.
- Kiểm ở `application/`, không ở script: đây là **cơ chế** (thứ tự mở khoá), không phải **nội dung**
  (Điều 27) — script quyết điều kiện HOÀN THÀNH, DB quyết điều kiện MỞ.

## ADR cần tạo

Nếu sau này muốn lọc lựa chọn hội thoại theo điều kiện (phương án C) — ADR riêng.

## Tài liệu liên quan

- [STORY.md §6, §8](../../13-game-design/STORY.md) — thứ tự arc 1 và luật viết quest.
- [feature-quest/CLAUDE.md](../../../server/server-features/feature-quest/CLAUDE.md) — mục ghi nhận
  lỗ hổng này.

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án duyệt 2026-09-05** và chốt cả hai câu hỏi.
2. ✅ **ĐÃ THI HÀNH cùng ngày** xuyên 6 tầng: `QuestTemplate` (`OptionalLong`) → `AcceptQuestUseCase`
   → `PostgresQuestTemplateAdapter` → `AcceptQuestOutcome = 4` → gateway → i18n vi/en.
3. ✅ **Test**: khẳng định cũ đã ĐẢO (đúng như nó tự hẹn), cộng 3 test mới — chưa làm quest 1 thì bị
   từ chối, làm XONG mà chưa lĩnh thưởng vẫn bị từ chối, lĩnh xong mới mở khoá.
4. 🐛 **Lỗi bắt được lúc thi hành, đáng ghi**: bản mapper đầu tiên gọi `rs.wasNull()` BÊN TRONG lời
   gọi constructor — tức SAU `getLong("id")` và `getString("script_ref")` — nên nó trả lời về
   `script_ref` (không bao giờ NULL) và **mọi quest đều trông như có tiên quyết id = 0**, chặn sạch
   cả quest mở màn. 8 test bắt ngay. `wasNull()` nói về **lần đọc cột GẦN NHẤT**; phải chụp lấy
   NGAY.
