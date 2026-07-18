# server-features/

Các module nghiệp vụ (Clean Architecture). **Chưa scaffold module nào ở Phase 0** — cố ý (Điều 9 / YAGNI).

Mỗi feature được tạo **từ template** khi tới phase của nó, không dựng 11 module rỗng trước:

| Feature | Tạo ở Phase |
|---|---|
| feature-account, feature-character | 3 |
| feature-inventory, feature-shop | 5 |
| feature-quest | 6 |
| feature-guild, feature-mail, feature-friend, feature-chat | 6–7 |
| feature-auction, feature-ranking | 8 |

**Cách tạo:** copy `docs/templates/feature-module/` → đổi `Inventory` thành tên feature, thêm `include(...)` vào `settings.gradle.kts`, thêm dòng vào bảng [REPOSITORY-STRUCTURE §2](../../docs/01-architecture/REPOSITORY-STRUCTURE.md). Cấu trúc bắt buộc: `api → application → domain ← infrastructure`.
