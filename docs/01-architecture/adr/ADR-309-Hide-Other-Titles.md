# ADR-309: Ẩn tên người chơi khác

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Ẩn tên người chơi khác" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: công tắc ẩn bảng tên trên đầu người chơi khác (giữ tên đồng đội nhóm)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-140](ADR-140-Overhead-Realm-Title.md) · [ADR-285](ADR-285-Hide-Others-Damage.md) |

---

## Decision

**D1 — Thuần client, một dòng cài đặt hiển thị** (`HIDE_OTHER_TITLES`, mặc định TẮT). Bật ⇒ nhãn trên đầu người ngoài
nhóm không vẽ; đồng đội nhóm (cùng nguồn với ADR-285) vẫn hiện.

**D2 — Lệch mô tả lúc hỏi:** game KHÔNG vẽ tên người chơi trên đầu — nhãn trên đầu là **danh hiệu cảnh giới** (ADR-140,
kèm nhãn Ma Đầu). Công tắc này ẩn đúng nhãn đó.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng cài đặt `HIDE_OTHER_TITLES`, giữ đồng đội | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `DisplaySettings.Option.HIDE_OTHER_TITLES`, `RealmTitles.useOthersFilter`/`hidesOther`, `RemoteCharacterPresenter`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Kiểm sống gameProbe.
