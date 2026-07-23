# CLAUDE.md — feature-mail

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-mail/**`.

## Cấu trúc (Clean Arch)

```
api/            MailFacade (+ ClaimResult/DeleteResult/AttachmentRequest/AttachmentView/MailView —
                 enum/record RIÊNG, không lộ type UseCase/domain.Mail ra ngoài)
application/    SendMailUseCase · ListMailUseCase · ClaimMailUseCase · DeleteMailUseCase
                MailPorts (gộp 4 Port cho ClaimMailUseCase, tránh vượt 4 tham số — Checkstyle
                ParameterNumber, cùng tinh thần ShopPorts)
                MailTransactionAbortedException (package-private, rollback nội bộ)
domain/         Mail (aggregate) · MailAttachment (VO) · MailRepositoryPort
                GoldPort · InventoryGatewayPort · AuditPort (bản sao ý tưởng của feature-shop,
                chỉ giữ chiều CẦN — creditGold/grantItem, không có deduct/remove)
                Transaction · TransactionRunner (bản khai riêng, không dùng chung feature khác)
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresMailAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-23, Phase 6 lát cắt 5)

- **Gửi thư KHÔNG mất gold** — MVP chưa có nhu cầu giới hạn (mail chủ yếu server→player: quest
  reward tồn đọng, GM đền bù). Nếu sau này thêm gửi thư có phí (chống spam mail giữa người chơi),
  đó là thay đổi ở `SendMailUseCase` + cần `GoldPort.deductGold` — chưa làm ở đây.
- **`senderId` kiểu `Long` (nullable), `null` = thư hệ thống** — không dùng sentinel số (0/-1, dễ
  nhầm ID thật). Chưa có khái niệm "GM gửi thư" thật ở MVP; field tồn tại sẵn cho lúc cần.
- **Xoá thư**: cấm xoá thư CHƯA nhận mà còn gold/item — phải `ClaimMailCommand` trước
  (`DeleteMailUseCase.MUST_CLAIM_FIRST`). Thư thông báo thuần (không đính kèm gì) xoá được ngay.
- **Không giới hạn số thư tồn** — GDD chưa chốt, để trống có chủ đích (giống nhiều giới hạn MVP
  khác trong dự án, vd `MAX_CHARACTERS_PER_ACCOUNT`).

## Transaction — mẫu Y HỆT feature-shop's buy, áp dụng lần thứ 2 cho "nhận thưởng"

`ClaimMailUseCase` là bản sao Ý TƯỞNG của `BuyItemUseCase`/`ClaimQuestRewardUseCase`: khoá dòng
(`findByIdForUpdate` — `SELECT ... FOR UPDATE`) → kiểm `claimed_at IS NULL` → cộng gold → cấp item
→ đánh dấu `claimed_at` → ghi audit, TẤT CẢ trong 1 transaction. Nếu cấp item thất bại (túi đầy)
giữa chừng, ném `MailTransactionAbortedException` để rollback CẢ gold đã cộng — không được "nhận
gold nhưng mất item" (DATABASE.md §5, đúng cảnh báo gốc ở GAMEPLAY.md §11 về duplicate qua mail).

`findByIdForUpdate` PHẢI chạy trong transaction (khoá dòng) — khác `findById` (đọc thuần, dùng cho
danh sách/xoá, không cần khoá vì không đổi gì).

## Cứng

✅ Quy tắc validate (goldAmount âm, quantity ≤ 0) ở `domain/` compact constructor (`Mail`,
   `MailAttachment`)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Nhận thưởng (gold+item+đánh dấu claimed+audit) trong 1 transaction (DATABASE.md §5)
✅ Module khác gọi qua `MailFacade`, không đọc bảng `mail`/`mail_item` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Tách riêng "cấp item" và "đánh dấu claimed" thành 2 transaction khác nhau

## Test

`domain/` test không mock. `application/` test với **Fake Port** (không Mockito) — `ClaimMailUseCaseTest`
verify cả nhánh rollback (túi đầy giữa chừng: gold KHÔNG được cộng thật, `claimed_at` KHÔNG đổi,
audit KHÔNG ghi) bằng Fake ghi nhận lời gọi, cùng cách `BuyItemUseCaseTest` verify
`shouldRollbackAndReturnInventoryFull_whenGrantFails`. Postgres thật
(`server-database/.../mail/MailIntegrationTest`) verify khoá dòng chống nhận 2 lần.
