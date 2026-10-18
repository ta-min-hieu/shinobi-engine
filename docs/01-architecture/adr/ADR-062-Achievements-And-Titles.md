# ADR-062: Thành tựu + danh hiệu — đếm mốc từ sự kiện có sẵn, danh hiệu hiện trong chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn "Thành tựu + danh hiệu" trong lượt hỏi lô 16 (AskUserQuestion; mô tả kèm: đếm mốc — hạ N quái, xong arc, thắng Arena…, mở danh hiệu hiện cạnh tên, gắn vào event có sẵn). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) · [ADR-057](ADR-057-Leaderboards.md) · [ADR-060](ADR-060-Daily-Check-In.md) |

---

## Context

Ngoài cấp và Lực Chiến, người chơi không có mục tiêu dài hạn nào nhìn thấy được. Mọi sự kiện cần đếm
đã có sẵn ở gateway: hạ quái (`onMonsterKilled`), lên cấp (`sendExperience` → `CharacterLevelSync`),
lĩnh thưởng quest, thắng Arena (`ArenaInstanceRegistry` chốt trận), điểm danh (ADR-060).

## Decision

**D1 — Năm loại mốc** (`AchievementKind`): `KILL_MONSTERS`, `CLAIM_QUESTS`, `WIN_DUELS`, `CHECK_IN` (đếm
cộng dồn, bảng `character_achievement_counter`) và `REACH_LEVEL` (so thẳng với cấp, không đếm). Bộ đầu 8
thành tựu (V64, tham số — Điều 27): hạ 10/100/1000 quái, đạt Cấp 10/30, lĩnh 10 thưởng nhiệm vụ, thắng
1/10 trận luận kiếm, điểm danh 7 lần. "Xong arc" = mốc cấp/nhiệm vụ (Q1 theo khuyến nghị — không gắn
mã quest cụ thể vào bảng, để nội dung đổi không vỡ thành tựu).

**D2 — Mỗi thành tựu mở MỘT danh hiệu** (`title_key`). Người chơi chọn tối đa một danh hiệu đang đeo
(`character_title`); chỉ đeo được danh hiệu đã mở (server kiểm trong cùng câu SQL).

**D3 — "Hiện cạnh tên" = dòng chat + bảng nhân vật** (Q2 theo khuyến nghị). Thế giới chưa vẽ tên trên
đầu nhân vật nào (không có nameplate) — thêm nameplate là việc render riêng. `ChatMessageEvent` thêm
`sender_title_key = 5` (chỉ thêm); client vẽ `[Danh hiệu] Tên`. Gateway giữ danh hiệu đang đeo trong bộ
nhớ (`ActiveTitles`, nạp lúc vào world, đổi khi đeo/bỏ, xoá lúc rời) — không đọc DB mỗi tin chat.

**D4 — Ghi tiến độ là việc PHỤ.** Chạy trên Virtual Thread của sự kiện gốc (hạ quái: một bước riêng
`ACHIEVEMENT` của `KillRewardDispatcher`); lỗi chỉ log, không làm hỏng phần thưởng chính. Mở khoá =
`INSERT ... ON CONFLICT DO NOTHING` ⇒ chỉ lượt chèn được mới báo, hai luồng cùng vượt mốc không báo đôi.
Cấp hiện tại được ghi lại mỗi lần vào world — nhân vật lên cấp trước khi có tính năng này tự mở thành tựu cấp.

**D5 — Giao thức: dải MỚI 1700–1799 "Achievement / Title"** (NETWORK.md §4 cập nhật), 5 opcode:
1700/1701 `ListAchievementsRequest`/`ListAchievementsResponse`, 1702/1703
`SetActiveTitleCommand`/`SetActiveTitleResponse`, 1704 `AchievementUnlockedEvent`. `protocolVersion` không tăng.

**D6 — Client.** Cửa sổ "Thành tựu" (phím **U** — H đã là của khung chọn hệ, `ReportPanel`): mỗi dòng tên + tiến độ, dòng đã mở có
nút "Đeo"; dòng đầu "Bỏ danh hiệu". Mở khoá ⇒ dòng hệ thống trong chat.

**D7 — Module mới `feature-achievement`** (domain thuần + use case + facade; adapter ở server-database).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Nameplate trên đầu nhân vật | Chưa có tên trên đầu ai cả — việc render riêng, lớn hơn cả tính năng này |
| Đếm bằng truy vấn lại `audit_log` | Không phải mọi sự kiện có audit (hạ quái không có); quét bảng lớn mỗi lần mở cửa sổ |
| EventBus engine-ecs | Các nguồn sự kiện đều ở gateway, chưa tới ngưỡng pub-sub (cùng lý do feature-quest) |
| Thành tựu theo mã quest | Nội dung quest đổi/đổi mã là thành tựu chết; mốc số lượng bền hơn |

## Consequences

- Một UPSERT `character_achievement_counter` mỗi lần hạ quái (cạnh 3 lần ghi sẵn có: rơi đồ, quest, EXP).
- Danh hiệu chỉ hiện ở chat + bảng thành tựu cho tới khi có nameplate.

## Revisit Trigger

- Có nameplate ⇒ `CharacterSpawnedEvent` mang danh hiệu.
- Thành tựu có thưởng vật phẩm ⇒ gửi qua thư hệ thống như ADR-060.

## Technical Decisions

- `AchievementHooks` (gateway) là nơi DUY NHẤT nối sự kiện → `AchievementFacade`. `ConnectedCharacterRegistry` giữ nó ở một TRƯỜNG package-private (không getter/setter — lớp đó đang đúng trần độ phức tạp PMD 80) và `GatewayBootstrap` gọi `bind` sau khi DB dựng; cú lên cấp đi qua `CharacterLevelSync` (chỗ duy nhất biết "đây là lên cấp").
- Mở khoá bằng `ON CONFLICT DO NOTHING RETURNING`; đeo danh hiệu bằng `INSERT ... SELECT ... WHERE EXISTS (đã mở)`.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `achievement.proto` · `V64__achievements_and_titles.sql` · `feature-achievement` · `AchievementHooks` · `AchievementCoordinator`
- Test: `AchievementUseCaseTest`, `AchievementDatabaseTest`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-18 (lô 16).
2. ⏳ Kiểm sống: hạ 10 quái ⇒ dòng "Đạt thành tựu"; đeo danh hiệu ⇒ chat hiện `[Danh hiệu] Tên` ở client kia.
