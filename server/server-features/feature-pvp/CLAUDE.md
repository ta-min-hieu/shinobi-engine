# CLAUDE.md — feature-pvp

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-pvp/**`.

## Cấu trúc (Clean Arch)

```
api/            PvpFacade (+ RecordDuelResultUseCase.DuelSettlement/GetPvpRatingUseCase.RatingView
                 — type của UseCase, KHÔNG cần bundle riêng vì chỉ 2 UseCase, dưới giới hạn 4 tham số)
application/    RecordDuelResultUseCase · GetPvpRatingUseCase
domain/         PvpRating (aggregate) · PvpRatingPort · EloRating (thuần tính toán, không chạm Port)
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresPvpRatingAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-25, Phase 8 lát cắt 4)

Chốt cùng chủ dự án qua `AskUserQuestion` trước khi code: phạm vi = **Arena 1v1 + MMR/ranking cơ
bản** (không phải Battlefield 5v5 hay Chiếm Lãnh Địa guild war — 2 hạng mục PvP còn lại của GDD §6
để dành lát cắt sau, cùng tinh thần Boss AI hoãn "summon minion").

- **Module này CHỈ lo phần cần PERSIST (rating/wins/losses)**. Vòng đời trận đấu (thách đấu, tạo
  World riêng, kết thúc trận) KHÔNG có dữ liệu nghiệp vụ nào cần lưu — đặt thẳng ở
  `server-gateway` (`ArenaInstanceRegistry`), giống hệt lý do `DungeonInstanceRegistry` KHÔNG phải
  `feature-dungeon` (ADR-026, ROADMAP Phase 8 lát cắt 1). Khi lát cắt sau cần lưu lịch sử trận đấu/
  season, đó là lúc mở rộng module này.
- **Elo cổ điển, K=32 cố định, KHÔNG season/decay** — `EloRating` thuần Java, test được không cần
  Port/DB. Điểm khởi điểm 1000 cho nhân vật CHƯA từng đấu (không tạo dòng DB lúc tạo nhân vật, chỉ
  tạo lúc trận đầu tiên kết thúc — tránh ghi rác cho nhân vật không bao giờ đấu).
- **`recordDuelResult` khoá {@code FOR UPDATE} + đọc + tính + ghi CẢ 2 người trong 1 transaction**
  (2026-09-20, thay `recordMatchResult` cũ) — không chỉ "ghi nguyên tử", mà ĐỌC-TÍNH-GHI nguyên tử.
  Bản cũ để `RecordDuelResultUseCase` tự đọc 2 rating rời rồi mới gọi Port ghi, hở ra khoảng trống
  giữa đọc/ghi: 1 nhân vật thắng 2 trận Arena chồng lấn thời gian (settle trên 2 Virtual Thread khác
  nhau qua `ArenaInstanceRegistry.settlementExecutor`) có thể mất nguyên 1 trận Elo — `wins`/`losses`
  vẫn đúng (cộng dồn) nhưng `rating` bị ghi đè bằng giá trị tính từ dữ liệu cũ. Khoá theo
  `character_id` TĂNG DẦN (không theo thắng/thua) để tránh deadlock giữa 2 trận chồng lấn 1 người.
  Xem `PostgresPvpRatingAdapterIntegrationTest` (server-database) cho phép đo race thật.
  ⚠️ **Trận ĐẦU ĐỜI từng lọt khoá** (sửa 2026-10-03, `98d1547`): `FOR UPDATE` không khoá được dòng
  chưa tồn tại, và dưới READ COMMITTED cũng không chờ INSERT chưa commit của giao dịch khác. Hai
  trận đầu của cùng một nhân vật settle cùng lúc ⇒ trận sau đọc 1000 mặc định, UPSERT chờ INSERT
  của trận trước rồi ghi đè ⇒ mất trọn Elo trận đầu (wins vẫn đúng). Nay `recordDuelResult` chèn dòng
  mặc định (`ON CONFLICT DO NOTHING` — câu này CÓ chờ INSERT đồng thời cùng khoá) trước khi khoá, vẫn
  theo thứ tự id tăng dần. "Không tạo dòng tới trận đầu" ở gạch đầu dòng trên vẫn đúng — dòng chỉ
  sinh ra trong giao dịch settle.
- **Cách ly PvP KHÔNG sửa `CombatSystem`/`SkillSystem`** — trận đấu diễn ra trong 1 `WorldFacade`
  instance riêng (ADR-026, giống dungeon) chỉ chứa đúng 2 người, nên "chỉ đánh được đối thủ" là hệ
  quả TỰ NHIÊN của cách ly instance, không cần thêm cờ team/faction ở tầng combat.
  ⚠️ **Lỗ hổng CŨ, KHÔNG PHẢI do lát cắt này gây ra, KHÔNG sửa ở đây** (ngoài phạm vi): nhân vật
  world CHÍNH về lý thuyết vẫn có thể `SetTargetCommand`/`UseSkillCommand` nhắm ID nhân vật khác
  (dương) — `CombatSystem`/`SkillSystem` không chặn theo sign, chỉ chặn tự nhắm chính mình
  (`WorldFacadeTest.shouldDealMoreDamage_whenAttackerElementCountersDefenderElement` dựa vào đúng
  hành vi này để test khắc chế nguyên tố bằng 3 nhân vật thay vì quái). Không có UI nào ở client
  hiện tại phơi bày đường này ở world chính; nếu sau này client cho phép chọn nhân vật khác làm
  target ngoài Arena, cần ADR riêng cho cơ chế "an toàn/safe-zone" thật.
  ⚠️ *(Rà 2026-10-03)* Mặt trái của câu trên: client hiện chỉ đặt mục tiêu là QUÁI, nên **trong Arena
  client thật cũng không nhắm được đối thủ** — trận đấu không đánh được bằng client thật. Kiểm sống
  ADR-051 PASS là nhờ `gameProbe` gửi thẳng id. Sửa phía client (không đổi protocol) đang chờ chủ dự
  án chọn hướng (["🔴 ĐANG CHỜ CHỦ DỰ ÁN"](../../../docs/README.md)).
- **Forfeit (`ExitDuelCommand`) tính THUA thật** (mất điểm) — khác `ExitDungeonCommand` (không mất
  gì). Disconnect giữa trận cũng tính THUA cho người mất kết nối (không được "trốn thua").
- **Thách đấu là request-based (giống Trade), KHÔNG cần gần nhau/cùng map** — challenge theo TÊN
  (`ChallengeDuelCommand.target_name`, gateway tự resolve qua `CharacterFacade.findByName` TRƯỚC
  khi gọi `ArenaInstanceRegistry`, cùng pattern `RequestTradeCommand`). Chỉ 1 thách đấu ĐẾN đang
  chờ tại 1 thời điểm cho mỗi người (thách đấu mới khi đang có thách đấu chờ → outcome riêng),
  hoàn toàn trong bộ nhớ (không bảng DB nào cho challenge, ephemeral như Trade session).
  Người đang ở HẦM NGỤC thì không thách, không bị thách, không vào trận được (2026-10-03,
  `DuelWhileInDungeonTest`) — chiều ngược của "hầm ngục bỏ qua người đang đấu"; cho qua là một nhân
  vật có hai thực thể sống. Trên dây có outcome RIÊNG (chủ dự án chọn 2026-10-14, README dòng 27;
  chỉ THÊM enum, `protocolVersion` không tăng): `IN_DUNGEON` / `TARGET_IN_DUNGEON` khi thách,
  `ACCEPT_DUEL_OUTCOME_IN_DUNGEON` khi chấp nhận (lời thách đã bị tiêu). Trước đó dùng tạm
  `ALREADY_IN_DUEL` / `TARGET_ALREADY_IN_DUEL` / `NOT_FOUND`, nên người chơi đọc "đang đấu".
- **Không giới hạn rating khi ghép trận** (không phải matchmaking tự động — người chơi tự chọn đối
  thủ qua tên) — MMR ở lát cắt này CHỈ là con số hiển thị/xếp hạng, chưa dùng để ghép trận tự động.

## Luật chốt 2026-10-11 (chủ dự án)

- **Hai người gục cùng tick = HOÀ, không đổi Elo.** Hiện KHÔNG có đường code: trường hợp đó không thể xảy
  ra (sát thương trừ ngay, người gục không ra đòn) — bất biến được ghim ở `ArenaAndRelogRuleTest`
  (server-world). Thêm nguồn sát thương trễ ⇒ ô đó đỏ ⇒ cài luật hoà ở `ArenaInstanceRegistry`.
- **Rớt mạng giữa trận = thua ngay**, không ân hạn.
- **World đấu trường không hồi máu ngoài chiến đấu.**

## Cứng

✅ Validate nghiệp vụ ở `application/`, trước khi chạm Port
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ `EloRating` không phụ thuộc `PvpRatingPort` — test thuần domain, không cần Fake
✅ Module khác gọi qua `PvpFacade`, không đọc bảng `pvp_rating` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Vòng đời trận đấu (challenge/world instance) — thuộc `server-gateway`, KHÔNG kéo vào module này

## Test

`domain/` test không mock (`EloRatingTest`/`PvpRatingTest`). `application/` test với
`FakePvpRatingPort` (không Mockito).
