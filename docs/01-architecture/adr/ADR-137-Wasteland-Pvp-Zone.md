# ADR-137: Vùng PvP Hoang Vực

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Vùng PvP Hoang Vực" trong lượt hỏi lô 25 (AskUserQuestion). |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-051](ADR-051-World-Changed-Event.md) (PvP chỉ ở Arena) · [ADR-046](ADR-046-Health-Recovery.md) (vùng an toàn, module `feature-world`) · bang chiến (`PlayerVersusPlayerRule`) |

---

## Bối cảnh

Từ 2026-09-24 PvP bị cấm ở mọi World trừ Arena, kèm ghi chú "cho tới khi dựng Hoang Vực — mở PvP bằng một loại zone
mới". Ngoại lệ duy nhất sau đó là bang chiến (hai bang đang chiến được đánh nhau ngoài vùng an toàn). STORY §7 tả Hoang
Vực là vùng linh mạch chết ngoài vòng luật lệ — đúng chỗ cho PvP tự do.

## Decision

**D1 — Loại zone mới `pvp_zone` (V112)**: bảng `pvp_zone(code, world_code, min_x, max_x)`, chỉ trục X (world chính khoá
di chuyển vào trục X — ADR-033). Nằm cạnh `safe_zone` trong `feature-world` (cùng là HÌNH HỌC BẢN ĐỒ, nạp MỘT LẦN lúc khởi
động): `SafeZonePort.listPvpZones` (default rỗng), `WorldGeometryFacade.listPvpZones`, gateway dịch sang
`SafeZoneCatalogSnapshot.OpenPvpRange`.

**D2 — Luật**: người A đánh được người B khi
`(cả A và B cùng đứng trong một dải PvP  HOẶC  bang chiến cho phép)  VÀ  cả hai ngoài mọi vùng an toàn`.
Vùng an toàn luôn thắng; một người ở trong dải còn người kia ở ngoài ⇒ không đánh được (không có đòn "với ra ngoài").
`WorldFacade.allowsPlayerVersusPlayer`; dải nạp thành mảng `float[]` cặp [min, max] — không cấp phát trong tick.

**D3 — Dải đầu tiên**: `wasteland_rim` = [179, 205] trên world `main`, giữa trạm cổng Hoang Vực [173.5, 178.5] và trạm
trước sân khảo hạch Trúc Cơ [205.5, 210.5] — hai đầu dải chạm hai vùng an toàn, ai muốn tránh PvP thì đứng trạm.

**D4 — Không đổi protocol**: dùng nguyên đường tấn công + `AttackResultEvent` sẵn có. Client CHƯA báo "bạn đang vào vùng
PvP" — để cho Khung UI tu tiên (mục cùng lô) hoặc lô sau.

## Kiểm chứng

- `PlayerVersusPlayerRuleTest`: hai người trong dải đánh được (không bang chiến); nạn nhân ngoài dải ⇒ không đòn nào.
- **Kiểm sống 2026-11-01** (gameProbe, server V112): Kagemaru x=190 đánh Benkei x=191 ⇒ 2 `ATTACK_RESULT_EVENT`
  (98 rồi 147, Benkei ngã); Kagemaru x=179.5 (trong), Benkei x=178.8 (ngoài, trong trạm) ⇒ 0 đòn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `pvp_zone` chỉ trục X, ở `feature-world` | Accepted |
| 2 | Cả hai phải trong dải; vùng an toàn thắng mọi luật PvP | Accepted |
| 3 | Dải đầu `wasteland_rim` [179, 205] | Accepted |
| 4 | Báo hiệu vùng PvP trên client | Treo — làm cùng Khung UI |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [STORY.md §7](../../13-game-design/STORY.md) · [ADR-051](ADR-051-World-Changed-Event.md) · [ADR-046](ADR-046-Health-Recovery.md)

## Việc cần làm tiếp theo

- ~~Chữ/viền cảnh báo "Vùng PvP" trên HUD khi bước vào dải (D4).~~ ✅ [ADR-141](ADR-141-Zone-Map-HUD-And-Wasteland-Waypoints.md) (2026-11-01).
- Cân nhắc phạt/thưởng khi hạ người trong Hoang Vực (rơi vàng, danh vọng) — cần chủ dự án quyết.
