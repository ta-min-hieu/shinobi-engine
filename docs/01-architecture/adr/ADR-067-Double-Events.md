# ADR-067: Sự kiện nhân đôi EXP / rơi đồ — ADMIN bật bằng `/gm event`, sống qua khởi động lại

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Sự kiện x2 EXP/rơi đồ" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: cờ sự kiện bật/tắt không cần deploy, GM bật bằng `/gm event`, client hiện thông báo). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §8](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Sự kiện mùa — Feature Flag bật/tắt không deploy lại") · [ADR-052](ADR-052-In-Game-GM-Commands.md) (lệnh GM + audit) |

---

## Context

GDD hứa sự kiện bật/tắt không cần deploy. Đã có lệnh GM có audit (ADR-052); chưa có cách nào đổi nhịp
EXP/rơi đồ mà không sửa SQL rồi khởi động lại.

## Decision

**D1 — Hai loại**: `EXP` (nhân EXP đánh quái, cả phần chia cho đồng đội; thưởng nhiệm vụ KHÔNG nhân) và
`DROP` (nhân số LƯỢT roll bảng rơi của mỗi con quái — mỗi lượt độc lập, không nhân số lượng một món). Hệ số
2–5, thời lượng 1–10.080 phút.

**D2 — Lệnh**: `/gm event <exp|drop> <hệ số> <phút>` và `/gm event <exp|drop> off`, cấp **ADMIN** (đổi nhịp
kinh tế cả server, cùng nhóm với `give`/`gold` — Q1 theo khuyến nghị). Có dòng `gm_audit_log` như mọi lệnh GM.

**D3 — Trạng thái** trong bộ nhớ (`GameEventFacade`, đọc ở mỗi lần thưởng — không chạm DB) + bảng
`game_event` (V69, một dòng mỗi loại, gắn dòng audit đã bật) để sống qua khởi động lại. Hết hạn theo đồng hồ,
không cần job dọn.

**D4 — Giao thức, chỉ thêm**: opcode **309** `GameEventsChangedEvent` (toàn bộ sự kiện còn hiệu lực; rỗng =
vừa tắt hết). Gửi lúc vào world nếu có, và cho MỌI người online mỗi lần bật/tắt. Hết hạn tự nhiên không gửi gì.

**D5 — Client**: một dòng hệ thống trong nhật ký chat mỗi sự kiện ("x2 kinh nghiệm đánh quái, tới 21:00" —
giờ máy người chơi); rỗng ⇒ "đã kết thúc".

**D6 — Nơi nhân**: EXP ở `ExperienceGrantPort` của gateway (bọc `CharacterFacade.grantExperience`);
rơi đồ ở `GrantLootUseCase.withRollCount(IntSupplier)` — feature-inventory không biết sự kiện là gì.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lịch sự kiện tự động (cron) | Chưa có nhu cầu; lệnh tay đủ cho beta |
| Nhân số lượng món rơi | Món không xếp chồng không nhân được; nhân lượt roll đúng nghĩa "rơi nhiều hơn" |
| Banner thường trực trên HUD | `WorldScreen` đang ở trần PMD; dòng chat đủ cho bản đầu |
| Cờ trong file cấu hình | Phải khởi động lại — trái mục tiêu "không deploy" |

## Consequences

- Một sink lạm phát có thể bị mở rộng bằng tay — mọi lần bật nằm trong `gm_audit_log`.

## Revisit Trigger

- Muốn lịch sự kiện tự động, sự kiện theo vùng/quái, hoặc banner HUD thường trực.

## Technical Decisions

- `GameEventKind`/`GameEvent`/`GameEventPort` (feature-gm domain), `GameEventFacade` (api), `PostgresGameEventAdapter`.
- Một `GameEventFacade` cho cả tiến trình (`ServerDatabaseBootstrap.wireGameEventFacade`, memo).

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V69__game_event.sql` · `GmCommandRouter#event` · `GameEventNoticeCoordinator`
- Test: `GameEventFacadeTest`, `GmCommandParserTest`, `GrantLootUseCaseTest#shouldRollTheTableTwice_*`, `GmCommandRouterDatabaseTest#eventShould*`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ✅ Kiểm sống 2026-10-21: `/gm event exp 2 5` ⇒ opcode 309 + dòng hệ thống; cùng loài `wandering_bandit` 35 → 70 EXP. Sự kiện rơi đồ x2 chưa đo (xác suất, cần nhiều lượt).
