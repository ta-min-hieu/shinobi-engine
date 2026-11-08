# ADR-226: Nhật ký chiến đấu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Nhật ký chiến đấu" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: xem 20 dòng gần nhất — ai đánh mình / mình đánh ai, bao nhiêu sát thương, chiêu gì). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-195](ADR-195-Death-Recap.md) (tóm tắt khi chết — phía server) |

---

## Decision

**D1 — Client thuần, không đổi protocol**: client vốn nhận mọi `AttackResultEvent` / `SkillResultEvent` trong tầm nhìn.
Một luồng chỉ NGHE (không "nuốt" gói — màn hình vẫn xử lý như cũ) giữ **20 dòng** gần nhất có dính tới chính mình: mình
đánh ai / ai đánh mình, sát thương hoặc trượt, đánh thường hay tên chiêu. Tên quái lấy từ gói "quái vào tầm nhìn";
người chơi khác hiện "Đối thủ #id". Đổi màn hình / vào lại world là xoá.

**D2 — Client**: `/nhatkychien` (hoặc `/combatlog`) in ra khung chat, cũ trước mới sau.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nghe gói kết quả đòn sẵn có, giữ 20 dòng của chính mình; `/nhatkychien` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): `CombatLogCoordinator`, `LocalIntentSender.localCharacterId`.
2. Kiểm sống: client thuần (chỉ nghe gói sẵn có) — môi trường kiểm sống không chạy client đồ hoạ; kiểm bằng biên dịch +
   bộ test client. Hai gói đầu vào (`AttackResultEvent`/`SkillResultEvent`) đã được kiểm sống từ các lô trước.
