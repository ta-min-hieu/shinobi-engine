# ADR-191: Nhường trưởng nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Nhường trưởng nhóm" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: trưởng nhóm chuyển quyền cho thành viên khác trong nhóm mà không phải rời nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (event 1318) · [ADR-183](ADR-183-Guild-Leader-Transfer.md) (bản của bang) |

---

## Bối cảnh

Trước ADR này quyền trưởng nhóm chỉ đổi tay khi trưởng nhóm **rời nhóm** (người vào sớm nhất lên thay) hoặc **rớt
mạng** (quyết định 2026-10-11, `HandOverPartyLeadershipUseCase`). Muốn để người khác dẫn đội vào hầm ngục thì trưởng
nhóm phải rời rồi xin vào lại. `PartyPort.transferLeadership` (nguyên tử, kiểm lại vai lúc ghi) đã có sẵn từ đợt
rớt mạng — chỉ thiếu đường để người chơi tự gọi.

## Decision

**D1 — Ai, cho ai**: chỉ **trưởng nhóm**, cho một **thành viên cùng nhóm** (theo tên). Trưởng nhóm cũ ở lại làm thành
viên. Miễn phí, không hồi chiêu, không cần người nhận đang online (trưởng nhóm tự chịu; người nhận vắng mặt thì lúc
họ rớt mạng lần sau quyền lại tự chuyển đi như cũ).

**D2 — Không schema mới**: dùng lại `PartyPort.transferLeadership`; phần kiểm (không phải trưởng nhóm / tự nhường cho
mình / không cùng nhóm) là `HandOverPartyLeadershipUseCase.transferTo`. Ghi thất bại vì một trong hai vừa rời / bị
kick ⇒ `TARGET_NOT_IN_PARTY`.

**D3 — Báo cả nhóm**: thành viên đang online (kể cả người nhường) nhận `PartyChangedEvent` lý do có sẵn
`PARTY_CHANGE_REASON_LEADER_CHANGED` ⇒ client tự hỏi lại nhóm. Không lý do mới.

**D4 — Giao thức, chỉ thêm**: `TransferPartyLeaderCommand{target_name}` / `TransferPartyLeaderResponse{outcome,
target_name}` 1332/1333. `protocolVersion` không tăng.

**D5 — Client**: lệnh chat `/nhuongnhom <tên>` (hoặc `/partyleader`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Trưởng nhóm nhường cho thành viên cùng nhóm, dùng lại `transferLeadership`, 1332/1333, 1318 LEADER_CHANGED | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): `HandOverPartyLeadershipUseCase.transferTo`, `PartyFacade.transferLeadership`,
   `Lot33Commands.transferPartyLeader` 1332/1333, `PartyLeaderCoordinator` `/nhuongnhom`.
2. ✅ Test: `HandOverPartyLeadershipUseCaseTest` (nhường được; từ chối 3 kiểu), `PartyLeaderCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `partyLeader:<tên>`): BaoDo01 (trưởng nhóm) nhường cho chính mình ⇒
   `CANNOT_TRANSFER_TO_SELF`; nhường cho ĐạoHữu01 ⇒ `OK`, DB đổi vai đúng, ĐạoHữu01 đang online nhận 1318; nhường lần
   hai ⇒ `NOT_LEADER`; tên lạ ⇒ `TARGET_NOT_IN_PARTY`; ĐạoHữu01 nhường lại ⇒ `OK`.
4. ✅ Kèm theo: sửa vi phạm Checkstyle `ParameterNumber` lọt qua ở ADR-188 (`linkedItemOf` 5 tham số ⇒ 4, đọc kênh từ
   chính lệnh chat).
