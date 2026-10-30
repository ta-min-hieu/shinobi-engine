# ADR-118: Thương nhân du hành

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Thương nhân du hành" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: NPC thương nhân xuất hiện ngẫu nhiên 2–3 lần/ngày ở một trạm trên trục trong 30 phút, bán hàng hiếm giới hạn số lượng toàn server; dòng hệ thống báo vị trí). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-30 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-100 (điểm truyền tống — dùng làm "trạm") · ADR-106 (lịch hoạt động) |

---

## Decision

**D1 — Lịch do server lên**: mỗi ngày (giờ Việt Nam) server tự lên lịch **2–3 lần ghé**, mỗi lần **30 phút**, bắt đầu ở
các giờ khác nhau trong khung 8h–22h30 (không chồng nhau), mỗi lần ở **một điểm truyền tống ngẫu nhiên** (ADR-100). Ngẫu
nhiên chỉ ở server. Bảng `traveling_merchant_visit` (V98); lên lịch là việc của tác vụ nền mỗi phút, chạy lại vô hại.

**D2 — Hàng hiếm, số lượng chung cả server**: mỗi lần ghé rút **3 món** theo trọng số từ `traveling_merchant_offer`
(10 món: Tẩy Luyện Thạch, bốn Linh Ngọc, Linh Thú Đan, Hồi Hồn Đan, Truyền Tống Phù, Túi Càn Khôn, Tẩy Căn Đan), mỗi món
một số lượng **dùng chung mọi người** (`traveling_merchant_stock.remaining`). Giá/số lượng/trọng số là đề xuất cân bằng.

**D3 — Mua phải đứng gần trạm** (≤ 8 m, kiểm bằng vị trí server ở world chính), một cái mỗi lần, MỘT giao dịch: khoá túi →
khoá dòng kho (còn hàng, lần ghé còn diễn ra) → thêm vật phẩm → trừ linh thạch → trừ kho → audit `TRAVELING_MERCHANT_BUY`.

**D4 — Báo cả server đúng một lần** khi một lần ghé bắt đầu (compare-and-set cờ `announced`): event 1028 tới mọi người
online, client hiện một dòng hệ thống "Thương nhân du hành đã tới {trạm}, ở lại tới {giờ}".

**D5 — Giao thức, chỉ thêm**: 1024/1025 xem (ở đâu cũng xem được), 1026/1027 mua, 1028 thông báo tới. Client: cửa sổ
"Thương nhân du hành" mở bằng `/thuongnhan`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Server tự lên lịch 2–3 lần/ngày ở điểm truyền tống, kho chung cả server, mua trong 8 m, báo một lần | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-30 (lô 23 phần 1): feature-shop `MerchantPlanner` (hàm thuần, có test) + `MerchantPort` +
   `TravelingMerchantFacade`; `PostgresMerchantAdapter` (V98); gateway `MerchantCommands` (+ tác vụ nền trong
   `ExpansionJobs`); client `MerchantCoordinator`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-30**: lúc khởi động server tự lên 2 lần ghé hôm nay (east_ridge 09:26 và
   12:17 giờ VN). Lần ghé chèn tay ở sect_gate (kho Hồi Hồn Đan còn 1): mua từ x=83 ⇒ NOT_PRESENT; túi Kagemaru đầy ⇒
   BAG_FULL, linh thạch không đổi (hoàn tác). tanthua44 đứng ở x=−4 nhận gói 1028 ĐÚNG MỘT LẦN (cờ `announced` lật),
   mua ⇒ OK (466 → 246), mua lần 2 ⇒ SOLD_OUT (kho chung còn 0), có audit `TRAVELING_MERCHANT_BUY`.
3. ⚠️ Lệch cần chủ dự án xem: thương nhân CHƯA có hình trong thế giới (không có model/NPC sinh lúc chạy) — chỉ có dòng
   thông báo + cửa sổ; vẽ NPC tạm cần cơ chế sinh NPC lúc chạy (hiện NPC chỉ nạp lúc khởi động).
