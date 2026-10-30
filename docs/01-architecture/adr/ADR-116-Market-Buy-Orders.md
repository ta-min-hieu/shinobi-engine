# ADR-116: Cầu mua ở phường thị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Cầu mua ở phường thị" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: mở rộng phường thị ADR-063, đặt lệnh mua (món, số lượng, giá, ký quỹ linh thạch); người bán bán thẳng vào lệnh; hết hạn hoàn ký quỹ qua thư). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-30 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-063 (phường thị) · ADR-115 (khoá an toàn) · DATABASE.md §5.1 (thứ tự khoá) |

---

## Decision

**D1 — Ký quỹ khi đặt**: lệnh = (loại vật phẩm, số lượng 1–999, đơn giá theo biên giá phường thị). Đặt lệnh trừ ngay
`số lượng × đơn giá` linh thạch. Tối đa **5** lệnh mở mỗi nhân vật. Bảng `market_buy_order` (V96).

**D2 — Bán vào lệnh, khớp từng phần**: người bán chọn một dòng túi đúng loại (chưa mặc, chưa cường hoá, không đang chào
giao dịch — cùng luật treo bán), bán tối đa số lệnh còn cần. Món vào **túi người đặt** (dù họ offline); người bán nhận
`số lượng × đơn giá` trừ **phí 5%** (cùng `MarketPolicy.feeOf`) từ ký quỹ. Túi người đặt đầy ⇒ từ chối
`BUYER_BAG_FULL`, không gì đổi. Một transaction: khoá túi HAI người theo `character_id` tăng dần → khoá dòng lệnh → vàng.
Không tự bán vào lệnh của mình.

**D3 — Huỷ / hết hạn**: người đặt huỷ ⇒ hoàn phần ký quỹ còn lại ngay. Lệnh mở **48 giờ**; quá hạn ⇒ `EXPIRED`, tác vụ
nền gửi thư hệ thống hoàn ký quỹ (khoá chống trùng tất định theo id lệnh — chết giữa chừng không gửi hai thư, không mất
tiền), rồi đánh dấu `refunded`.

**D4 — Giao thức, chỉ thêm**: 1015/1016 liệt kê (50 lệnh giá cao nhất của người khác + lệnh của mình), 1017/1018 đặt,
1019/1020 bán vào lệnh, 1021/1022 huỷ, 1023 `BuyOrderFilledEvent` báo người đặt nếu online. Đặt lệnh và bán vào lệnh đi
qua khoá an toàn (ADR-115). Client: cửa sổ "Cầu mua" mở bằng `/caumua` (3 tab: lệnh của người khác — nút Bán; của tôi —
Huỷ; đặt lệnh — mọi món trong catalog, ô nhập "số lượng x đơn giá").

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ký quỹ lúc đặt, khớp từng phần vào túi người đặt, phí 5% bên bán, hoàn khi huỷ/hết hạn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-30 (lô 23 phần 1): `feature-market` `BuyOrder`/`BuyOrderPort`/`BuyOrderPolicy`/`BuyOrderUseCase`/
   `BuyOrderFacade`; `PostgresBuyOrderAdapter` (V96, dùng lại adapter túi/vàng/audit của phường thị); gateway
   `BuyOrderCommands` + `ExpansionJobs.buyOrderRefunds`; client `BuyOrderCoordinator`. Test Postgres
   `Lot23IntegrationTest` (ký quỹ, khớp một phần, phí, huỷ hoàn tiền, hết hạn).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-30**: Kagemaru đặt lệnh 3 Tiểu Hồi Nguyên Đan × 40 ⇒ linh thạch 77154 → 77034
   (ký quỹ 120). tanthua44 bán 3 từ dòng chỉ có 2 ⇒ INVALID_QUANTITY; bán 2 ⇒ OK, nhận 76 (80 − phí 4, 390 → 466), túi
   Kagemaru 10 → 12 viên, lệnh 2/3 ACTIVE. Huỷ ⇒ hoàn 40 (77034 → 77074), lệnh CANCELLED.
