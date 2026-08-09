# ADR-033: Đổi mô hình di chuyển sang trái/phải + nhảy thật (kiểu Ninja School Online)

| | |
|---|---|
| Status | 🟢 **Accepted** (2026-08-08, chủ dự án chốt qua chat) |
| Date | 2026-08-08 |
| Deciders | Chủ dự án chốt hướng (2026-08-08, qua chat, 2 câu hỏi lựa chọn scope); AI soạn |
| Related | [ADR-021](ADR-021-Box2D-vs-AABB.md) (AABB tự viết, KHÔNG Box2D) · [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) (camera 2.5D khoá góc) · [ADR-018](ADR-018-Tick-Rate.md) (60Hz) · [PHYSICS.md](../../03-engine/PHYSICS.md) · [RENDERING.md §11.1](../../03-engine/RENDERING.md) |

---

## Context

Di chuyển hiện tại (từ Phase 3, chưa đổi qua Phase 8/9): tự do 2 chiều trên mặt phẳng đất world XY —
`GameAction.MOVE_LEFT/RIGHT/UP/DOWN` (WASD + mũi tên), `MoveCommand{dirX, dirY}`, server tính vị trí
mới mỗi tick theo `MOVE_SPEED_MPS` (`server-world.WorldConstants`), **không có khái niệm chiều cao
gameplay** — nhân vật luôn đặt ở `z=0` (xem `CharacterModelRenderer.setCharacterPosition`,
`trn(x, y, 0f)` — tham số Z hardcode 0). Camera là orthographic khoá góc (yaw 35°, pitch 30°, ADR-031)
nhìn xuống mặt phẳng đó — không phải side-view thuần, nhưng world-space đã sẵn trục Z làm "chiều đứng"
nhờ phép xoay Y-up→Z-up của model.

Trong lúc kiểm chứng bằng mắt bug tư thế nhân vật (RENDERING.md §11.1, cùng phiên), chủ dự án quan sát
nhân vật đang di chuyển tự do 4 hướng và yêu cầu đổi sang cảm giác di chuyển kiểu **Ninja School
Online**: phím Lên là **nhảy** thay vì di chuyển. Hỏi lại 2 câu để chốt scope, chủ dự án chọn mức lớn
nhất ở cả hai:

1. **Bỏ hẳn di chuyển lên/xuống màn hình** — chỉ còn trái/phải + nhảy (platformer thật), không phải
   giữ 4 hướng và thêm nhảy như một khả năng cộng thêm.
2. **Nhảy có tác dụng gameplay thật** — né được đòn tầm thấp, lên được bệ cao — không phải chỉ là hoạt
   ảnh/thẩm mỹ.

Đây là thay đổi vượt xa "sửa bug có test tái hiện" hay "thêm code đúng pattern có sẵn" ở
[/CLAUDE.md §3](../../../CLAUDE.md) — nó **đổi protocol** (`MoveCommand` cần trường mới cho ý định
nhảy; `CharacterPosition`/state cần thêm trục dọc) và **đổi mô hình vật lý** (world lần đầu có trọng
lực + trục thẳng đứng thật, không chỉ mặt phẳng 2D) — cả hai đều nằm trong danh sách 🛑 phải dừng và
tạo ADR ở [/CLAUDE.md §4](../../../CLAUDE.md). ADR này là bước "dừng lại và hỏi" đó — ghi lại quyết
định đã chốt về HƯỚNG, còn các con số/API cụ thể vẫn mở để bàn tiếp lúc code.

## Decision

Đổi mô hình di chuyển của nhân vật (người chơi) sang **trái/phải trên một trục ngang + nhảy thật theo
trục đứng**, có tác dụng gameplay (né đòn tầm thấp, lên bệ cao). Cụ thể:

1. **`GameAction`**: bỏ `MOVE_UP`/`MOVE_DOWN` (dùng cho di chuyển tự do), thêm `JUMP`. Giữ
   `MOVE_LEFT`/`MOVE_RIGHT`. Phím Lên/W ánh xạ sang `JUMP` thay vì di chuyển.
2. **World**: thêm trục đứng (Z) THẬT cho gameplay lần đầu tiên — nhân vật có `z`, vận tốc dọc
   `vz`, trạng thái `grounded`/`airborne`. Trọng lực + vòng nhảy tự viết trong tinh thần ADR-021
   (KHÔNG Box2D) — mở rộng `engine-physics`/AABB hiện có, không đổi engine vật lý.
3. **Protocol**: `MoveCommand` cần trường ý định nhảy (hoặc packet riêng `JumpCommand` — quyết định lúc
   viết code, không chốt ở ADR này); vị trí nhân vật gửi cho client khác cần thêm `z`. Server vẫn là
   nguồn chân lý duy nhất cho `z`/`vz`/trạng thái nhảy (Server Authority, /CLAUDE.md §5) — client chỉ
   dự đoán cục bộ giống cách dự đoán `x`/`y` hiện tại (CLIENT.md §6), không tự quyết kết quả né đòn.
4. **Combat**: `CombatSystem`/`AttackResultEvent` cần đọc trạng thái airborne của mục tiêu để xử lý né
   đòn tầm thấp — phạm vi chính xác "đòn nào coi là tầm thấp" chưa chốt, để lại cho lúc thiết kế chi
   tiết (có thể cần tham chiếu GDD).
5. **Animation**: clip `jump` đã có sẵn trong mọi `.glb` đã xuất (xác nhận thật, xem RENDERING.md §11.1)
   nhưng cố ý chưa dùng tới nay (`CharacterModelRenderer`, ghi chú "GameAction chưa có ý định nhảy") —
   ADR này gỡ bỏ đúng điều kiện chặn đó.
6. **Bản đồ**: cần tối thiểu một khu vực pilot có bệ/chướng ngại để việc "lên bệ cao" có ý nghĩa —
   nội dung bản đồ là việc riêng (art/level design), không thuộc phạm vi kỹ thuật của ADR này.

## Alternatives Considered

| Lựa chọn | Vì sao không chọn |
|---|---|
| A. Giữ 4 hướng tự do, nhảy chỉ là hoạt ảnh (không đổi vị trí/vật lý) | Chủ dự án từ chối tường minh ở câu hỏi #1 — không phải "cảm giác Ninja School Online" thật |
| B. Giữ 4 hướng tự do, THÊM nhảy có tác dụng thật (né đòn) mà không bỏ lên/xuống | Ít phá vỡ bản đồ/PvP/dungeon đã xây hơn phương án được chọn, nhưng chủ dự án từ chối tường minh — không đúng cảm giác platformer 1-trục của Ninja School Online |
| C. (được chọn) Bỏ lên/xuống, chỉ trái/phải + nhảy thật, có tác dụng gameplay | Đúng yêu cầu chủ dự án cả hai câu hỏi; đánh đổi: phạm vi rà soát lại lớn nhất (§Consequences) |

## Consequences

⚠️ **Đây là thay đổi nền tảng, không phải một tính năng cộng thêm.** Toàn bộ nội dung/hệ thống đã xây
từ Phase 3 (Character/Map/Movement) tới Phase 9 (Optimization) — đã **đóng Exit Criteria** — đều ngầm
giả định di chuyển tự do 2 chiều:

- **Bản đồ hiện có**: thiết kế cho không gian mở 2D, chưa có bệ/chướng ngại theo trục dọc.
- **AI quái** (FSM tự viết, ADR-022): đuổi/né người chơi bằng vector 2D tự do — cần rà lại có còn đúng
  khi người chơi chỉ di chuyển 1 trục ngang hay không.
- **Dungeon/PvP/Arena** (Phase 8, đã đóng): thiết kế/cân bằng giả định không gian chiến đấu 2 chiều tự
  do — thu hẹp còn 1 trục ngang có thể đổi hẳn cảm giác cận chiến/né đòn đã cân bằng.
- **Load/soak test Phase 9** (N=1000/2000, đã đóng với số liệu thật): đo trên mô hình di chuyển cũ —
  thêm vật lý nhảy (tick logic mỗi khung cho `z`/`vz`) có chi phí CPU riêng, **chưa đo**, không suy
  luận tĩnh là "chắc chắn rẻ" (Điều 9 — benchmark trước, refactor sau).
- **`character_position`** (DB): hiện chỉ có `x`, `y` — thêm `z` là **đổi database schema** (🛑 mục
  riêng ở /CLAUDE.md §4, migration Flyway mới khi vào code, không chốt số cột ở ADR này).

Không đề xuất rollback lại bản đồ/dungeon hiện có trong phạm vi ADR — đó là quyết định nội dung riêng,
để chủ dự án và/hoặc một lượt thiết kế lại bản đồ quyết sau khi cơ chế nhảy chạy được.

## Revisit Trigger

Xem lại nếu sau khi triển khai, đo thật cho thấy chi phí tick vật lý dọc (gravity/jump) đẩy `kage.tick.duration`
p99 vượt ngân sách 16.6ms ở N tham chiếu đã đo Phase 9 (BENCHMARK.md §5.2) — khi đó cân nhắc đơn giản
hoá mô hình nhảy (vd bỏ gia tốc thay đổi liên tục, dùng bảng chiều cao rời rạc) thay vì tối ưu vi mô.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bỏ di chuyển lên/xuống, chỉ trái/phải + nhảy | **Chốt** (chủ dự án, câu hỏi #1) |
| 2 | Nhảy có tác dụng gameplay thật (né đòn tầm thấp, lên bệ cao) | **Chốt** (chủ dự án, câu hỏi #2) |
| 3 | Vật lý nhảy tự viết, không Box2D | **Chốt**, kế thừa ADR-021 |
| 4 | Tên trường protocol cụ thể (`MoveCommand` mở rộng hay `JumpCommand` riêng) | ⏳ Mở — quyết lúc code |
| 5 | "Đòn tầm thấp" là những đòn nào (danh sách skill/animation cụ thể) | ⏳ Mở — cần đối chiếu GDD |
| 6 | Nội dung bản đồ pilot có bệ/chướng ngại | ⏳ Mở — việc art/level design riêng |
| 7 | **Nhảy đôi**: giữ nút nhảy một lúc → cú thứ hai giữa không trung, CAO HƠN cú đầu | **Chốt** (chủ dự án, playtest 2026-08-09) |
| 8 | Trần nhảy giữa không trung = 1 lần/mỗi lần rời đất, server đếm | **Chốt** — hệ quả bắt buộc của Điều 15, không có trần thì client sửa đổi bay vô hạn |
| 9 | Chọn mục tiêu bằng CLICK CHUỘT vào quái; phím Enter = dùng skill đang chọn | **Chốt** (chủ dự án, playtest 2026-08-09) |

## ADR cần tạo

Không cần ADR con — các quyết định mở ở bảng trên là chi tiết triển khai, không phải quyết định kiến
trúc mới, xử lý trong quá trình code + review bình thường.

## Tài liệu liên quan

- [PHYSICS.md](../../03-engine/PHYSICS.md) — mô hình AABB hiện tại, phải viết thêm mục trọng lực/nhảy
- [RENDERING.md §11.1](../../03-engine/RENDERING.md) — clip `jump` đã có sẵn trong `.glb`, chưa dùng
- [CLIENT.md §6](../../04-client/CLIENT.md) — quy tắc dự đoán cục bộ, áp dụng cho cả trục dọc mới
- [BENCHMARK.md §5.2](../../09-performance/BENCHMARK.md) — ngân sách tick N=1000/2000 cần đo lại

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án Accept 2026-08-08** (qua chat, cùng phiên soạn ADR).
2. ✅ **Hợp đồng protocol chốt 2026-08-09** — `JumpCommand` (rỗng, opcode 306) + `MoveResponse` thêm
   `z`/`grounded` (add-only, không đổi field `x`/`y` đang dùng — shared/CLAUDE.md). `MoveCommand.dir_y`
   GIỮ NGUYÊN trên dây nhưng server-world bỏ qua giá trị này cho người chơi (Server Authority). Chi
   tiết: `movement.proto`, `opcode_registry.proto`.
3. ✅ **Server physics + client input xong 2026-08-09** — `VerticalMovementSystem` (engine-ecs, trọng
   lực + đáp đất, có hỗ trợ `Platform` cho va chạm bệ dù chưa map nào dùng); `GameAction` bỏ
   `MOVE_UP`/`MOVE_DOWN` thêm `JUMP`; `WorldScreen` dự đoán nhảy cục bộ + wire clip `jump` (ưu tiên
   cao nhất trong `CharacterAnimationClips.loopingClipFor`). Verify thật: `VerticalMovementSystemTest`
   (5 test), `WorldFacadeTest.shouldApplyJumpVelocity_...`, E2E qua socket thật
   (`GatewayServerIntegrationTest.clientShouldBecomeAirborne_...`).
4. ✅ **Combat xong 2026-08-09** — `CombatSystem` đọc `target.grounded`: airborne né TUYỆT ĐỐI đòn
   đánh thường (ghi đè RNG), verify bằng `CombatSystemTest` (test trực tiếp System, không qua tick
   loop thật — tránh flaky vì cửa sổ nhảy ~0.7s có thể không trùng đúng lúc quái hết cooldown 1s).
   **CHƯA áp cho skill** (`SkillSystem`) — phân loại "đòn nào tầm thấp" cho skill vẫn mở (Technical
   Decision #5), việc riêng khi có GDD chốt danh sách.
5. ✅ **Rà AI quái xong 2026-08-09 — KHÔNG cần sửa code**: `AiSystem` (chase/patrol/return) chỉ đọc
   `PositionComponent.x/y` của người chơi qua khoảng cách Euclid thuần (`findNearestCharacterInRange`,
   `moveToward`) — không bao giờ đọc input axis/hướng di chuyển của người chơi, chỉ đọc vị trí HIỆN
   TẠI. Quái vẫn đuổi/tấn công đúng bất kể người chơi di chuyển tự do hay chỉ còn 1 trục ngang, vì góc
   nhìn của AI luôn là "người chơi đang ở đâu", không phải "người chơi di chuyển kiểu gì". Không có
   z-term trong bất kỳ phép tính khoảng cách nào của AI — nghĩa là quái (luôn `grounded=true`, không
   nhảy) coi người chơi đang lơ lửng như đang đứng tại `(x,y)` đó, không aggro/mất aggro theo độ cao.
   Đây là hành vi CHẤP NHẬN ĐƯỢC cho lát cắt MVP (không phải bug), ghi lại làm Revisit Trigger nếu
   sau này map pilot có bệ cao khiến "quái đánh trúng người chơi đang đứng trên bệ cao hẳn" trở thành
   vấn đề UX thật.
6. 🟡 **Một phần — JMH proxy đo 2026-08-09, CHƯA đo `kage.tick.duration` production thật.**
   `VerticalMovementBenchmark` (engine-ecs, cùng phương pháp `EcsWorldUpdateBenchmark` dùng cho
   Revisit Trigger ADR-006) cô lập đúng `VerticalMovementSystem`, kịch bản XẤU NHẤT (100% entity
   airborne cùng lúc — thực tế không bao giờ xảy ra vì 1 lần nhảy chỉ ~0.7s):

   | N | Chi phí đo thật | % ngân sách tick 16.6ms |
   |---|---|---|
   | 100 | 0.499 µs | ~0.003% |
   | 500 | 3.954 µs | ~0.024% |
   | 1000 (tham chiếu Phase 9) | 6.532 µs | ~0.039% |
   | 5000 | 32.630 µs | ~0.197% |

   Ngay ở kịch bản xấu nhất phi thực tế, chi phí vẫn nhỏ hơn 0.2% ngân sách ở mọi N đã đo — không có
   dấu hiệu đáng lo. **Vẫn CHƯA đủ** theo đúng chuẩn Điều 9 đã áp dụng cho Revisit Trigger ADR-006
   trước đây (đo `kage.tick.duration` THẬT qua `WorldTickScheduler` production, không phải JMH cô
   lập 1 System) — số liệu JMH này là bằng chứng ủng hộ mạnh nhưng không thay thế, để lại làm việc
   riêng khi có hạ tầng load-test rảnh (Testcontainers Postgres/Redis, `tools:load-test`).
7. ⏳ **Chưa làm** — nội dung bản đồ pilot có bệ/chướng ngại để việc "lên bệ cao" đo được; việc
   art/level design riêng, không phải kỹ thuật.
8. ⏳ **Chưa làm** — broadcast `z`/`grounded` cho nhân vật KHÁC qua `CharacterMovedEvent` (hiện chỉ
   CHÍNH người chơi thấy mình nhảy đúng qua `MoveResponse`; người khác luôn thấy z=0). Hoãn có chủ ý ở
   lát cắt đầu vì kéo theo sửa `AreaOfInterestTracker.lastSyncedPosition` (hot path zero-allocation đã
   tối ưu kỹ, xem PHYSICS.md §9).
9. ✅ **Nhảy đôi + chọn mục tiêu bằng chuột + Enter dùng skill xong 2026-08-09** (Technical Decisions
   #7-#9, chủ dự án playtest lượt hai và nêu 2 thiếu sót: "chưa nhảy được 2 lần như Ninja School",
   "chưa đánh được quái").

   - **Nhảy đôi**: `PositionComponent.airJumpsUsed` (engine-ecs) đếm số cú nhảy giữa không trung kể
     từ lần rời đất gần nhất; `VerticalMovementSystem` xoá về 0 lúc đáp đất — client và server dùng
     CHUNG hệ này nên hạn mức reset giống hệt nhau, không cần thêm field nào trên dây.
     `WorldFacade.handleJumpIntent` suy loại nhảy từ trạng thái THẬT (đứng đất → `JUMP_VELOCITY_MPS`
     7 m/s; lơ lửng còn hạn mức → `AIR_JUMP_VELOCITY_MPS` 8 m/s, cao hơn đúng như chủ dự án mô tả).
     Client kích hoạt cú thứ hai khi GIỮ nút quá `AIR_JUMP_HOLD_SECONDS` (0.3s — ngay trước đỉnh cú
     đầu, `7/20` = 0.35s) HOẶC khi bấm lại giữa không trung; thời điểm là cảm giác điều khiển thuần
     client, **số lần** mới là thứ server đếm.
   - **Không đổi protocol**: vẫn đúng một `JumpCommand` rỗng (opcode 306) cho cả hai loại nhảy —
     client không nói loại, server tự suy (Server Authority). Đây là lý do mục này KHÔNG cần ADR mới.
   - **Chọn mục tiêu**: `MonsterPicker` bắt click theo KHÔNG GIAN MÀN HÌNH (chiếu vị trí quái ra rồi
     so hộp bao 2D) thay vì ray-cast — quái chưa có model 3D để ray-cast vào, và cách này dùng chung
     cho cả nhánh 3D lẫn nhánh hình thay thế. Con đang nhắm đổi màu vàng ngay trong thế giới.
     `GameAction.USE_SKILL` (Enter) đi qua đúng đường của nút HUD, không nhân bản logic hồi chiêu/Khí.
   - Verify thật: `JumpInputControllerTest` (7 test — luật nhảy client tách khỏi `WorldScreen` để
     test được ở nhịp 60Hz thật), `VerticalMovementSystemTest` (+2 test hạn mức),
     `MonsterPickerTest` (5 test), `CombatHudControllerTest` (+2 test chọn mục tiêu), và
     `WorldFacadeTest.shouldAllowExactlyOneAirJump_whenJumpCommandSpammed` — spam 40 lệnh nhảy qua
     tick loop 60Hz THẬT rồi đo đỉnh `z`: phải cao hơn đỉnh một cú nhảy đơn (1.225m) mà không vượt
     trần hai cú (2.825m). Đã kiểm chứng cả hai chiều bằng mutation tay (đặt `MAX_AIR_JUMPS` = 0 và
     = 99, test fail đúng như mong đợi) — không phải test chỉ xanh vì lỏng.
</content>
