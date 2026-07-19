# ADR-017: Auth token — JWT hay session token tự phát hành?

| | |
|---|---|
| Status | ⏳ **Proposed — CHƯA CHỐT, đang chặn Phase 3** |
| Date | 2026-07-19 |
| Deciders | Chủ dự án |
| Related | [SECURITY.md §3](../../08-security/SECURITY.md), [SECURITY.md §4](../../08-security/SECURITY.md) (IDOR), [NETWORK.md §14](../../05-server/NETWORK.md), [ADR-005](ADR-005-Why-Redis.md) ✅ Accepted (phụ thuộc Redis) |

---

## Context

`ConnectRequest(token)` từ client phải được Gateway xác thực (qua `feature-account`), trả về session (`characterId`, quyền) trước khi cho phép bất kỳ Command gameplay nào — mọi `characterId` sau đó **luôn lấy từ session, không bao giờ từ packet** (chống IDOR, đã Accepted, quan trọng nhất trong SECURITY.md). ADR này quyết định token đó có hình dạng gì.

Game cần khả năng **ban / đăng xuất cưỡng chế tức thì** — một game MMORPG PvP có kinh tế trong game khiến việc chờ token tự hết hạn sau khi ban là không chấp nhận được (người bị ban vẫn phá game tới khi hết hạn).

Đây cũng là ADR đầu tiên **tiêu thụ** quyết định của ADR-005 (Redis, ✅ Accepted 2026-07-19) — điều kiện tiên quyết để lựa chọn cần tra cứu phía server đã sẵn sàng.

## Decision

**Đề xuất: Session token tự phát hành (opaque, random) + lưu ở Redis (ADR-005), không dùng JWT.**

*(Đề xuất, chưa Accepted — AI không tự chốt Status, theo [adr/README.md Quy tắc #5](README.md).)*

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **JWT (self-contained, ký bằng khoá server)** | Stateless — không cần tra DB/cache mỗi request; chuẩn phổ biến, thư viện sẵn có | **Không thu hồi được cho tới khi hết hạn** — cách duy nhất để "ban ngay" là giữ một blocklist phía server tra mỗi request, lúc đó JWT mất hết lợi thế "stateless" mà vẫn cõng thêm độ phức tạp giải mã/verify chữ ký mỗi request | Không chọn |
| **Session token tự phát hành (UUID/random 256-bit) + Redis** | **Thu hồi tức thì** (xoá key Redis = mất hiệu lực ngay — ban, logout, đổi mật khẩu); đơn giản (chuỗi random, không cần thư viện JWT/JOSE, không cần quản lý khoá ký) | Cần tra Redis mỗi request có auth — nhưng game vốn đã cần Redis cho session/online-state (ADR-005), không phải chi phí phát sinh mới | **Ứng viên được chọn** |
| JWT + refresh token ngắn hạn (kiểu OAuth2) | Giảm bớt vấn đề thu hồi bằng cách refresh token ngắn | Vẫn còn cửa sổ token JWT truy cập sống sau khi ban (dù ngắn hơn); thêm hẳn một tầng phức tạp (2 loại token, luồng refresh) chỉ để né vấn đề mà session token giải quyết triệt để và đơn giản hơn | Không chọn |
| Session token + PostgreSQL (không Redis) | Bền hơn (không mất khi Redis restart) | Mỗi request auth phải query DB — DB không thiết kế cho tải đọc mỗi request ở tần suất này (đúng vấn đề Redis được đưa vào để giải quyết, xem ADR-005) | Không chọn |

## Consequences

**Tích cực:**
- Ban/logout cưỡng chế có hiệu lực ngay lập tức (`DEL session:<token>` ở Redis) — không có cửa sổ trễ.
- Không cần quản lý khoá ký/verify JWT, không cần thư viện JOSE — bề mặt tấn công nhỏ hơn (không có lớp giải mã token phía server nào có thể bị lợi dụng).
- Nhất quán với ADR-005: Redis vốn đã cần cho session/online-state — token tự phát hành tận dụng thẳng hạ tầng đó, không thêm thành phần mới.

**Đánh đổi phải chấp nhận:**
- Không có Redis khả dụng thì không xác thực được request nào (đây vốn đã là ràng buộc đã ghi ở ADR-005: mất Redis = mất session, không mất dữ liệu — chấp nhận được, nhưng nghĩa là auth có single point of failure vận hành, cần Redis HA ở production).
- Mỗi request cần auth tốn 1 round-trip Redis — chấp nhận được ở quy mô hiện tại, cần đo lại nếu tải auth cao bất thường (ví dụ HTTP API riêng cho web/launcher, không chỉ qua Gateway socket).

**Chi tiết còn để ngỏ, không thuộc phạm vi quyết định kiến trúc của ADR này (tham số triển khai, chỉnh được ở Phase 3 mà không cần ADR mới):**
- Thời hạn (TTL) mặc định: đề xuất khởi điểm **sliding TTL 24h**, tự gia hạn mỗi request đã xác thực thành công (không cần refresh token riêng, vì token có thể thu hồi tức thì nên không cần giới hạn thời gian sống ngắn để giảm rủi ro lộ token — rủi ro đó đã được xử lý bằng khả năng thu hồi).
- Độ dài/entropy: tối thiểu 256-bit random từ `SecureRandom`, encode base64url — đủ để chống đoán/brute-force.
- Không tái sử dụng cơ chế này cho login ban đầu (username/password → token) — đó vẫn qua TLS + Argon2id/bcrypt như SECURITY.md §3 đã Accepted, không đổi.

**Module bị ảnh hưởng:** `feature-account` (phát hành/thu hồi token), `server-gateway` (verify token ở `ConnectRequest`), `server-database` (Redis adapter, theo ADR-005).

## Khuyến nghị (chờ chủ dự án quyết)

**Đề xuất: Session token tự phát hành + Redis**, đúng hướng SECURITY.md §3 đã gợi ý sẵn. Lý do cốt lõi: MMORPG có kinh tế trong game bắt buộc phải ban được ngay, JWT không đáp ứng được yêu cầu này mà không tự phá vỡ lợi thế "stateless" của chính nó.

**Cần chủ dự án trả lời:**
1. Đồng ý đánh đổi "1 round-trip Redis mỗi request auth" để lấy khả năng ban tức thì không? (Khuyến nghị: có — chi phí thấp, lợi ích vận hành cao cho game PvP.)
2. TTL 24h sliding có hợp lý không, hay cần chính sách khác (ví dụ TTL ngắn hơn cho thiết bị chưa "tin cậy")? — có thể quyết định riêng sau, không chặn Accept ADR này vì đây là tham số, không phải kiến trúc.

## Revisit Trigger

- Dự án cần hỗ trợ auth cho hệ thống bên thứ ba (SSO, OAuth provider ngoài) → JWT (hoặc token chuẩn OIDC) có thể trở nên cần thiết cho **luồng đó riêng**, không nhất thiết thay thế session token cho gameplay.
- Tải auth vượt khả năng Redis single-instance dù đã có HA → đánh giá lại round-trip cost, cân nhắc cache token ở tầng gateway (ngắn hạn, vẫn tôn trọng thu hồi).
- Không có — nếu không có 2 trigger trên xảy ra, coi quyết định này ổn định lâu dài.
