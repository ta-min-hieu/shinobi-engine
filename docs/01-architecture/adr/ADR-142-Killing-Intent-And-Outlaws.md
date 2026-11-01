# ADR-142: Sát khí và Ma Đầu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn mục lô 26 "Phạt giết người (sát khí): giết người chơi ở Hoang Vực tăng điểm sát khí; sát khí cao bị lính trạm truy đuổi và rơi vàng khi chết". Mọi con số và cách hiểu "lính trạm truy đuổi" do AI chọn — **đề xuất**, xem mục "Cần chủ dự án xem lại". |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) · [ADR-141](ADR-141-Zone-Map-HUD-And-Wasteland-Waypoints.md) · [ADR-102](ADR-102-Guild-War.md) · [ADR-140](ADR-140-Overhead-Realm-Title.md) · [ADR-100](ADR-100-Teleport-Talisman.md) |

---

## Bối cảnh

ADR-137 mở dải PvP tự do ở Hoang Vực mà không có giá phải trả: hạ người khác không mất gì. ADR-137 "Việc cần làm tiếp
theo" đã ghi cần chủ dự án quyết phạt/thưởng; lô 26 chủ dự án chọn hướng sát khí.

## Decision

**D1 — Sát khí (V115, bảng `character_killing_intent`)**: người chơi hạ một người chơi khác mà người bị hạ đứng trong
dải PvP tự do (vị trí cuối ở world chính), **không** phải lần hạ bang chiến (hai người cùng đang trong giờ chiến) và
người bị hạ **không** phải Ma Đầu ⇒ người hạ +1 sát khí, tối đa 10. Sát khí tự tan **1 điểm mỗi 30 phút**, kể cả
offline — tính lười từ `updated_at`, không job nào ghi lại DB.

**D2 — Ma Đầu = sát khí ≥ 3** (đề xuất cân bằng):
- **Lính trạm không che** (cách AI hiểu "lính trạm truy đuổi"): trong dải PvP, trạm an toàn giữa dải (vd. Trạm Tử Mạch
  Sâu @368) không còn chặn đòn nhắm vào Ma Đầu (`PlayerVersusPlayerRule.isHunted`). Trạm NGOÀI dải PvP vẫn che như cũ.
- **Không được truyền tống** — `TELEPORT_OUTCOME_OUTLAW` (giá trị enum mới).
- **Chết thì rơi vàng**: 10% linh thạch, tối đa 5.000 mỗi lần chết, bởi bất kỳ ai (kể cả quái); người hạ là người chơi
  thì nhận đúng số đó. Một transaction: kiểm lại Ma Đầu TRONG transaction, trừ + cộng qua `GoldLedger` (gói 209 tự phát)
  + `audit_log` (`OUTLAW_GOLD_DROP`). Người hạ chạm trần vàng ⇒ cả lượt rơi bị huỷ.
- Hạ Ma Đầu **không** sinh sát khí (trừ ma vệ đạo).
- Thành Ma Đầu ⇒ báo toàn server một dòng hệ thống song ngữ.

**D3 — Protocol, chỉ THÊM**: `CharacterSpawnedEvent.outlaw = 7`, `CharacterAppearanceChangedEvent.outlaw = 5`,
`TeleportOutcome.TELEPORT_OUTCOME_OUTLAW = 6`. `protocolVersion` KHÔNG tăng.

**D4 — Gateway**: `KillingIntentHooks` — `onCharacterDefeated` đến từ tick thread chỉ lọc bằng RAM (vị trí cuối, dải
PvP nạp một lần, ảnh chụp Ma Đầu dạng mảng id đã sắp xếp) rồi đẩy việc DB sang Virtual Thread. Ảnh chụp làm mới mỗi
30 giây (bắt phân rã) và ngay khi ai vừa thành Ma Đầu; ai đổi trạng thái thì World đang chứa họ phát lại gói 307. Người
hạ nhận một dòng hệ thống "Sát khí +1 (n/10)".

**D5 — Client**: nhãn đỏ "Ma Đầu" một dòng phía trên danh hiệu cảnh giới của người khác (`RealmTitles`, không cấp phát
mỗi khung). Chính mình không có nhãn — biết qua dòng hệ thống.

## Kiểm chứng

- `KillingIntentRulesTest` (phân rã, trần, ngưỡng, vàng rơi); `KillingIntentIntegrationTest` (Postgres thật: 3 lần hạ ⇒
  Ma Đầu, 30 phút sau hết; Ma Đầu 8.000 vàng chết ⇒ người hạ +800; người thường không rơi gì).
- `PlayerVersusPlayerRuleTest`: Ma Đầu trong trạm an toàn giữa dải bị đánh trúng; người thường cùng chỗ thì không.
- **Kiểm sống 2026-11-01** (gameProbe hai người, server thật, V115): Kagemaru đặt sẵn 2 sát khí, hạ Benkei ở x=320
  (trong dải `wasteland_deep`) ⇒ "Sát khí +1 (3/10)" + dòng báo toàn server thành Ma Đầu. Lượt sau cả hai trong Trạm Tử
  Mạch Sâu @368: `teleport:sect_gate` ⇒ `TELEPORT_OUTCOME_OUTLAW`; Benkei thấy gói 303 của Kagemaru `outlaw=true`; 11
  đòn của Benkei trúng Kagemaru NGAY TRONG trạm an toàn. Vàng rơi khi Ma Đầu chết chỉ kiểm bằng test Postgres (probe
  không dựng được cảnh Benkei Lv19 hạ Kagemaru Lv60).

## Cần chủ dự án xem lại

- Cách hiểu "lính trạm truy đuổi" = trạm giữa dải PvP không che Ma Đầu (không có NPC lính thật đánh — cần cơ chế NPC
  chiến đấu, có thể đi chung mục "Đồng minh hiện hình" của lô 26).
- Các con số: ngưỡng 3, trần 10, tan 30 phút/điểm, rơi 10% tối đa 5.000.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Sát khí lưu điểm + mốc, phân rã tính lười | Accepted |
| 2 | Ma Đầu: trạm giữa dải không che, cấm truyền tống, chết rơi vàng | Accepted (số là đề xuất) |
| 3 | Cờ `outlaw` trên gói 303/307 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Lính trạm là NPC chiến đấu thật (nếu chủ dự án muốn) — sau khi có cơ chế đồng minh/NPC đánh.
