# ADR-185: Bảng chiêu mộ bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Bảng chiêu mộ bang" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: bang treo lời chiêu mộ (cấp tối thiểu, lời giới thiệu); người chưa có bang xem danh sách và gửi đơn ngay từ đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) (phó bang chủ cùng quyền treo) · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (trần thành viên theo cấp bang) |

---

## Decision

**D1 — Một dòng mỗi bang (V146)**: `guild_recruitment(guild_id PK, min_level 1..100, message, updated_at)`, xoá theo
bang. Bang chủ **hoặc phó** treo / sửa / gỡ (`canManageMembership`). Lời chiêu mộ một dòng, ≤ **80 ký tự**, qua bộ lọc
từ cấm của chat.

**D2 — Bảng**: ai cũng xem được; 50 bang, cấp bang giảm dần rồi mới cập nhật trước; mỗi dòng: tên, cấp bang, số người /
trần thành viên (`GuildLevelTable.maxMembers`), cấp tối thiểu, lời chiêu mộ.

**D3 — Xin vào = đơn cũ, cấp tối thiểu KIỂM Ở SERVER**: nút "Xin vào" gửi đúng `SendGuildJoinRequestCommand` hiện có
(duyệt như mọi đơn). Bang đang chiêu mộ mà cấp người xin thấp hơn `min_level` ⇒ outcome MỚI
`SEND_GUILD_JOIN_REQUEST_OUTCOME_LEVEL_TOO_LOW = 5` — áp cho MỌI đường gửi đơn tới bang đó, không chỉ từ bảng. Tên bang
dị dạng (vd chứa U+0000) không bao giờ chạm DB ở bước tra này (`GuildName.isValid`).

**D4 — Giao thức, chỉ thêm**: `PostGuildRecruitmentCommand{open, min_level, message}` / `Response{outcome, open}`
751/752; `ListGuildRecruitmentRequest{}` / `Response{entries}` 753/754.

**D5 — Client**: `/chieumo` (hoặc `/recruit`) mở bảng có nút "Xin vào"; `/chieumo dang <cấp> <lời>` treo, `/chieumo go` gỡ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Một dòng chiêu mộ / bang (V146), bang chủ hoặc phó, ≤ 80 ký tự qua bộ lọc chat, cấp tối thiểu kiểm ở đơn xin vào, 751–754 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): V146, `GuildRecruitmentPort`/`GuildRecruitmentFacade`, `PostgresGuildRecruitmentAdapter`,
   `GuildRecruitmentCommands` 751–754, kiểm cấp trong `handleSendGuildJoinRequestBlocking`, `GuildRecruitmentCoordinator`.
2. ✅ Test: `GuildRecruitmentIntegrationTest` (Postgres thật), `GuildRecruitmentCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `recruit:<cấp>=<lời>`, `recruit:off`, `recruitBoard`, `applyGuild:<bang>`):
   BaoDo01 (thành viên thường) treo ⇒ `NOT_ALLOWED`; bang chủ ĐạoHữu01 treo "từ cấp 5" ⇒ `OK`, bảng hiện
   "LinhKiemMon Lv1 2/50 từCấp5"; BaoDo01 (cấp 1) xin vào ⇒ `LEVEL_TOO_LOW` (kiểm ở server); hạ về cấp 1 ⇒ đơn đi tiếp tới
   luật cũ (`ALREADY_IN_GUILD`); gỡ ⇒ bảng rỗng.
4. Sau này: nút "Bảng chiêu mộ" trong cửa sổ bang khi chưa có bang.
