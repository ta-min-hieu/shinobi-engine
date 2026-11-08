# ADR-218: Hồ sơ cá nhân (dòng giới thiệu)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Hồ sơ cá nhân" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: một dòng giới thiệu tự viết ≤ 60 chữ, hiện khi người khác soi trang bị). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-171](ADR-171-Inspect-Player.md) (soi trang bị) |

---

## Decision

**D1 — Cột `character.bio`** (V164, `VARCHAR(60)`, mặc định rỗng). Server cắt khoảng trắng hai đầu, từ chối quá 60 ký
tự hoặc có ký tự điều khiển, và từ chối từ cấm bằng **cùng bộ lọc chat** (`ProfanityFilter`). Rỗng = xoá.

**D2 — Hiện ở đâu**: `InspectCharacterResponse.bio = 7` — người soi thấy một dòng "Giới thiệu: …" đầu bảng trang bị.
Người đã tắt cho soi (ADR-171) thì không lộ dòng này (cùng luật).

**D3 — Protocol**: `SetBioCommand{bio}` / `SetBioResponse{outcome, bio}` 259/260.

**D4 — Client**: `/gioithieu <chữ>` (hoặc `/bio`); không kèm gì là xoá.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `bio` 60 ký tự (V164), lọc từ cấm như chat, hiện khi soi; 259/260; `/gioithieu` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): V164, `InspectPrivacyPort.bio/setBio`, `InspectPrivacyFacade.setBio`,
   `Lot36Commands.setBio`, `BioCoordinator`, dòng "Giới thiệu" trong `InspectCoordinator`.
2. ✅ Test: `Lot36IntegrationTest` (ghi / đọc lại).
3. ✅ Kiểm sống gameProbe 2026-11-08: BaoDo01 `bio:Đệ tử Linh Kiếm Môn` ⇒ `OK`; ĐạoHữu01 `inspect:BaoDo01` ⇒
   `giớiThiệu='Đệ tử Linh Kiếm Môn'`; `bio:` (rỗng) ⇒ xoá. Đã xoá lời giới thiệu thử của cả hai.
