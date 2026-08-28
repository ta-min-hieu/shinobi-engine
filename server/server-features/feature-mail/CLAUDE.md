# CLAUDE.md — feature-mail

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-mail/**`.

## Cấu trúc (Clean Arch)

```
api/            MailFacade (+ SendResult/SendOutcome/ClaimResult/DeleteResult/AttachmentRequest/
                 AttachmentView/MailView — enum/record RIÊNG, không lộ type UseCase/domain.Mail)
application/    SendMailUseCase · ListMailUseCase · ClaimMailUseCase · DeleteMailUseCase
                MailPorts (gộp 5 Port cho ClaimMailUseCase VÀ SendMailUseCase, tránh vượt 4 tham
                số — Checkstyle ParameterNumber, cùng tinh thần ShopPorts)
                MailTransactionAbortedException (nhận: túi đầy) · SendMailAbortedException (gửi:
                mang theo lý do) — cả hai package-private, rollback nội bộ
domain/         Mail (aggregate) · MailAttachment (VO) · MailRepositoryPort
                MailDraft · MailAttachmentDraft · MailContentViolation (biên giới dữ liệu CHƯA
                kiểm từ client — validate() trả outcome thay vì ném exception)
                GoldPort · InventoryGatewayPort · AuditPort (bản sao ý tưởng của feature-shop, đủ
                HAI chiều: deductGold/consumeItem lúc gửi, creditGold/grantItem lúc nhận)
                Transaction · TransactionRunner (bản khai riêng, không dùng chung feature khác)
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresMailAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-23, Phase 6 lát cắt 5)

- ~~**Gửi thư KHÔNG mất gold**~~ — **ĐÃ THAY 2026-08-28** (xem mục "Lỗ đúc vàng/vật phẩm" bên
  dưới). Không có PHÍ bưu điện, nhưng gold/vật phẩm ĐÍNH KÈM bị ký quỹ khỏi người gửi. Thu thêm
  phí gửi (chống spam mail giữa người chơi) vẫn là quyết định thiết kế game CHƯA chốt.
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

## ✅ Lỗ đúc vàng/vật phẩm — ĐÃ VÁ 2026-08-28 (phát hiện 2026-08-02)

**Lỗ hổng cũ.** `SendMailCommand` cho client gửi `gold_amount`/`attachments` bất kỳ, còn
`SendMailUseCase` CHỈ có `MailRepositoryPort` — không trừ gì từ người gửi trước khi ghi
`gold_amount`/`mail_item`. Trong khi `ClaimMailUseCase` thì cộng gold thật + cấp item thật. Ghép
lại: tự gửi thư cho chính mình kèm số bất kỳ rồi nhận = đúc vàng/vật phẩm từ không khí; gửi cùng
một món cho 10 người = nhân bản 10 lần. Vi phạm thẳng Server Authority (/CLAUDE.md §5).

**Quyết định của chủ dự án**: phương án (a) — ký quỹ. Gửi thư KHÔNG mất phí bưu điện, nhưng
gold/vật phẩm ĐÍNH KÈM bị trừ khỏi người gửi ngay lúc gửi.

**Cách vá.**
- `SendMailUseCase` nhận `MailPorts` (không còn chỉ `MailRepositoryPort`), trừ vàng
  (`GoldPort.deductGold`) và trừ vật phẩm (`InventoryGatewayPort.consumeItem`) TRONG CÙNG
  transaction với `INSERT INTO mail` + `mail_item`. Thiếu ở bước nào → `SendMailAbortedException`
  → rollback CẢ phần đã trừ.
- Trừ vàng đi qua `GoldLedger` (server-database) — điểm ghi `character_stat.gold` DUY NHẤT theo
  ADR-035 Decision-3, nên người gửi tự động nhận `GoldChangedEvent` (opcode 209) khi commit.
  `reasonKey` = `gold.reason.send_mail`.
- Dữ liệu client bẩn không còn thành exception im lặng: `MailDraft.validate()` (domain) kiểm
  `goldAmount` âm, `quantity <= 0`, `itemTemplateId <= 0`, `subject`/`body` vượt bề rộng cột và
  trả về `MailContentViolation` → outcome thật. Trước đây các trường hợp này ném
  `IllegalArgumentException`/`SQLException` trong `authExecutor` và client KHÔNG nhận được gì.
- `item_template_id` không tồn tại rơi vào cùng nhánh "không sở hữu" (`ATTACHMENT_NOT_OWNED`) —
  không ai sở hữu một template không có thật, nên không cần tra catalog riêng.

**6 outcome mới** (`mail.proto`, CHỈ thêm giá trị enum — NETWORK.md §6): `INSUFFICIENT_GOLD`(3),
`ATTACHMENT_NOT_OWNED`(4), `INVALID_GOLD_AMOUNT`(5), `INVALID_ATTACHMENT`(6),
`SUBJECT_TOO_LONG`(7), `BODY_TOO_LONG`(8).

**Thư hệ thống (`senderId = null`) KHÔNG bị ký quỹ** — không có ai để trừ, đó là ý nghĩa của thư
đền bù GM/quest reward tồn đọng. Client không với tới đường này: gateway luôn truyền `characterId`
lấy từ SESSION.

⚠️ **Còn mở — cần chủ dự án quyết**: chỉ ký quỹ được món CHƯA cường hoá (`enhance_level = 0`).
`mail_item` và `MailAttachmentView` không chở `enhance_level`, nên gửi một món +7 sẽ xoá mức cường
hoá đó vĩnh viễn. Hiện chặn lại (`ATTACHMENT_NOT_OWNED`); mở ra thì phải đổi schema + protocol —
cả hai đều là /CLAUDE.md §4.

## Cứng

✅ Quy tắc validate (goldAmount âm, quantity ≤ 0) ở `domain/` — HAI lớp: `MailDraft.validate()`
   trả outcome cho dữ liệu client, `Mail`/`MailAttachment` compact constructor là chốt cuối
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Nhận thưởng (gold+item+đánh dấu claimed+audit) trong 1 transaction (DATABASE.md §5)
✅ Gửi thư (trừ gold+trừ item+tạo thư+audit) trong 1 transaction — cùng lý do
✅ Mọi thay đổi `character_stat.gold` đi qua `GoldLedger` (ADR-035 Decision-3, có
   `GoldLedgerSourceGuardTest` quét mã nguồn gác)
✅ Module khác gọi qua `MailFacade`, không đọc bảng `mail`/`mail_item` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Tách riêng "cấp item" và "đánh dấu claimed" thành 2 transaction khác nhau
❌ Ghi `gold_amount`/`mail_item` từ packet client mà không trừ của người gửi — ĐÂY là lỗ đúc vàng

## Test

`domain/` test không mock. `application/` test với **Fake Port** (không Mockito) — `ClaimMailUseCaseTest`
verify cả nhánh rollback (túi đầy giữa chừng: gold KHÔNG được cộng thật, `claimed_at` KHÔNG đổi,
audit KHÔNG ghi) bằng Fake ghi nhận lời gọi, cùng cách `BuyItemUseCaseTest` verify
`shouldRollbackAndReturnInventoryFull_whenGrantFails`. Postgres thật
(`server-database/.../mail/MailIntegrationTest`) verify khoá dòng chống nhận 2 lần.

`SendMailUseCaseTest` dùng một Fake `TransactionRunner` **có rollback THẬT** (chụp ảnh trước, khôi
phục khi action ném) — bản `action -> action.accept(null)` một dòng để lại hiệu ứng nửa vời và làm
nhánh "trừ vàng xong mới phát hiện thiếu món" báo xanh giả.

Test hồi quy chống đúc vàng nằm ở `MailIntegrationTest` (Postgres thật, KHÔNG phải fake): gửi thư
cho chính mình rồi nhận → tổng vàng và tổng vật phẩm ĐÚNG BẰNG lúc đầu
(`shouldNotMintGoldOrItems_whenSendingToSelfAndClaiming`), và tổng vàng hai người không đổi khi
chuyển cho nhau (`shouldConserveTotalGold_whenTransferringBetweenTwoCharacters`).
