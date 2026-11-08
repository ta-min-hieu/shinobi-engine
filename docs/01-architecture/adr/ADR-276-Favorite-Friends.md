# ADR-276: Bạn bè yêu thích

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Bạn bè yêu thích" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Ghim bạn lên đầu danh sách (lưu cục bộ)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-173](ADR-173-Friend-Notes.md) (ghi chú bạn bè) |

---

## Decision

**D1 — Thuần client.** `/yeuthich <tên>` (hoặc `/favorite`) ghim / bỏ ghim; `/yeuthich` liệt kê. Tối đa 20, khoá theo tên
đã bỏ dấu, lưu Preferences.

**D2 — Khung bạn bè.** Người được ghim đứng đầu danh sách (giữ thứ tự server trong mỗi nhóm) với dấu `[*]` trước tên (font chữ của game không có ký tự ★ — test `MessageBundleIntegrityTest` bắt được).
`FriendCoordinator` dùng CÙNG một danh sách đã sắp cho cả dòng vẽ lẫn nút Xoá theo chỉ số dòng — nếu không, sắp lại thứ tự
sẽ làm nút Xoá xoá nhầm người.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Preferences; ghim lên đầu, `[*]`; cùng thứ tự cho vẽ và nút Xoá | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `FavoriteFriendsCoordinator`, `FriendCoordinator.useFavorites`.
2. ✅ Test: `Lot43ClientTest.favoritesOrderFirst`.
