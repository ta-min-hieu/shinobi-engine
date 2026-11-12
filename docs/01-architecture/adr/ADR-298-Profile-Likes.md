# ADR-298: Thích hồ sơ người chơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Thích hồ sơ người chơi" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: /thich <tên>: mỗi ngày thích hồ sơ một người một lần; hồ sơ hiện tổng lượt thích). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-218](ADR-218-Character-Bio.md) · [ADR-171](ADR-171-Inspect-Player.md) |

---

## Decision

**D1 — Bảng `profile_like`** (V173): khoá chính (người thích, người được thích, ngày UTC) ⇒ mỗi ngày một lượt; không tự
thích mình (CHECK + kiểm ở facade).

**D2 — Dây:** `LikeProfileCommand{target_name}` / `LikeProfileResponse{outcome, target_name, likes}` **271/272**. Thêm
trường **`InspectCharacterResponse.likes = 8`** (chỉ THÊM, client cũ bỏ qua) — soi trang bị thấy "Lượt thích hồ sơ: N".
Client `/thich <tên>`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `profile_like`; 271/272; `InspectCharacterResponse.likes` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `ProfileLikePort`/`Facade`, `PostgresProfileLikeAdapter`, `Lot46Commands.likeProfile`/`withLikes`, `ProfileLikeCoordinator`, `InspectCoordinator`.
2. ✅ Test: `Lot46IntegrationTest.profileLikesOncePerDay`.
3. ✅ Kiểm sống gameProbe 2026-11-12: `likeProfile:BaoDo01` ⇒ OK thích=1; lần hai ⇒ ALREADY_TODAY thích=1; tự thích ⇒ SELF. Đã xoá lượt thích thử.
