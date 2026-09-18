# GLOSSARY.md

**Thuật ngữ Việt–Anh dùng thống nhất toàn dự án**

| | |
|---|---|
| Version | 1.1.0 — thuật ngữ gameplay đổi theo theme tu tiên ([ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)) |
| Status | Draft |

> Đảm bảo mọi tài liệu, code, commit dùng **cùng một thuật ngữ** cho cùng khái niệm.
> **Không tự dịch tự do** — tra bảng này trước. Tên class/package trong code dùng đúng cột "EN".

---

## 1. Kiến trúc & Kỹ thuật

| Tiếng Việt | EN (dùng trong code/doc) | Ghi chú |
|---|---|---|
| Hiến pháp | Constitution | [KAGE-CONSTITUTION.md](00-governance/KAGE-CONSTITUTION.md) |
| Nguồn chân lý duy nhất | Single Source of Truth (SSOT) | Điều 1 |
| Ghi chú quyết định kiến trúc | Architecture Decision Record (ADR) | [ADR Index](01-architecture/adr/README.md) |
| Kiến trúc lai | Hybrid Architecture | Điều 25 |
| Cổng | Port | Interface ở boundary |
| Bộ chuyển đổi | Adapter | Impl cụ thể của Port |
| Facade công khai | Facade | `InventoryFacade` |
| Trường hợp sử dụng | Use Case | `AddItemUseCase` |
| Thực thể (ECS) | Entity | Chỉ ID |
| Thành phần | Component | Dữ liệu thuần |
| Hệ thống | System | Chứa logic |
| Nhóm (ECS) | Family | Bộ lọc entity |
| Bộ ánh xạ Component | ComponentMapper | — |
| Sự kiện | Event | Một chiều, quá khứ: `MonsterKilledEvent` |
| Lệnh | Command | Client→Server, cần Response |
| Phản hồi | Response | Trả lời 1 Command |
| Mã lỗi | Error Code / errorKey | [NETWORK.md §7](05-server/NETWORK.md) |
| Cờ tính năng | Feature Flag | Điều mục Feature Flag |
| Khả năng quan sát | Observability | [OBSERVABILITY.md](07-infrastructure/OBSERVABILITY.md) |
| Đo hiệu năng | Benchmark | [BENCHMARK.md](09-performance/BENCHMARK.md) |
| Kiểm thử tải | Load Test | — |
| Kiểm thử chịu tải dài | Soak Test | ≥2h |
| Đối tượng giả (test) | Fake | In-memory thật, khác Mock |
| Đối tượng mô phỏng | Mock | [TESTING.md §4](10-quality/TESTING.md) |
| Bất biến | Immutable | Điều 43 |
| Vùng quan tâm | Area of Interest (AoI) | [SERVER.md §6](05-server/SERVER.md) |
| Bước thời gian cố định | Fixed Timestep | [ENGINE.md §4](03-engine/ENGINE.md) |
| Nội suy | Interpolation | [CLIENT.md §7](04-client/CLIENT.md) |
| Dự đoán phía client | Client Prediction | [CLIENT.md §6](04-client/CLIENT.md) |
| Đơn vị thế giới | World Unit | 1 = 1 mét |
| Trôi kiến trúc | Architecture Drift | [AI-WORKING-RULES.md §15](00-governance/AI-WORKING-RULES.md) |
| Sandbox script | Sandbox | [SCRIPTING.md §4](06-plugin/SCRIPTING.md) |

---

## 2. Gameplay

> ⚠️ Từ v1.1.0 (theme tu tiên — [ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)):
> cột "Tiếng Việt" là **tên hiển thị mới**; cột "EN" là **tên code/DB — GIỮ NGUYÊN**, không đổi
> theo theme. Class/opcode/schema tiếp tục dùng đúng cột EN cũ.

| Tiếng Việt (tu tiên) | EN (code/DB — không đổi) | Ghi chú |
|---|---|---|
| Linh căn | Faction (Element) | Hỏa/Thủy/Phong/Lôi/Thổ = Fire/Water/Wind/Thunder/Earth; Phong/Lôi = dị linh căn (tên cũ: Hệ phái) |
| Linh Lực | Chi | `current_chi` — resource pool (tên cũ: Khí) |
| Tu Vi | Experience (EXP) | `experience` — điểm kinh nghiệm |
| Cảnh giới | Realm (display của Level) | Luyện Khí/Trúc Cơ/Tử Phủ/Kim Đan/Nguyên Anh/Hoá Thần — **mỗi cảnh giới 9 tầng** (Sơ/Trung/Hậu kỳ = 1–3/4–6/7–9); chỉ là cách trình bày Lv1–100 ([ADR-038 D2](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)) |
| Độ Kiếp Chuyển Thế | Rebirth | `rebirth_count` (tên cũ: Chuyển Sinh) |
| Đạo Vận | Origin Point | Bonus vĩnh viễn khi rebirth (tên cũ: Điểm Nguồn) |
| Tẩy Căn Đan | Faction Change Stone | Đổi linh căn (tên cũ: Đá Chuyển Hệ) |
| Linh Thạch | Gold | `gold` — tiền tệ chính (tên cũ: Vàng) |
| Tôi luyện | Enhancement | `enhance_level` +0→+15 (tên cũ: Cường hoá) |
| Hộ Linh Phù | Protect Scroll | Chống rớt cấp tôi luyện (tên cũ: cuộn Bảo Vệ) |
| Khảm Linh Ngọc | Gem Socketing | Post-MVP |
| Pháp khí | Equipment | `item_template` (tên cũ: Trang bị) |
| Bộ pháp khí | Set | Bonus 4/6/8 món |
| Công pháp | Skill | `skill_template` (tên cũ: Kỹ năng) |
| Thần Thông | Ultimate Skill | Lv50 — Tử Phủ tầng 7 (tên cũ: Tuyệt Kỹ) |
| Pháp thuật (chủ động) | Active Skill | — |
| Tâm pháp (bị động) | Passive Skill | — |
| Hồi chiêu | Cooldown | Server giữ, không tin client |
| Tông môn (hub) | Sect (Hub) | Safe zone — Thanh Hư Tông (tên cũ: Học Viện Ninja / Ninja Academy) |
| Trạm tiếp ứng | Way Post | Vùng an toàn NHỎ dọc trục (`waypost_*`, V49) — che chở nhưng KHÔNG hồi đầy máu, khác hub. Đệ tử trực trạm giữ một góc trận pháp hộ sơn thu nhỏ (ADR-050) |
| Đệ tử tạp dịch / ngoại môn / nội môn | Outer/Inner Disciple | Bậc tường thuật, không phải cơ chế |
| Linh Mạch | Faction Zone | Luyện cấp theo nguyên tố (tên cũ: Lãnh Địa Hệ Phái) |
| Hoang Vực | Contested Zone | PvP mở (tên cũ: Vùng Tranh Chấp) |
| Bí Cảnh | Dungeon (Instance) | Party-scoped — tên cũ giữ nguyên, đã chuẩn tu tiên |
| Chế độ khó | Hard Mode | — |
| Yêu Vương | World Boss | Theo lịch (tên cũ: Boss Thế Giới) |
| Yêu thú | Monster | `monster_template` (tên cũ: Quái) |
| Yêu thú tinh anh | Elite Monster | — |
| Khinh công | (movement flavor) | Cơ chế nhảy [ADR-033](01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md) — chỉ đổi cách gọi |
| Bảng rơi đồ | Drop Table | Config |
| Bang hội | Guild | Từ Lv25 |
| Chiếm Lãnh Địa | Guild Territory War | Post-MVP |
| Tổ đội | Party | 2–5 người |
| Đấu trường | Arena (ranked 1v1) | MMR/season |
| Chiến trường | Battlefield (5v5 objective) | — |
| Linh thú | Pet | Post-MVP (tên cũ: Thú cưng) |
| Phi kiếm / Linh cầm | Mount | Chỉ tốc độ + thẩm mỹ (tên cũ: Cưỡi thú) |
| Thời trang | Cosmetic / Fashion | Pay-for-cosmetic |
| Đấu giá | Auction House | Post-MVP |
| Thư | Mail | — |
| Bạn bè | Friend | — |
| Trò chuyện | Chat | Kênh: Thế giới/Bang hội/Party/Riêng tư/Khu vực (GAMEPLAY.md §10) |
| Nhiệm vụ | Quest | MAIN/DAILY/GUILD/EVENT |
| Đối thoại | Dialogue | Script |
| Sự kiện mùa | Festival Event | Feature Flag |
| Bảng xếp hạng | Ranking / Leaderboard | Redis + snapshot |
| Điểm tiêu tiền | Gold Sink | Chống lạm phát |
| Vòng lặp cốt lõi | Core Loop | [GDD §9](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| Chuỗi lên đồ | Progression | — |
| Máy tự chơi | Bot | Anti-cheat ngoài MVP |
| Nhân bản vật phẩm | Item Duplication | ⚠️ Rủi ro kinh tế nghiêm trọng |

---

## 3. Vận hành

| Tiếng Việt | EN | Ghi chú |
|---|---|---|
| Nhật ký kiểm toán | Audit Log | Bắt buộc cho trade/gold |
| Quản trị viên game | Game Master (GM) | [ADMIN.md](12-devtools/ADMIN.md) |
| Nhịp máy chủ | Tick / Tick Rate | **60Hz** ([ADR-018](01-architecture/adr/ADR-018-Tick-Rate.md)) |
| Ngân sách nhịp | Tick Budget | 16.6ms @60Hz |
| Quá hạn nhịp | Tick Overrun | Log WARN |
| Tắt máy êm | Graceful Shutdown | Lưu state trước |
| Giới hạn tần suất | Rate Limiting | — |
| Bảo vệ replay | Replay Protection | Cần idempotency |
| Tính bất biến thao tác | Idempotency | ✅ ADR-025 |

---

## 4. Quy tắc bổ sung thuật ngữ mới

Khi thêm khái niệm mới:

1. Tra bảng này — có gần giống thì **tái sử dụng**, không tạo từ mới.
2. Nếu thật mới → thêm dòng vào bảng tương ứng **cùng PR** giới thiệu khái niệm ([DOCUMENTATION.md §7](11-process/DOCUMENTATION.md)).
3. Tên class/package dùng đúng cột "EN" — **không tự dịch khác** giữa các module.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tài liệu tiếng Việt, code tiếng Anh | Accepted |
| 2 | GLOSSARY ở `docs/` root vì xuyên suốt mọi nhóm | Accepted |
| 3 | Thêm thuật ngữ mới cùng PR giới thiệu khái niệm | Accepted |

## Tài liệu liên quan

- [DOCUMENTATION.md §11](11-process/DOCUMENTATION.md) — ngôn ngữ
- [GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md) — nguồn thuật ngữ gameplay
- [README.md](README.md) — index

## Việc cần làm tiếp theo

1. Bổ sung khi có thuật ngữ mới (§4).
2. Package root `dev.kage` đã chốt ([ADR-012](01-architecture/adr/ADR-012-Package-Root.md)) — tên thương hiệu game đổi sau không đụng package.
