# ADR-056: Bảng nhân vật + Lực Chiến — `GetCharacterStatsRequest` (210/211), server tính, client chỉ hiện

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn mục "Bảng nhân vật + Lực Chiến" trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi ghi rõ "chọn = Accept ADR đó và thi hành luôn"; mô tả kèm: phím C, chỉ số + Lực Chiến theo công thức GDD §5, 2 opcode mới, không đổi DB). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5 "Chỉ số", "Lực Chiến"](../../13-game-design/GAME-DESIGN-DOCUMENT.md) · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (chỉ số trang bị) · [ADR-057](ADR-057-Leaderboards.md) (xếp hạng Lực Chiến dùng cùng công thức) |

---

## Context

- Người chơi không có cách nào xem chỉ số của mình: STR/AGI/INT/VIT, ATK/DEF/RES, tỉ lệ chí mạng/né
  chỉ sống ở server (`CombatConstants.deriveCombatStats` + bonus trang bị). Mặc đồ, lên cấp, chọn hệ
  phái đều đổi chỉ số mà client không thấy gì ngoài vạch máu.
- GDD §5 đã định nghĩa **Lực Chiến** là chỉ số tổng hợp CHỈ ĐỂ HIỂN THỊ/XẾP HẠNG, không bao giờ dùng
  tính damage, công thức đặt ở `CombatConstants` — nhưng chưa có dòng code nào.
- Client không được tự tính (client/CLAUDE.md: "Tự tính damage/giá/tỉ lệ — server tính").

## Decision

1. **Opcode trong dải Character**: `OPCODE_GET_CHARACTER_STATS_REQUEST = 210`,
   `OPCODE_GET_CHARACTER_STATS_RESPONSE = 211` (character.proto). Chỉ thêm — `protocolVersion` không tăng.
   ```proto
   message GetCharacterStatsRequest {}
   message GetCharacterStatsResponse {
     int32 level = 1;
     int32 strength = 2; int32 agility = 3; int32 intelligence = 4; int32 vitality = 5;
     int32 max_hp = 6;
     int32 attack = 7; int32 skill_damage = 8; int32 defense = 9; int32 resistance = 10;
     int32 hit_permille = 11; int32 dodge_permille = 12; int32 crit_permille = 13;
     int32 crit_damage_percent = 14;
     int64 combat_power = 15;
     int64 equipment_bonus_hp = 16; // phần máu tối đa đến từ trang bị — để HUD nói rõ "100 + 18"
   }
   ```
   Tỉ lệ đi trên dây bằng **phần nghìn nguyên** (không `float`): client chỉ định dạng, không làm tròn
   lại — hai client không bao giờ hiện hai số khác nhau cho cùng một nhân vật.
2. **Nguồn số = chỉ số NỀN + trang bị**, đúng thứ `CharacterLoadout` dựng lúc attach (cùng một
   hàm, không viết lại công thức lần hai): `WorldFacade.statSheet(baseStats, equipmentBonus)` — hàm
   thuần, đọc `CombatConstants`. **Không** gồm buff/debuff tạm thời (đọc ECS sống phải đi qua tick
   thread; con số trên bảng nhân vật là con số "bản thân", không phải con số giữa trận).
3. **Lực Chiến** — `CombatConstants.combatPower(sheet)`, đúng công thức GDD §5, làm tròn xuống:
   ```
   HP×1 + Chi×0,5 + (ATK + SKILL_DMG)×3,5 + (DEF + RES)×3 + CRIT%×100×2 + DODGE%×100×1,5
        + HIT%×100×0,5 + ATK_SPEED_bonus%×100×1,5 + Level×50
   ```
   `Chi` (Linh Lực tối đa) và `ATK_SPEED_bonus` lấy giá trị code đang có (Linh Lực tối đa hằng số; tốc
   đánh chưa có bonus ⇒ 0). Hệ số là placeholder của GDD — đổi hệ số là đổi một hằng số.
4. **Client**: cửa sổ **Bảng nhân vật** mở bằng phím **C** (còn trống), hỏi lại server MỖI lần mở,
   có nút Làm mới. Widget chỉ vẽ chuỗi đã dịch (UI.md §4).
5. Chạm DB (đọc chỉ số + trang bị) ⇒ chạy trên `authExecutor`, qua `dbCommandRateLimiter` sẵn có.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Gửi chỉ số kèm `SelectCharacterResponse`/mỗi lần đổi đồ | Thêm field vào message đang chạy cho một màn hình hiếm mở; số cũ khi đổi đồ giữa chừng |
| Client tự tính từ STR/AGI… | Lộ công thức, lệch khi server đổi hệ số — vi phạm client/CLAUDE.md |
| Đọc chỉ số sống từ ECS (gồm buff) | Phải hỏi tick thread bất đồng bộ; con số nhảy theo buff làm bảng khó hiểu |
| Lưu `combat_power` vào DB | Đổi schema cho một con số suy ra được; dễ lệch khi đổi công thức |

## Consequences

- Người chơi thấy được hậu quả của mặc đồ/lên cấp/chọn hệ phái.
- Lực Chiến có một định nghĩa duy nhất ở server — ADR-057 xếp hạng dùng lại đúng hàm này.

## Revisit Trigger

- Có buff dài hạn (đan dược tăng chỉ số theo giờ) ⇒ cân nhắc hiện thêm cột "đang có hiệu lực".
- Có tốc đánh/Linh Lực theo chỉ số ⇒ cập nhật hai số hạng đang là hằng số trong công thức.

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | Opcode 210/211, chỉ thêm |
| 2 | Tỉ lệ bằng phần nghìn nguyên trên dây |
| 3 | Nền + trang bị, không buff; một hàm thuần dùng chung với attach |
| 4 | Lực Chiến theo GDD §5 ở `CombatConstants`, làm tròn xuống |

## ADR cần tạo

Không.

## Tài liệu liên quan

[GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) · [CLIENT.md](../../04-client/CLIENT.md) · [NETWORK.md](../../05-server/NETWORK.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18). `CharacterStatSheetTest`; panel chung `ReportPanel` (phím C).
2. ⏳ Kiểm sống: mở bảng, mặc/gỡ một món, số đổi đúng.
