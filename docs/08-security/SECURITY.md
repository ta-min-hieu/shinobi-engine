# SECURITY.md

**Bảo mật và chống gian lận**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 42–43](../00-governance/KAGE-CONSTITUTION.md#chương-ix--nguyên-tắc-bảo-mật) |

> Điều 42: **Security không phải tính năng. Security là yêu cầu mặc định.**
> Điều 43: **Mọi dữ liệu từ client là không đáng tin cho tới khi server validate.**

---

## 1. Mô hình đe doạ

Ai tấn công, tấn công cái gì:

| Kẻ tấn công | Muốn gì | Cách |
|---|---|---|
| **Người chơi gian lận** | Mạnh hơn, giàu hơn | Sửa client, packet giả, bot |
| **Gold seller** | Kiếm tiền thật | Bot cày, duplicate item, spam quảng cáo |
| **Kẻ trộm tài khoản** | Bán tài khoản | Brute-force, phishing, credential stuffing |
| **Kẻ phá hoại** | Làm sập server | Flood packet, exploit |
| **Kẻ tấn công hạ tầng** | Dữ liệu / kiểm soát | SQL injection, thoát script sandbox |

⚠️ **Kẻ tấn công phổ biến nhất là người chơi của chính bạn.** Không phải hacker chuyên nghiệp — mà là người chơi tải tool về dùng.

---

## 2. Nguyên tắc nền

| Nguyên tắc | Nghĩa là |
|---|---|
| **Không tin client** | Điều 43 — mọi Command validate lại ở server |
| **Server Authority** | Điều 15 — client gửi ý định, server quyết kết quả |
| **Defense in depth** | Nhiều lớp; một lớp thủng không mất tất cả |
| **Fail secure** | Nghi ngờ → từ chối, không "cho qua cho chắc" |
| **Không tự viết crypto** | Dùng chuẩn |
| **Audit mọi thứ có giá trị** | Không log = không điều tra được |

---

## 3. Authentication

| Hạng mục | Quyết định |
|---|---|
| Mật khẩu lưu | **Argon2id** (ưu tiên) hoặc **bcrypt**. ❌ **Không bao giờ**: MD5, SHA1, SHA256 thuần, plaintext |
| Salt | Tự động (Argon2/bcrypt lo) |
| Truyền mật khẩu | Qua **TLS**. Không hash ở client (hash ở client = hash **trở thành** mật khẩu) |
| Brute-force | Rate limit + khoá tạm sau N lần sai + captcha (nếu cần) |
| 2FA | ⏳ Post-MVP — nhưng schema `account` nên chừa chỗ |

⚠️ **Không hash mật khẩu ở client rồi gửi hash.** Nhiều người tưởng an toàn hơn — thực ra hash đó trở thành mật khẩu, ai chặn được là đăng nhập được. TLS đã lo việc bảo vệ đường truyền.

### Token — ✅ chốt session token + Redis (ADR-017)

| | JWT | Session token tự phát hành |
|---|---|---|
| Ưu | Stateless, không cần tra DB | **Thu hồi được ngay** (ban user, đăng xuất) |
| Nhược | ⚠️ **Không thu hồi được** cho tới khi hết hạn | Cần tra Redis mỗi request |

**Chốt: session token + Redis, sliding TTL 24h.** Lý do: game cần **ban tức thì**. JWT không thu hồi được nghĩa là ban một người chơi rồi họ vẫn chơi tiếp tới khi token hết hạn. Chi tiết: [ADR-017](../01-architecture/adr/ADR-017-Auth-Token.md).

---

## 4. Authorization

| Kiểm tra | Ví dụ |
|---|---|
| Người chơi có sở hữu nhân vật này không? | Command gửi `characterId` — **server kiểm tra thuộc account của session** |
| Có quyền GM không? | GM command chỉ account có role GM |
| Có ở trong guild không? | Guild command |

⚠️ **Lỗ hổng kinh điển (IDOR)**: client gửi `characterId` trong packet, server tin luôn → người chơi sửa id = điều khiển nhân vật người khác.

→ **Không bao giờ lấy `characterId` từ packet.** Lấy từ **session**.

```java
// ❌ THẢM HOẠ
void handleMove(MoveCommand cmd) {
    var character = characters.get(cmd.characterId());   // ❌ client tự khai!
}

// ✅ Đúng
void handleMove(Session session, MoveCommand cmd) {
    var character = characters.get(session.characterId());  // ✅ từ session
}
```

---

## 5. Packet Validation

Ba lớp ([NETWORK.md §12](../05-server/NETWORK.md)):

| Lớp | Kiểm tra | Ví dụ |
|---|---|---|
| **Transport** | Kích thước packet, framing | Packet 10MB → drop |
| **Protocol** | Opcode hợp lệ, decode được, session hợp lệ | Opcode 9999 → drop |
| **Nghiệp vụ** | ⚠️ **Quan trọng nhất** | Đủ tiền? Ở gần NPC? Cooldown xong? |

⚠️ **Lớp 1–2 không thay thế lớp 3.** Packet đúng format hoàn hảo vẫn có thể là "mua item với giá âm".

**Validate mọi số**: âm? tràn? ngoài phạm vi?

```java
// ❌ quantity = -1 → "bán" item để nhận tiền?
// ✅ record compact constructor validate (CODING-STANDARDS.md §6)
public record BuyItemCommand(long itemId, int quantity) {
    public BuyItemCommand {
        if (quantity <= 0 || quantity > MAX_BUY_QUANTITY)
            throw new InvalidCommandException("quantity không hợp lệ: " + quantity);
    }
}
```

---

## 6. Replay Protection

Kẻ tấn công ghi lại packet hợp lệ rồi gửi lại nhiều lần.

| Cách chặn | Chi tiết |
|---|---|
| `sequenceId` tăng dần | Server từ chối sequenceId ≤ cái đã thấy |
| **Idempotency key** cho Command có side-effect | ✅ `idempotencyKey` client sinh + enforce ở DB trong transaction — [ADR-025](../01-architecture/adr/ADR-025-Idempotency.md) Accepted |
| TLS | Chặn replay từ ngoài (nhưng không chặn client tự replay) |

✅ **ADR-025 đã Accepted (2026-07-19).** Thiết kế đã có hướng đóng lỗ hổng duplicate — việc còn lại là triển khai `idempotencyKey` thật khi viết Command side-effect (`BuyItemCommand`, trade, mail-claim...) ở Phase 3, không phải thiết kế lại từ đầu.

---

## 7. Flood / Rate Limiting

| Mức | Giới hạn | Hành động |
|---|---|---|
| Per-connection, per-opcode | [NETWORK.md §13](../05-server/NETWORK.md) | Drop + counter |
| Per-account | Tổng Command/giây | Ngắt kết nối |
| Per-IP | Số kết nối đồng thời | ⚠️ Cẩn thận: NAT chung IP (quán net, nhà nhiều người) |
| Kết nối mới | Số connect/giây | Chống connect flood |

⚠️ **Rate limit theo IP dễ gây thiệt hại người dùng thật ở Việt Nam** — quán net, chung cư dùng chung IP. Ưu tiên rate limit theo **account/session**, dùng IP làm lớp cuối.

**Không xử lý ở tầng app**: DDoS thật (volumetric) — đó là việc của hạ tầng mạng/CDN.

---

## 8. Anti-Cheat

| Hack | Chặn bằng | Hiệu quả |
|---|---|---|
| **Damage hack** | Server tính damage ([GAMEPLAY.md §3](../05-server/GAMEPLAY.md)) | ✅ Chặn hoàn toàn |
| **Speed hack** | Server kiểm tra khoảng cách/tick | ✅ Chặn hoàn toàn |
| **Teleport** | Như trên | ✅ |
| **Đi xuyên tường** | Server chạy physics ([PHYSICS.md §3](../03-engine/PHYSICS.md)) | ✅ |
| **Cooldown hack** | Server giữ cooldown | ✅ |
| **Item duplicate** | Transaction + audit ([DATABASE.md §5](../05-server/DATABASE.md)) | ✅ nếu làm đúng |
| **Packet giả** | Validation (§5) | ✅ |
| **Bot cày** | ⚠️ **Khó** — hành vi hợp lệ về mặt kỹ thuật | ⚠️ Phát hiện thống kê |
| **Maphack** (thấy quái ẩn) | AoI — chỉ gửi thứ trong tầm nhìn ([SERVER.md §6](../05-server/SERVER.md)) | ✅ nếu AoI chặt |
| **Sửa client** | ❌ **Không chặn được** | Chấp nhận — server authority làm nó vô dụng |

**Nguyên tắc**: kiến trúc Server Authority (Điều 15) khiến **phần lớn hack trở nên vô nghĩa**. Không cần anti-cheat phía client (dễ bypass, xâm phạm quyền riêng tư).

⚠️ **Bot: trung thực về giới hạn.** Bot không "hack" gì cả — nó chơi đúng luật, chỉ là máy chơi. Phát hiện bot cần phân tích hành vi (pattern di chuyển quá đều, chơi 24/7, phản ứng quá nhanh). **Ngoài scope MVP.**

---

## 9. Audit Log

⚠️ **Bắt buộc** ([DATABASE.md §2.7](../05-server/DATABASE.md)).

| Ghi audit cho | Vì sao |
|---|---|
| Trade | Điều tra lừa đảo/duplicate |
| Gold thay đổi | Truy vết lạm phát |
| Item tạo/xoá | Truy vết duplicate |
| Cường hoá | Khiếu nại "tôi mất đồ" |
| Mail có item | Duplicate |
| GM command | ⚠️ **Ai cũng phải bị audit, kể cả GM** |
| Đăng nhập | Truy vết trộm tài khoản |

**Audit log không được xoá.** Nếu cần dọn → archive, không delete.

⚠️ **Không có audit = khi có sự cố, không thể trả lời "chuyện gì đã xảy ra"** → không thể hoàn trả cho người chơi, không thể ban đúng người.

---

## 10. Script Sandbox

Xem [SCRIPTING.md §4](../06-plugin/SCRIPTING.md).

⚠️ **Script là bề mặt tấn công nghiêm trọng nhất của hệ thống.** Script thoát sandbox = toàn quyền trên server.

**Điểm chết người**: expose object Java cho script → script đi tới `getClass().getClassLoader()` → thoát.

→ **API script chỉ nhận/trả dữ liệu thuần.**

---

## 11. SQL Injection

| ✅ | ❌ |
|---|---|
| `PreparedStatement` với `?` | Nối chuỗi SQL |

```java
// ❌ THẢM HOẠ
"SELECT * FROM character WHERE name = '" + name + "'"

// ✅
var stmt = conn.prepareStatement("SELECT * FROM character WHERE name = ?");
stmt.setString(1, name);
```

**Không dùng ORM** ([ADR-004](../01-architecture/adr/ADR-004-Why-PostgreSQL.md)) → **trách nhiệm này thuộc về ta**, không có framework che. → SpotBugs + code review (§2.8 [CODE-REVIEW.md](../02-coding-standards/CODE-REVIEW.md)) phải bắt.

---

## 12. Chat Security

| Rủi ro | Chặn |
|---|---|
| **XSS** | ⚠️ Client render chat — **không render HTML**, chỉ text thuần |
| Spam / quảng cáo gold | Rate limit + lọc từ khoá + báo cáo |
| Lừa đảo | Audit log chat báo cáo |
| Ký tự điều khiển / Unicode lạ | Sanitize input |

⚠️ **Tên nhân vật cũng là input người dùng** — validate: độ dài, ký tự cho phép, từ cấm, ký tự trông giống nhau (homoglyph: `Admin` vs `Аdmin` với А Cyrillic).

---

## 13. Secret Management

| ❌ Không bao giờ | ✅ |
|---|---|
| Secret trong repo | Env var / secret manager |
| Secret trong Docker image | Runtime injection ([DOCKER.md §4](../07-infrastructure/DOCKER.md)) |
| Secret trong log | Redact |
| Secret hardcode | Config |

⚠️ **Secret đã commit = đã lộ** → **rotate**, không chỉ xoá khỏi git.

Bật **secret scanning** ([OWASP.md](OWASP.md)).

---

## 14. Bảo mật hạ tầng

| Hạng mục | Quy tắc |
|---|---|
| Container | Không chạy root ([DOCKER.md §4](../07-infrastructure/DOCKER.md)) |
| DB | Không expose ra internet; chỉ server truy cập |
| Redis | ⚠️ **Redis mặc định không có auth** — phải bật password + không expose |
| TLS | Bắt buộc Production — cơ chế cụ thể [ADR-027](../01-architecture/adr/ADR-027-TLS-Transport-Security.md) ✅ Accepted, code xong 2026-07-31 |
| Dependency | Scan CVE ([OWASP.md](OWASP.md)) |

⚠️ **Redis expose ra internet không password là lỗ hổng cực kỳ phổ biến** — bot quét internet tìm Redis mở suốt ngày.

---

## 15. Xử lý sự cố

| Bước | Hành động |
|---|---|
| 1. Phát hiện | Metrics bất thường ([OBSERVABILITY.md §8](../07-infrastructure/OBSERVABILITY.md)) — VD `kage.gold.total` tăng vọt |
| 2. Chặn | Tắt tính năng bị exploit (Feature Flag — Điều 63) |
| 3. Điều tra | Audit log (§9) |
| 4. Khắc phục | Rollback item/gold; ban tài khoản |
| 5. Vá | Sửa lỗ hổng + test |
| 6. Hậu kiểm | Vì sao lọt? thêm gate gì? |

⚠️ **Feature Flag là công cụ ứng cứu quan trọng nhất** — tắt được tính năng bị exploit trong 1 phút thay vì chờ deploy 1 giờ.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **Argon2id/bcrypt** cho mật khẩu | MD5/SHA thuần = không an toàn | Accepted |
| 2 | **Không hash mật khẩu ở client** | Hash trở thành mật khẩu | Accepted |
| 3 | **`characterId` lấy từ SESSION, không từ packet** | Chống IDOR | Accepted — quan trọng nhất |
| 4 | **Session token + Redis**, không JWT | Game cần ban tức thì | ✅ Accepted — ADR-017 |
| 5 | Rate limit theo **account/session** trước, IP là lớp cuối | NAT chung IP ở VN | Accepted |
| 6 | **Không anti-cheat phía client** | Dễ bypass; Server Authority đã đủ | Accepted |
| 7 | **Chống bot: ngoài scope MVP** | Bot chơi đúng luật — khó về bản chất | Accepted — trung thực |
| 8 | **Audit log bắt buộc**, không xoá, GM cũng bị audit | Không có = không điều tra được | Accepted |
| 9 | Script API **chỉ dữ liệu thuần** | Object Java → thoát sandbox | Accepted |
| 10 | Chat render **text thuần**, không HTML | XSS | Accepted |
| 11 | **Secret đã commit → rotate** | Git history vĩnh viễn | Accepted |
| 12 | **Redis phải có password + không expose** | Lỗ hổng phổ biến | Accepted |
| 13 | **Idempotency key + enforce DB** cho Command side-effect | Đóng lỗ hổng replay/duplicate | ✅ Accepted — ADR-025 |

## ADR cần tạo

Không còn — ADR-017, ADR-025 đã Accepted 2026-07-19. Xem [adr/README.md](../01-architecture/adr/README.md).

## Tài liệu liên quan

- [OWASP.md](OWASP.md) — ⏳ stub: dependency scan, SAST/DAST
- [NETWORK.md](../05-server/NETWORK.md) — §5, §6, §7
- [DATABASE.md](../05-server/DATABASE.md) — audit log, transaction
- [SCRIPTING.md](../06-plugin/SCRIPTING.md) — §10
- [GAMEPLAY.md §12](../05-server/GAMEPLAY.md) — anti-cheat gameplay
- [CODE-REVIEW.md §2.8](../02-coding-standards/CODE-REVIEW.md) — checklist bảo mật

## Việc cần làm tiếp theo

1. Phase 3: triển khai `idempotencyKey` thật (ADR-025) khi viết Command side-effect đầu tiên (`BuyItemCommand` hoặc tương đương) — ADR đã Accepted, còn lại là code.
2. Phase 3: §4 (lấy characterId từ session) — làm sai từ đầu là lỗ hổng IDOR toàn hệ thống.
3. Phase 3: audit log (§9) **cùng lúc** với trade/gold, không để sau.
4. Phase 0: Redis password + không expose (§14).
