# ADR-148: Dịch Danh Phù — đổi tên nhân vật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Dịch Danh Phù (đổi tên)" trong lượt hỏi lô 27 (AskUserQuestion; mô tả kèm: vật phẩm đổi tên nhân vật — kiểm trùng/độ dài/ký tự, cooldown, audit, người xung quanh thấy tên mới). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | V54 (`UNIQUE lower(name)`) · luật tên mới 2026-10-11/10-14 (`Character.validateNewName`, NFC) |

---

## Decision

**D1 — Vật phẩm (V120)**: `rename_talisman` (Dịch Danh Phù, xếp chồng 5), bán ở cửa hàng tông môn **20.000** linh
thạch (bán lại 2.000). Số là PLACEHOLDER (Điều 27).

**D2 — Luật tên**: tên mới qua ĐÚNG luật lúc tạo nhân vật — chuẩn hoá NFC rồi `Character.validateNewName` (2–16 ký tự
chữ/số/gạch dưới, không ký tự vô hình/dạng tương thích, không trộn Latin với Cyrillic/Hy Lạp) — kiểm ở facade TRƯỚC khi
chạm DB. Trùng tên (kể cả khác hoa-thường) do `UNIQUE lower(name)` của V54 chặn. Đổi chỉ hoa-thường của chính mình
được phép; trùng nguyên văn tên hiện tại ⇒ `SAME_NAME`.

**D3 — Một giao dịch**: khoá túi → khoá dòng `character` → hồi chiêu (dòng mới nhất của `character_name_history` +
**7 ngày**) → tiêu một phù → `UPDATE character SET name` (vi phạm UNIQUE ⇒ `NAME_TAKEN`) → ghi lịch sử tên + audit
`CHARACTER_RENAME`. Từ chối ở bất kỳ bước nào ⇒ rollback, **không mất phù**.

**D4 — Hiệu lực ngay, không cache nào phải dọn**: mọi nơi hiển thị tên (chat, bạn bè, bang, thư, giao dịch, bảng xếp
hạng) đều tra tên theo id lúc cần; không bảng nào lưu tên lặp lại (đã soát migration V1–V119). Bảng xếp hạng cache 60 giây
nên tên mới hiện ở lượt làm mới kế tiếp. Gói spawn nhân vật không mang tên (chỉ cảnh giới — ADR-140), nên người xung
quanh không cần event mới.

**D5 — Giao thức, chỉ thêm**: 228/229 `RenameCharacterCommand{new_name}` / `RenameCharacterResponse{outcome, name,
next_allowed_at_epoch_ms}`. `protocolVersion` không tăng.

**D6 — Client**: lệnh chat `/doiten <tên mới>` (hoặc `/rename`); kết quả là một dòng hệ thống (kể cả lúc được đổi lần
sau nếu đang hồi chiêu).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Đá người chơi về màn chọn nhân vật sau khi đổi | Không cần: không nơi nào giữ tên trong RAM (D4); đá ra chỉ làm phiền |
| Không hồi chiêu | Đổi tên liên tục là công cụ lừa đảo/né truy nã; 7 ngày đủ cho người đổi vì lý do thật |
| Event báo tên mới cho người xung quanh | Không có chỗ nào trên màn hình người khác đang hiện tên nhân vật trong world |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phù tiêu hao bán ở cửa hàng, luật tên như lúc tạo, một giao dịch, hồi chiêu 7 ngày, lịch sử tên + audit | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [ADMIN.md](../../12-devtools/ADMIN.md) (tra lịch sử tên: `SELECT * FROM character_name_history WHERE character_id = ?`)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 27): V120, `RenamePort`/`RenameRules`/`RenameFacade` (feature-character),
   `PostgresRenameAdapter`, `RenameCommands` (228/229), `RenameCoordinator` (`/doiten`), bước probe `rename:<tên>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server `installDist` thật, TLS, DB chạy V120): `rename:baodo01` ⇒ `NAME_TAKEN`
   (trùng `BaoDo01` khác hoa-thường); `rename:co dau` ⇒ `INVALID_NAME`; tên hiện tại ⇒ `SAME_NAME`; `rename:ĐạoHữu01` ⇒
   `OK` (phù 2→1, `character_name_history` + audit `CHARACTER_RENAME`), ngay sau đó `rename:ĐạoHữu02` ⇒ `ON_COOLDOWN`
   kèm mốc +7 ngày; đăng nhập lại thấy tên mới.
