# ADR-119: Linh điền

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Linh điền" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: 4 ô ruộng, mở thêm bằng linh thạch, gieo hạt, chín sau 1–4 giờ THẬT, ra linh thảo; bạn bè tưới +10%). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-30 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-098 (hái thảo — nguồn linh thảo kia) · ADR-079 (luyện đan — nơi tiêu linh thảo) · bạn bè (`feature-friend`) |

---

## Decision

**D1 — Ruộng theo nhân vật, thời gian THẬT**: mỗi nhân vật có **4 ô** (bảng `spirit_field`, V99), mở thêm tới **8 ô**
bằng linh thạch (giá ô thứ 5/6/7/8 = 1000/1500/2000/2500 — `SpiritFieldRules.unlockCost`). Gieo một hạt giống từ túi vào
ô trống; ô chín ở `ripe_at = planted_at + grow_minutes` (đồng hồ server, không phải tick). Không cần online — chín cả khi
offline. Không có trạng thái héo/sâu bệnh (giữ đơn giản cho v1).

**D2 — Hạt giống là dữ liệu**: bảng `field_seed` (hạt → số phút, linh thảo ra, số lượng). Hai hạt ban đầu, bán ở cửa hàng
tông môn: **Hạt Linh Thảo** 60 phút ⇒ 3 Linh Thảo (giá 120) và **Hạt Linh Thảo Thượng Phẩm** 240 phút ⇒ 14 Linh Thảo
(giá 400). Số phút/sản lượng/giá là **đề xuất cân bằng**.

**D3 — Bạn bè tưới**: chỉ người đang là bạn bè mới xem/tưới được ruộng của nhau (kiểm ở gateway qua `FriendFacade`); tự
tưới ruộng mình không được. Mỗi ô tưới **một lần mỗi vụ** (`watered_by`, compare-and-set), ô phải đang lớn. Ô được tưới
thì sản lượng kỳ vọng **+10%**: `base × 1,1`, phần lẻ thành +1 theo xác suất ngẫu nhiên của server — vụ nhỏ (3 cây) vẫn
được đúng +10% về trung bình thay vì bị làm tròn mất. Người tưới không nhận gì (v1).

**D4 — Mỗi lệnh ghi là MỘT giao dịch** dưới khoá túi chung của người thao tác: gieo = khoá túi → trừ 1 hạt → chèn ô; thu
hoạch = khoá ô (chín) → thêm linh thảo (xếp chồng, túi đầy ⇒ `BAG_FULL`, ô giữ nguyên) → xoá ô; mở ô = trừ linh thạch →
tăng `plots`. Tưới chỉ ghi dòng ô của người kia, không chạm túi ai.

**D5 — Giao thức, chỉ thêm** (`crafting.proto`): 1804/1805 xem ruộng (`owner_name` rỗng ⇒ của mình; kèm
`server_time_ms` để client đếm ngược không tin đồng hồ máy), 1806/1807 gieo, 1808/1809 thu hoạch, 1810/1811 tưới,
1812/1813 mở ô. Client: cửa sổ "Linh điền" mở bằng `/linhdien`, hai tab (ruộng của tôi / ruộng bạn bè — gõ tên bạn rồi
Làm mới).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 4→8 ô theo nhân vật, chín theo đồng hồ server, hạt là dữ liệu `field_seed`, bạn bè tưới một lần/vụ ⇒ +10% kỳ vọng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-30/31 (lô 23 phần 2a): feature-world `SpiritFieldRules` (hàm thuần, có test) + `SpiritFieldPort`
   + `SpiritFieldFacade`; `PostgresSpiritFieldAdapter` (V99); gateway `SpiritFieldCommands`; client
   `SpiritFieldCoordinator`; bước probe `field`/`plant`/`harvest`/`water`/`unlockPlot`; test Postgres trong
   `Lot23IntegrationTest`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (server mới, DB dev lên V99): Benkei gieo ô 0/1 ⇒ OK; gieo lại ô 0 ⇒
   PLOT_OCCUPIED; hết hạt ⇒ NO_SEED; gieo Linh Thảo (không phải hạt) ⇒ UNKNOWN_SEED; thu ô chưa chín ⇒ NOT_RIPE; xem
   ruộng Kagemaru (bạn) ⇒ OK; mở ô 5 ⇒ OK, gói 209 vàng −1000. Kagemaru tưới ô 0 của Benkei ⇒ OK, tưới lại ⇒
   ALREADY_WATERED, ô trống ⇒ PLOT_EMPTY, tự tưới ruộng mình ⇒ NOT_FRIEND, xem ruộng tanthua44 (không phải bạn) ⇒
   NOT_FRIEND. Ép chín bằng SQL ⇒ thu ô 1 + ô 0 (đã tưới) ⇒ OK, tổng 6 Linh Thảo (3 + 3; ô tưới ra 3 là nhánh 70%), thu
   lại ⇒ PLOT_EMPTY. Nhánh BAG_FULL thật CHƯA kiểm sống (linh thảo xếp chồng vào chồng sẵn có nên không chạm trần túi).
3. ⚠️ Lệch cần chủ dự án xem: ruộng chưa có hình trong thế giới (chỉ có cửa sổ); hai hạt giống chưa có icon (đã ghi nợ ở
   `ContentReferenceIntegrationTest`).
