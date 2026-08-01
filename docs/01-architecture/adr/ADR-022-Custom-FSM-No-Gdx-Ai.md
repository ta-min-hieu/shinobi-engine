# ADR-022: Tự viết FSM cho AI quái, không dùng gdx-ai — pathfinding hoãn tới khi thật sự cần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-08-01 |
| Deciders | AI (Claude) đề xuất; chờ chủ dự án chốt |
| Related | [AI.md §5-§6](../../03-engine/AI.md), [ADR-006](ADR-006-Why-ECS.md) (Ashley, cùng lý do "bảo trì chậm"), [engine/CLAUDE.md](../../../engine/CLAUDE.md) ("AI quái: FSM đơn giản, không GOAP, không A* ở MVP") |

---

## Context

`AI.md §5` đã tự đề xuất "KHÔNG dùng gdx-ai cho FSM" và ghi rõ "→ Cần ADR-022" — nhưng ADR chưa từng
được viết chính thức, dù `engine/CLAUDE.md` (thẻ luật vùng, auto-load khi sửa `engine/**`) đã ghi sẵn
kết luận "AI quái: FSM đơn giản, **không** GOAP, **không** A* ở MVP" như một luật đang thực thi.

**Xác nhận bằng code thật** (không suy đoán): `grep` toàn bộ `*.gradle.kts` cho `gdx-ai` → không có
kết quả nào — thư viện chưa từng được thêm vào `libs.versions.toml`/bất kỳ module nào.
`server/server-world/AiSystem.java` (code THẬT đang chạy qua Phase 4-9) tự viết FSM bằng
`switch`/`enum` state thuần Java (IDLE/PATROL/CHASE/RETURN, khớp đúng mô tả AI.md §3), không có
thư viện AI nào được import. Không có pathfinding nào tồn tại trong codebase (`grep` "pathfind"/
"AStar" toàn repo → rỗng) — quái đuổi mục tiêu bằng vector thẳng tới target (`tickChase`), không có
né chướng ngại vật.

Đây là tình huống giống hệt ADR-011 (ArchUnit) và ADR-006 (Ashley "bảo trì chậm" — cùng lý do được
dùng để loại gdx-ai ở đây): quyết định đã chạy thật trong code, chỉ còn thiếu văn bản ghi lại.

## Decision

**Không dùng `gdx-ai`.** FSM AI quái tiếp tục tự viết bằng `enum`/`switch` thuần Java trong `System`
(đúng ranh giới ECS — logic ở System, không phải component/thư viện ngoài). Pathfinding (A*) **chưa
cần** — quái hiện chase bằng vector thẳng, chưa có bằng chứng cần né chướng ngại vật (chưa có map/
tường thật, cùng nguyên nhân gốc với [ADR-021](ADR-021-Box2D-vs-AABB.md)). Khi pathfinding thật sự
cần, đánh giá lại `gdx-ai` cho riêng phần đó ở ADR/Revisit Trigger riêng — không quyết trước.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Tự viết FSM bằng `enum`/`switch`, không thư viện ngoài** | 0 dependency; FSM quái đơn giản (< 100 dòng, đúng ước tính AI.md §5); đã chạy thật ổn định qua Phase 4-9 (dungeon, boss AI, 5 hệ phái); toàn quyền kiểm soát thứ tự/hiệu năng trong tick — không phụ thuộc lifecycle của thư viện ngoài | Phải tự viết lại nếu sau này cần Behavior Tree phức tạp hơn (chưa có nhu cầu) | **Chọn** — khớp dữ liệu thật, đúng đề xuất gốc AI.md §5, tiết kiệm 1 dependency cho vấn đề đã giải quyết tốt bằng code thuần |
| **B. `gdx-ai` cho FSM** | API sẵn có, khỏi tự viết | AI.md §5 tự ghi nhận: thư viện **bảo trì rất chậm** (chậm hơn cả Ashley — ADR-006 đã chấp nhận rủi ro tương tự có điều kiện, không nên nhân đôi rủi ro bảo trì-chậm cho 1 dependency nữa nếu tránh được); FSM thực tế đã tự viết xong, đổi sang gdx-ai bây giờ là refactor không cần thiết cho hệ thống đang chạy tốt | **Không chọn** — không có lý do kỹ thuật để đổi thứ đang hoạt động |
| **C. `gdx-ai` chỉ cho A* pathfinding (không cho FSM)** | Giải đúng phần phức tạp hơn (pathfinding) nếu cần, để lại FSM tự viết cho phần đơn giản | **Chưa có pathfinding nào được dùng** — quái hiện chase thẳng, không né vật cản (vì chưa có map/tường thật, cùng gốc rễ với ADR-021); thêm dependency cho tính năng chưa tồn tại là xây trước khi biết cần (Điều 30) | **Không chọn ngay** — đúng ứng viên cho lúc pathfinding thật sự cần (map có chướng ngại vật), không quyết trước |

## Consequences

**Tích cực:**
- 0 dependency mới, khớp Điều 9 và đúng đề xuất gốc trong AI.md.
- Gỡ nợ tài liệu: AI.md §5 không còn "→ Cần ADR-022" treo, `adr/README.md` không còn liệt ADR-022 là
  "chưa viết" dù code đã chạy thật từ lâu.

**Đánh đổi phải chấp nhận:**
- Nếu sau này cần Behavior Tree phức tạp hơn FSM (nhiều điều kiện lồng nhau), phải tự viết thêm hoặc
  xem lại quyết định này — chấp nhận được, chưa có bằng chứng cần ngay.
- Pathfinding vẫn là câu hỏi mở, không phải "đã trả lời bằng Phương án A" — ADR này chỉ trả lời phần
  FSM, không đóng luôn câu hỏi A*/gdx-ai-cho-pathfinding.

**Module bị ảnh hưởng:**
- Không module code nào đổi — quyết định này giữ nguyên hành vi hiện tại (`AiSystem` không đổi).
- `docs/03-engine/AI.md` §5 nên cập nhật link trỏ về ADR này thay vì "→ Cần ADR-022" (việc tài liệu
  nhỏ, có thể làm cùng lúc Accept).

## Revisit Trigger

- **Khi map thật có chướng ngại vật** (cùng Revisit Trigger với [ADR-021](ADR-021-Box2D-vs-AABB.md))
  **và** quái cần né vật cản khi chase → đánh giá `gdx-ai` A* (Phương án C) hoặc thuật toán tự viết,
  dựa trên độ phức tạp map thật lúc đó.
- **Nếu FSM quái cần nhiều hơn ~5-6 state với điều kiện chuyển trạng thái lồng nhau phức tạp** (dấu
  hiệu cụ thể: code FSM trong `AiSystem` khó đọc/khó thêm state mới an toàn) → cân nhắc Behavior Tree,
  tự viết hoặc thư viện.
- Không có trigger nào khác — nếu không rơi vào 2 trường hợp trên, tự viết FSM (Phương án A) coi là ổn
  định lâu dài.
