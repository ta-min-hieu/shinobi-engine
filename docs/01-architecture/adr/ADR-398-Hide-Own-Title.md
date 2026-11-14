# ADR-398: Ẩn nhãn của chính mình

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Ẩn nhãn của chính mình" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /annhanminh: ẩn tên/danh hiệu trên đầu nhân vật của mình). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-140](ADR-140-Overhead-Realm-Title.md) · [ADR-309](ADR-309-Hide-Other-Titles.md) |

---

## Decision

**D1:** công tắc hiển thị MỚI `HIDE_OWN_TITLE` (mặc định tắt) trong bảng Cài đặt hiển thị; bật thì không vẽ danh hiệu cảnh giới trên đầu chính mình (người khác vẫn thấy như cũ — chỉ là hiển thị phía mình). Bổ sung cho ẩn nhãn người ngoài nhóm (ADR-309).

**D2:** `/annhanminh` (`/hidemytitle`) bật/tắt cùng công tắc đó. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `DisplaySettings.HIDE_OWN_TITLE` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `RealmTitles.drawSelf/useSelfFilter`, `DisplaySettingsCoordinator`, `ChatViewCoordinator`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
