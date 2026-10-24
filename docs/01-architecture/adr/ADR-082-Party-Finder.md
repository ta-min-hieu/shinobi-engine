# ADR-082: Bảng tìm nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Bảng tìm nhóm" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: đăng tin tìm đội — mục tiêu + cấp, người khác bấm xin vào; tin tự hết hạn 10 phút; opcode mới, chỉ giữ trong RAM). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-party · [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) |

---

## Decision

**D1 — Tin chỉ trong RAM** (`PartyFinderBoard`, feature-party domain; một bản dùng chung ở gateway): mỗi trưởng nhóm tối đa MỘT tin; tin hết hạn sau
**10 phút**; khởi động lại server là mất hết (chấp nhận — tin tìm nhóm không phải tài sản).

**D2 — Nội dung tin**: mục tiêu chọn từ danh sách CỐ ĐỊNH (`PartyGoal`: luyện cấp / nhiệm vụ / hầm ngục / boss thế
giới) + cấp tối thiểu. Không chữ tự do ⇒ không cần lọc từ, không spam được nội dung.

**D3 — Đăng tin**: chưa có nhóm thì server tạo nhóm trước (người đăng làm trưởng nhóm); đã ở nhóm mà không phải
trưởng nhóm ⇒ `NOT_LEADER`.

**D4 — Xin vào = vào ngay** nếu đủ cấp và nhóm còn chỗ: gateway gọi đúng hai bước có sẵn (mời thay trưởng nhóm →
chấp nhận) — tin công khai là lời mời công khai. Nhóm đầy hoặc giải tán thì tin tự biến mất khỏi danh sách.

**D5 — Giao thức, chỉ thêm**: 1319/1320 `ListPartyFinderRequest/Response`, 1321/1322 `PostPartyFinderCommand/Response`,
1323/1324 `JoinPartyFinderCommand/Response`, 1325/1326 `RemovePartyFinderCommand/Response`.

**D6 — Client**: cửa sổ Nhóm thêm nút "Bảng tìm nhóm" (`ReportPanel`, mỗi tin một dòng + nút "Vào").

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lưu DB | Tin sống 10 phút; DB chỉ thêm việc dọn |
| Chữ tự do | Cần lọc từ + chống spam; danh sách mục tiêu đủ dùng |
| Xin vào chờ trưởng nhóm duyệt | Thêm vòng event + UI duyệt; tin công khai đã là sự đồng ý |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tin RAM 10 phút, mục tiêu cố định, vào ngay, dải 1319–1326 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `PartyFinderBoard` · `PartyFacade`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19) — `PartyFinderBoard` (feature-party domain, giữ ở `ConnectedCharacterRegistry`), `PartyFinderCommands` (gateway).
2. ⏳ Kiểm sống bằng gameProbe.
