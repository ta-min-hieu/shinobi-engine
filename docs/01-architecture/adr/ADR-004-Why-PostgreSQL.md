# ADR-004: Chọn PostgreSQL làm RDBMS chính, không dùng ORM

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 15, 25](../../00-governance/KAGE-CONSTITUTION.md), [DATABASE.md](../../05-server/DATABASE.md) |

---

## Context

Cần RDBMS làm **source of truth** cho account, character, inventory, guild. Yêu cầu:

- **ACID nghiêm ngặt** — sai lệch ở đây = mất item/gold thật của người chơi.
- Kiểu dữ liệu phong phú (JSON cho `progress_json`, `base_stats_json`).
- License permissive (Hiến pháp để ngỏ khả năng Open Source).
- Tồn tại được 10 năm.

Quyết định này gồm **hai phần**: chọn RDBMS, và chọn cách truy cập nó.

## Decision

1. Dùng **PostgreSQL 16/17** làm RDBMS chính.
2. **Không dùng ORM (Hibernate/JPA).** Truy cập DB bằng SQL tường minh qua JDBC + HikariCP, đặt sau Port trong `server-database`.

## Alternatives Considered

### Phần 1 — RDBMS

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **PostgreSQL** | JSONB query được hiệu quả (hợp `progress_json`); ACID mạnh; PostgreSQL License (permissive, không rủi ro đổi license); partition/index phong phú; cộng đồng lớn | Vận hành tối ưu sâu phức tạp hơn MySQL | **Chọn** |
| MySQL / MariaDB | Phổ biến, nhiều tài liệu tiếng Việt | JSON support yếu hơn; lịch sử ownership qua Oracle gây lo ngại (MariaDB fork chính vì lý do này) | Không chọn |
| MongoDB | Schema linh hoạt, scale ngang dễ | **Không ACID mạnh cho dữ liệu tài chính game**; không hợp Clean/Hexagonal đã chọn (Điều 25) | Không chọn cho dữ liệu core |
| SQLite | Đơn giản, không cần server | Không hợp nhiều kết nối đồng thời | Không chọn — có thể dùng cho tool local |

### Phần 2 — Cách truy cập

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **JDBC + SQL tường minh** | Thấy đúng SQL chạy thật; không proxy động; không lazy loading bất ngờ; kiểm soát hoàn toàn hiệu năng | Viết mapping thủ công (bù bằng Record + text block) | **Chọn** |
| Hibernate / JPA | Ít code mapping | **Proxy động + reflection** vi phạm Điều 31; lazy loading gây N+1 khó phát hiện; ORM che mất SQL thật → không tối ưu được; entity mutable phá Điều 43 (Immutable) | Không chọn |
| jOOQ | Type-safe SQL, không che SQL | Thêm codegen; license thương mại cho một số DB (PostgreSQL thì free) | **Không chọn ở MVP** — cân nhắc lại nếu mapping thủ công quá cồng kềnh (ADR mới) |
| Spring Data | Repository tự sinh | Kéo theo Spring — đã từ chối ở [ADR-009](ADR-009-Why-No-Spring-Boot.md) | Không chọn |

## Consequences

**Tích cực:**
- Nhìn code là biết SQL nào chạy → tối ưu index/query có cơ sở (Điều 30).
- Không có "magic" (Điều 12).
- JSONB cho phép lưu dữ liệu bán cấu trúc (quest progress) mà vẫn query được.

**Đánh đổi phải chấp nhận:**
- **Phải viết mapping row → object thủ công.** Chấp nhận có ý thức: rõ ràng > ngắn gọn (Điều 17).
- Đổi schema phải sửa SQL ở nhiều chỗ → bù bằng việc cô lập toàn bộ SQL trong `infrastructure/` của từng feature.
- Không có second-level cache tự động → cache là quyết định tường minh qua Redis (Điều 12).

**Module bị ảnh hưởng:** `server-database`, `<feature>/infrastructure/`.

⚠️ **Bắt buộc**: tạo database với **UTF-8 collation** ngay từ đầu (tiếng Việt có dấu). Sai sót này rất khó sửa sau khi có dữ liệu thật.

## Revisit Trigger

- **RDBMS**: không revisit. Cần scale ghi vượt 1 primary → **sharding**, không đổi RDBMS.
- **Không-ORM**: nếu mapping thủ công chiếm >30% code của `infrastructure/` và gây bug lặp lại → mở ADR đánh giá jOOQ (không phải Hibernate).
