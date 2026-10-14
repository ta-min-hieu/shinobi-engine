# CLAUDE.md — feature-character

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-character/**`.

## Cấu trúc (Clean Arch)

```
api/            CharacterFacade         ← module khác (server-gateway) CHỈ gọi qua đây
application/    CreateCharacterUseCase · ListCharactersUseCase
domain/         Character (aggregate) · Faction · CharacterPort · Transaction · TransactionRunner
                IdempotencyKeyReusedException · CharacterNameTakenException
```

`CharacterNameTakenException` (2026-09-24): `existsByName` trong `CreateCharacterUseCase` chỉ là
kiểm sớm, KHÔNG nguyên tử với INSERT. Hai người tạo cùng một tên đúng cùng lúc đều qua được nó; UNIQUE
`character.name` chặn người thứ hai, adapter dịch thành exception này, use case đổi thành
`DuplicateCharacterNameException` — người chơi nhận "tên trùng" như đường thường.

Giới hạn 4 nhân vật/account (vá 2026-09-25): `countByAccount` ngoài transaction cũng chỉ là kiểm
sớm. Phép kiểm QUYẾT ĐỊNH là `CharacterPort#lockAccountAndCountCharacters` — khoá dòng `account`
(`FOR NO KEY UPDATE`) rồi đếm lại, trong CÙNG transaction với INSERT. Trước bản vá, 8 lượt tạo song
song trên account có 3 nhân vật cho ra 11 nhân vật (`CharacterLimitRaceIntegrationTest`).

Vị trí đã lưu KHÔNG hữu hạn (vá 2026-10-03, `5aab474`): trước bản vá hướng-NaN (2026-09-24) một
`MoveCommand` có thể cho nhân vật `x = NaN`, và giá trị đó được ghi xuống cột REAL `pos_x` lúc đăng
xuất ⇒ mỗi lần vào lại thế giới đều mang NaN, vật lý/AoI/kiểm tầm đầu độc vĩnh viễn, NaN còn đi ra
client trong `SelectCharacterResponse`. Nay `PositionSnapshot` chuẩn hoá toạ độ không hữu hạn về `0`
(điểm xuất phát của nhân vật chưa từng lưu vị trí) — phủ cả response lẫn mọi đường attach đọc vị trí
đã lưu; `WorldFacade.handleAttach` (server-world) là lớp phòng thủ thứ hai, rơi về điểm hồi sinh.
Test: `PositionSnapshotTest`, `ServerAuthorityWorldTest`.

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — không có
`infrastructure/` trong module này (xem feature-account/CLAUDE.md mục "Khác template chuẩn" —
adapter thật nằm ở `server-database`, ArchUnit chặn JDBC/Redis theo package
`dev.kage.server.database`, không theo Gradle module).

## Quyết định MVP ghi lại ở đây (không có trong docs gốc)

- **Hệ phái**: GDD §4 nói "chọn hệ ở Lv10" nhưng MVP chỉ có 1 hệ chơi được (Hỏa) — nhân vật mới tạo
  LUÔN `Faction.HOA`, không có bước chọn hệ lúc tạo. Khi Post-MVP mở thêm hệ, việc "chọn/đổi hệ ở
  Lv10" là tính năng RIÊNG, không phải sửa lại `CreateCharacterUseCase`.
- **Giới hạn 4 nhân vật/account**: không có trong docs, tự chọn số hợp lý
  (`CreateCharacterUseCase.MAX_CHARACTERS_PER_ACCOUNT`) — tham số triển khai, chỉnh được không cần ADR.
- **Validate tên nhân vật**: chỉ độ dài (2-16) + charset Unicode chữ/số — CHƯA có banned-word/
  homoglyph dù SECURITY.md §12 yêu cầu đủ cả hai. Dự án cá nhân, rủi ro thấp, để trống có chủ đích.
  Hệ quả (chạy thử regex 2026-10-06): tên toàn ký tự VÔ HÌNH (Hangul filler U+3164/U+115F/U+FFA0 là
  `\p{L}`), tên khổ toàn phần/chữ toán học/Cyrillic giả Latin đều được nhận; UNIQUE của cột `name`
  phân biệt hoa thường nên `Kage`/`kage` là hai nhân vật khác nhau. Đổi luật là việc của chủ dự án (đã đưa vào bảng câu hỏi).
  **Đã đổi cho tên MỚI**: 2026-10-11 chặn ký tự vô hình + dạng tương thích (`validateNewName`);
  2026-10-14 chuẩn hoá **NFC** trước khi kiểm và lưu dạng NFC (`normalizeNewName` trong
  `CreateCharacterUseCase`) — tiếng Việt gõ dấu tách rời được nhận. Trùng hoa-thường vẫn mở.
- **Thông điệp lỗi của `validateName` KHÔNG chép nguyên văn tên** (2026-10-06): gateway log nó ở mỗi
  lượt tạo sai; chỉ ghi độ dài + `U+XXXX` của ký tự sai đầu tiên (`CharacterTest`). Cùng luật cho
  `Account.validateUsername`.

Bất biến tạo/chọn nhân vật dưới đua trên Postgres thật (đúng một người thắng một tên, không
`character_stat` mồ côi, khoá idempotency của người thua không bị đốt, giới hạn + tên trùng đan xen):
`CharacterCreationInvariantsIntegrationTest` (server-database), `AccountCharacterWireDatabaseTest`
(server-gateway — kiểm cả `error_key` qua dây).

## Idempotency (ADR-025 ✅ Accepted)

`CreateCharacterCommand` là Command side-effect ĐẦU TIÊN của dự án dùng `idempotencyKey`. Thiết kế:
`idempotency_key` là bảng riêng ở `server-database`, ghi trong CÙNG transaction với insert
character — vi phạm unique constraint → `IdempotencyKeyReusedException` → KHÔNG tạo nhân vật lần 2.
**Đây KHÔNG phải full replay semantics** (retry không trả lại chính xác nhân vật đã tạo lần đầu,
chỉ đảm bảo không tạo trùng) — client phải tự `ListCharactersRequest` lại sau lỗi
`error.duplicate_request` để biết nhân vật đã được tạo hay chưa. Đủ cho MVP, không phải giải pháp
cuối cùng.

## Cứng

✅ Quy tắc nghiệp vụ ở `domain/` (validate trong compact constructor + `Character.validateName`)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Module khác gọi qua `CharacterFacade`, không đọc bảng `character`/`character_stat` trực tiếp
✅ `accountId` luôn từ session (server-gateway), KHÔNG BAO GIỜ từ packet (Điều 24, chống IDOR)

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL/Redis
❌ Chỉ số dẫn xuất (ATK/DEF...) — server tính lúc cần, không lưu (chặn Phase 4)

## Test
`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito).
