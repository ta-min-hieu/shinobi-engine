# ADR-057: Bảng xếp hạng (Bia Anh Danh) — 3 bảng top 50, opcode 1202/1203, server cache 60 giây

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn mục "Bảng xếp hạng" trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi ghi rõ "chọn = Accept ADR đó và thi hành luôn"; mô tả kèm: top 50 theo Cảnh giới (cấp+EXP), Elo đấu trường, Lực Chiến; 2 opcode; server cache 60s; không đổi DB, chỉ thêm index). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [STORY.md §7 dòng "Bảng xếp hạng — Bia Anh Danh"](../../13-game-design/STORY.md) · [GDD §7 "Xếp hạng"](../../13-game-design/GAME-DESIGN-DOCUMENT.md) · [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) (công thức Lực Chiến) · [opcode_registry.proto dải 1200–1299 "Ranking"](../../../shared/shared-protocol/src/main/proto/opcode_registry.proto) |

---

## Context

GDD §7 liệt "Xếp hạng — Cảnh giới/PvP/…/Lực Chiến", STORY §7 đặt sẵn tên "Bia Anh Danh", dải opcode
1200–1299 "Ranking" để trống từ Phase 0. `pvp_rating` (Elo) đã có từ Phase 8 nhưng chỉ người chơi
tự hỏi được số của mình (`GetPvpRatingRequest`). Không có chỗ nào cho người chơi so mình với người khác.

## Decision

1. **Opcode** (`ranking.proto` mới, dải 1200): `OPCODE_GET_LEADERBOARD_REQUEST = 1202`,
   `OPCODE_GET_LEADERBOARD_RESPONSE = 1203` (1200/1201 là `GetPvpRating`). Chỉ thêm, `protocolVersion` không tăng.
   ```proto
   enum LeaderboardKind { LEADERBOARD_KIND_UNSPECIFIED = 0; LEADERBOARD_KIND_REALM = 1;
                          LEADERBOARD_KIND_ARENA = 2; LEADERBOARD_KIND_COMBAT_POWER = 3; }
   message GetLeaderboardRequest { LeaderboardKind kind = 1; }
   message LeaderboardEntry { int32 rank = 1; int64 character_id = 2; string name = 3;
                              Faction faction = 4; int32 level = 5; int64 value = 6; }
   message GetLeaderboardResponse { LeaderboardKind kind = 1; repeated LeaderboardEntry entries = 2;
                                 int32 own_rank = 3;   // 0 = ngoài top
                                 int64 own_value = 4; int64 computed_at_epoch_ms = 5; }
   ```
2. **Ba bảng, top 50**:
   - **REALM (Cảnh giới)**: `level DESC, exp DESC, id ASC` (người tới trước giữ hạng khi bằng nhau).
     `value` = EXP; client hiện cảnh giới từ `level` (ADR-038).
   - **ARENA**: `pvp_rating.rating DESC`, chỉ người đã đánh ít nhất một trận (có dòng `pvp_rating`).
   - **COMBAT_POWER**: Lực Chiến của ADR-056 (nền + trang bị) — tính ở Java cho MỌI nhân vật lúc làm
     mới bảng (không có cột để `ORDER BY`).
3. **Cache 60 giây ở gateway** (`LeaderboardCache`, một bản/loại bảng, làm mới lười khi có người hỏi
   mà bản cũ quá 60 s; hai người hỏi cùng lúc chỉ một lượt tính — khoá theo loại bảng). Người chơi
   thấy `computed_at` để biết số có thể trễ một phút. `own_rank` tính trên CHÍNH bản cache đó.
4. **Không đổi schema** ngoài index đọc: V60 `CREATE INDEX ... ON character (level DESC, exp DESC, id)`
   và `ON pvp_rating (rating DESC)`. Lực Chiến không có index (tính ở Java).
5. **Điều phối ở gateway** — cùng luật "điều phối liên feature là việc của gateway": `CharacterFacade`
   (top theo cấp, danh sách id cho Lực Chiến), `PvpFacade` (top Elo), `CharacterLoadout` (chỉ số).
   Không thêm module mới.
6. **Client**: cửa sổ **Bia Anh Danh** phím **L**, ba tab, dòng của chính mình tô đậm, hỏi lại mỗi lần
   mở/đổi tab.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Module `feature-ranking` riêng | Không có nghiệp vụ/trạng thái riêng — chỉ đọc dữ liệu của feature khác; module rỗng ruột |
| Cột `combat_power` lưu sẵn để ORDER BY | Đổi schema + phải cập nhật ở mọi đường đổi đồ/lên cấp; lệch khi đổi công thức |
| Tính Lực Chiến chỉ cho top N theo cấp | Bỏ sót người cấp thấp đồ tốt — sai đúng ở chỗ bảng này tồn tại để cho thấy |
| Không cache | Mỗi lần mở là quét toàn bảng + N lần đọc trang bị |

## Consequences

- Bảng Lực Chiến tốn N lần đọc trang bị mỗi lần làm mới (≤ 1 lần/60 s). Ở quy mô Closed Beta (vài
  trăm nhân vật) không đáng kể; vượt vài nghìn thì xem Revisit Trigger.
- Tên hiện trên bảng là tên nhân vật — đã qua luật tên (NFKC, chặn filler, dòng 42/68).

## Revisit Trigger

- Số nhân vật > 5.000 hoặc lượt làm mới Lực Chiến > 1 s ⇒ chuyển sang cột lưu sẵn hoặc job nền.
- Có mùa giải PvP ⇒ bảng ARENA theo mùa.

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | `ranking.proto`, opcode 1202/1203 |
| 2 | 3 bảng top 50, hoà thì id nhỏ trước |
| 3 | Cache 60 s ở gateway, làm mới lười, khoá theo loại |
| 4 | V60 chỉ thêm index |

## ADR cần tạo

Không.

## Tài liệu liên quan

[NETWORK.md](../../05-server/NETWORK.md) · [DATABASE.md](../../05-server/DATABASE.md) · [CLIENT.md](../../04-client/CLIENT.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18). ⚠️ Opcode thật là **1202/1203** (1200/1201 đã thuộc `GetPvpRating` từ Phase 8 — bản nháp ghi nhầm). Cache: `LeaderboardCache` (một bản mỗi bộ facade, khoá theo loại bảng). Panel `ReportPanel` phím L.
2. ⏳ Kiểm sống với vài nhân vật thật trên DB dev.
