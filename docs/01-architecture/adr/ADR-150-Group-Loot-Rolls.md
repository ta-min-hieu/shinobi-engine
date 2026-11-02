# ADR-150: Tung xúc xắc chia đồ nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Xúc xắc chia đồ nhóm" trong lượt hỏi lô 27 (AskUserQuestion; mô tả kèm: đồ hiếm khi đánh nhóm: mỗi người chọn Cần/Tham/Bỏ trong 15 giây, server tung xúc xắc quyết người nhận). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | Chia phần đồng đội 2026-10-11 (EXP chia, đồ rơi về người hạ) · [ADR-121](ADR-121-Chance-Encounters.md) (cửa sổ do server mở) · [ADR-098](ADR-098-Herb-Gathering.md) (trạng thái RAM + hẹn giờ nền) |

---

## Decision

**D1 — "Đồ hiếm" = trang bị**: món có `item_template.equip_slot` (mặc được). Không cần cột mới — bản đầu chưa có cột
phẩm chất cho `item_template`; đan dược, nguyên liệu, bản đồ vẫn vào túi người ra đòn cuối như cũ.

**D2 — Khi nào tung**: người ra đòn cuối ở trong nhóm VÀ có ít nhất một đồng đội **đang online cùng World** (cùng khu
vực world chính / cùng hầm ngục). Không có ⇒ không đổi gì so với trước. Người được chia = người ra đòn cuối + những đồng
đội đó.

**D3 — Một lượt tung mỗi món, 15 giây, RAM ở gateway**: `GrantLootUseCase` roll như cũ rồi TÁCH các món trang bị ra
(không vào túi ai, chưa audit); mỗi món mở một lượt, gửi 1328 cho mọi người được chia. Mỗi người chọn **Cần / Tham /
Bỏ** (1329/1330; không chọn trong hạn = Bỏ). Mọi người đã chọn ⇒ chốt ngay; không thì chốt lúc hết 15 giây.

**D4 — Phân xử, server tung**: có ai Cần ⇒ chỉ người Cần tung, không thì người Tham tung; mỗi người 1–100, cao nhất
thắng (hoà thì một số ẩn phá hoà). **Cả nhóm Bỏ ⇒ món về người ra đòn cuối** — đồ không bao giờ biến mất vì không ai bấm.

**D5 — Giao đồ**: người thắng nhận qua đúng đường đồ rơi (`GrantLootUseCase.grantRolled` — cùng giao dịch, cùng audit
`LOOT_GRANT`, kèm `ItemGrantedEvent`); túi đầy ⇒ **thư hệ thống kèm món** (khác đồ rơi thường vốn mất khi túi đầy).
Kết quả 1331 (người thắng, từng người chọn gì/tung bao nhiêu, có đi thư không) gửi mọi người được chia.

**D6 — Mất khi khởi động lại**: lượt tung đang mở nằm trong RAM — server tắt giữa 15 giây thì món đó mất, cùng mức
chấp nhận với đồ rơi khi túi đầy. Ghi bảng DB cho một cửa sổ 15 giây là thêm ghi DB vào đường hạ quái nóng nhất.

**D7 — Giao thức, chỉ thêm**: 1328 `LootRollStartedEvent`, 1329/1330 `LootRollChoiceCommand`/`Response`, 1331
`LootRollResultEvent` (dải Party). `protocolVersion` không tăng. API mới (chỉ thêm) của feature-inventory:
`InventoryFacade.grantLoot(characterId, code, Predicate divert)` và `grantRolledLoot`.

**D8 — Client**: cửa sổ "Chia chiến lợi phẩm" do server mở (như kỳ ngộ) với ba nút; nhiều món thì xếp hàng; kết quả
là một dòng hệ thống.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Thêm cột phẩm chất `item_template.rarity` | Đổi schema + phân loại lại mọi vật phẩm; trang bị đã là thứ người chơi tranh nhau |
| Chia lượt (round-robin) | Không cho người cần thật sự giành món; Cần/Tham là quy ước quen thuộc của MMO |
| Lượt tung lưu DB | Xem D6 |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Trang bị rơi khi có đồng đội cùng World ⇒ tung Cần/Tham/Bỏ 15 giây trong RAM, server tung 1–100, cả nhóm Bỏ về người hạ, túi đầy gửi thư | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [feature-inventory/CLAUDE.md](../../../server/server-features/feature-inventory/CLAUDE.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 27): `GrantLootUseCase.Loot`/`grantRolled`, `LootRollHooks` (gateway), dispatch 1329,
   `LootRollCoordinator` (cửa sổ tự mở), bước probe `lootRoll:<id>:<need|greed|pass>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 hai người cùng nhóm (DB dev tạm cho mộc nhân rơi `wooden_sword` 100%, đã gỡ sau):
   `ĐạoHữu01` hạ mộc nhân ⇒ CẢ HAI nhận 1328 `rollId`; không ai chọn trong 15 giây ⇒ món về người hạ (`ITEM_GRANTED`
   + `LOOT_GRANT` audit); `BaoDo01` chọn Tham (tung 16), người hạ không chọn ⇒ `BaoDo01` thắng, nhận món, cả hai nhận
   1331; chọn sau khi đã chốt ⇒ `NOT_FOUND`.
