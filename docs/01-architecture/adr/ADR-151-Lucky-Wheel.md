# ADR-151: Vòng quay may mắn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Vòng quay may mắn" trong lượt hỏi lô 27 (AskUserQuestion; mô tả kèm: mỗi ngày 1 lượt miễn phí + lượt mua bằng linh thạch; server quay theo trọng số, audit, trần lượt/ngày). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-149](ADR-149-Daily-Fortune.md) (cùng module `feature-checkin`, cùng ngày giờ VN) · [ADR-121](ADR-121-Chance-Encounters.md) (phần thưởng theo trọng số) |

---

## Decision

**D1 — Dữ liệu (V122)**: `wheel_prize(code, label_key, weight, reward_gold, reward_item_template_id, reward_quantity)` —
8 ô: túi linh thạch nhỏ 200 (30), đan dược ×2 (20), linh thảo ×5 (15), túi lớn 800 (12), Truyền Tống Phù (10), Hộ
Linh Phù (7), Tàng Bảo Đồ (5), **Kho báu tông môn 10.000** (1). `character_wheel_spin(character_id, day, paid,
prize_id, spun_at)` — mỗi lượt một dòng. Số là PLACEHOLDER (Điều 27).

**D2 — Lượt**: mỗi ngày (giờ Việt Nam, như quẻ bói/điểm danh) **1 lượt miễn phí**, sau đó tối đa **10 lượt mua**, mỗi
lượt **1.000 linh thạch**. Một nút "Quay": còn lượt miễn phí thì dùng nó, không thì lượt mua. Quay ở đâu cũng được.

**D3 — Một giao dịch**: khoá túi → đếm lượt hôm nay → (lượt mua) trừ linh thạch `gold.reason.lucky_wheel` → server
chọn ô theo trọng số → trao vật phẩm (túi đầy ⇒ `BAG_FULL`) / cộng linh thạch (vượt trần ⇒ `GOLD_CAP`) → ghi lượt +
audit `LUCKY_WHEEL_SPIN`. Từ chối ở bất kỳ bước nào ⇒ rollback trọn: **không mất lượt, không mất tiền**.

**D4 — Kinh tế**: giá trị linh thạch kỳ vọng của một lượt ≈ 60 + 96 + 100 = 256 (chưa tính vật phẩm) < 1.000 —
vòng quay **cố ý là chỗ hút linh thạch** khỏi nền kinh tế, đổi lại cơ hội trúng vật phẩm/kho báu. Chủ dự án chỉnh
bằng migration sau khi có số liệu Closed Beta.

**D5 — Giao thức, chỉ thêm**: 232/233 `GetLuckyWheelRequest{}` / `GetLuckyWheelResponse{prizes[], free_spins_left,
paid_spins_left, spin_cost}` (chỉ đọc), 234/235 `SpinLuckyWheelCommand{}` / `SpinLuckyWheelResponse{outcome, prize,
paid, free_spins_left, paid_spins_left}`. `protocolVersion` không tăng.

**D6 — Client**: lệnh chat `/vongquay` (hoặc `/wheel`) mở cửa sổ: dòng đầu là nút "Quay" kèm số lượt còn lại và giá,
các dòng sau là 8 ô thưởng; trúng gì in thêm một dòng hệ thống. Chưa có hình vòng quay xoay (cần art) — để sau.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lượt mua không giới hạn | Biến thành máy đánh bạc vô tận; trần 10/ngày giữ nó là hoạt động hằng ngày |
| Hai nút "quay miễn phí" / "quay mua" | Người chơi phải tự nhớ còn lượt miễn phí không; một nút + server chọn là đủ |
| Quay phải đứng gần NPC | Không thêm quyết định nào; vòng quay là hoạt động hằng ngày như điểm danh |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1 lượt miễn phí + 10 lượt mua 1.000 mỗi ngày giờ VN, 8 ô theo trọng số, một giao dịch, từ chối thì rollback trọn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 27): V122, `LuckyWheelPort`/`LuckyWheelRules`/`LuckyWheelFacade` (feature-checkin),
   `PostgresLuckyWheelAdapter`, `LuckyWheelCommands` (232–235), `LuckyWheelCoordinator` (`/vongquay`), bước probe
   `wheel`, `spin`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, TLS, DB chạy V122): `wheel` ⇒ 8 ô, 1 miễn phí, 10 lượt mua, giá
   1.000; `spin` lượt miễn phí ⇒ Hộ Linh Phù vào túi; còn 500 linh thạch ⇒ `INSUFFICIENT_GOLD`, không đổi gì; nạp
   5.000 ⇒ hai lượt mua (`GOLD_CHANGED_EVENT` −1.000 rồi +200 túi nhỏ; −1.000 rồi Truyền Tống Phù), còn 3.200, `wheel`
   báo còn 8 lượt mua; 3 dòng `character_wheel_spin` + 3 audit `LUCKY_WHEEL_SPIN`.
3. Art: hình vòng quay xoay (để sau).
