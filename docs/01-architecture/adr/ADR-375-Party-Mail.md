# ADR-375: Thư gửi cả nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Thư gửi cả nhóm" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /thunhom <tiêu đề> | <nội dung>: một thư chữ tới mọi thành viên khác trong nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-312](ADR-312-Friends-Mail.md) · [ADR-186](ADR-186-Guild-Check-In.md) |

---

## Decision

**D1 — Gửi:** người gửi phải đang ở nhóm; thư (không đính kèm, không vàng) đi qua `MailFacade.sendMail` tới từng thành viên KHÁC, mỗi thư thành công kèm `NewMailEvent` cho người đang online. Kiểm: tiêu đề 1–64 ký tự, nội dung ≤ 1024, không từ cấm (cùng trần thư thường). Không trần theo ngày: nhóm tối đa 5 người, không phải kênh spam diện rộng như thư bạn bè (ADR-312).

**D2 — Dây:** `SendPartyMailCommand{subject, body}` / `SendPartyMailResponse{outcome, recipients}` **1354/1355**, outcome `OK / NOT_IN_PARTY / NO_RECIPIENTS / INVALID_CONTENT`. Client `/thunhom` (`/partymail`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1354/1355, gửi qua `MailFacade` tới thành viên khác, không trần ngày | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `Lot56Commands.sendPartyMail`, `PartyMailCoordinator`.
2. ✅ Test: `Lot56ServerTest.validatesPartyMail`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `partyMail:`): ĐạoHữu01 gửi ⇒ `SEND_PARTY_MAIL_OUTCOME_OK x1`, thư tới BaoDo01 trong DB (đã xoá lại sau khi kiểm).
