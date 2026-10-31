# ADR-126: Thiên Kiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Thiên Kiếp" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: tầng 9 → cảnh giới mới phải độ kiếp; World riêng, N đợt lôi kích (% máu) có giới hạn giờ; thua mất 10% EXP tầng, thắng +chỉ số vĩnh viễn). Chi tiết do AI chọn theo lời chủ dự án ngày 2026-10-31: "tự chọn chi tiết Thiên Kiếp rồi làm tiếp đi". |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) D2 (6 cảnh giới × 9 tầng) · [ADR-089](ADR-089-Rebirth.md) (chuyển thế) · ADR-113 (World riêng một người của Trấn Yêu Tháp) |

---

## Decision

**D1 — Trần cảnh giới**: tầng 9 của năm cảnh giới đầu — **cấp 18, 36, 54, 72, 90** (đúng mốc `CultivationRealm` ở
client) — là trần. Chưa độ kiếp thì EXP **vẫn tích** nhưng cấp đứng yên (`ExperienceCurve.progressOf(exp, trần)`, đường
suy cấp DUY NHẤT là `GrantExperienceUseCase`). Qua đủ 5 kiếp thì lên tới 100. Cột `character.tribulations_passed`
(V106); nhân vật đã vượt mốc trước luật này được tính là đã qua các kiếp bên dưới cấp hiện tại — không ai tụt cấp.
Chuyển thế (ADR-089) đưa số kiếp về 0 cùng cấp và EXP.

**D2 — Độ kiếp**: `/dokiep` khi đứng ở trần và EXP đã đủ cho cấp kế. Server đưa vào một **World riêng không quái**
(Bí Cảnh một người, cùng đường nhận/dời của Trấn Yêu Tháp). Sau 5 giây, lôi kích giáng **3 + số kiếp đã qua** đợt (3→7),
cách nhau **6 giây**, mỗi đợt **20% máu tối đa** — tính là đang chiến đấu nên hồi máu ngoài chiến đấu không chạy; người
chơi phải tự dùng đan dược ở các kiếp sau (kiếp cuối tổng 140% máu). "Giới hạn giờ" chính là chuỗi đợt có nhịp cố định:
sống qua đợt cuối thêm một nhịp là thắng.

**D3 — Thua**: gục (đường gục của Bí Cảnh tự đưa về world chính) hoặc rời đài giữa chừng / rớt mạng ⇒ mất **10% lượng EXP
cần cho cấp kế** (không bao giờ tụt xuống dưới đầu cấp trần), audit `TRIBULATION_FAILED`.

**D4 — Thắng**: ghi kiếp (compare-and-set, audit `TRIBULATION_PASSED`) TRƯỚC khi đưa về world chính, rồi đồng bộ vạch
EXP — lên cấp ngay vì EXP đã đủ. Thưởng vĩnh viễn **mỗi kiếp: +3 công, +3 thủ, +3 kháng, +30 máu tối đa**, cộng qua đúng
chuỗi chỉ số ngoài trang bị đang có (kỹ năng bang → chuyển thế → sưu tầm → tẩy luyện → Thiên Kiếp).

**D5 — Trạng thái kiếp chỉ ở RAM** (`TribulationHooks`, nhịp 1 giây trong `ExpansionJobs`): server khởi động lại giữa kiếp
thì kiếp đó coi như chưa diễn ra, không phạt ai.

**D6 — Giao thức, chỉ thêm**: 330/331 `StartTribulationCommand/Response`, 332 `TribulationEvent` (STRIKE/PASSED/FAILED);
lệnh World mới `StrikeCharacterCommand`. Client hiện dòng hệ thống; máu tụt hiện qua vạch máu như mọi đòn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Trần cấp 18/36/54/72/90; World riêng không quái; 3→7 đợt × 20% máu cách 6 giây; thua −10% EXP cấp kế; thắng +3/+3/+3/+30 mỗi kiếp | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 3d): `TribulationRules` (có test) / `TribulationPort` / `TribulationFacade`;
   `ExperienceCurve.progressOf(exp, trần)` + constructor có trần của `GrantExperienceUseCase`; `PostgresTribulationAdapter`
   (V106); chuyển thế reset số kiếp; World `StrikeCharacterCommand`; `DungeonInstanceRegistry.enterSolo`; gateway
   `TribulationHooks` + `TribulationEffects`; client `TribulationCoordinator` (`/dokiep`); bước probe `dokiep:<giây>`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V106; quy đổi đúng: Kagemaru cấp 42 ⇒ đã qua 2 kiếp):
   Kagemaru `/dokiep` ⇒ NOT_READY. tanthua44 nạp EXP 17.150 (đủ cấp 19): vào world vạch EXP hiện cấp 18 (bị trần giữ);
   `/dokiep` ⇒ OK 3 đợt ⇒ ba gói 332 STRIKE, máu 212 → 170 → 128 → 86 (đúng 42 = 20%) ⇒ gói 308 MAIN, EXP cấp 19
   leveledUp, PASSED, máu tối đa 212 → 244; DB `tribulations_passed = 1`, audit `TRIBULATION_PASSED`. Benkei vào đài,
   chịu đợt 1 rồi rớt mạng ⇒ FAILED: EXP 17.150 → 16.970 (−180 = 10% của 1.800), sàn 15.300, số kiếp vẫn 0. Nhánh gục
   vì sét chưa thử sống (máu 3 đợt đầu không đủ hạ ai) — đường gục là đường có sẵn của Bí Cảnh.
3. ⚠️ Chủ dự án xem: toàn bộ con số ở D1–D4 do AI chọn; chưa có hình/hiệu ứng sét (chỉ dòng chữ + vạch máu).
