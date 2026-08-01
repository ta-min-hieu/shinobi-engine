# ADR-030: Không xây Plugin System — dùng module Gradle, xoá `engine-plugin-api`

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án chốt qua chat) |
| Date | 2026-08-01 |
| Deciders | Chủ dự án |
| Related | **Supersedes [ADR-007](ADR-007-Why-Plugin-System.md)** · [PLUGIN.md](../../06-plugin/PLUGIN.md) · [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md) · [ARCHITECTURE.md](../ARCHITECTURE.md) |

---

## Context

[ADR-007](ADR-007-Why-Plugin-System.md) (Proposed 2026-07-16) hoãn quyết định phạm vi Plugin System
tới "sau Phase 6 (MVP)", với Revisit Trigger ghi rõ: *"Nếu không có nhu cầu plugin rõ ràng → Superseded
bởi ADR 'không làm plugin system', dùng module Gradle. Đó là kết quả hợp lệ và không phải thất bại."*

Phase 6 (MVP) đã đóng từ 2026-07-23. Từ đó tới nay (Phase 7-9 + toàn bộ mảng art), **mọi tính năng lớn**
(Guild/Party/Trade, Dungeon/Boss AI/4 hệ phái còn lại/Arena, Quest/NPC/Dialogue/Friend/Mail/Chat,
Inventory/Equipment/Shop) đều viết thẳng vào `server-features/<feature>/` theo pattern Clean
Architecture đã có (xem `docs/templates/feature-module/`) — **không một tính năng nào** cần tới
`engine-plugin-api`. Module này tồn tại rỗng (chỉ có `build.gradle.kts`, không một dòng `src/main` nào)
từ Phase 0 tới nay.

ADR-007 tự đặt 2 câu hỏi cần chủ dự án trả lời trước khi chốt phạm vi (bên thứ ba hay chỉ nội bộ? có
cần hot-load không restart server?) — trả lời trực tiếp qua chat: **không làm plugin system**, không
cần giữ chỗ nữa.

## Decision

**Không xây Plugin System.** Tính năng lớn tiếp tục viết thẳng vào `server-features/<feature>/` (module
Gradle + Clean Architecture, đúng pattern đang dùng suốt 9 Phase). Xoá hẳn module rỗng
`engine-plugin-api` khỏi `settings.gradle.kts` và filesystem — không giữ chỗ cho một kiến trúc không có
bằng chứng cần tới sau 9 Phase quan sát thực tế (Điều 9 Simplicity, Điều 30 đo trước khi xây).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Không plugin system, xoá `engine-plugin-api`** ✅ | Khớp đúng 9 Phase dữ liệu thật (không tính năng nào cần); đỡ 1 module rỗng gây nhầm lẫn "đang dùng dở"; đúng Điều 9/30 | Nếu sau này thật sự cần plugin (bên thứ ba, hot-reload) phải tạo lại module + thiết kế API từ đầu | **Chọn** — đúng nhánh Revisit Trigger mà chính ADR-007 đã vạch sẵn cho kết quả "không cần" |
| **B. Giữ `engine-plugin-api` rỗng, chỉ đổi Status ADR-007** | Không tốn công dọn dẹp | Module rỗng tồn tại vô thời hạn không phục vụ gì, dễ khiến người/AI sau nhầm là "đang triển khai dở", vi phạm Điều 9 (không giữ thứ không phục vụ mục đích rõ ràng) | **Không chọn** — chủ dự án đã xác nhận muốn dọn luôn khi được hỏi |
| **C. Xây Plugin System đầy đủ ngay bây giờ** | Sẵn sàng cho bên thứ ba nếu sau này cần | Không có bằng chứng nhu cầu nào sau 9 Phase; đúng cái bẫy "framework phục vụ framework" mà chính ADR-007 đã cảnh báo | **Không chọn** — không có tín hiệu nhu cầu thật |

## Consequences

**Tích cực:**
- Gỡ nợ kiến trúc: không còn module rỗng tồn tại không rõ mục đích.
- `ARCHITECTURE.md`/`REPOSITORY-STRUCTURE.md`/`ENGINE.md`/`PLUGIN.md` phản ánh đúng trạng thái thật của
  codebase thay vì một kế hoạch chưa từng triển khai.
- Không tốn effort thiết kế API cho nhu cầu chưa từng xuất hiện.

**Đánh đổi phải chấp nhận:**
- Nếu tương lai thật sự cần plugin (bên thứ ba viết mở rộng, hot-reload không restart server), phải tạo
  lại module + thiết kế từ đầu — chấp nhận được, vì thiết kế lúc đó sẽ dựa trên nhu cầu **thật** thay vì
  đoán trước (đúng tinh thần Điều 30 mà cả ADR-007 lẫn ADR này đều tuân theo).
- `PLUGIN.md` cần một đợt rà soát riêng để phản ánh quyết định này (không làm trong ADR này — việc tài
  liệu lớn cần đề xuất bố cục trước theo CLAUDE.md §13); trước mắt chỉ cập nhật dòng trích ADR-007 đầu
  file để không dẫn sai.

**Module bị ảnh hưởng:**
- `engine/engine-plugin-api` (**xoá**) — khỏi `settings.gradle.kts` và filesystem.
- `docs/01-architecture/ARCHITECTURE.md`, `docs/01-architecture/REPOSITORY-STRUCTURE.md`,
  `docs/03-engine/ENGINE.md` — bỏ dòng/tham chiếu `engine-plugin-api`.

## Revisit Trigger

Có nhu cầu **thật** (không phải giả định) cần một trong hai: (a) bên thứ ba ngoài đội ngũ muốn viết mở
rộng cho engine, hoặc (b) một tính năng cụ thể cần nạp/gỡ code lúc runtime không restart server. Khi đó
tạo module mới + ADR riêng, thiết kế dựa trên nhu cầu cụ thể đã quan sát được, không phục hồi lại
`engine-plugin-api` cũ (đã xoá, không còn ý nghĩa tham chiếu).
