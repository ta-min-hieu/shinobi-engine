# ADR-054: Lệnh bỏ nhiệm vụ — `AbandonQuestCommand` (612/613), bỏ rồi nhận lại được

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn mục "Bỏ nhiệm vụ (dòng 69a)" trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi ghi rõ "chọn = Accept ADR đó và thi hành luôn"). AI soạn văn bản sau khi được chọn, theo đúng mô tả trong câu hỏi. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md dòng 69(a)](../../README.md) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (nhật ký nhiệm vụ 610/611) · [ADR-044](ADR-044-Quest-Prerequisite-Enforcement.md) (quest tiên quyết) · [NETWORK.md §6](../../05-server/NETWORK.md) |

---

## Context

Fuzz máy trạng thái quest (2026-10-07, dòng 69) không thấy trạng thái kẹt nào, nhưng chỉ ra một đường
kẹt VĨNH VIỄN có thật: nếu `on_event` của một script quest lỗi (nội dung hỏng, hoặc một đợt sửa script
làm tiến độ cũ không còn đọc được), dòng `character_quest_progress` mắc ở `IN_PROGRESS` mãi —
`AcceptQuestUseCase` trả `ALREADY_ACCEPTED`, và không có lệnh nào xoá được dòng đó ngoài SQL tay.
Với quest tiên quyết (ADR-044), một quest kẹt chặn cả phần còn lại của cốt truyện.

## Decision

1. **Opcode mới trong dải Quest**: `OPCODE_ABANDON_QUEST_COMMAND = 612`,
   `OPCODE_ABANDON_QUEST_RESPONSE = 613`. Chỉ THÊM — `protocolVersion` không tăng (NETWORK.md §6).
   ```proto
   message AbandonQuestCommand { string quest_code = 1; }
   enum AbandonQuestOutcome {
     ABANDON_QUEST_OUTCOME_UNSPECIFIED = 0;
     ABANDON_QUEST_OUTCOME_OK = 1;
     ABANDON_QUEST_OUTCOME_QUEST_NOT_FOUND = 2;   // mã không tồn tại
     ABANDON_QUEST_OUTCOME_NOT_IN_PROGRESS = 3;   // chưa nhận, đã hoàn thành, hoặc đã lĩnh
   }
   message AbandonQuestResponse { AbandonQuestOutcome outcome = 1; string quest_code = 2; }
   ```
2. **Chỉ bỏ được quest `IN_PROGRESS`.** Quest đã `COMPLETED` chỉ còn việc lĩnh thưởng — cho bỏ là cho
   người chơi tự xoá phần thưởng đã kiếm; `CLAIMED` là trạng thái cuối không bao giờ lùi (ADR-044).
3. **Bỏ = xoá dòng tiến độ** (`DELETE ... WHERE status = 'IN_PROGRESS'` — compare-and-delete, một lệnh
   SQL là trọng tài), nên **nhận lại được ngay** từ đầu, tiến độ 0, qua đúng đường `on_accept` cũ.
   Không thêm trạng thái `ABANDONED`: không có dòng = `NOT_STARTED` là quy ước V11 đã có.
4. **Đồ đã giao cho quest (`ctx.take_item`) KHÔNG được trả lại.** Script không khai "đã lấy gì";
   trả lại phải đoán, và đoán sai là in đồ. Chữ xác nhận ở client nói rõ điều này. Ghi `audit_log`
   `QUEST_ABANDON` kèm `progress_json` lúc bỏ — đủ để hoàn tay nếu có khiếu nại.
5. **Xếp hàng sau sự kiện quest** của cùng nhân vật (`QuestFacade` khoá theo nhân vật đã có): bỏ không
   chen giữa một lần `on_event` đang chạy. Lần ghi compare-and-set của sự kiện tới sau sẽ thua (không
   còn dòng) — đúng ý.
6. **Client**: nhật ký nhiệm vụ (J) thêm nút **Bỏ** ở dòng đang làm; bấm lần một đổi nhãn thành
   **Bấm lần nữa để bỏ** (viewer chặn `confirm()`, xác nhận dựng trong UI); OK ⇒ hỏi lại danh sách.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Trạng thái `ABANDONED` lưu lại | Thêm một nhánh cho mọi chỗ đọc `status`; không ai cần lịch sử bỏ ngoài audit |
| Bỏ = khoá vĩnh viễn (không nhận lại) | Chính là kẹt vĩnh viễn ở dạng khác — trái mục đích dòng 69(a) |
| Trả lại đồ đã giao | Script không khai đồ đã lấy; phải thêm sổ riêng ⇒ đổi schema, rủi ro in đồ |
| Cho bỏ cả quest `COMPLETED` | Mất thưởng do bấm nhầm; không gỡ kẹt gì vì `COMPLETED` không kẹt (lĩnh được) |

## Consequences

- Người chơi tự gỡ được mọi quest kẹt do script lỗi, không cần GM/SQL.
- Nhận-bỏ-nhận lặp lại không khai thác được gì: phần thưởng chỉ trao ở `on_claim`, và `on_accept` của
  mọi script hiện có chỉ ghi tiến độ 0 (kiểm 2026-10-18: không script nào trao thưởng ở `on_accept`;
  `test_fixture_accept_gives_reward.js` chỉ là fixture kiểm rằng việc đó bị bỏ qua).
- Client cũ không có nút Bỏ — không vỡ gì.

## Revisit Trigger

- Có quest trao vật phẩm/vàng ở `on_accept` ⇒ nhận-bỏ-nhận thành máy in; phải chặn trước khi thêm.
- Có quest dài nhiều chặng mà người chơi hay bỏ nhầm ⇒ cân nhắc khôi phục tiến độ.

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | Opcode 612/613, chỉ thêm, `protocolVersion` không tăng |
| 2 | Chỉ `IN_PROGRESS`; xoá dòng bằng compare-and-delete |
| 3 | Không hoàn đồ; audit `QUEST_ABANDON` kèm `progress_json` |

## ADR cần tạo

Không.

## Tài liệu liên quan

[feature-quest/CLAUDE.md](../../../server/server-features/feature-quest/CLAUDE.md) · [NETWORK.md](../../05-server/NETWORK.md) · [GAMEPLAY.md §9](../../05-server/GAMEPLAY.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18) — xem `AbandonQuestUseCase`, `AbandonQuestIntegrationTest`.
2. ⏳ Kiểm sống bằng client thật: nhận một quest, bỏ, nhận lại.
