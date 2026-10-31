# ADR-121: Kỳ ngộ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Kỳ ngộ" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: đi/đánh quái có xác suất nhỏ (server tung) gặp NPC ẩn hỏi 1 câu ⇒ thưởng hoặc trận đánh; 3 lần/ngày; nội dung script). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-113](ADR-113-Demon-Tower.md) (sinh quái một lần `spawnMonsterOnce`) · [ADR-101](ADR-101-Bounty-Board.md) (bước phụ của đường thưởng hạ quái) |

---

## Decision

**D1 — Kích hoạt khi hạ quái ở world chính**: người ra đòn cuối có **2%** gặp kỳ ngộ (server tung, trượt thì không chạm
DB), tối đa **3 lần/ngày** (ngày theo giờ Việt Nam), mỗi lúc **một** kỳ ngộ chờ, **10 phút** không chọn thì tan (vẫn tính
vào trần ngày). Không kích hoạt trong Bí Cảnh/Arena. Chỉ "đánh quái", không "đi đường" — di chuyển chạy trên tick thread,
gắn xác suất vào đó là thêm việc chạm DB vào đường nóng nhất; hạ quái đã có sẵn bước phụ trên Virtual Thread.

**D2 — Mở và chọn đều là MỘT giao dịch** mở bằng khoá túi của nhân vật (thứ tự khoá chung): hai lượt hạ quái song song
không thể mở hai kỳ ngộ hay vượt trần ngày; chọn = khoá dòng kỳ ngộ (của mình, chưa chọn, còn hạn) → cấp vật phẩm (túi
đầy ⇒ `BAG_FULL`, kỳ ngộ vẫn chờ) → cộng linh thạch (`gold.reason.encounter`) → đánh dấu đã chọn → audit
`ENCOUNTER_CHOICE`. Chọn lần hai không được thưởng lại.

**D3 — Nội dung là DỮ LIỆU, không phải script** (V101: `encounter`, `encounter_option`, `character_encounter`; chữ ở
bundle i18n). ⚠️ **Lệch mô tả lượt hỏi** ("nội dung script"): mỗi lựa chọn chỉ cần thưởng cố định và/hoặc gọi một loài
quái — bảng DB đủ diễn tả, lại được cổng `verifyContentKeys` soát khoá chữ; script GraalJS (sandbox mới ở mức
CONSTRAINED, ADR-016) để dành khi có kỳ ngộ cần logic thật (rẽ nhánh theo cấp, hệ phái...). Ba kỳ ngộ bản đầu: lão nhân
bên đường (linh thạch / đan / gọi cường đạo), suối linh (linh thảo / linh thạch), bia phong ấn (linh thạch / nhiều linh
thạch + gọi thạch quái). Chữ do AI viết theo bối cảnh tu tiên, **chủ dự án duyệt lại** (STORY §8).

**D4 — "Trận đánh"**: lựa chọn có `spawn_monster_code` thì server sinh MỘT con quái (không hồi sinh) cách người chơi 4 m
ở world chính — hạ nó được thưởng thường như mọi con quái. Loài quái cố định theo dữ liệu, chưa theo cấp người chơi.

**D5 — Giao thức, chỉ thêm**: 324 `EncounterStartedEvent` (server mở cửa sổ), 325/326 `ChooseEncounterOption`
`Command/Response`. `protocolVersion` không tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 2%/lượt hạ quái ở world chính, 3/ngày giờ VN, một chờ, 10 phút; nội dung DB; lựa chọn có thể gọi một con quái | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 2c): feature-world `EncounterRules` (có test) / `EncounterPort` /
   `EncounterFacade`; `PostgresEncounterAdapter` (V101); gateway `EncounterHooks` (bước phụ đường thưởng hạ quái + lệnh
   chọn); client `EncounterCoordinator`; bước probe `encounter:<id>:<lựa chọn>`; test Postgres trong
   `Lot23IntegrationTest`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V102; kỳ ngộ chèn bằng SQL vì 2% quá hiếm để chờ — đường mở
   kỳ ngộ từ lượt hạ quái chỉ kiểm qua test): bia phong ấn, lựa chọn 7 ⇒ INVALID_OPTION; lựa chọn 1 ⇒ OK +300 linh thạch
   (gói 209 `gold.reason.encounter`), chọn lại ⇒ NOT_FOUND, id lạ ⇒ NOT_FOUND. Lần đầu quái KHÔNG xuất hiện: nhân vật vừa
   vào world chưa nhúc nhích thì registry chưa có vị trí — đã sửa (đọc vị trí đã lưu như `NpcProximity`), chạy lại: lão
   nhân, lựa chọn 2 ⇒ `wandering_bandit` hiện ở x=4 (người chơi x=0). Suối linh, lựa chọn 0 ⇒ Linh Thảo 4 → 7. Audit
   `ENCOUNTER_CHOICE` đủ 3 dòng.
3. ⚠️ Chủ dự án xem: chữ ba kỳ ngộ (D3); quái gọi ra chưa theo cấp người chơi (D4).
