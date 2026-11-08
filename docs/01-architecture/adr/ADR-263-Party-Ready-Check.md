# ADR-263: Kiểm tra sẵn sàng nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Kiểm tra sẵn sàng nhóm" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /sansang: trưởng nhóm hỏi, mỗi thành viên bấm Sẵn sàng/Chưa, 30 giây rồi in kết quả cho cả nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (nhóm) · [ADR-150](ADR-150-Group-Loot-Rolls.md) (trạng thái RAM có hẹn giờ) |

---

## Decision

**D1 — Hai lệnh, không event mới.** `StartReadyCheckCommand{}` → `StartReadyCheckResponse{outcome, seconds}` (1337/1338) và
`RespondReadyCheckCommand{ready}` → `RespondReadyCheckResponse{outcome}` (1339/1340), dải Party. Lời mời và kết quả là
**dòng chat hệ thống song ngữ** (`ChatMessageEvent`, kênh UNSPECIFIED — cùng khuôn nhắc lịch bang ADR-256), nên không cần
opcode event và client cũ vẫn đọc được.

**D2 — Luật.** Chỉ trưởng nhóm mở (`NOT_LEADER` nếu không); mỗi nhóm một lượt (`ALREADY_ACTIVE`). Danh sách người được hỏi
chụp lúc mở = thành viên ĐANG online; trưởng nhóm tự tính "sẵn sàng". Người vào nhóm sau / không có mặt lúc mở trả lời ⇒
`NO_CHECK`. Chốt khi ai cũng đã trả lời hoặc sau 30 giây (chốt đúng MỘT lần dù hai đường tới cùng lúc —
`ConcurrentHashMap.remove(key, value)`); kết quả liệt kê sẵn sàng / chưa / không trả lời.

**D3 — Trạng thái trong RAM gateway** (`PartySignals`), hẹn giờ bằng `CompletableFuture.delayedExecutor` trên Virtual
Thread; khởi động lại thì lượt đang mở mất — chấp nhận như xúc xắc chia đồ ADR-150. Không đổi DB.

**D4 — Client** `/sansang` (hoặc `/readycheck`) mở; `/sansang co | khong` (`yes | no`) trả lời.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1337–1340; lời mời/kết quả qua dòng chat hệ thống; RAM gateway, 30 giây | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `PartySignals`, `Lot42Commands.startReadyCheck/respondReadyCheck`, `ReadyCheckCoordinator`.
2. ✅ Test: `PartySignalsTest` (đủ người ⇒ chốt ngay; hết giờ liệt kê người im lặng; chốt hai lần chỉ báo một lần).
