# ADR-090: Tranh đoạt Linh Mạch (bản đầu)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Tranh đoạt Linh Mạch (bản đầu)" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: khung giờ hằng tuần, bang giữ một vùng trên world chính để tích điểm, bang thắng nhận thư thưởng + danh hiệu bang 1 tuần, data-driven). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (`safe_zone` — cùng kiểu vùng chữ nhật trên world chính) · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) · [ADR-087](ADR-087-Arena-Seasons.md) (hàng đợi thư thưởng bền) · [ADR-081](ADR-081-World-Boss.md) (luồng nền có lịch) |

---

## Decision

**D1 — Dữ liệu (V84)**: bảng `spirit_vein` là THAM SỐ (Điều 27): dải X trên world chính, lịch hằng tuần theo UTC
(`weekday` ISO, `start_minute`, `duration_minutes`), thưởng mỗi thành viên (vàng + vật phẩm). Hai cột trạng thái
`holder_guild_id`/`held_until` — bang giải tán ⇒ `SET NULL`. Seed một Linh Mạch: **Thanh Vân Linh Mạch**, X 20..32 (ngay
đông vùng an toàn tông môn), **thứ Bảy 13:00 UTC = 20:00 giờ Việt Nam, 30 phút**, thưởng 3000 vàng + 1 Linh Thú Đan.
Số là PLACEHOLDER cân bằng.

**D2 — Chấm điểm**: luồng nền ở gateway mỗi **10 giây**. Đếm người đứng trong vùng theo bang dựa trên vị trí đã biết của
world chính (`ConnectedCharacterRegistry#snapshotPositions` — cùng nguồn với lưu vị trí định kỳ); người đang ở hầm
ngục/Arena không tính. Bang **đông nhất, hơn hẳn mọi bang khác** được 1 điểm; hoà ở đầu = vùng tranh chấp, không ai được
điểm. Không có ai trong bang thì không tính. Chưa có cơ chế "chỉ người còn sống" — người chết hồi sinh ở điểm hồi sinh
nên tự rời vùng.

**D3 — Chốt khung**: khung đóng ⇒ chốt NGUYÊN TỬ và chỉ một lần (khoá dòng `spirit_vein`, khoá chính
`spirit_vein_settlement`). Bang nhiều điểm nhất thắng; hoà điểm thì bang đạt tới trước (`reached_at`) thắng. Bang thắng
giữ danh hiệu **Chủ Linh Mạch 7 ngày** kể từ lúc khung đóng; mọi thành viên lúc chốt được xếp vào hàng đợi
`spirit_vein_payout` trong CÙNG giao dịch, luồng nền gửi thư dần với khoá chống trùng suy từ (Linh Mạch, khung, nhân
vật) — server chết giữa chừng không mất, không gửi đôi (cùng cách ADR-087). Báo toàn server lúc mở khung và lúc chốt.

**D4 — Giao thức, chỉ thêm**: 731/732 `GetSpiritVeinStatusRequest`/`Response` (vùng, đang mở, giây tới lần đổi trạng
thái, bang đang giữ + hạn, bảng điểm khung đang mở). Không đổi message cũ, không tăng `protocolVersion`.

**D5 — Client**: nút "Linh Mạch" trong cửa sổ Bang (G) mở bảng chỉ đọc (`SpiritVeinCoordinator`). Danh hiệu "Chủ Linh
Mạch" hiện ở bảng đó; CHƯA gắn lên bảng tên nhân vật — để bản sau.

**D6 — Module**: nằm trong `feature-guild` (bang sở hữu khái niệm), không module mới.

## Consequences

- Chi phí: mỗi nhịp 10 giây trong khung một truy vấn bang cho mỗi người đứng trong vùng; ngoài khung chỉ một truy vấn
  danh sách Linh Mạch + hàng đợi thư. Không đụng tick thread.
- Chưa có buff cho bang giữ Linh Mạch — chỉ danh hiệu + thư thưởng (đúng "bản đầu"). Thêm buff là quyết định cân bằng
  sau.
- Vị trí đã biết có độ trễ tới nhịp lấy mẫu của world chính — chấp nhận được với nhịp chấm 10 giây.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Vùng X + lịch tuần UTC + thưởng trong `spirit_vein` (V84) | Accepted |
| 2 | 10 giây/nhịp, bang đông nhất (không hoà) +1 điểm | Accepted |
| 3 | Chốt nguyên tử một lần, giữ 7 ngày, hàng đợi thư bền | Accepted |
| 4 | 731/732 chỉ đọc; nút trong cửa sổ Bang | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `SpiritVeinRules` · `PostgresSpiritVeinAdapter` · `SpiritVeinScheduler` · `SpiritVeinCommands` · `SpiritVeinCoordinator`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ✅ Kiểm sống 2026-10-24: mở khung bằng SQL, Kagemaru (bang Thanh Hu Thu) đứng X=26 ⇒ `spirit_vein_score` 2 điểm sau ~20 s; rút `duration_minutes` cho khung đóng ⇒ chốt `winner_guild_id=4`, `held_until` +7 ngày, thư 3000 + spirit_beast_pill cho thành viên, hàng đợi thư rỗng. Ghi chú: đổi lịch bằng SQL làm bộ hẹn chốt luôn khung "tuần trước" của lịch mới (rỗng, không người thắng) — vô hại, chỉ thêm một dòng `spirit_vein_settlement`.
3. Bản sau (cần chủ dự án chọn): buff cho bang giữ, danh hiệu trên bảng tên, nhiều Linh Mạch.
