# ADR-005: Dùng Redis-compatible cache — chọn Redis hay Valkey?

| | |
|---|---|
| Status | ⏳ **Proposed — CHƯA CHỐT, đang chặn Phase 3** |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [ADR-004](ADR-004-Why-PostgreSQL.md), [Hiến pháp Điều 15](../../00-governance/KAGE-CONSTITUTION.md), [DATABASE.md](../../05-server/DATABASE.md) |

---

## Context

Cần một in-memory store cho: session, trạng thái online, leaderboard tạm thời, rate-limit counter, pub/sub event giữa các instance.

**Vấn đề khiến ADR này chưa chốt được:** Redis đã đổi license năm 2024 từ BSD sang **SSPL/RSAL (dual-license)**. SSPL không được OSI công nhận là mã nguồn mở. Hiến pháp ghi rõ dự án **có thể** chuyển sang MIT/Apache-2.0 nếu Open Source — nếu điều đó xảy ra, phụ thuộc Redis bản mới có thể gây vấn đề pháp lý/triết lý.

**Valkey** là fork của Redis 7.2 (bản BSD cuối cùng), do Linux Foundation bảo trợ, giữ license BSD, **tương thích API và protocol**.

## Decision

**Phần đã chốt:**
- Dùng một in-memory store **tương thích Redis protocol**.
- **Redis không bao giờ là nơi duy nhất chứa dữ liệu sở hữu của người chơi** (item, gold, exp). Mất Redis = mất session, **không** mất dữ liệu (Điều 15 + [ARCHITECTURE.md §15](../ARCHITECTURE.md)).
- Truy cập qua Port trong `server-database` → đổi implementation không đụng gameplay.

**Phần CHƯA chốt:** Redis hay Valkey.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Redis 7.x (license mới)** | Hệ sinh thái lớn nhất; tài liệu nhiều nhất; Redis Stack có module mở rộng | **License SSPL/RSAL** — không phải OSI open source; rủi ro nếu dự án Open Source hoặc bán dịch vụ hosted | Ứng viên A |
| **Valkey** | **License BSD** (an toàn tuyệt đối); Linux Foundation bảo trợ; tương thích API/protocol Redis → đổi qua lại gần như không tốn công; AWS/Google/Oracle hậu thuẫn | Trẻ hơn; hệ sinh thái công cụ quản trị ít hơn; ít tài liệu tiếng Việt | Ứng viên B |
| Hazelcast / Ignite | Java-native, embedded được | Nặng hơn; API khác hẳn; cộng đồng nhỏ hơn cho use case này | Không chọn |
| Chỉ dùng PostgreSQL (bỏ cache) | Bớt 1 thành phần vận hành | Session/rate-limit/leaderboard ghi rất nhiều → giết DB; pub/sub của PostgreSQL yếu | Không chọn |
| Caffeine (in-process cache) | Không cần service riêng, cực nhanh | Không chia sẻ được giữa nhiều instance; mất khi restart | **Bổ sung**, không thay thế — dùng cho cache read-only local |

## Consequences

**Vì API tương thích, chi phí đổi Redis↔Valkey gần bằng 0** *nếu* ta:
- Chỉ dùng **lệnh Redis chuẩn** (GET/SET/ZADD/PUBLISH...).
- **Không** dùng Redis Stack module độc quyền (RedisJSON, RediSearch, RedisTimeSeries).
- Đặt sau Port trong `server-database`.

→ **Đây là ràng buộc bắt buộc**, ghi nhận ngay cả khi chưa chốt bên nào.

**Module bị ảnh hưởng:** `server-database`, mọi feature dùng cache/session.

## Khuyến nghị (chờ chủ dự án quyết)

**Đề xuất: Valkey.**

Lý do: chi phí đổi gần bằng 0, nhưng loại bỏ hoàn toàn rủi ro license — trong khi Hiến pháp để ngỏ khả năng Open Source. Đánh đổi duy nhất (ít tài liệu hơn) không đáng kể vì API giống hệt Redis, tài liệu Redis dùng lại được.

**Cần chủ dự án trả lời**: dự án có ý định Open Source hoặc cung cấp dịch vụ hosted trong tương lai không? Nếu **không bao giờ** → Redis cũng an toàn.

## Revisit Trigger

- Chủ dự án chốt định hướng license của dự án → chốt ADR này ngay.
- Redis đổi lại về license OSI-approved → Redis trở thành lựa chọn mặc định.
- Nếu chọn Valkey và Valkey ngừng bảo trì → quay lại Redis (chi phí thấp nhờ ràng buộc ở mục Consequences).
