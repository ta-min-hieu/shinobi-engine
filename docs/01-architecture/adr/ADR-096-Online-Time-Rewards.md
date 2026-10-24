# ADR-096: Thưởng thời gian online

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Thưởng thời gian online" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: mốc 10/30/60 phút online mỗi ngày nhận quà (vàng/đan); migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-060](ADR-060-Daily-Check-In.md) (ngày theo giờ Việt Nam, thưởng qua thư) · [ADR-095](ADR-095-Seclusion-Offline-Exp.md) |

---

## Decision

**D1 — Đếm**: gateway giữ giờ vào world của từng nhân vật trong RAM; lúc rời world cộng số giây của phiên (phần thuộc
NGÀY HÔM NAY theo giờ Việt Nam — phiên vắt qua nửa đêm chỉ tính phần sau nửa đêm) vào `online_time(character_id, day)`.
Số giây hôm nay = đã lưu + phần phiên đang chạy — server tính, client chỉ hiển thị.

**D2 — Ba mốc mỗi ngày**: **10 phút** → 300 linh thạch · **30 phút** → 2 `minor_healing_pill` · **60 phút** → 1.000 linh
thạch + 1 `spirit_herb`. Mốc đã nhận ghi ở bitmask `online_time.claimed`; nhận là MỘT câu UPDATE có điều kiện (đủ giây,
bit chưa bật) nên hai lệnh song song không nhận đôi. Quà gửi bằng THƯ hệ thống (túi đầy không mất quà — cùng cách điểm
danh). Số là PLACEHOLDER cân bằng.

**D3 — Giao thức, chỉ thêm**: 218/219 `GetOnlineRewardsRequest`/`Response{seconds_today, milestones[]}`, 220/221
`ClaimOnlineRewardCommand{milestone_minutes}`/`Response{outcome}`.

**D4 — Client**: tab "Online" của cửa sổ Hằng ngày (E) — giây hôm nay, ba mốc kèm nút Nhận.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lưu giây định kỳ mỗi phút | Ghi DB liên tục cho số liệu chỉ cần đúng lúc nhận/lúc thoát |
| Phát quà thẳng vào túi | Túi đầy ⇒ mất quà hoặc phải từ chối nhận; thư không có vấn đề đó |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Giây theo ngày VN, lưu lúc thoát, 3 mốc 10/30/60, bitmask, quà qua thư | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V86__lot21_daily.sql` · `OnlineRewardPolicy` · `PostgresOnlineTimeAdapter` · `OnlineSessions` · `DailyCommands`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 2) — test `OnlineRewardPolicyTest` (nửa đêm giờ VN), `DailyIntegrationTest`.
2. ✅ Kiểm sống 2026-10-24 (giây hôm nay đặt 650 bằng SQL): mốc 10 phút `OK` + thư 300 linh thạch; nhận lại `ALREADY_CLAIMED`; mốc 30 `NOT_REACHED`.
