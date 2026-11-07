# ADR-205: Xoá nhân vật có chờ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Xoá nhân vật" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: xoá nhân vật có chờ 3 ngày, đăng nhập lại trong thời gian đó thì khôi phục được). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-012](ADR-012-Package-Root.md) · [ADR-183](ADR-183-Guild-Leader-Transfer.md) (nhường chức bang chủ) |

---

## Decision

**D1 — Xoá mềm, không xoá dòng**: V160 thêm `character.deletion_requested_at`. Dòng `character` KHÔNG bao giờ bị xoá thật —
khoá ngoại từ hàng chục bảng (túi đồ, thư, bạn bè, audit, …) trỏ vào nó, và audit phải còn đọc được. Quá **3 ngày**
(`CharacterDeletionFacade.GRACE`) thì nhân vật **biến khỏi danh sách** và **không còn chiếm chỗ** trong trần 4 nhân vật /
tài khoản (lọc thẳng trong câu liệt kê / đếm của `PostgresCharacterAdapter` — cùng con số 3 ngày, ghi chú hai phía).
**Tên vẫn bị giữ** (không giải phóng tên — chờ chủ dự án nếu muốn khác).

**D2 — Ai xoá được gì**: chỉ nhân vật của chính tài khoản (từ session); không xoá nhân vật đang chơi (`IN_WORLD`); không
xoá bang chủ (`GUILD_LEADER` — nhường chức trước, bang không được mất chủ). Yêu cầu lại khi đang chờ giữ nguyên mốc cũ.

**D3 — Khôi phục = chọn lại**: `SelectCharacterRequest` với nhân vật đang chờ xoá ⇒ bỏ đánh dấu rồi vào world bình thường;
quá hạn ⇒ `error.character_not_found` như nhân vật không tồn tại.

**D4 — Giao thức, chỉ thêm**: `CharacterSummary.deletion_epoch_seconds = 5` (lúc xoá hẳn; 0 = không chờ);
`DeleteCharacterRequest{character_id}` / `DeleteCharacterResponse{outcome, characters}` 257/258.

**D5 — Client**: màn chọn nhân vật có nút **Xoá** cạnh mỗi dòng — bấm lần đầu chỉ hỏi lại, bấm lần hai mới gửi; dòng đang
chờ xoá ghi "(chờ xoá: còn N ngày M giờ, chọn để khôi phục)".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Xoá mềm 3 ngày (V160), lọc khỏi danh sách / trần khi quá hạn, chọn lại = khôi phục, 257/258, nút Xoá hai lần | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): V160, `CharacterDeletionPort`/`Facade`, `PostgresCharacterDeletionAdapter`, lọc trong
   `PostgresCharacterAdapter`, `handleDeleteCharacter` + khôi phục trong `handleSelectCharacterBlocking`,
   `CharacterSelectScreen` nút Xoá, `CharacterDeletionText`.
2. ✅ Test: `CharacterDeletionIntegrationTest` (Postgres thật), `CharacterDeletionTextTest`.
3. **Kiểm sống 2026-11-07 (gameProbe):** `deleteChar:2` khi đang chơi ĐạoHữu01 → `IN_WORLD`; `deleteChar:1` (nhân vật tài khoản khác) → `NOT_FOUND`; `deleteChar:3` → `OK`, danh sách trả về `PhuTanThu(chờ xoá)`, DB có `deletion_requested_at`; đăng nhập chọn PhuTanThu → vào world bình thường và `deletion_requested_at` về NULL (huỷ xoá khi chọn lại). Kèm vá ADR-194: `RemoveEnemy` với tên chứa NUL/quá dài từng trả 500 (fuzz test bắt được) — nay facade từ chối trước khi chạm DB.
4. Chờ chủ dự án: 3 ngày; tên nhân vật đã xoá có được giải phóng không.
