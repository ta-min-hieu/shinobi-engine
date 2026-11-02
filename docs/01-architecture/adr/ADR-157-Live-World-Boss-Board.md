# ADR-157: Bảng sát thương boss thế giới trực tiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Săn boss theo giờ + bảng sát thương" trong lượt hỏi lô 28, rồi ở lượt hỏi làm rõ (lịch boss + thưởng top đã có ở ADR-081) chọn **"Bảng sát thương trực tiếp"** (mô tả kèm: trong lúc đánh boss thế giới, người đang đánh thấy bảng top 5 + hạng/sát thương của mình, cập nhật mỗi 2 giây; opcode mới, không đổi thưởng). AI soạn văn bản và chọn chi tiết. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-081](ADR-081-World-Boss.md) (lịch boss, sổ sát thương, thưởng top qua thư) |

---

## Decision

**D1 — Ảnh chụp sổ qua hàng lệnh của World**: sổ sát thương (`WorldBossDamageLedger`) chỉ sống trên tick thread. Lệnh
mới `WorldBossStandingsCommand(reply)` vào inbox; tick thread dựng bản sao bất biến (`WorldBossStanding`) rồi gọi
`reply` — gateway đẩy ngay sang Virtual Thread. Hai giây một lần, không phải mỗi khung ⇒ không vi phạm luật cấp phát
trong game loop.

**D2 — Gửi cho ai**: job `WorldBossBoardJob` (bộ hẹn giờ `ExpansionJobs`, **2 giây**) hỏi khu vực ĐẦU TIÊN của world chính
(nơi boss xuất hiện — ADR-081). Mỗi người có tên trong sổ và đang online nhận top **5** (tên, sát thương), hạng + sát
thương của mình, số người tham chiến. Boss biến khỏi sổ (chết) ⇒ một gói `ended` cho những người đã thấy bảng ⇒ client gỡ.

**D3 — Chỉ hiển thị**: thưởng vẫn theo ADR-081 (thư cho top lúc boss chết). Không đổi schema.

**D4 — Giao thức, chỉ thêm**: 414 `WorldBossBoardEvent{boss_code, top[], my_rank, my_damage, participants, ended}`.

**D5 — Client**: khung nhiều dòng trong cột góc trên-phải của HUD (dưới biển vùng), cùng cách gỡ khỏi ô như biển vùng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ảnh chụp sổ qua inbox World mỗi 2 s, top 5 + hạng mình cho người trong sổ, ended khi boss chết, chỉ hiển thị | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): `WorldBossStanding`, `WorldBossDamageLedger.standings`,
   `WorldFacade.requestWorldBossStandings`, `WorldBossBoardJob`, opcode 414, `HudBanners.setBossBoard`,
   `WorldBossBoardCoordinator`.
2. ✅ Kiểm sống 2026-11-02 (`KAGE_WORLD_BOSS_FIRST_SPAWN_SECONDS=5`): Yêu Vương xuất hiện ở x≈-32, ĐạoHữu01
   đánh trúng 25/đòn ⇒ nhận `WORLD_BOSS_BOARD_EVENT` (414) đúng nhịp 2 giây (t=2005/3995/5994ms), 37 byte = một
   dòng. Lần đánh đầu né hết 2 đòn ⇒ sổ rỗng ⇒ KHÔNG gửi gì (đúng D: chỉ gửi khi có người gây sát thương).
