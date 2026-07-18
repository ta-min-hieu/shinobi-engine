# ADR-012: Package root `dev.kage`

| | |
|---|---|
| Status | ✅ Accepted |
| Date | 2026-07-18 |
| Deciders | Chủ dự án |
| Related | [REPOSITORY-STRUCTURE.md §5–6](../REPOSITORY-STRUCTURE.md), [Hiến pháp Điều 5](../../00-governance/KAGE-CONSTITUTION.md) |

---

## Context

Cần chốt package root **trước Phase 0** vì đổi package sau khi đã có code = sửa mọi `package`/`import` trên toàn bộ codebase — một trong những refactor tốn công và dễ sót nhất.

Lưu ý phân biệt: **package root** (định danh kỹ thuật, nội bộ) khác **tên thương hiệu game** (hiển thị cho người chơi). Tên thương hiệu có thể đổi sau thoải mái mà không đụng package.

## Decision

Package root: **`dev.kage`**.

Cấu trúc: `dev.kage.<module>` — ví dụ `dev.kage.inventory`, `dev.kage.engine.ecs`, `dev.kage.protocol`.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **`dev.kage`** | Ngắn, trung tính, đã dùng nhất quán trong toàn bộ tài liệu + template; `dev.*` là namespace hợp lệ không cần sở hữu domain | Không gắn với domain thương mại (nếu sau này cần) | **Chọn** — package là tên kỹ thuật nội bộ, không cần trùng branding |
| `com.<brand>.<game>` (theo domain sở hữu) | Chuẩn nếu publish thư viện công khai ra Maven Central | Phụ thuộc quyết định tên thương hiệu (chưa có); đổi khi rebrand | Không chọn — dự án chưa publish library công khai; buộc package theo branding chưa chốt là tự trói |
| `io.github.<user>` | Miễn phí, hợp OSS cá nhân | Gắn với username cá nhân, khó chuyển nếu đổi chủ | Không chọn |

## Consequences

**Tích cực:**
- Template trong `docs/templates/` (đang dùng `dev.kage`) copy vào là chạy, không phải sửa placeholder.
- Tên thương hiệu game đổi sau không đụng một dòng package nào.

**Đánh đổi phải chấp nhận:**
- Nếu **sau này publish một module ra Maven Central công khai** (VD: `kage-engine` như một engine tái sử dụng — mục tiêu ở Điều 4), có thể cần đổi groupId theo domain sở hữu. **Nhưng**: groupId Maven (metadata publish) **không bắt buộc** trùng package Java — có thể giữ `dev.kage` làm package và đặt groupId riêng khi publish. Nên đây không phải rào cản thật.

**Module bị ảnh hưởng:** Toàn bộ — mọi file `.java`.

## Revisit Trigger

Không có — coi là ổn định lâu dài. Rebrand game **không** kéo theo đổi package (đó chính là lý do tách hai khái niệm). Chỉ xem lại nếu quyết định publish engine ra Maven Central và có ràng buộc groupId cụ thể — kể cả khi đó, ưu tiên giữ package, đổi groupId.
