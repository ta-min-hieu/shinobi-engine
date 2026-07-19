# ADR-025: Idempotency cho Command có side-effect

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-19 |
| Deciders | Chủ dự án |
| Related | [SECURITY.md §6](../../08-security/SECURITY.md) (Replay Protection), [NETWORK.md §5](../../05-server/NETWORK.md) (Request/Response Correlation), [GAMEPLAY.md §8](../../05-server/GAMEPLAY.md) (Trade), [GAMEPLAY.md §11](../../05-server/GAMEPLAY.md) (Mail), [ROADMAP.md](../../11-process/ROADMAP.md) dòng "Không mở Trade nếu ADR-025 chưa Accepted" |

---

## Context

Đây là **lỗ hổng thiết kế hiện tại được chính tài liệu dự án đánh dấu là rủi ro nghiêm trọng nhất** (SECURITY.md §6, mục "Việc cần làm tiếp theo" ưu tiên #1).

`sequenceId` tăng dần (đã Accepted) chặn được **replay** (kẻ tấn công ghi lại và gửi lại packet cũ) và **out-of-order** — server từ chối `sequenceId` ≤ cái đã thấy. Nhưng `sequenceId` **không** giải quyết được tình huống retry hợp lệ: nếu client gửi `BuyItemCommand`, mất kết nối trước khi nhận `response` (client không biết server đã xử lý hay chưa), rồi thử lại — request thử lại đó dùng một `sequenceId` **mới** (vì `sequenceId` luôn tăng), nên với server đây là một Command hoàn toàn mới, không phải bản lặp. Kết quả: mua 2 lần, trừ tiền 2 lần, hoặc trade/mail-claim bị nhân đôi item — phá kinh tế server (GAMEPLAY.md §8, §11 đều gọi đây là lỗi nguy hiểm nhất của MMORPG).

`sequenceId` monotonic và idempotency là **hai cơ chế giải quyết hai vấn đề khác nhau**, không thể dùng cái này thay cái kia:
- `sequenceId` → chống replay/out-of-order (kẻ tấn công chủ động).
- Idempotency key → chống double-effect từ retry hợp lệ (client tự thử lại vì không chắc request trước có tới nơi hay không).

## Decision

Mọi Command được đánh dấu **có side-effect** (`BuyItemCommand`, `TradeConfirmCommand`, `MailClaimCommand`, và các Command tương tự cần Transaction+audit theo GAMEPLAY.md) mang theo một **`idempotencyKey`** do client sinh (UUID v4), giữ nguyên khi client tự retry cùng một hành động, sinh mới khi người chơi bắt đầu một hành động mới. Server enforce duy nhất mỗi key ở tầng **DB** (unique constraint/bảng riêng, trong cùng transaction với side-effect thật) cho các Command đã bắt buộc Transaction+audit (GAMEPLAY.md — trade, shop, mail-item, cường hoá); Redis chỉ dùng làm cache tăng tốc phát hiện trùng ở fast-path, **không phải nguồn chân lý** cho việc chống trùng (nhất quán với ràng buộc đã Accepted ở ADR-005: Redis không bao giờ là nơi duy nhất chứa dữ liệu quan trọng).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **`idempotencyKey` (client-generated) + enforce ở DB, cache ở Redis** | Đúng nguồn chân lý — DB transaction vốn đã bắt buộc cho các thao tác này (GAMEPLAY.md), thêm 1 cột/bảng unique không phải kiến trúc mới; sống sót qua restart Redis (khớp ADR-005); tận dụng transaction có sẵn để đảm bảo "check-and-mark" atomic, không có race condition giữa kiểm tra key và ghi hiệu ứng | Cần client-core sinh và gửi kèm UUID cho đúng nhóm Command — thay đổi format Command có side-effect (nhưng đây vốn dĩ là việc phải làm để đóng lỗ hổng, không phải chi phí phụ) | **Ứng viên được chọn** |
| Chỉ dựa vào `sequenceId` (giữ nguyên hiện trạng) | Không cần thay đổi gì | **Không giải quyết được vấn đề** — đã phân tích ở Context: retry hợp lệ luôn có `sequenceId` mới, không bị chặn | Không chọn — đây chính là lỗ hổng đang treo |
| Idempotency key chỉ ở Redis (không enforce ở DB) | Nhanh, không đụng schema DB | Mất key khi Redis restart giữa lúc có request đang "treo" (client vừa gửi, chưa nhận response, Redis restart) → mất khả năng chống trùng đúng lúc cần nhất; vi phạm nguyên tắc ADR-005 "Redis không phải nguồn chân lý" | Không chọn |
| Server tự sinh idempotency key từ nội dung request (hash payload) | Client không cần thay đổi gì | Hai hành động **khác nhau nhưng cùng nội dung** (mua cùng 1 item 2 lần liên tiếp, cố ý) sẽ bị hash trùng và bị chặn nhầm thành "duplicate" — sai use case thật; không phân biệt được "retry của cùng 1 ý định" với "2 ý định giống hệt nhau" | Không chọn |
| Exactly-once ở tầng transport (TCP/Netty) | — | TCP đảm bảo byte-stream không trùng ở tầng transport, nhưng không ngăn được **tầng ứng dụng** gửi lại (client chủ động resend sau timeout) — hoàn toàn không giải quyết vấn đề đang bàn | Không chọn |

## Consequences

**Tích cực:**
- Đóng lỗ hổng 🔴 mà chính tài liệu dự án gọi là rủi ro nghiêm trọng nhất hiện tại — mở khoá Trade theo đúng điều kiện đã ghi ở ROADMAP.md ("Không mở Trade nếu ADR-025 chưa Accepted").
- Tận dụng transaction đã bắt buộc sẵn (GAMEPLAY.md) — không phải xây cơ chế đồng bộ mới, chỉ thêm 1 ràng buộc unique vào transaction có sẵn.
- Áp dụng thống nhất cho mọi Command side-effect tương lai (guild, auction...) — pattern lặp lại được, không phải giải pháp vá riêng cho từng feature.

**Đánh đổi phải chấp nhận:**
- `shared-protocol`: Command có side-effect cần thêm field `idempotencyKey` — đổi định dạng packet cho nhóm Command này (🛑 theo CLAUDE.md §4, đổi protocol cần dừng lại xác nhận trước khi code, dù ADR này đã là bước xác nhận đó).
- Cần định nghĩa rõ **danh sách Command nào được coi là "có side-effect"** (không phải tất cả Command cần key này — Command chỉ đọc, hoặc Command không có hậu quả tài chính/vật phẩm, không cần) — việc phân loại này nên làm ở Phase 3 khi viết `feature-*` cụ thể, dựa trên danh sách gốc GAMEPLAY.md đã liệt kê (trade, shop, mail-item, cường hoá) làm khởi điểm.
- TTL cho bảng/khoá idempotency ở DB cần giới hạn hợp lý (ví dụ giữ 24-48h rồi dọn) để tránh phình bảng vô hạn — chi tiết triển khai, không chặn việc Accept ADR này.

**Module bị ảnh hưởng:** `shared-protocol` (thêm field `idempotencyKey`), mọi `feature-*` có Command side-effect (`feature-inventory`, tương lai `feature-trade`/`feature-shop`/`feature-mail`), `server-database` (bảng/constraint idempotency, Redis cache layer).

## Lý do chốt

Chủ dự án xác nhận 2026-07-19: đồng ý thêm field `idempotencyKey` vào Command có side-effect (đổi định dạng protocol, xác nhận theo CLAUDE.md §4). Đây là cách duy nhất trong các phương án xét ở trên vừa giải quyết đúng gốc vấn đề (client không biết retry có bị trùng hay không) vừa nhất quán với các ràng buộc đã Accepted khác (transaction bắt buộc, Redis không phải nguồn chân lý). Việc phân loại "Command nào cần key" sẽ làm dần ở Phase 3 khi viết `feature-*` cụ thể, không cần quay lại ADR này.

## Revisit Trigger

- Phát sinh loại side-effect không map được vào mô hình "transaction DB + unique key" (ví dụ side-effect chạy trên hệ thống ngoài, không có transaction DB bao quanh được) → cần thiết kế bổ sung, không thay thế ADR này.
- Số lượng Command cần `idempotencyKey` tăng tới mức chi phí thêm field vào **mọi** Command (không chỉ nhóm side-effect) rẻ hơn chi phí duy trì danh sách phân loại → cân nhắc gộp field vào toàn bộ `Command` base thay vì chỉ nhóm có side-effect.
