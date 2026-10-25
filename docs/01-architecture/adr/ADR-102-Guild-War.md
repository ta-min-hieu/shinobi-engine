# ADR-102: Bang chiến

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Bang chiến" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: bang chủ tuyên chiến bang khác (tốn vàng): 1 giờ hai bang đánh nhau ngoài vùng an toàn, đếm số lần hạ, bang thắng nhận thư; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (vùng an toàn) · [ADR-090](ADR-090-Spirit-Vein-Contest.md) (luồng nền + chốt nguyên tử) · `WorldFacade#pvpAllowed` (cờ PvP theo World, 2026-09-24) |

---

## Decision

**D1 — Tuyên chiến**: bang chủ tuyên chiến một bang khác theo tên, trả **10.000 linh thạch** (của bang chủ, đốt). Hai bang
mỗi bên tối đa một trận đang chờ/đang đánh; cặp vừa đánh xong phải chờ **24 giờ** mới tuyên lại. Trận bắt đầu **5 phút**
sau khi tuyên (báo cả hai bang), kéo dài **60 phút**.

**D2 — PvP theo CẶP người**: World chính nhận một luật mới "A được đánh B không" (thay cho cờ boolean cả World): đúng khi
World cho PvP (đấu trường) HOẶC A và B thuộc hai bang đang chiến VÀ cả hai đứng ngoài vùng an toàn. Bảng "nhân vật →
bang đang chiến" là ảnh chụp RAM luồng nền dựng lại mỗi 10 giây (tick thread chỉ đọc, không chạm DB). Đòn đánh thường
lẫn chiêu ENEMY đều đi qua luật này.

**D3 — Đếm**: mỗi lần một thành viên bị thành viên bang đối địch hạ trong lúc trận đang đánh ⇒ +1 điểm cho bang người hạ
(`guild_war.kills_a`/`kills_b`). Chết trong bang chiến hồi sinh như thường, không phạt thêm.

**D4 — Chốt**: hết giờ ⇒ chốt NGUYÊN TỬ một lần (khoá dòng `guild_war`). Bang nhiều điểm hơn thắng, mọi thành viên bang
thắng nhận thư **2.000 linh thạch**; hoà không ai nhận. Báo toàn server. Số là PLACEHOLDER cân bằng.

**D5 — Giao thức, chỉ thêm**: 733/734 `DeclareGuildWarCommand{target_guild_name}`/`Response{outcome}`, 735/736
`GetGuildWarStatusRequest`/`Response{wars[]}`.

**D6 — Client**: nút "Bang chiến" trong cửa sổ Bang (G) mở bảng trạng thái + ô tên bang + nút Tuyên chiến.

## Thi hành (2026-10-25, lô 21 phần 4)

- **DB (V88)**: `guild_war` (hai phe, `starts_at`/`ends_at`, `kills_a`/`kills_b`, `settled_at`, `winner_guild_id`) +
  `guild_war_payout` (hàng đợi thư, cùng khuôn Linh Mạch). Bang giải tán ⇒ CASCADE xoá trận.
- **Tuyên** (`PostgresGuildWarAdapter#declare`): một giao dịch — bang chủ? bang đích tồn tại/khác bang mình? khoá HAI
  dòng `guild` theo `id` tăng dần (giữ luật "dòng `guild` trước" của feature-guild) → không bang nào có trận chưa chốt →
  cặp không đánh nhau trong 24 giờ qua → trừ 10.000 qua `GoldLedger` (`gold.reason.guild_war`) → chèn trận + audit
  `GUILD_WAR_DECLARED`. Báo toàn server ngay.
- **PvP theo cặp** (D2): `CombatSystem`/`SkillSystem` hỏi `PlayerVersusPlayerRule.allows(attacker, target)` thay cho
  `BooleanSupplier` cả World. `WorldFacade#allowsPlayerVersusPlayer`: đấu trường ⇒ luôn đúng; còn lại ⇒ luật bang chiến
  VÀ cả hai không đứng trong vùng an toàn nào (`TriggerZoneMembershipComponent` rỗng). Luật bang chiến là
  `GuildWarRoster` (gateway) — mảng nguyên thuỷ đã sắp xếp + tìm nhị phân, không boxing/cấp phát trên tick thread; mọi
  khu vực world chính dùng chung một roster.
- **Luồng nền** `GuildWarScheduler` mỗi 10 giây: dựng lại roster từ các trận đang trong giờ, báo "khai chiến" một lần
  mỗi trận, chốt trận hết giờ (`SELECT … FOR UPDATE` dòng chưa chốt ⇒ đúng một lần), gửi thư 2.000 với khoá chống trùng
  suy từ (trận, nhân vật).
- **Đếm hạ** (D3): `ConnectedCharacterRegistry#onCharacterDefeated` (tick thread) → `GuildWarHooks` lọc bằng roster rồi
  đẩy `recordKill` sang Virtual Thread; SQL tự kiểm lại hai người thuộc hai bang đang trong giờ chiến với nhau.
- **Client** (D6): nút "Bang chiến" hàng hai cửa sổ Bang → `GuildWarCoordinator` (bảng trận, ô tên bang, dòng Tuyên
  chiến). Trong giờ chiến của bang mình, click một người chơi ở world chính là NHẮM họ (`DuelTargeting#useGuildWar`) —
  ngoài giờ vẫn là mở lời Luận Kiếm như cũ. Client hỏi lại tình hình khi mở cửa sổ, về world chính, nhận thông tin bang,
  hoặc thấy tin hệ thống về bang chiến.
- **Probe**: `--lot21=declareWar:<bang>,warStatus,hitPlayer:<id>`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 10.000 vàng, chờ 5 phút, đánh 60 phút, PvP theo cặp ngoài vùng an toàn, đếm lần hạ, thư 2.000 cho bang thắng | Accepted |

## ADR cần tạo

- Không.

## Việc cần làm tiếp theo

1. ✅ Thi hành (lô 21 phần 4, 2026-10-25).
2. ✅ Kiểm sống bằng gameProbe 2026-10-25 (DB dev V88, Kagemaru bang "Thanh Hu Thu" vs Benkei bang "HuyetSat"):
   tuyên OK (vàng 90.174 → 80.174, audit `GUILD_WAR_DECLARED`); tên sai ⇒ `UNKNOWN_GUILD`; tuyên lần hai ⇒
   `ALREADY_AT_WAR`. Đối chứng trận CHƯA mở (x=−44, ngoài vùng an toàn): 0/3 đòn trúng. Dời `starts_at` về hiện tại
   (rút ngắn 5 phút chờ cho phép đo): 2 đòn hạ Benkei, điểm 1:0 trong `warStatus`. Cả hai đứng trong `sect_grounds`
   trong giờ chiến: 0/3 trúng, điểm giữ 1:0. Dời `ends_at` về hiện tại: chốt một lần (winner = bang Kagemaru), thư
   "Bang chiến / Guild war" 2.000 tới Kagemaru, hàng đợi rỗng. Bẫy công cụ gặp: bước `hitPlayer` bản đầu đếm cả đòn QUÁI
   đánh mục tiêu (Benkei chết vì quái gần x=160) — nay chỉ đếm người đánh id dương.
