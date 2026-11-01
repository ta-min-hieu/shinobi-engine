# ADR-129: Động phủ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-31) — chủ dự án chọn "Động phủ" trong lượt hỏi lô 24 (AskUserQuestion; mô tả kèm: World cá nhân, tiểu linh mạch sinh linh thạch, bày trí, mời bạn bè). Chi tiết (con số, món bày trí, luật mời) do AI chọn — mọi con số là ĐỀ XUẤT CÂN BẰNG. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-126](ADR-126-Heavenly-Tribulation.md) (World riêng một người) · [ADR-119](ADR-119-Spirit-Field.md) (linh điền — sản lượng theo giờ thật) · [ADR-090](ADR-090-Spirit-Vein-Contest.md) (Linh Mạch của bang — KHÁC cái này) |

---

## Decision

**D1 — Vào động phủ**: `/dongphu` đưa người chơi vào một **World riêng không quái** của chính họ (đường nhận/dời của
hầm ngục, khoá instance riêng theo chủ động phủ — không trùng khoá với đài Thiên Kiếp hay Trấn Yêu Tháp). Rời bằng lệnh
rời hầm ngục có sẵn; gục hay rớt mạng thì như mọi hầm ngục.

**D2 — Tiểu linh mạch**: sinh **linh thạch** theo giờ thật, kể cả khi offline. Cấp 1 = **30 linh thạch/giờ**, mỗi cấp
thêm 30 (cấp 5 = 150/giờ). Kho chứa tối đa **24 giờ** sản lượng; đầy thì ngừng tích. **Thu chỉ được khi đang đứng
trong động phủ của mình** (`/dongphu thu`) — lý do để ghé. Nâng cấp `/dongphu nang`: **2.000 × cấp hiện tại** linh
thạch (2.000 → 8.000), thu hết kho trước khi nâng để không ai mất phần đã tích. Mọi lượt cộng/trừ linh thạch đi qua
transaction + audit.

**D3 — Bày trí**: bốn món bày trí mua bằng linh thạch, mỗi món một lần, **mỗi món +10% sản lượng linh mạch** (đủ bốn
= +40%): Linh Tùng 1.500 · Ngọc Đăng 2.000 · Vân Bình Phong 2.500 · Liên Trì 3.000. Danh mục là bảng DB (Điều 27).
Lần đầu chưa có hình bày trí trong World — client hiện danh sách trong bảng động phủ.

**D4 — Mời bạn**: `/dongphu moi <tên>` gửi lời mời tới một **bạn bè đang online**; người được mời có **10 phút**
`/dongphu tham <tên>` để vào CÙNG World động phủ của chủ (chủ có ở trong hay không cũng được). Khách không thu được
linh thạch. Lời mời chỉ ở RAM.

**D5 — Giao thức, chỉ thêm**: 335/336 `CaveCommand/CaveResponse` (một lệnh, trường `action`), 337 `CaveInviteEvent`.
Schema mới V108: `cave_dwelling`, `cave_decoration_template`, `cave_decoration`. `protocolVersion` KHÔNG tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | World riêng theo chủ; linh mạch 30/giờ/cấp, kho 24 giờ, thu trong động phủ; nâng 2.000 × cấp; 4 món bày trí +10% mỗi món; mời bạn online, 10 phút | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 24 phần 3): `CaveRules` / `CavePort` / `CaveFacade` (feature-world, có test),
   `PostgresCaveAdapter` (V108, có test Postgres trong `Lot23IntegrationTest`), gateway `CaveCommands` (khoá instance
   `-(2^40) - chủ`), `DungeonInstanceRegistry.enterShared` + `instanceKeyOf` (thay `enterSolo`; đài Thiên Kiếp gọi
   `enterShared(id, -id, …)`), client `CaveCoordinator` (`/dongphu`), bước probe `cave:<lệnh>[/<tham số>]` (`Lot24Steps`).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V108): Kagemaru xem (cấp 1, 30/giờ, kho 0/720) ⇒ thu ở ngoài
   `NOT_IN_OWN_CAVE` ⇒ `/dongphu` vào World riêng (308 DUNGEON) ⇒ thu `NOTHING_TO_COLLECT`. Lùi mốc tích 30 giờ bằng SQL ⇒
   kho 720/720 (trần 24 giờ) ⇒ thu +720 (gói 209 `gold.reason.cave_vein`) ⇒ nâng −2.000 lên cấp 2 (60/giờ) ⇒ bày Linh Tùng
   −1.500 ⇒ 66/giờ, kho 1.584 ⇒ bày lại `ALREADY_PLACED`, mã lạ `UNKNOWN_DECORATION`; audit đủ 3 dòng. Mời Benkei (bạn) ⇒
   OK + Benkei nhận 337; mời hieutm (không phải bạn) ⇒ `NOT_FRIEND`. Benkei `/dongphu tham Kagemaru` ⇒ vào World động phủ,
   thu ⇒ `NOT_IN_OWN_CAVE`; Kagemaru vào sau ⇒ hai người thấy nhau (303) — CHUNG một World. hieutm thăm ⇒ `NOT_INVITED`.
3. ⚠️ Chủ dự án xem: toàn bộ con số ở D2–D4 do AI chọn. Món bày trí đã hiện hình 3D từ lô 25 —
   [ADR-138](ADR-138-Cave-Decorations-In-3D.md); động phủ có hậu cảnh parallax chung.
