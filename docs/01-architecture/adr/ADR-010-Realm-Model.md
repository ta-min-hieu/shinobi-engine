# ADR-010: Single-realm ở MVP, giữ sẵn `realm_id` cho multi-realm

| | |
|---|---|
| Status | ✅ Accepted |
| Date | 2026-07-18 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 9, 19](../../00-governance/KAGE-CONSTITUTION.md), [DATABASE.md §2.1](../../05-server/DATABASE.md), [ARCHITECTURE.md §14](../ARCHITECTURE.md) |

---

## Context

Cần quyết định mô hình server **trước Phase 0** vì nó xác định có cột `realm_id` trong bảng `character` (và các bảng dữ liệu nhân vật) hay không. Thêm cột này **sau khi đã có dữ liệu người chơi thật** là migration đau đớn — phải backfill toàn bộ, đổi mọi khóa/index liên quan.

Căng thẳng: Điều 9 (Simplicity — đừng xây hạ tầng cho quy mô chưa tồn tại) vs Điều 19 (Scalability — giả định số người chơi sẽ tăng).

## Decision

**Single-realm ở MVP** (một server logic duy nhất), **nhưng schema có sẵn cột `realm_id`** (mặc định = 1) trong `character` và các bảng dữ liệu nhân vật cấp cao.

Nghĩa là: code Phase 0–8 chỉ giả định một realm (không routing, không chuyển realm), nhưng dữ liệu đã sẵn sàng để mở multi-realm sau này **không cần migration schema**.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Single-realm + `realm_id` dự phòng** | Code đơn giản như single-realm thuần (Điều 9); nhưng mở đường multi-realm với chi phí bây giờ ≈ 0 (chỉ 1 cột hằng số) | `realm_id` "thừa" trong suốt MVP | **Chọn** — mua bảo hiểm rẻ nhất cho Điều 19 |
| Single-realm thuần (không `realm_id`) | Gọn hơn đúng 1 cột | Đông người → mở server 2 = migration toàn bộ dữ liệu, đổi PK/index | Không chọn — tiết kiệm 1 cột không đáng rủi ro migration lớn |
| Multi-realm đầy đủ ngay | Sẵn sàng scale ngang từ đầu | Routing, chuyển realm, đồng bộ xuyên realm... — over-engineering khi chưa có 1 người chơi (vi phạm Điều 9); làm chậm MVP | Không chọn |

## Consequences

**Tích cực:**
- Code gameplay Phase 0–8 không phải nghĩ về multi-realm — đơn giản, đúng Điều 9.
- Mở multi-realm sau (nếu tải thật cần) chỉ là thêm logic routing, **không** migration schema.
- Khớp chiến lược scale tuần tự bước 0→5 ở [ARCHITECTURE.md §14](../ARCHITECTURE.md).

**Đánh đổi phải chấp nhận:**
- Mọi truy vấn nhân vật mang theo `realm_id` (mặc định 1) — chi phí không đáng kể.
- ⚠️ **Cạm bẫy cần tránh**: code MVP **không được hardcode "realm = 1"** rải rác. Đọc `realm_id` từ dữ liệu/config, để khi bật multi-realm không phải sửa khắp nơi. Đây là điều kiện để lời hứa "không migration" thành hiện thực.

**Module bị ảnh hưởng:** `server-database` (schema), `feature-character`, mọi bảng dữ liệu nhân vật.

## Revisit Trigger

- Khi tải thật (đo ở [Phase 9](../../11-process/ROADMAP.md)) cho thấy một realm không kham nổi số người chơi đồng thời → mở ADR "bật multi-realm", tận dụng `realm_id` đã có.
- Không revisit vì lý do "nghe nói game lớn nào cũng multi-realm".
