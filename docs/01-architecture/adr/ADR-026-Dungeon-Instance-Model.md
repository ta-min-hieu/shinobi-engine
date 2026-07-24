# ADR-026: Dungeon Instance Model

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-24 |
| Deciders | Chủ dự án |
| Related | [ARCHITECTURE.md §14](../ARCHITECTURE.md) (Scalability), [SERVER.md §3](../../05-server/SERVER.md) (Game Tick single-thread), [GAMEPLAY.md §6](../../05-server/GAMEPLAY.md) (Boss & Dungeon), [ROADMAP.md](../../11-process/ROADMAP.md) (Phase 8 gate)

---

## Context

GAMEPLAY.md §6 đánh dấu mô hình dungeon instance là "quyết định kiến trúc chưa giải quyết": mỗi
instance (mỗi party vào Bí Cảnh) là một "world" riêng trong **cùng process**, hay **process
riêng**? Quyết định này ảnh hưởng trực tiếp chiến lược scaling ở ARCHITECTURE.md §14 và là điều
kiện chặn mở Phase 8 (ROADMAP.md).

Trạng thái kiến trúc hiện tại (ARCHITECTURE.md §14, "Bước 0"): **1 process, 1 DB** — chưa tách
gateway/world (Bước 1), chưa có nhiều world instance (Bước 2). Điều 30 (đo trước, tối ưu/scale
sau) cấm nhảy sang bước scale N+1 khi chưa có số liệu chứng minh bước N đã hết dư địa — Phase 9
(Optimization) mới là Phase đầu tiên được phép tối ưu dựa trên đo đạc thật.

## Decision

Dungeon instance chạy **cùng process** với world chính, trong cùng JVM. Mỗi lần một party vào
dungeon, server tạo một **World/ECS instance riêng** (entity, state riêng biệt — không chia sẻ
với world chính hay dungeon instance khác), nhưng đây **không phải OS process riêng**. Không thêm
hạ tầng orchestration (process spawn/kill, service discovery, IPC) ở Phase 8.

Game tick vẫn giữ nguyên tắc single-thread cho mỗi World (SERVER.md §3) — dungeon World có thể
tick trên thread riêng của chính nó (không chia sẻ thread với world chính, tránh 1 dungeon tick
chậm làm chậm map thường), nhưng chi tiết threading cụ thể là việc triển khai, không phải nội dung
ADR này.

⚠️ **Cách ly lỗi bắt buộc**: exception trong 1 dungeon World phải được bắt (catch) ở ranh giới
World đó — không được lan lên làm sập world chính hay dungeon instance khác. Đây là điều kiện bắt
buộc khi code, không phải tuỳ chọn.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Cùng process, World riêng trong 1 JVM** | Khớp đúng Bước 0 hiện tại (ARCHITECTURE.md §14) — không nhảy cóc bước scale khi chưa có số liệu (Điều 30); không cần hạ tầng orchestration mới; đơn giản nhất để code và debug; tận dụng transaction/kết nối DB đã có sẵn trong process | Crash 1 dungeon World cần catch kỹ để không kéo sập world chính hoặc process; tất cả dungeon instance dùng chung tài nguyên JVM (heap, CPU) với world chính — giới hạn số instance đồng thời thấp hơn phương án tách process | **Ứng viên được chọn** |
| Process riêng cho mỗi dungeon instance | Cô lập lỗi tốt hơn (crash 1 dungeon không ảnh hưởng world chính); mỗi instance có tài nguyên JVM riêng | Nhảy 2 bước scale (Bước 1: tách gateway/world, Bước 2: nhiều world instance) khi chưa có số liệu tải thật chứng minh cần — vi phạm trực tiếp Điều 30; cần hạ tầng orchestration (spawn/kill process, discovery, đồng bộ DB/session giữa process) chưa tồn tại trong TECHNOLOGY-STACK.md hiện tại; KUBERNETES.md hiện đang là stub (ARCHITECTURE.md §14) — chưa có nền tảng để orchestrate | Không chọn — sớm hơn nhu cầu thật, thêm chi phí hạ tầng chưa cần |

## Consequences

**Tích cực:**
- Mở Phase 8 mà không cần xây hạ tầng orchestration process mới — giữ đúng tinh thần Điều 9 (đơn
  giản trước) và Điều 30 (đo trước khi scale).
- Nhất quán với chiến lược scale đã Accepted ở ARCHITECTURE.md §14 (Bước 0 → 1 → 2 tuần tự, không
  nhảy cóc).
- Tái sử dụng hạ tầng process hiện có (kết nối DB, Redis, session) — không cần đồng bộ dữ liệu
  xuyên process cho dungeon.

**Đánh đổi phải chấp nhận:**
- Số dungeon instance đồng thời bị giới hạn bởi tài nguyên của 1 JVM (heap, CPU) — nếu tải thật
  vượt ngưỡng, cần revisit (xem Revisit Trigger).
- Phải tự đảm bảo cách ly lỗi ở tầng code (try/catch quanh mỗi dungeon World tick) vì không có cách
  ly ở tầng OS process như phương án kia.
- Thiết kế World instance (tạo/huỷ theo vòng đời party vào/ra dungeon) là việc kỹ thuật mới cần làm
  trong Phase 8, chưa có sẵn pattern để tái dùng từ world chính (world chính hiện là singleton, xem
  code hiện tại trước khi triển khai).

**Module bị ảnh hưởng:** `server-world` (World factory cho dungeon instance, vòng đời tạo/huỷ theo
party), tương lai `feature-dungeon` mới.

## Lý do chốt

Chủ dự án xác nhận 2026-07-24 (qua `AskUserQuestion` trước khi code): chọn cùng process vì khớp
đúng giai đoạn scale hiện tại (Bước 0, ARCHITECTURE.md §14) và tránh vi phạm Điều 30 (không scale
khi chưa có số liệu tải thật). Phương án process riêng để dành cho khi có số liệu chứng minh 1 JVM
không kham nổi số dungeon instance cần thiết — lúc đó tự nhiên trùng với Bước 1/2 của chiến lược
scale đã có sẵn, không cần ADR riêng.

## Revisit Trigger

- Đo được (Phase 9 Optimization trở đi) 1 JVM không kham nổi số dungeon instance đồng thời cần
  thiết (tick p99 vượt ngân sách khi nhiều dungeon World chạy song song) → cân nhắc tách dungeon
  instance ra world instance riêng (trùng Bước 2 của ARCHITECTURE.md §14), không phải phát minh cơ
  chế mới.
- Phát hiện lỗi cách ly thực tế (1 dungeon World crash làm sập process/world chính) trong quá trình
  code Phase 8 → cần thiết kế lại cơ chế catch/cách ly trước khi tiếp tục, không phải lý do chuyển
  sang process riêng ngay.
