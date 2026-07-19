# CLAUDE.md — feature-character

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-character/**`.

## Cấu trúc (Clean Arch)

```
api/            CharacterFacade         ← module khác (server-gateway) CHỈ gọi qua đây
application/    CreateCharacterUseCase · ListCharactersUseCase
domain/         Character (aggregate) · Faction · CharacterPort · Transaction · TransactionRunner
                IdempotencyKeyReusedException
```

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
