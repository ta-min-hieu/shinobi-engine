# ADR-060: Điểm danh hằng ngày — quà theo chu kỳ 7 ngày, gửi bằng thư hệ thống

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn "Điểm danh hằng ngày" trong lượt hỏi lô 16 (AskUserQuestion; mô tả kèm: mỗi ngày đăng nhập nhận thưởng qua thư hệ thống, chu kỳ 7 ngày, mốc 0h giờ VN như nhiệm vụ ngày). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) (mốc 0h giờ VN) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) (khoá chống trùng của thư) · [GDD §10](../../13-game-design/GAME-DESIGN-DOCUMENT.md) |

---

## Context

Người chơi không có lý do quay lại mỗi ngày ngoài nhiệm vụ ngày (ADR-055), mà nhiệm vụ ngày đòi đánh
quái. Điểm danh là vòng giữ chân rẻ nhất: vào game là có quà.

## Decision

**D1 — Luật.** Mỗi ngày (mốc 0h `Asia/Ho_Chi_Minh`, cùng mốc ADR-055), **lần vào world đầu tiên** nhận
quà của ngày kế tiếp trong chu kỳ 7 ngày. Hết ngày 7 quay lại ngày 1. **Bỏ ngày không phạt** (Q2 theo
khuyến nghị): chu kỳ đi theo số lần đã điểm danh, không theo lịch.

**D2 — Thứ tự: gửi quà TRƯỚC, ghi trạng thái SAU (compare-and-set).** Thư và trạng thái thuộc hai
feature, không chung transaction. Khoá chống trùng của thư là UUID tất định theo (nhân vật, ngày) —
`UUID.nameUUIDFromBytes("check-in:<id>:<yyyy-mm-dd>")` — nên:
- hai kết nối cùng vào world một lúc ⇒ cùng khoá ⇒ chỉ MỘT lá thư; compare-and-set chỉ cho một lượt ghi;
- tiến trình chết giữa hai bước ⇒ lần vào sau trong cùng ngày gửi lại đúng khoá (trùng ⇒ không thư mới)
  rồi mới ghi trạng thái. Ghi trạng thái trước thì chết giữa hai bước là MẤT quà vĩnh viễn.
- Giới hạn còn lại: chết giữa hai bước VÀ lần vào sau rơi sang ngày khác ⇒ khoá khác ⇒ quà ngày đó
  nhận hai lần. Cửa sổ vài mili giây, chỉ lợi người chơi; chấp nhận.

Khoá ghi vào bảng `idempotency_key` sẵn có (giữ 7 ngày — `RetentionJanitor`) — thư hệ thống nay cũng có
thể mang khoá (sửa ý D4 của ADR-058: "thư hệ thống không qua đường này" — đường gửi vốn đã ghi khoá cho
cả `senderId = null`, chỉ là trước đây không ai truyền).

**D3 — Quà đi thư hệ thống**, không cộng thẳng vào túi: túi đầy không mất quà, lĩnh qua `MAIL_CLAIM` có
audit sẵn, offline không phải lo. Báo `NEW_MAIL_EVENT` như mọi thư — **không opcode mới, không đổi
client**.

**D4 — Dữ liệu (V62).** `daily_check_in_reward(day 1..7, gold_amount, item_code, quantity)` là tham số
(Điều 27) — số đầu tiên là số TẠM: tổng 750 linh thạch/chu kỳ (ngang 7 ngày nhiệm vụ ngày = 770) + 5
đan dược + 2 bùa hộ cường hoá. `daily_check_in(character_id, last_day, last_claim_date, updated_at)`.

**D5 — Module mới `feature-checkin`** (domain thuần + use case + facade; adapter ở server-database).
Gateway hiện thực port gửi quà (`CheckInMailSender`) và gọi điểm danh ngay sau khi vào world, cùng luật
"việc phụ" với báo online cho bạn bè: lỗi dừng ở đó, không thành "chọn nhân vật thất bại".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Cộng thẳng vàng/đồ vào túi | Túi đầy ⇒ mất quà hoặc phải thêm luật "để dành"; đi thư có sẵn mọi thứ |
| Opcode "bấm nhận" + panel lịch 7 ngày | Thêm protocol + UI cho một việc mà vào game là đủ; để dành khi cần hiển thị lịch |
| Bỏ ngày ⇒ về ngày 1 | Phạt người bận; khuyến nghị không phạt |
| Đặt vào feature-character/feature-mail | Trộn trách nhiệm; feature thư không nên biết chu kỳ điểm danh |

## Consequences

- Một dòng `daily_check_in` mỗi nhân vật, một dòng `idempotency_key` + một thư mỗi lần điểm danh.
- Mốc 0h lặp ở hai nơi (`DailyReset` của feature-quest và `CheckInCycle`) — đổi một bên thì đổi cả hai.

## Revisit Trigger

- Muốn hiển thị lịch 7 ngày / bấm nhận thủ công ⇒ thêm opcode đọc trạng thái.
- Có tiền nạp/VIP ⇒ quà theo cấp VIP.

## Technical Decisions

- UUID tất định theo (nhân vật, ngày) làm khoá thư; compare-and-set trên `(last_day, last_claim_date)`.
- `INSERT ... ON CONFLICT DO NOTHING` cho lần đầu.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `server/server-features/feature-checkin/` · `CheckInMailSender` · `V62__daily_check_in.sql`
- Test: `ClaimDailyCheckInUseCaseTest` (mốc 0h VN, chu kỳ, bỏ ngày, chết giữa hai bước), `DailyCheckInDatabaseTest` (Postgres thật, 6 lượt song song ⇒ 1 thư)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-18 (lô 16).
2. ✅ Kiểm sống 2026-10-21: Kagemaru vào world ~15 lần trong ngày, chỉ có ĐÚNG MỘT thư "Điểm danh" (100 linh thạch); tanthua44 cũng một. Bộ đếm `CHECK_IN` = 1.
3. Chủ dự án chỉnh bảng quà bằng SQL khi có số đo kinh tế.
