# ADR-128: Trận pháp nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-31) — chủ dự án chọn "Trận pháp nhóm" trong lượt hỏi lô 24 (AskUserQuestion; mô tả kèm: thành viên nhóm đứng gần nhau nhận buff theo tổ hợp linh căn). Chi tiết (bán kính, con số, tên trận) do AI chọn — mọi con số là ĐỀ XUẤT CÂN BẰNG. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (1318 nhóm đổi) · [ADR-126](ADR-126-Heavenly-Tribulation.md) (chuỗi chỉ số ngoài trang bị) · [ADR-066](ADR-066-Faction-Change.md) (linh căn) |

---

## Decision

**D1 — Khi nào thành trận**: thành viên cùng nhóm, đang online, ở **cùng một World** (world chính cùng kênh, hoặc cùng
một phó bản), đứng trong **12 m** quanh mình (tính cả mình) — từ 2 người trở lên là có trận. Mỗi người tự tính trận của
mình (người đứng giữa có thể nối hai đầu mà hai đầu không nối nhau — chấp nhận, đơn giản và công bằng).

**D2 — Thưởng theo linh căn có mặt trong trận** (mỗi linh căn tính MỘT lần dù nhiều người cùng linh căn), chỉ số phẳng:

| Linh căn | Thưởng |
|---|---|
| Hoả | công +8 |
| Thuỷ | kháng +8 |
| Phong | công +4, kháng +4 |
| Lôi | công +6, thủ +2 |
| Thổ | thủ +8 |

Thêm theo số linh căn khác nhau: **3–4 ⇒ "Tam Tài Trận" máu tối đa +40**; **đủ 5 ⇒ "Ngũ Hành Trận" máu tối đa +100**.
Hai người cùng linh căn vẫn thành trận (thưởng của đúng linh căn đó) — nhóm đồng môn không bị phạt.

**D3 — Cơ chế**: nhịp **3 giây** trong `ExpansionJobs` (Virtual Thread): duyệt các nhân vật online đang ở trong nhóm,
đọc danh sách thành viên MỘT lần mỗi nhóm, vị trí từ bộ nhớ của registry (không chạm DB), World từ `WorldRouteLookup`.
Thưởng giữ ở RAM; cộng vào cuối chuỗi chỉ số ngoài trang bị (… → Thiên Kiếp → trận pháp). Chỉ khi trận của một người
**đổi** mới gửi lại `applyEquipmentBonus` vào World và báo client — đứng yên trong trận không tốn gì.

**D4 — Giao thức, chỉ thêm**: 1327 `FormationChangedEvent` (danh sách linh căn trong trận, số người, tên trận nếu có,
các chỉ số thưởng). Client hiện dòng hệ thống khi trận lập / đổi / tan. `protocolVersion` KHÔNG tăng. Không đổi schema.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bán kính 12 m, cùng World, ≥2 người; thưởng phẳng theo linh căn có mặt + máu theo số linh căn; nhịp 3 giây; RAM; opcode 1327 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 24 phần 2): `FormationRules` (luật thuần, có test) + `FormationHooks` (nhịp 3 giây,
   cache linh căn 60 giây, mắt xích cuối của `bindGuildBonus`) ở gateway; client `FormationCoordinator`; probe giải mã
   1327. Ở phó bản/đấu trường vị trí trong registry đã đóng băng ở toạ độ world chính, nên cả nhóm cùng một instance tính
   là cùng trận, không xét 12 m.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (ba probe cùng lúc, nhóm 6; tạm đổi linh căn dev rồi trả lại): Kagemaru
   (Hoả), Benkei (tạm Thổ), hieutm (tạm Thuỷ, đứng x=4) ⇒ cả ba nhận 1327 `THREE_TALENTS +8/+8/+8/+40` trong ~2,5 giây,
   máu tối đa 479→519 / 196→236 / 104→144. hieutm thoát ⇒ hai người còn lại nhận `PLAIN [Hoả, Thổ] +8/+8/+0/+0`, máu
   tối đa về 479 / 196. Ra vào trận không thành nút hồi máu: máu hiện tại giữ theo TỈ LỆ, làm tròn xuống (luật 2026-10-11).
3. ⚠️ Chủ dự án xem: toàn bộ con số ở D1–D2 do AI chọn; chưa có hình vẽ trận dưới chân (chỉ dòng chữ).
