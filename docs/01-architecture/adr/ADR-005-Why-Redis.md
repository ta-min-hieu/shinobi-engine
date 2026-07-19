# ADR-005: Dùng Redis làm in-memory cache/session store

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-16 (Proposed) — chốt Accepted 2026-07-19 |
| Deciders | Chủ dự án |
| Related | [ADR-004](ADR-004-Why-PostgreSQL.md), [Hiến pháp Điều 15](../../00-governance/KAGE-CONSTITUTION.md), [DATABASE.md](../../05-server/DATABASE.md) |

---

## Context

Cần một in-memory store cho: session, trạng thái online, leaderboard tạm thời, rate-limit counter, pub/sub event giữa các instance.

**Lịch sử license Redis (lý do ADR này ban đầu Proposed, không chốt ngay được):** Redis đổi license tháng 3/2024 từ BSD sang **SSPL/RSAL (dual-license)** — SSPL không được OSI công nhận là mã nguồn mở, khiến cộng đồng tạo ra **Valkey** (fork từ Redis 7.2, bản BSD cuối cùng, do Linux Foundation bảo trợ, tương thích API/protocol). Đây là bối cảnh khiến bản đề xuất đầu tiên (2026-07-16) nghiêng về Valkey để né rủi ro license.

**⚠️ Cập nhật dữ kiện quan trọng (phát hiện 2026-07-19, xác minh qua WebSearch):** Redis Inc. đã **đảo ngược quyết định** — kể từ **Redis 8.0 (phát hành 1/5/2025)**, Redis quay lại open source dưới license **AGPLv3, được OSI chính thức công nhận**. Vấn đề license gốc khiến ADR này chưa chốt được **đã không còn tồn tại** kể từ bản Redis 8 — độc lập với việc dự án là cá nhân hay không.

(Bổ sung, không còn là lý do quyết định nhưng vẫn đúng: chủ dự án cũng xác nhận 2026-07-19 đây là dự án cá nhân, chắc chắn chỉ một mình dùng, không có kế hoạch Open Source/hosted cho bên thứ ba — nên kể cả nếu Redis vẫn còn ở SSPL, rủi ro cũng không áp dụng thực tế cho ngữ cảnh này.)

## Decision

Dùng **Redis** (bản 8.x trở lên, license AGPLv3) làm in-memory store cho session/trạng thái online/leaderboard tạm/rate-limit/pub-sub.

Kèm các ràng buộc bắt buộc:
- **Redis không bao giờ là nơi duy nhất chứa dữ liệu sở hữu của người chơi** (item, gold, exp). Mất Redis = mất session, **không** mất dữ liệu (Điều 15 + [ARCHITECTURE.md §15](../ARCHITECTURE.md)).
- Truy cập qua Port trong `server-database` → đổi implementation không đụng gameplay.
- Chỉ dùng **lệnh Redis chuẩn** (GET/SET/ZADD/PUBLISH...) — giữ khả năng đổi sang Valkey gần như miễn phí nếu cần (xem Revisit Trigger), dù lý do "cần đổi vì license" giờ không còn.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Redis 8.x (AGPLv3)** | **License AGPLv3 — OSI-approved**, hệ sinh thái lớn nhất, tài liệu nhiều nhất, tính năng mới nhất (vector set, JSON/Search/TimeSeries đã gộp vào core từ bản 8) | AGPLv3 có điều khoản "network copyleft" (nếu **phân phối lại** bản sửa đổi dưới dạng dịch vụ mạng, phải mở mã nguồn phần sửa đổi) — không ảnh hưởng gì việc tự host, tự dùng không phân phối lại | **Ứng viên được chọn** |
| **Valkey** | License BSD (không có điều khoản network copyleft); Linux Foundation bảo trợ; tương thích API/protocol Redis | Hệ sinh thái/tài liệu ít hơn Redis; không còn lý do license để ưu tiên hơn Redis kể từ Redis 8 | Không chọn — vẫn là lựa chọn dự phòng tốt nếu cần |
| Hazelcast / Ignite | Java-native, embedded được | Nặng hơn; API khác hẳn; cộng đồng nhỏ hơn cho use case này | Không chọn |
| Chỉ dùng PostgreSQL (bỏ cache) | Bớt 1 thành phần vận hành | Session/rate-limit/leaderboard ghi rất nhiều → giết DB; pub/sub của PostgreSQL yếu | Không chọn |
| Caffeine (in-process cache) | Không cần service riêng, cực nhanh | Không chia sẻ được giữa nhiều instance; mất khi restart | **Bổ sung**, không thay thế — dùng cho cache read-only local |

## Consequences

**Tích cực:**
- License AGPLv3 (OSI-approved) — nhất quán license mở xuyên suốt hạ tầng, không cần lo ngại gì kể cả nếu dự án đổi hướng Open Source sau này.
- Hệ sinh thái/tài liệu lớn nhất trong nhóm in-memory store tương thích Redis protocol — thuận lợi cho một dev đơn lẻ.

**Đánh đổi phải chấp nhận:**
- AGPLv3 có điều khoản network copyleft — nếu sau này **sửa đổi mã nguồn Redis rồi phân phối** dưới dạng dịch vụ, phần sửa đổi phải mở mã nguồn. Không ảnh hưởng cách dùng hiện tại (chạy Redis nguyên bản qua Docker image chính thức, không sửa mã nguồn Redis).
- Vì API tương thích, chi phí đổi Redis↔Valkey gần bằng 0 **nếu** giữ đúng ràng buộc "chỉ dùng lệnh chuẩn, đặt sau Port" — đây là ràng buộc bắt buộc, ghi nhận để giữ tuỳ chọn mở.

**Module bị ảnh hưởng:** `server-database`, mọi feature dùng cache/session.

## Revisit Trigger

- Dự án sửa đổi mã nguồn Redis rồi phân phối/host dưới dạng dịch vụ cho bên thứ ba (kích hoạt điều khoản network copyleft của AGPLv3) → đánh giá lại, cân nhắc Valkey (BSD, không network copyleft).
- Redis Inc. đổi license lần nữa theo hướng kém mở hơn AGPLv3 → đánh giá lại, Valkey là phương án dự phòng đã ràng buộc sẵn chi phí chuyển đổi thấp.
- Không có trigger nào khác — coi quyết định này ổn định lâu dài.
