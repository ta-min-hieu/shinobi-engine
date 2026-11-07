# ADR-190: Đổi phím tắt

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Đổi phím tắt" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: cửa sổ cài đặt phím: đổi phím mở túi, kỹ năng, bản đồ...; lưu trên máy người chơi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-143](ADR-143-Display-Settings.md) (cài đặt lưu trên máy — cùng tệp `Preferences`) · [ADR-023](ADR-023-Desktop-First-Input.md) (`InputMapper`) |

---

## Decision

**D1 — Thuần client, lưu trên máy**: không opcode, không bảng — phím là sở thích của người ngồi trước máy, không phải dữ
liệu gameplay. Lưu trong `Preferences` libGDX (cùng tệp với cài đặt hiển thị); giá trị hỏng bị bỏ, về mặc định.

**D2 — 27 phím đổi được** (`KeyBindings.Binding`): 21 phím mở cửa sổ (túi I, nhiệm vụ J, thư M, bạn bè F, bang G, nhóm P,
cửa hàng B, giao dịch N, luận võ K, linh căn H, chat T, quan hệ Q, hằng ngày E, đồ giám Y, tinh luyện X, nhân vật C, Bia
Anh Danh L, thành tựu U, phường thị O, Linh Ngọc V, công pháp R) + 6 phím hành động (trái A, phải D, nhảy W, kỹ năng
Enter, pháp bảo 1, đánh Z). Phím phụ (mũi tên, Space, Ctrl, NumPad) và Esc **dành riêng**, không gán được — chúng luôn giữ
nghĩa cũ. Không cho trùng: phím đang của việc khác ⇒ từ chối (đổi việc kia trước).

**D3 — Cách áp, không cấp phát**: mọi chỗ so phím hỏi `KeyBindings.shared().resolve(phím mặc định)` — một ô mảng 256
phần tử `remap[mặc định] = đang dùng`; gọi được mỗi khung (`DesktopInputMapper`). 14 cửa sổ (`ReportPanel` + 11 panel riêng)
và bộ map phím desktop đổi sang cách này; phím mặc định của từng chỗ không đổi trong code.

**D4 — Client**: lệnh chat `/phimtat` (hoặc `/keys`): in mọi phím; `/phimtat <tên> <phím>` đổi (vd `/phimtat tuido K`);
`/phimtat macdinh` trả hết về mặc định. ⚠️ Lệch mô tả lúc hỏi ("cửa sổ cài đặt phím"): bản này là lệnh chat; cửa sổ có
nút "bấm phím mới" là việc sau. Nhãn phím in sẵn trên vài nút HUD (nếu có) chưa đổi theo.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thuần client, 27 phím, phím phụ + Esc dành riêng, không trùng, `resolve` qua mảng 256; `/phimtat` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): `KeyBindings` + `GdxPreferencesKeyBindingsStore` (client-core `input`), 14 chỗ so phím
   + `DesktopInputMapper` dùng `resolve`, `KeyBindingsCoordinator` `/phimtat`.
2. ✅ Test: `KeyBindingsTest` (đổi + lưu + đọc lại, trùng / dành riêng / lạ, giải phóng rồi lấy, trả mặc định, phím mặc
   định không trùng nhau), `KeyBindingsCoordinatorTest` (cú pháp).
3. Không kiểm sống bằng gameProbe — probe không chạy client đồ hoạ; logic nằm trọn trong lớp đã test.
4. Sau này: cửa sổ đổi phím (bấm phím mới), nhãn phím trên HUD theo phím đã đổi.
