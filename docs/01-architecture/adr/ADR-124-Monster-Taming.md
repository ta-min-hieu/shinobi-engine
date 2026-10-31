# ADR-124: Thu phục yêu thú

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Thu phục yêu thú" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: quái < 20% máu + Phong Yêu Phù ⇒ thu phục thành linh thú ADR-072, tỉ lệ theo cấp). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-072](ADR-072-Spirit-Pets.md) (linh thú ô PET) · [ADR-117](ADR-117-Revive-In-Place.md) (khuôn tiêu-trước-hoàn-sau + hỏi World có trần chờ) |

---

## Decision

**D1 — Điều kiện**: nhắm một con quái, gõ `/thuphuc`. Server kiểm: có **Phong Yêu Phù** (tiêu một lá), đứng trong **6 m**,
quái còn **≤ 20% máu**, loài nằm trong bảng `tameable_monster` (V104 — boss không có). Tỉ lệ = tỉ lệ gốc của loài, trừ
**5% mỗi cấp quái hơn người**, sàn 5% (`TamingRules`). Ngẫu nhiên ở server (nguồn random gameplay của World).

**D2 — Một lượt tick, không chạy đua**: gateway tiêu phù (DB) rồi gửi lệnh `CaptureMonster` vào World; trong CÙNG một lượt
tick World kiểm tầm/máu/loài, tung tỉ lệ và nếu trúng thì gỡ quái theo đường "quái chết" nhưng **không rơi đồ, không
EXP** (điểm spawn lên lịch hồi sinh như thường). Bảng tỉ lệ tính sẵn trên Virtual Thread — tick thread chỉ tra bảng.
World từ chối vì điều kiện ⇒ **hoàn phù**; chỉ **tung trượt** mới mất phù (quái vẫn đó, thử lại được).

**D3 — Phần thưởng**: một linh thú (ô PET, ADR-072) theo loài vào túi + audit `TAME_MONSTER`; túi đầy thì linh thú đi
**qua thư** (không mất con đã bắt). Bản đầu hai loài: Thạch quái hộ mạch (`stone_ogre`, gốc 40%) ⇒ **Thạch Linh** (thủ/kháng/
máu), yêu thú Hoang Vực (`wasteland_beast`, gốc 30%) ⇒ **Hoang Lang** (công/máu); cùng cấp yêu cầu 20 của ô PET. Phong Yêu
Phù bán ở cửa hàng tông môn 300 linh thạch. Mọi con số là đề xuất cân bằng.

**D4 — Giao thức, chỉ thêm**: 328/329 `TameMonsterCommand/Response` (`TameOutcome`). Client không có cửa sổ — kết quả
hiện thành dòng hệ thống.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phong Yêu Phù + ≤20% máu + 6 m; tỉ lệ gốc −5%/cấp chênh; bắt nguyên tử trong tick; trượt mất phù, sai điều kiện hoàn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 3b): `TamingRules` (có test) / `TamingPort` / `TamingFacade`;
   `PostgresTamingAdapter` (V104); World `CaptureMonsterCommand` + `CaptureRule`/`CaptureResult`; gateway
   `TamingCommands`; client `TamingCoordinator` (`/thuphuc`); bước probe `tame:<monsterId>`; test Postgres.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V104, bước probe `beatTame:<loài>:<giây>`): tanthua44 (Lv15)
   đánh Thạch quái 200 máu đều 50/nhát ⇒ 200→150→100→50→**0**, KHÔNG lần nào rơi vào ô ≤ 20% (≤ 40) — xem mục 4. Tạm
   nâng máu Thạch quái lên 230 trong DB dev (đã trả lại 200): 230→…→30 (13%) ⇒ `/thuphuc` ⇒ **CAPTURED** lần đầu,
   Thạch Linh vào túi, phù 3 → 2, audit `TAME_MONSTER`. Nhánh hoàn phù: hình nộm đầy máu ⇒ HP_TOO_HIGH, Huyết Sa Hộ
   Pháp cách 110 m ⇒ TOO_FAR, id lạ ⇒ NOT_FOUND — phù vẫn 2.
3. ⚠️ Chủ dự án xem: hai loài/linh thú bản đầu và tỉ lệ (D3); trả lời quá trần chờ 2 giây sau khi World đã bắt thì phù
   được hoàn nhưng không có linh thú (hiếm, giống ADR-117).
4. ⚠️ **Phát hiện khi kiểm sống — cần chủ dự án quyết**: sát thương đòn thường gần như cố định, nên ô ≤ 20% máu có thể
   bị NHẢY QUA hoàn toàn (Thạch quái 200 máu, đòn 50 ⇒ không bao giờ ≤ 40). Đề xuất: (a) nâng ngưỡng lên 30%, hoặc (b)
   cho dùng phù khi còn ≤ 20% HOẶC ≤ một đòn của chính người dùng, hoặc (c) giữ và để người chơi tự chọn vũ khí/chiêu
   yếu hơn. Chưa tự đổi — mô tả lượt hỏi ghi rõ "< 20%".
