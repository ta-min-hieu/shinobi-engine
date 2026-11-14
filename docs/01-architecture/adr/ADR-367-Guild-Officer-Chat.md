# ADR-367: Kênh chat lãnh đạo bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Kênh chat lãnh đạo bang" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: /lanhdao <tin>: tin chỉ gửi tới bang chủ và phó bang chủ đang online). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) · [ADR-235](ADR-235-Guild-Mail.md) |

---

## Decision

**D1 — Ai:** chỉ bang chủ / phó bang chủ (ADR-184) gửi được; tin tới mọi bang chủ / phó đang online, kể cả người gửi (để thấy tin mình). Kiểm như chat: một dòng, ≤ 200 ký tự, không từ cấm. Không lưu, không giao lúc offline.

**D2 — Dây:** `OfficerChatCommand{body}` / `OfficerChatResponse{outcome}` **789/790**; tin đi bằng `ChatMessageEvent{channel = GUILD, officer_only = true}` (field 10, CHỈ THÊM) thay vì thêm một giá trị `ChatChannel` mới — client cũ vẫn hiện như tin bang. Client `/lanhdao <tin>` (`/officer`), dòng nhận mang tiền tố "[Lãnh đạo]".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 789/790, tin `channel=GUILD` + `officer_only=10` tới bang chủ/phó online | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `Lot55Commands.officerChat`, `OfficerChatCoordinator` (bộ đổi tin nối tiếp `ChatCoordinator.useEventRewriter`).
2. ✅ Test: đường quyền dùng `GuildFacade.MyGuildView` sẵn có; kiểm sống.
3. ✅ Kiểm sống gameProbe 2026-11-14: ĐạoHữu01 (bang chủ) `officerChat` ⇒ OK và nhận tin kênh bang; BaoDo01 (thành viên thường, đang online) KHÔNG nhận tin đó; BaoDo01 gửi ⇒ NOT_OFFICER.
