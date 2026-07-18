# ADR-007: Xây dựng Plugin System cho Engine

| | |
|---|---|
| Status | ⏳ **Proposed — chốt phạm vi sau Phase 6 (MVP)** |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 4, 9, 27](../../00-governance/KAGE-CONSTITUTION.md), [PLUGIN.md](../../06-plugin/PLUGIN.md), [SCRIPTING.md](../../06-plugin/SCRIPTING.md) |

---

## Context

Hiến pháp Điều 4 đặt mục tiêu xây "nền tảng tái sử dụng để phát triển nhiều game khác nhau", và Điều 27 cấm hardcode nội dung (Quest/NPC/Boss/Festival).

Hai cơ chế khác nhau thường bị nhầm lẫn:

| | **Plugin** | **Script** |
|---|---|---|
| Viết bằng | Java (compile) | Ngôn ngữ script (Lua/JS) |
| Ai viết | Lập trình viên | Game designer |
| Nạp lúc | Khởi động server | Runtime, hot-reload |
| Dùng cho | Tính năng lớn (Dungeon system, Guild War) | Nội dung (quest cụ thể, dialogue NPC) |

ADR này về **Plugin**. Script là [ADR riêng](#) — xem [SCRIPTING.md](../../06-plugin/SCRIPTING.md).

**Căng thẳng cốt lõi cần giải quyết:** Plugin System là thứ dễ trở thành "framework phục vụ framework" — vi phạm trực tiếp Điều 10 (Business First) và Điều 9 (Simplicity). Rất nhiều dự án xây plugin system xong không bao giờ viết plugin thứ hai.

## Decision (đề xuất — chờ duyệt)

Xây Plugin System, nhưng **hoãn tới sau Phase 6 (MVP)**, và **giới hạn phạm vi tối thiểu**:

1. **Phase 0–6: KHÔNG xây plugin system.** Chỉ định nghĩa `engine-plugin-api` ở mức interface rỗng để giữ chỗ kiến trúc.
2. **Sau Phase 6** (khi đã có gameplay thật để biết plugin cần gì): xây plugin loader tối thiểu.
3. Plugin **chỉ dùng API công khai**, không sửa engine (Điều 21–22).
4. Plugin **không** có sandbox mạnh — plugin là Java, chạy cùng JVM, tin cậy ở mức "code của chính đội ngũ". Sandbox thật là việc của **Script** (Điều 27), không phải Plugin.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Hoãn tới sau Phase 6 (MVP), phạm vi tối thiểu** | Thiết kế API dựa trên nhu cầu **thật** đã quan sát được, không phải tưởng tượng; tuân thủ Điều 9 + YAGNI | Có thể phải refactor gameplay để tách thành plugin | **Đề xuất** |
| Xây plugin system đầy đủ ngay từ Phase 1 | Kiến trúc "sạch" từ đầu | Thiết kế API cho nhu cầu chưa biết → gần như chắc chắn sai; vi phạm Điều 9, 10; trì hoãn gameplay | Không chọn — đây là cái bẫy kinh điển của dự án engine |
| Không bao giờ có plugin, dùng module Gradle | Đơn giản nhất; không cần classloader isolation | Mâu thuẫn Điều 4; không cho phép bên thứ ba mở rộng; phải rebuild+redeploy cho mọi tính năng | Không chọn — nhưng **đây là fallback hợp lệ** nếu sau Phase 6 thấy không thực sự cần |
| Chỉ dùng Script, bỏ Plugin | Ít cơ chế hơn | Script không hợp cho tính năng lớn cần hiệu năng (Dungeon system) | Không chọn |
| OSGi | Chuẩn công nghiệp cho plugin Java | **Cực kỳ phức tạp**; nặng; vi phạm Điều 9 nghiêm trọng | Không chọn |

## Consequences

**Tích cực:**
- Không tốn công xây framework cho nhu cầu chưa tồn tại.
- `engine-plugin-api` giữ chỗ → thêm plugin sau không phá cấu trúc module.

**Đánh đổi phải chấp nhận:**
- Từ Phase 0–6, tính năng lớn viết thẳng vào `server-features/` → sau này muốn tách thành plugin phải refactor. **Chấp nhận có ý thức**: refactor code đã hiểu rõ rẻ hơn thiết kế API cho code chưa tồn tại.
- ⚠️ **Rủi ro**: "hoãn" dễ thành "không bao giờ làm". → Phải có mốc cụ thể trong [ROADMAP.md](../../11-process/ROADMAP.md), không để trôi.

**Module bị ảnh hưởng:** `engine-plugin-api` (giữ chỗ), `server-features/*` (có thể refactor sau).

## Câu hỏi cần chủ dự án trả lời

1. Plugin có dành cho **bên thứ ba** (cộng đồng viết plugin) hay chỉ **nội bộ đội ngũ**? → Quyết định mức độ sandbox/classloader isolation cần thiết. Nếu chỉ nội bộ, phạm vi giảm rất nhiều.
2. Có kịch bản thật nào cần **hot-load plugin không restart server** không? → Nếu không, bỏ hẳn classloader isolation, plugin chỉ là module nạp lúc khởi động.

## Revisit Trigger

- Sau Phase 6 (MVP): đánh giá lại dựa trên gameplay thật. Nếu không có nhu cầu plugin rõ ràng → **Superseded bởi ADR "không làm plugin system"**, dùng module Gradle. Đó là kết quả hợp lệ và không phải thất bại.
- Nếu có bên thứ ba thật sự muốn viết plugin → mở rộng phạm vi (sandbox, versioning, distribution).
