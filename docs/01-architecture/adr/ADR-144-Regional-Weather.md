# ADR-144: Thời tiết theo vùng (chỉ hiển thị)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn mục lô 26 "Thời tiết theo vùng: bão cát đỏ ở Hoang Vực sâu, mưa linh khí ở tông môn. Chỉ hiển thị, có thể kèm hiệu ứng nhẹ". Cách làm và mọi con số do AI chọn — **đề xuất**. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-123](ADR-123-Day-Night.md) (gói 327, cùng khuôn) · [ADR-143](ADR-143-Display-Settings.md) (công tắc) · [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) |

---

## Decision

**D1 — Chỉ hiển thị**: thời tiết KHÔNG đổi một chỉ số gameplay nào (phần "có thể kèm hiệu ứng nhẹ" của mục lô 26
chưa làm — xem "Cần chủ dự án xem lại").

**D2 — Server quyết khi nào (V116, bảng `weather_region`)**: mỗi vùng là một dải trục X của world chính; trời đổi tất
định theo đồng hồ — vùng "đang có" khi `(phút epoch + offset) mod period < active`. Hai vùng:
- `sect_spirit_rain` [−40, 100] — mưa linh khí 20 phút mỗi giờ;
- `wasteland_sandstorm` [307, 417] (trùng dải PvP Hoang Vực sâu) — bão cát đỏ 15 phút mỗi 45 phút, lệch pha 20 phút.
Nội dung ở DB, cơ chế ở Java (Điều 27); bảng đọc qua `WorldGeometryFacade` như `safe_zone`/`pvp_zone`.

**D3 — Protocol, chỉ THÊM**: `WeatherEvent` (opcode **340**, Server→Client, mọi người online) — các vùng ĐANG có
thời tiết. `WeatherJob` gửi lặp mỗi 30 giây vô điều kiện, cùng lý do với gói ngày đêm 327: người vừa vào world nhận
trạng thái trong tối đa một nhịp. `protocolVersion` KHÔNG tăng.

**D4 — Client**: `WeatherCoordinator` so vị trí của chính mình với các vùng (ngoài world chính thì trời quang);
`WeatherOverlay` vẽ ở không gian màn hình sau cảnh, trước lớp phủ đêm: vệt mưa xiên màu ngọc / màn bụi đỏ + vệt cát bay
ngang. Đậm/nhạt dần 2 giây; đổi loại thì nhạt hết loại cũ rồi mới đậm loại mới. 140 hạt trong mảng cố định — không cấp
phát mỗi khung. Công tắc "Thời tiết" thêm vào `/caidat` (ADR-143). Tối đa 32 vùng mỗi gói, vùng ngược bị bỏ.

## Kiểm chứng

- `WeatherRegionTest` (cửa sổ hoạt động + lệch pha), `WeatherJobTest` (gói chỉ chứa vùng đang có),
  `SafeZoneIntegrationTest.shouldSeedTheTwoWeatherRegions` (Postgres thật), `WeatherCoordinatorTest` (chọn theo vị
  trí, ngoài world chính trời quang, đậm/nhạt khi đổi loại).
- **Kiểm sống 2026-11-01** (gameProbe, server thật, V116, probe nay giải mã gói 340): phút epoch mod 60 = 22 ⇒ gói
  `zones=[]` đúng lịch (mưa chỉ phút 0–19, bão cát lệch pha cũng ngoài cửa sổ). Tạm đặt `offset_minutes = 40` cho mưa
  ⇒ khởi động lại ⇒ gói `zones=[sect_spirit_rain[-40.0,100.0]:WEATHER_KIND_SPIRIT_RAIN]`; trả offset về 0 sau đó.
  Lớp vẽ mưa/cát CHƯA xem bằng mắt trên client thật — chỉ unit test.

## Cần chủ dự án xem lại

- Hiệu ứng gameplay nhẹ (vd. bão cát giảm tầm nhìn/aggro, mưa linh khí hồi chi nhanh hơn) — CHƯA làm, cần quyết.
- Chu kỳ và vùng là đề xuất.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lịch thời tiết tất định theo đồng hồ, bảng DB, server phát mỗi 30 giây | Accepted |
| 2 | Thời tiết chỉ hiển thị | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-123](ADR-123-Day-Night.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Hiệu ứng gameplay nhẹ nếu chủ dự án muốn.
