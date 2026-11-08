# ADR-220: Ký hiệu mục tiêu nhóm trên đầu quái

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Ký hiệu mục tiêu nhóm" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: vẽ ký hiệu trên đầu con quái được trưởng nhóm đánh dấu, tiếp ADR-213). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-213](ADR-213-Party-Target-Marker.md) (đánh dấu mục tiêu nhóm) · [ADR-140](ADR-140-Overhead-Realm-Title.md) (chữ trên đầu) |

---

## Decision

**D1 — Client thuần, không đổi protocol.** Khi nhận `PartyTargetMarkedEvent` (ADR-213), client giữ id con quái được
đánh dấu; mỗi khung, nếu con quái đó đang hiện trong tầm nhìn, vẽ một dòng chữ cam **"[ MỤC TIÊU NHÓM ]"** phía trên đầu
nó, cùng bộ vẽ chữ trên đầu của danh hiệu cảnh giới (`RealmTitles`, không gian màn hình, có bóng chữ). Bỏ dấu (id 0) thì
hết vẽ.

**D2 — Vẽ cả khi tắt danh hiệu trên đầu** (ADR-143): đây là thông tin chiến đấu, không phải trang trí.

**D3 — Chữ, không phải hình**: font tiếng Việt không có ký hiệu hình (◎, ☠…) — dùng chữ trong ngoặc vuông; đổi sang
icon sprite là việc art sau, không đổi gì khác.

**D4 — Không cấp phát mỗi khung**: id đã đóng hộp một lần lúc nhận event.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chữ cam trên đầu quái được đánh dấu, qua `RealmTitles.drawMarker`; vẽ cả khi tắt danh hiệu | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `RealmTitles.drawMarker`, `PartyMarkCoordinator.markedKey`,
   `ExpansionWindows.drawPartyMark`.
2. Kiểm sống: phần vẽ là client đồ hoạ thuần — môi trường kiểm sống không chạy client đồ hoạ, nên chỉ kiểm bằng biên
   dịch + bộ test client; dữ liệu đầu vào (event 1336) đã kiểm sống ở ADR-213.
3. Icon sprite thay chữ (art).
