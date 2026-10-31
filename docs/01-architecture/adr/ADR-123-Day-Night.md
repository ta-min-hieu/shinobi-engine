# ADR-123: Ngày đêm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Ngày đêm" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: 1 giờ thật = 1 ngày game; đêm quái +15% mạnh, rơi đồ +20%, vài loài chỉ ra ban đêm; client tối dần nền). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-067 (sự kiện x2 — cùng đường nhân lượt rơi đồ) · ADR-037 (điểm spawn là dữ liệu) |

---

## Decision

**D1 — Đồng hồ server, không lưu gì**: một giờ thật là một ngày; phút **40–59 của mỗi giờ (UTC)** là đêm (`DayNightCycle`,
hàm thuần ở feature-world). Không cột DB, không trạng thái — mọi tiến trình tính ra cùng một kết quả từ đồng hồ.

**D2 — Đêm mạnh hơn, giàu hơn**: đòn thường của **quái** gây **115%** sát thương (người chơi không đổi; chiêu boss/kỹ
năng quái chưa nhân — bản đầu chỉ đòn thường, nơi gần như mọi sát thương quái thường tới). Rơi đồ kỳ vọng **+20%**: mỗi lần
hạ quái ban đêm có 20% được thêm một lượt tung bảng rơi đồ (nhân chung với sự kiện x2 của ADR-067). Ngẫu nhiên ở server.

**D3 — Điểm spawn chỉ ra ban đêm** (`monster_spawn.night_only`, V103): ban ngày điểm đó chờ, không hồi sinh; tới đêm thì ra
quái như thường. Quái đã ra lúc đêm **không tự biến mất** khi trời sáng — chỉ không hồi sinh tới đêm sau (giữ đơn giản,
không có đường "xoá quái đang sống" mới trong tick). Bản đầu đánh dấu con THỨ HAI của hai cặp quái trùng loài: Huyết Sa
Hộ Pháp ở x=136 và yêu thú Hoang Vực ở x=200 — ban ngày mỗi loài vẫn còn một con nên không chặn nhiệm vụ. Là đề xuất nội
dung, chủ dự án duyệt lại.

**D4 — Đường đi**: gateway chạy nhịp 30 giây (`DayNightJob` trong `ExpansionJobs`) — gửi lệnh `SetNight` vào MỌI kênh world
chính (vào tick thread qua inbox như mọi lệnh khác; kênh mới mở nhận trong tối đa một nhịp) và phát gói **327
`DayNightEvent`** cho mọi người online. Gửi lặp vô điều kiện thay vì chỉ lúc đổi — người vừa vào world nhận trạng thái
trong ≤ 30 giây mà không cần đường riêng. Bí Cảnh/Arena không nhận (luôn là "ngày").

**D5 — Client chỉ là hình**: lớp phủ tối trong suốt trên cảnh (dưới HUD) + màu nền tối theo, mờ dần chừng 4 giây; một dòng
hệ thống lúc trời chuyển. Không tính gì gameplay ở client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phút 40–59 mỗi giờ là đêm; quái đánh thường 115%; +1 lượt rơi đồ 20%; điểm spawn chỉ-đêm; nhịp 30 giây + gói 327 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 3a): `DayNightCycle` (có test); world `SetNightCommand` + `CombatSystem`
   nhân sát thương quái (có test) + chặn hồi sinh điểm chỉ-đêm; V103; `DayNightJob`; lượt rơi đồ ban đêm ở
   `ServerDatabaseBootstrap`; client `DayNightShade`/`DayNightCoordinator`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V103): server bật lúc 06:47 UTC (đêm), tanthua44 đứng ở x=128
   ⇒ thấy Huyết Sa Hộ Pháp ở x=120 VÀ x=136 (điểm chỉ-đêm ra sau khi nhịp đầu báo đêm), nhận gói 327. Khởi động lại
   lúc 07:01 UTC (ngày) ⇒ chỉ còn con ở x=120, điểm x=136 không ra, gói 327 đêm=false. Sát thương 115% kiểm bằng test
   `CombatSystemTest` (cùng seed: quái ×1,15, người chơi không đổi); rơi đồ +20% kiểm bằng `DayNightCycleTest`. Lớp phủ
   tối của client CHƯA nhìn bằng mắt (probe không vẽ).
3. ⚠️ Chủ dự án xem: hai điểm chỉ-đêm (D3); chiêu boss chưa nhân 115% (D2); quái chỉ-đêm không tan lúc sáng (D3).
