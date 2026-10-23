# ADR-074: Rút lời mời kết bạn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-23) — chủ dự án chọn "71(c): rút lời mời kết bạn" trong lượt hỏi lô 18 (AskUserQuestion; mô tả kèm: người gửi rút được lời mời đang chờ, opcode mới, nút trong cửa sổ bạn bè; câu chọn lẫn "để sau" đã được hỏi lại — chủ dự án chọn "Làm cả hai"). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-23 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md](../../README.md) dòng 71(c) · feature-friend |

---

## Context

Lời mời kết bạn đang chờ chỉ người NHẬN xử lý được (chấp nhận/từ chối). Người gửi mời nhầm không có cách rút;
lời mời chiếm một trong 50 chỗ chờ của người nhận (dòng 71(b)) cho tới khi người kia bấm.

## Decision

**D1 — Opcode 1114/1115** `CancelFriendRequestCommand { string target_name }` →
`CancelFriendRequestResponse { CancelFriendRequestOutcome outcome }` với `OK` / `TARGET_NOT_FOUND` /
`NOT_PENDING`. Theo TÊN (như `RemoveFriendCommand`) vì client không có danh sách lời mời đã gửi. Chỉ THÊM,
`protocolVersion` không tăng.

**D2 — Chỉ lời mời CHÍNH MÌNH gửi, còn PENDING**: xoá dòng `friendship` có người gửi = mình, người nhận = người
kia, trạng thái PENDING. Không đụng lời mời chiều ngược (người kia mời mình — cái đó là "từ chối"). Rút xong mời
lại được ngay — KHÔNG kích hoạt hạn chờ 10 phút của nhánh bị từ chối (dòng 71(b)).

**D3 — Không báo người nhận**: không có event lời mời đến, nên cũng không có event rút; danh sách chờ của họ tự
đúng lần tải sau.

**D4 — Client**: cửa sổ bạn bè có nút "Rút lời mời" dùng tên đang gõ ở ô thêm bạn.

## Alternatives Considered

- **Theo `friendship_id`** — client không biết id của lời mời mình gửi; thêm danh sách "đã gửi" là một cặp opcode
  nữa. Để sau nếu cần giao diện danh sách.

## Consequences

- Không đổi schema. Thêm một use case + một method port.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Rút theo tên, chỉ lời mời mình gửi còn chờ | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [NETWORK.md](../../05-server/NETWORK.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-23 (lô 18) — `RemoveFriendUseCase#withdrawRequest` (dùng lại câu "từ chối" có điều kiện PENDING của port, không thêm method port), `FriendFacade#withdrawRequest`, nút "Rút mời". ⏳ Chưa kiểm sống.
