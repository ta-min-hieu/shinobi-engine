# ADR-066: Đổi linh căn — lần đầu và một lần dưới Lv30 miễn phí, sau đó tốn Tẩy Căn Đan

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Đổi linh căn (Tẩy Căn Đan)" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: GDD §4 đổi 1 lần miễn phí trước Lv30, sau đó tốn Tẩy Căn Đan bán ở cửa hàng). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §4](../../13-game-design/GAME-DESIGN-DOCUMENT.md) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (đổi ngoại hình tức thì) |

---

## Context

`SelectFactionUseCase` chỉ chặn cấp 10 và cho đổi không giới hạn, không tốn gì; client tự đóng cửa sổ sau
lần đầu để người chơi khỏi lỡ tay. GDD: "đổi 1 lần miễn phí trước Lv30, sau đó tốn Tẩy Căn Đan".

## Decision

**D1 — Luật** (`FactionChangePolicy`): lần chọn ĐẦU (thức tỉnh, rời Hỏa ở Lv10) luôn miễn phí — đó là thức
tỉnh, không phải "đổi" (Q1 theo khuyến nghị). Lần thứ hai miễn phí nếu cấp còn **dưới 30**. Mọi lần sau tốn
**một Tẩy Căn Đan**. Chọn đúng hệ đang có ⇒ `error.faction_unchanged`, không tốn gì.

**D2 — Dữ liệu** (V68): `character.faction_changes` (đếm, mặc định 0 — người đã đổi trước đây đếm lại từ 0);
vật phẩm `faction_change_pill` (xếp 99), cửa hàng bán 20.000 / mua lại 1.000 (tham số).

**D3 — Một transaction**: khoá túi đồ → khoá dòng nhân vật (`FOR UPDATE`) → quyết miễn phí → (nếu không)
lấy một viên đan theo luật lấy đồ chung (`InventoryItemTake`: không món đang mặc/cường hoá/đang chào giao
dịch) → ghi hệ + tăng đếm → audit `FACTION_CHANGE`. Hết đan ⇒ `error.faction_change_pill_required`, không
ghi gì. Hiệu lực chiến đấu + ngoại hình ngay như cũ (ADR-045).

**D4 — Giao thức không đổi**: dùng `SelectFactionRequest/Response` + hai khoá lỗi mới.

**D5 — Client**: cửa sổ H mở được mọi lúc từ Lv10 với đủ năm hệ (kể cả về Hỏa); lời mời "nhấn H" chỉ hiện
cho người chưa thức tỉnh; tiêu đề cửa sổ nói luật chi phí. Client không biết số lần đã đổi — server quyết.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lần thức tỉnh tính là "lần miễn phí" duy nhất | Người chơi chưa thử hệ nào đã phải trả tiền để sửa lựa chọn đầu |
| Đan bỏ qua giới hạn cấp | Không có lý do; giữ Lv10 |
| Reset chiêu/điểm khi đổi | Chưa có hệ điểm công pháp lúc viết; ADR-067 tự xử lý |

## Consequences

- Đổi linh căn sau lần thứ hai là một gold sink (20.000).

## Revisit Trigger

- Có nguồn đan ngoài cửa hàng (quest/sự kiện); muốn client hiện "lần này miễn phí/không" ⇒ thêm field.

## Technical Decisions

- `FactionChangePort` (domain feature-character) + `PostgresFactionChangeAdapter`; luật lấy đồ tách thành
  `InventoryItemTake` dùng chung với giao đồ nhiệm vụ.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V68__faction_change.sql` · `SelectFactionUseCase` · `FactionController` · Test `SelectFactionUseCaseTest`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ⏳ Kiểm sống: nhân vật Lv≥10 đổi 3 lần, lần ba bị từ chối khi không có đan.
