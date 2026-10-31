# ADR-125: Luận võ đại hội

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Luận võ đại hội" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: giải tuần, đăng ký trong ngày, loại trực tiếp dùng World Arena, vô địch nhận danh hiệu + thư; lịch qua ADR-106). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | đấu trường 1v1 (Phase 8, `ArenaInstanceRegistry`) · [ADR-051](ADR-051-World-Changed-Event.md) (đổi World) · [ADR-087](ADR-087-Arena-Seasons.md) (Elo) · [ADR-062](ADR-062-Achievements-And-Titles.md) (danh hiệu) · [ADR-106](ADR-106-Activity-Calendar.md) |

---

## Decision

**D1 — Lịch**: mỗi tuần một giải, mặc định **Chủ nhật 20:00 giờ Việt Nam** (vận hành đổi bằng `KAGE_TOURNAMENT_AT`, ví dụ
`"SATURDAY 21:00"`). Đăng ký bằng `/luanvo` **trong ngày diễn ra giải, trước giờ bắt đầu**, cấp ≥ 10, tối đa 64 người.

**D2 — Loại trực tiếp qua World Arena có sẵn**: tới giờ, server ghép cặp ngẫu nhiên (lẻ người thì một người đi thẳng) và
**tự mở trận** cho từng cặp bằng CHÍNH đường thách đấu + chấp nhận của đấu trường 1v1 — luật trận giữ nguyên (rớt mạng
là thua, không hồi máu, Elo vẫn tính). Người vắng mặt lúc mở trận bị xử thua; cả hai vắng thì bên A đi tiếp. Trận quá
**5 phút** thì xử thua bên A (người được ghép trước) qua đường rút lui của đấu trường; quá 10 phút mà đấu trường vẫn
chưa chốt thì xử thẳng trong bảng giải. Hết trận của vòng thì ghép vòng sau; còn một người là vô địch.

**D3 — Trạng thái ở DB** (V105: `tournament`, `tournament_entry`, `tournament_match`), điều khiển bởi `TournamentDirector`
(feature-pvp, thuần — đấu trường và thông báo đi qua interface gateway hiện thực) chạy nhịp 15 giây. Khởi động lại
server giữa giải thì đi tiếp từ DB.

**D4 — Vô địch**: thư **5.000 linh thạch** + thành tựu `tournament_1` (loại mới `WIN_TOURNAMENTS`) mở danh hiệu **"Đệ
Nhất Luận Võ"** (ADR-062); cả server nhận một dòng thông báo. Mỗi người nhận dòng "vòng N gặp X" khi được ghép.

**D5 — Giao thức, chỉ thêm**: 1612/1613 `RegisterTournamentCommand/Response`, 1614 `TournamentNoticeEvent`.

⚠️ **Lệch mô tả lượt hỏi**: "lịch qua ADR-106" — ADR-106 trong code là **điểm năng động hằng ngày**, không phải lịch
sự kiện; chưa nối (cần thêm một hoạt động vào enum `ActivityFacade.Activity` — đổi nhỏ, chờ chủ dự án xác nhận ý).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Giải tuần giờ VN (env), đăng ký trong ngày, server tự mở trận qua đấu trường 1v1, quá giờ xử thua bên A, vô địch thư + danh hiệu | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 3c): feature-pvp `TournamentRules` / `TournamentPort` / `TournamentDirector` (có
   test) / `TournamentFacade`; `PostgresTournamentAdapter` (V105); gateway `TournamentHooks` (+ móc chốt trận ở
   `ArenaInstanceRegistry`); client `TournamentCoordinator` (`/luanvo`); bước probe `tournament`; test Postgres.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V105): server bật với `KAGE_TOURNAMENT_AT="SATURDAY 15:29"`;
   Kagemaru `/luanvo` ⇒ OK, lần hai ⇒ ALREADY_REGISTERED; Benkei ⇒ OK. Cả hai đứng online: 15:29 nhận gói 1614 "vòng 1
   gặp …", ~15 giây sau trận tự mở (gói 308 ARENA cho cả hai). Không ai đánh ⇒ quá 5 phút server xử thua bên A
   (Benkei) qua đường rút lui của đấu trường ⇒ 308 MAIN + 1611 (Elo 1017/998) ⇒ gói 1614 vô địch tới cả hai. DB:
   giải FINISHED, champion = Kagemaru; thư "Luận võ đại hội" 5.000 linh thạch; bộ đếm `WIN_TOURNAMENTS` = 1.
3. ⚠️ Chủ dự án xem: ghi chú `feature-pvp/CLAUDE.md` — client thật **chưa nhắm được đối thủ trong Arena** (đang chờ chủ
   dự án), nên trận giải đánh bằng client thật cũng chịu chung hạn chế; trận mà một bên kẹt ở Bí Cảnh suốt giải thì chờ
   mãi (không có hạn "chưa mở được trận"); số thưởng 5.000 là đề xuất.
