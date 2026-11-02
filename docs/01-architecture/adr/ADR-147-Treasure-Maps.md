# ADR-147: Tàng Bảo Đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Tàng Bảo Đồ" trong lượt hỏi lô 27 (AskUserQuestion; mô tả kèm: vật phẩm bản đồ kho báu — dùng → server chỉ định một toạ độ ngẫu nhiên trong vùng; tới đúng chỗ dùng lại để "đào" → nhận vàng/vật phẩm, đôi khi bật ra quái canh kho; rơi từ quái; cần migration + 2-3 opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-121](ADR-121-Chance-Encounters.md) (kỳ ngộ — cùng khuôn thưởng + gọi quái) · [ADR-098](ADR-098-Herb-Gathering.md) (tầm 2,5 m theo trục X) · [ADR-129](ADR-129-Cave-Dwelling.md) (một lệnh, trường `action`) · [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) (dải PvP) |

---

## Decision

**D1 — Dữ liệu (V119)**: vật phẩm `treasure_map` (Tàng Bảo Đồ, xếp chồng 20, không bán ở cửa hàng); `treasure_spot(code,
name_key, x, y, guardian_monster_code)` — 6 điểm chôn dọc trục chính world chính, **ngoài hai dải PvP** (179–205,
307–417) để đi đào không thành mồi cho Ma Đầu; `treasure_reward(code, weight, result_key, reward_gold,
reward_item_template_id, reward_quantity, spawns_guardian)` — 6 phần thưởng theo trọng số; `character_treasure_hunt
(character_id PK, spot_id, started_at)` — **một cuộc tìm đang mở mỗi nhân vật**, không hết hạn (đăng nhập lại vẫn còn).
Nguồn bản đồ: rơi từ `wandering_bandit` 6%, `archive_warden` 8%, `stone_ogre` 10%. Mọi số là PLACEHOLDER (Điều 27).

**D2 — Đọc bản đồ**: một giao dịch dưới khoá túi — đang có cuộc tìm ⇒ `ALREADY_HUNTING` (KHÔNG tiêu, trả lại đích
cũ); không có bản đồ ⇒ `NO_MAP`; còn lại tiêu một tấm, server chọn ĐỀU một điểm chôn, ghi cuộc tìm + audit
`TREASURE_MAP_READ`. Client nhận tên điểm + toạ độ X chính xác — trục di chuyển là một chiều (ADR-033), giấu toạ độ chỉ
bắt người chơi dò từng mét chứ không thêm quyết định nào.

**D3 — Đào**: server kiểm (a) có cuộc tìm, (b) nhân vật ở world chính (không ở hầm ngục/đấu trường), (c) vị trí (mẫu
registry, vừa vào chưa nhúc nhích thì chỗ đã lưu) cách điểm chôn **≤ 2,5 m** theo X. Qua cả ba thì mở MỘT giao dịch:
khoá túi → khoá dòng cuộc tìm (`FOR UPDATE`, phải đúng điểm vừa kiểm) → tung một phần thưởng theo trọng số → cấp vật
phẩm (túi đầy ⇒ `BAG_FULL`, rollback, **cuộc tìm vẫn còn**) → cộng linh thạch (`gold.reason.treasure`) → xoá cuộc tìm →
audit `TREASURE_DIG`. Hai lệnh đào song song: lệnh sau chờ khoá túi rồi thấy không còn cuộc tìm ⇒ `NO_HUNT` — không thể
lĩnh hai lần.

**D4 — Quái canh kho**: phần thưởng `spawns_guardian` (8/100 trọng số, kèm 400 linh thạch) sinh MỘT con
`guardian_monster_code` của điểm đó cách người đào 4 m ở world chính (đường `spawnMonsterOnce` của kỳ ngộ, không hồi
sinh). Loài theo vùng, chưa theo cấp người chơi — như ADR-121 D4.

**D5 — Giao thức, chỉ thêm**: 341/342 `TreasureHuntCommand{action: STATUS|READ|DIG}` / `TreasureHuntResponse{action,
outcome, hunting, spot_name_key, spot_x, distance, result_key, gold, item_template_id, quantity, guardian_spawned}`.
Phản hồi luôn kèm cuộc tìm còn mở SAU lệnh và khoảng cách do server tính. `protocolVersion` không tăng.

**D6 — Client**: cửa sổ mở bằng lệnh chat `/tangbaodo` (hoặc `/treasure`) — hai dòng có nút "Đọc" / "Đào", dòng trạng
thái ghi điểm chôn, X và còn bao nhiêu mét; kết quả đào hiện thêm một dòng chat hệ thống. Chữ do AI viết theo bối cảnh
tu tiên, **chủ dự án duyệt lại** (STORY §8). Dấu hiệu trên bản đồ/mặt đất để bản sau (cần art).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Đọc bản đồ là một kiểu "dùng vật phẩm" (512/513) | `UseItem` hiện chỉ có hiệu ứng tức thời (đan, phù); cuộc tìm cần trả về đích + khoảng cách — nhồi vào `UseItemResponse` là đổi nghĩa message đang chạy |
| Giữ cuộc tìm trong RAM (như bụi linh thảo ADR-098) | Bản đồ là vật phẩm đã TIÊU: khởi động lại server mà mất cuộc tìm là mất đồ của người chơi |
| Điểm chôn ngẫu nhiên liên tục trên trục X | Có thể rơi vào dải PvP, vùng an toàn hay ngoài biên nền; bảng điểm cố định kiểm soát được và đặt tên được |
| Đào mất vài giây như hái linh thảo | Thêm luồng hẹn giờ + trạng thái "đang đào" mà không thêm quyết định nào; bản đầu đào tức thời |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bản đồ rơi từ quái, cuộc tìm lưu DB (một mỗi nhân vật), điểm chôn là dữ liệu ngoài dải PvP, đào ≤ 2,5 m trong một giao dịch, phần thưởng theo trọng số có thể gọi quái canh kho | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 27): V119, `TreasurePort`/`TreasureRules`/`TreasureFacade` (feature-world),
   `PostgresTreasureAdapter`, `TreasureHooks` (gateway, opcode 341/342), `TreasureCoordinator` (`/tangbaodo`), bước probe
   `treasure:<status|read|dig>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server `installDist` thật, TLS, DB mới chạy V1→V119): `treasure:status` ⇒
   `NO_HUNT`; `read` khi túi rỗng ⇒ `NO_MAP`; thêm 3 tấm ⇒ `read` ra `old_pine_west` X=-35 (còn 2 tấm), `read` lần hai ⇒
   `ALREADY_HUNTING` (không tiêu); đào ở X=0 ⇒ `TOO_FAR` distance=35; đứng X=-35,33 ⇒ `OK` `small_cache`
   `GOLD_CHANGED_EVENT` +250 `gold.reason.treasure`, đào lại ⇒ `NO_HUNT`, audit `TREASURE_MAP_READ`/`TREASURE_DIG`. Kho
   có canh (ép trọng số tạm trên DB dev rồi trả lại): X=91 ⇒ `TOO_FAR` distance=4,0; X=96,08 ⇒ `OK` +400 và
   `stone_ogre` hiện ở X=100,08, đánh ngay.
3. ⚠️ Cân bằng — **chủ dự án xem lại**: quái canh kho không theo cấp người chơi (D4). Lúc kiểm sống, nhân vật Lv1 bị
   `stone_ogre` (Lv6, 37 sát thương/đòn) hạ sau 3 đòn. Bản đồ rơi từ quái Lv3–6 nên người cầm bản đồ thường đã qua
   vùng đó, nhưng một tấm có thể đưa người chơi thấp cấp tới điểm X=225. Hướng chỉnh nếu cần: chỉ chọn điểm chôn có
   loài canh kho ≤ cấp người đọc, hoặc thêm `min_level` cho `treasure_spot` (đổi schema ⇒ migration mới).
4. Art: dấu "X" trên mặt đất ở điểm chôn khi đang tìm (cần sprite/model — để sau).
