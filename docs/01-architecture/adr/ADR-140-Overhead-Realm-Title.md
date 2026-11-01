# ADR-140: Danh hiệu cảnh giới trên đầu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — phần "danh hiệu cảnh giới hiện trên đầu" của mục "Nghi lễ đột phá cảnh giới" chủ dự án chọn ở lô 25 (ADR-136 D3 đã tách ra treo vì cần đổi protocol). Cách làm do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) D3 · [ADR-132](ADR-132-Costume-Tier-By-Realm.md) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (gói 307) |

---

## Decision

**D1 — Protocol, chỉ THÊM**: `CharacterSpawnedEvent.level = 6` và `CharacterAppearanceChangedEvent.level = 4`.
`protocolVersion` KHÔNG tăng (client cũ bỏ qua field lạ, client mới gặp server cũ đọc 0 = chưa biết ⇒ không vẽ).

**D2 — Gateway**: `CharacterAppearance` mang thêm cấp; gói 307 nay phát khi bậc trang phục HOẶC cảnh giới đổi
(`differsVisiblyFrom` — Cấp 55/73/91 mở cảnh giới mà không đổi bậc trang phục). Cấp giữa các mốc không phát gì: danh
hiệu chỉ là TÊN CẢNH GIỚI, không phải số cấp, nên người khác không cần biết từng lần lên cấp.

**D3 — Client**: `RealmTitles` vẽ tên cảnh giới (`CultivationRealm`, i18n) phía trên đầu chính mình và mọi người đang
thấy, ở không gian màn hình như chữ damage, vàng nhạt có bóng, cỡ 75% chữ HUD; chỉ nhánh 3D. Không cấp phát mỗi khung
(`RemoteCharacterPresenter` lặp `LongMap`). Quái/NPC không có danh hiệu.

## Kiểm chứng

- `CharacterAppearanceTest` (chỉ số cảnh giới, đổi cảnh giới không đổi bậc vẫn báo); `FrameAllocationTest` client xanh.
- `runWorldPreview` khung `10-arc4-quai-hoang-vuc` vẽ sáu danh hiệu trên dải quái: chữ đọc rõ trên hậu cảnh, nằm trên
  đầu, dưới mốc chữ damage.
- **Kiểm sống 2026-11-01** (gameProbe, probe in thêm `level` của gói 303): Benkei thấy Kagemaru `costume=B3 level=60`,
  Kagemaru thấy Benkei `costume=B2 level=19`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thêm field `level` vào 2 gói hiện có, không opcode mới | Accepted |
| 2 | Danh hiệu = tên cảnh giới, phát lại chỉ khi cảnh giới đổi | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Tuỳ chọn ẩn/hiện danh hiệu trong cài đặt (chưa có màn cài đặt đồ hoạ).
