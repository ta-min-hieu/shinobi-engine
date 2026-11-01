# ADR-143: Cài đặt hiển thị (ẩn danh hiệu, chữ damage, hậu cảnh)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn mục lô 26 "Cài đặt ẩn danh hiệu: bật/tắt danh hiệu cảnh giới trên đầu, chữ damage, hậu cảnh parallax" (việc treo của ADR-140). Cách làm do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-140](ADR-140-Overhead-Realm-Title.md) · [ADR-142](ADR-142-Killing-Intent-And-Outlaws.md) · ART-PIPELINE §2.8 (hậu cảnh) |

---

## Decision

**D1 — Thuần client, không protocol, không DB**: ba công tắc hình lưu trên MÁY người chơi qua libGDX `Preferences`,
dùng chung bộ `kage-client` với cài đặt ngôn ngữ (khoá `display.realm_titles`, `display.damage_numbers`,
`display.parallax`). Mặc định đều BẬT — người chưa đụng tới cài đặt thấy y như trước.

**D2 — Mở bằng lệnh chat `/caidat` (hoặc `/settings`)**: một `ReportPanel` "Cài đặt hiển thị", mỗi công tắc một dòng
"<tên>: Bật/Tắt" kèm nút đảo. Cùng khuôn các cửa sổ không-phím khác (`/luyenkhi`, `/linhdien`). Chưa có màn cài đặt
riêng ở màn đăng nhập.

**D3 — Mỗi lớp vẽ tự hỏi công tắc của mình** (`DisplaySettings.switchFor` trả một `BooleanSupplier` cố định, đọc một ô
cờ — không cấp phát mỗi khung): `RealmTitles` (danh hiệu + nhãn Ma Đầu), `CombatFeedbackPresenter` (số damage; hiệu ứng
skill vẫn vẽ), `HorizonBackdrop` (tắt ⇒ không núi xa/gần, không mây trôi; còn trời và biển mây phẳng). `WorldScreen`
không thêm nhánh nào (đang ở trần độ phức tạp PMD của lớp) — nối bằng `DisplaySettingsCoordinator.applyTo`.

## Kiểm chứng

- `DisplaySettingsTest` (mặc định bật, đảo được và sống qua lần mở game sau, công tắc đã phát cho lớp vẽ thấy ngay
  lần đảo). `check` của `client-core` xanh, kể cả `InboundPacketFuzzTest`/`InboundPacketScenarioTest` (bộ giả libGDX
  trong test nay trả một `Preferences` rỗng thay vì `null`).
- Chưa kiểm bằng mắt trên client thật — chỉ unit test.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lưu cục bộ bằng `Preferences`, không qua server | Accepted |
| 2 | Lệnh chat `/caidat` thay vì phím tắt mới | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-140](ADR-140-Overhead-Realm-Title.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Màn cài đặt đầy đủ (âm lượng, độ phân giải, ngôn ngữ trong game) nếu chủ dự án muốn.
