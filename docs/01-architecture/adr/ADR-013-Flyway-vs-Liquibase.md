# ADR-013: Dùng Flyway làm công cụ migration DB

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-19 (Proposed) — chốt Accepted 2026-07-19 |
| Deciders | Chủ dự án |
| Related | [ADR-004](ADR-004-Why-PostgreSQL.md), [ADR-005](ADR-005-Why-Redis.md), [DATABASE.md §10](../../05-server/DATABASE.md), [TECHNOLOGY-STACK.md §5](../TECHNOLOGY-STACK.md), Điều 40 |

---

## Context

`server-database` cần một công cụ quản lý migration schema PostgreSQL có version + rollback (Điều 40, [DATABASE.md §10](../../05-server/DATABASE.md)): mọi thay đổi schema đi qua script có version, không sửa DB bằng tay, migration phải có rollback plan (script hoặc kế hoạch tường minh), không phá client cũ (cột NOT NULL mới → nullable trước, backfill, rồi mới bắt buộc).

ADR-004 đã chốt: **không dùng ORM**, truy cập DB bằng SQL tường minh qua JDBC + HikariCP. Công cụ migration vì vậy nên làm việc trực tiếp với SQL thuần, không kéo theo một lớp mapping/abstraction đối lập với triết lý đó.

**Vấn đề khiến ADR này chưa chốt được — phát hiện mới khi khảo sát thực tế (2026-07-19):**

Liquibase, kể từ bản **5.0 (phát hành 2025)**, đổi license Community từ Apache-2.0 sang **Functional Source License (FSL-1.1-ALv2)**. FSL **không được OSI công nhận là open source** ("source available"/"fair source", tự động chuyển về Apache-2.0 sau 2 năm kể từ mỗi bản phát hành). Đây là **cùng dạng rủi ro license mà Redis từng gặp năm 2024** khi đổi sang SSPL (xem [ADR-005](ADR-005-Why-Redis.md) — dù Redis sau đó đã đảo ngược, quay lại AGPLv3 OSI-approved từ bản 8/2025; Liquibase/FSL chưa có dấu hiệu đảo ngược tương tự) — Hiến pháp để ngỏ khả năng dự án chuyển Open Source, và một dependency FSL ở lớp hạ tầng lõi (`server-database`) có thể lặp lại đúng vấn đề đó.

Đồng thời, Flyway (Redgate) từ tháng 5/2025 đã dừng bán tier Teams cho khách mới — **rollback tự động (`undo` migration) giờ chỉ có ở bản Enterprise trả phí**; bản Community (Apache-2.0, miễn phí vĩnh viễn) không có rollback tự động, phải tự viết migration "undo" bằng tay.

## Decision

Dùng **Flyway (Community edition)** cho migration schema PostgreSQL ở `server-database`.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Flyway Community** | License **Apache-2.0**, OSI-approved, miễn phí vĩnh viễn; migration là file `.sql` thuần đánh version (`V1__create_account.sql`) — khớp triết lý "SQL tường minh, không ORM" của ADR-004 gần như hoàn hảo; đơn giản, ít khái niệm phải học | Không có rollback tự động ở bản Community (undo migration là tính năng Enterprise từ 5/2025) — phải tự viết SQL rollback thủ công | **Ứng viên A** |
| **Liquibase Community (5.0+)** | Hỗ trợ nhiều định dạng changelog (SQL/XML/YAML/JSON); có cơ chế `rollback` cho changeset (dù nhiều tính năng nâng cao đã chuyển sang tier trả phí); công cụ diff schema mạnh | **License FSL — không phải OSI open source** kể từ 5.0 (cùng dạng rủi ro Redis từng gặp ở ADR-005, nhưng chưa đảo ngược như Redis); định dạng changelog XML/YAML khuyến khích trừu tượng hoá SQL — lệch triết lý "SQL tường minh" của ADR-004; phức tạp hơn Flyway cho nhu cầu hiện tại | **Ứng viên B** |
| Liquibase bản cũ (4.x, ghim version, Apache-2.0) | Né được FSL, giữ license an toàn | Không nhận bản vá bảo mật/tính năng mới lâu dài — tự đẩy dự án vào "mắc kẹt" ở một bản cũ, rủi ro kỹ thuật nợ dồn | Không chọn |
| Tự viết script migration (SQL thuần + bảng `schema_version` tự quản) | Không phụ thuộc thư viện ngoài | Tự làm lại đúng thứ Flyway đã làm tốt (order, checksum, idempotent apply) — vi phạm nguyên tắc không phát minh lại bánh xe cho hạ tầng không phải core-gameplay | Không chọn |

## Consequences

**Tích cực:**
- Migration file `.sql` thuần → không cần học thêm ngôn ngữ changelog riêng, review code dễ (đúng là SQL, đọc được ngay).
- License Apache-2.0 miễn phí vĩnh viễn, không phụ thuộc quyết định hosting/Open Source tương lai.

**Đánh đổi phải chấp nhận:**
- Không có rollback tự động — mọi thay đổi schema **nguy hiểm** (xoá cột, đổi kiểu dữ liệu) bắt buộc phải có migration "undo" viết tay đi kèm trong cùng PR, hoặc dùng chiến lược an toàn có sẵn ở DATABASE.md §10 (thêm cột nullable → backfill → mới bắt buộc NOT NULL) để tránh cần rollback thật sự.
- Nếu sau này cần tính năng nâng cao (schema diff, đa nguồn DB) sẽ phải cân nhắc lại — nhưng dự án chỉ dùng PostgreSQL (ADR-004), nên rủi ro này thấp.

**Module bị ảnh hưởng:** `server-database` (migration scripts thật nằm ở đây, theo DATABASE.md §10).

## Lý do chốt Flyway (không phải Liquibase)

Chủ dự án xác nhận 2026-07-19: dự án cá nhân, chắc chắn chỉ một mình chủ dự án dùng, ưu tiên phần mềm **miễn phí sử dụng** hơn là thuần khiết OSI-approved. Cả Flyway Community lẫn Liquibase Community (FSL) đều **miễn phí để tự cài/tự dùng cho một dự án cá nhân** — FSL chỉ hạn chế việc đem chính phần mềm đó thương mại hoá cạnh tranh lại, không hạn chế gì việc tự dùng. Vì vậy lý do "an toàn license" nêu ở mục Context/Alternatives không còn là yếu tố quyết định — tiêu chí chốt Flyway trở thành thuần kỹ thuật: mô hình "file SQL đánh version" khớp tự nhiên với quyết định "không ORM, SQL tường minh" đã Accepted ở ADR-004, không cần học thêm ngôn ngữ changelog XML/YAML của Liquibase, và với một dev đơn lẻ thì càng ít khái niệm phải nhớ càng tốt. Việc thiếu rollback tự động không phải bất lợi lớn trong thực tế — DATABASE.md §10 đã yêu cầu quy trình an toàn (nullable→backfill→NOT NULL) khiến rollback tự động ít khi thực sự cần tới; khi cần, viết migration "undo" thủ công là chi phí chấp nhận được.

## Revisit Trigger

- Flyway đổi license Community sang non-OSI (giống Liquibase 5.0) → xem lại toàn bộ ADR này.
- Liquibase quay lại Apache-2.0 hoàn toàn (không giữ khoảng trễ FSL 2 năm) → cân nhắc lại nếu rollback tự động trở thành nhu cầu thật sự cấp thiết.
- Số lượng migration "undo" viết tay gây lỗi thật (đã xảy ra sự cố production do rollback thủ công sai) → đánh giá lại, có thể trả phí Flyway Enterprise hoặc chuyển Liquibase.
