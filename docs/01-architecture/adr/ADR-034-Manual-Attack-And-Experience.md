# ADR-034 — Đánh thường BẰNG TAY + hệ thống kinh nghiệm/lên cấp

- **Status**: Accepted (chủ dự án quyết trực tiếp qua chat 2026-08-09, cùng phiên playtest)
- **Ngày**: 2026-08-09
- **Liên quan**: [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (cùng chuỗi playtest),
  [GDD §6](../../13-game-design/GAME-DESIGN-DOCUMENT.md)

## Context

Trong lượt playtest thứ ba của ngày (sau khi nhảy đôi + click chọn mục tiêu vừa xong), chủ dự án
chơi thử và nêu hai điều, nguyên văn:

1. *"sao tôi mới chọn vào chưa nhấn nút đánh mà quái đã mất hp thế"*
2. *"sửa lại đi tôi phải bấm đánh mới được đánh mà không có vạch kinh nghiệm để biết bao giờ lên
   level à"*

Điều thứ nhất KHÔNG phải bug — đó là thiết kế có sẵn từ Phase 4: **GDD §6 "Auto-target đánh
thường"**, `CombatSystem` tự đánh mục tiêu mỗi khi hết cooldown. Nó chỉ vô hình cho tới hôm nay vì
trước đó người chơi gần như không chủ động chọn được mục tiêu (mục tiêu do client tự gán cho con
quái đầu tiên nhìn thấy). Việc thêm click-chọn-mục-tiêu làm cơ chế cũ lộ ra và chủ dự án bác bỏ nó.

Điều thứ hai phơi ra một khoảng trống thật: cột `character.exp` (migration V2) và `character.level`
đã tồn tại từ đầu, quest có cộng EXP vào đó (`PostgresQuestExpAdapter`), nhưng **không nơi nào đọc
EXP ra để đổi thành cấp**, và **giết quái không cho EXP**. Nghĩa là dự án có ô chứa kinh nghiệm mà
chưa bao giờ có hệ thống kinh nghiệm.

Cả hai đều nằm trong danh sách 🛑 của [/CLAUDE.md §4](../../../CLAUDE.md): đổi protocol (thêm
opcode) và đổi cơ chế combat đã cân bằng từ Phase 4. ADR này ghi lại quyết định đã chốt.

## Decision

### 1. Đánh thường chỉ ra đòn khi người chơi bấm

- `SetTargetCommand` **chỉ còn nghĩa "nhắm ai"**, không kéo theo đòn nào.
- Thêm `AttackCommand` (opcode **407**, message RỖNG) — mỗi gói là một Ý ĐỊNH ra một đòn. Mục tiêu
  lấy từ `TargetComponent` phía server, KHÔNG từ gói tin: client không được chọn đánh ai theo từng
  đòn (Server Authority, cùng khuôn `JumpCommand` của ADR-033).
- `CombatSystem` tách hai chế độ theo component định danh: entity có `CharacterIdComponent` chỉ
  đánh khi có `requestAttack`; **quái/boss GIỮ NGUYÊN tự đánh**. Bỏ auto cả hai phía nghĩa là không
  ai đánh người chơi nữa.
- Cooldown vẫn thuộc về server và không đổi (`ATTACK_INTERVAL_SECONDS` = 1 đòn/giây). Spam
  `AttackCommand` không đánh nhanh hơn — đây là điểm tấn công mới nên có test riêng.
- Client: phím **Z**/Ctrl hoặc **nút Đánh** tròn góc dưới-phải (to hơn nút chiêu — nó là hành động
  bấm nhiều nhất trong trận). Giữ nút thì tự lặp theo đúng nhịp cooldown.

### 2. Kinh nghiệm và lên cấp

- `ExperienceCurve` (feature-character/domain): cần `100 × cấp hiện tại` EXP để lên cấp kế, cấp tối
  đa 100. **Cấp luôn được SUY LẠI từ tổng EXP**, không cộng dồn từng bước — nhờ vậy EXP quest đã
  tích từ Phase 6 tự động quy ra đúng cấp ngay lần nhận EXP đầu tiên.
- `MonsterExperience`: bù nhìn 20 EXP, boss 200 EXP. Hardcode theo `monster_code`, cùng lý do
  `CombatConstants.MONSTER_STATS` chưa đọc DB — bảng `monster_template` chưa tồn tại.
- `ExperienceChangedEvent` (opcode **208**) mang `level`/`total_exp`/`exp_into_level`/
  `exp_for_next_level`/`leveled_up`. Server gửi sẵn cả tiến độ trong cấp thay vì để client tự suy:
  đường cong là luật chơi, thuộc về server (Điều 15).
- Gửi 2 thời điểm: ngay khi vào world (vạch có số từ đầu) và mỗi lần nhận EXP.
- **Không đổi schema DB** — dùng đúng 2 cột `exp`/`level` đã có từ V2.
- HUD: vạch thứ ba (vàng cam) dưới Máu/Khí, số hiển thị là tiến độ TRONG cấp.

## Alternatives Considered

| Lựa chọn | Vì sao không chọn |
|---|---|
| Giữ auto-attack, chỉ thêm nút đánh để "đánh thêm" | Chủ dự án nói rõ "phải bấm đánh mới được đánh" — auto vẫn chạy thì không giải quyết điều họ khó chịu |
| Bỏ auto-attack cho CẢ quái | Không ai đánh người chơi nữa; người chơi bất tử, không phải điều được yêu cầu |
| Gửi `target_entity_id` trong `AttackCommand` | Cho client quyết mục tiêu từng đòn = thêm bề mặt phải validate lại, không được lợi gì (server đã biết mục tiêu) |
| Đường cong EXP cấp số nhân | Đúng "cảm giác MMO" hơn nhưng lần lên cấp ĐẦU TIÊN rơi ra ngoài tầm một buổi test — mà đó chính là thứ đang cần nghiệm thu |
| Chờ có bảng `monster_template` rồi mới làm EXP | Chặn tính năng sau một việc hạ tầng chưa lên lịch; bảng tra Java thay được bằng một cột khi bảng ra đời |

## Consequences

- **GDD §6 "Auto-target đánh thường" nay SAI với code** — mục này cần sửa lại khi chủ dự án cập nhật
  GDD. ADR này là nguồn đúng cho tới lúc đó (thứ tự thắng ở /CLAUDE.md §14: ADR Accepted trên tài
  liệu chuyên môn).
- **Mọi test combat qua `WorldFacade` phải tự bấm đánh.** 11 test đã phải sửa; thêm helper
  `targetAndAttackUntil`/`attackFor` (WorldFacadeTest) và `startAttackSpammer`
  (GatewayServerIntegrationTest) vì các test đó CHỜ ĐỌC frame nên không vừa đọc vừa bấm được trên
  cùng một luồng.
- **PvP/duel cũng thành bấm tay** — cân bằng Arena (Phase 8) giả định hai bên tự đánh đều đặn; giờ
  ai bấm nhanh hơn có lợi thế. Chưa đo lại, ghi làm Revisit Trigger.
- Số cân bằng EXP (20/con, 100×cấp) là **placeholder**, sửa ở đúng hai lớp `ExperienceCurve` và
  `MonsterExperience`.
- Chi phí thêm mỗi lần giết quái: một `UPDATE ... RETURNING` (+ một `UPDATE level` khi lên cấp),
  chạy trên Virtual Thread riêng như loot/quest — không đụng tick thread.

## Revisit Trigger

Xem lại nếu (a) chủ dự án thấy bấm đánh liên tục mỏi tay ở nội dung farm dài — khi đó cân nhắc chế
độ "giữ nút = đánh liên tục" ở tầng server thay vì client tự lặp; hoặc (b) đo thật cho thấy PvP đã
biến thành thi bấm nhanh thay vì thi cân bằng chỉ số.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Người chơi phải bấm mới đánh thường | **Chốt** (chủ dự án) |
| 2 | Quái/boss vẫn tự đánh | **Chốt** — hệ quả bắt buộc, xem Alternatives |
| 3 | `AttackCommand` rỗng, mục tiêu từ server | **Chốt** |
| 4 | Đường cong EXP `100 × cấp`, tối đa cấp 100 | ⏳ Placeholder — cân bằng thật thuộc GDD |
| 5 | EXP mỗi quái (20 / 200) | ⏳ Placeholder — chuyển thành cột khi có `monster_template` |
| 6 | Cân bằng lại PvP sau khi đổi sang bấm tay | ⏳ Mở — chưa đo |

## ADR cần tạo

Không. Các mục ⏳ ở trên là tham số nội dung, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [GAME-DESIGN-DOCUMENT.md §6](../../13-game-design/GAME-DESIGN-DOCUMENT.md) — mục "Auto-target" cần sửa
- [ADR-033](ADR-033-Ninja-School-Style-Movement.md) — cùng chuỗi playtest, cùng khuôn "command rỗng"
- [SECURITY.md §8](../../08-security/SECURITY.md) — chống cooldown hack, nay áp cho `AttackCommand`

## Việc cần làm tiếp theo

1. ⏳ **Chủ dự án cập nhật GDD §6** — bỏ "Auto-target đánh thường", ghi lại luật mới.
2. ⏳ **Chốt số cân bằng EXP** (Technical Decisions #4/#5) sau khi chơi thử đủ lâu.
3. ⏳ **Đo lại PvP** sau khi đổi sang bấm tay (Technical Decision #6).
4. ⏳ **Hiệu ứng lên cấp** — `leveled_up` đã có trên dây nhưng client mới chỉ cập nhật số, chưa có
   gì ăn mừng (chữ bay/âm thanh). Việc nhỏ, để lại khi có art/audio.
