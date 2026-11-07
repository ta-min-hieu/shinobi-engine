# ADR-211: Gợi ý kết bạn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Gợi ý kết bạn" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: gợi ý người chơi cùng tầm cảnh giới đang online để kết bạn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-074](ADR-074-Withdraw-Friend-Request.md) (lời mời kết bạn) · [ADR-091](ADR-091-Block-List.md) (chặn) |

---

## Decision

**D1 — Ai được gợi ý**: người **đang online**, chênh tối đa **10 cấp** (≈ một cảnh giới), chưa là bạn, chưa gửi lời mời
cho mình, không ai chặn ai, không phải chính mình. Tối đa **10** người, theo id tăng dần (tất định). Chỉ xét 200 người
online đầu tiên — một lần hỏi không bao giờ thành hàng trăm lần đọc DB. Không lưu gì, không bảng mới.

**D2 — Protocol**: `SuggestFriendsRequest{}` / `SuggestFriendsResponse{repeated FriendSuggestion{name, level}}`
1151/1152. Chỉ trả tên + cấp (thông tin ai đứng cạnh cũng thấy), không trả id.

**D3 — Client**: `/goiyban` (hoặc `/suggestfriends`) in danh sách "Tên (cảnh giới)"; thêm `/ketban <tên>` (hoặc
`/addfriend`) gửi lời mời ngay từ khung chat — cùng lệnh gửi lời mời sẵn có, kết quả hiện như khi gõ tên ở cửa sổ Bạn bè.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Online + chênh ≤ 10 cấp + chưa bạn / chưa mời / không chặn, tối đa 10; 1151/1152; `/goiyban`, `/ketban` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `FriendSuggestions`, `ConnectedCharacterRegistry.onlineCharacterIds`,
   `Lot35Commands.suggestFriends`, `FriendSuggestionCoordinator`.
2. ✅ Test: `FriendSuggestionsTest` (lọc cấp / bạn / chặn, trần 10), `FriendSuggestionCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07: giữ PhuTanThu (Lv1) online, BaoDo01 (Lv1, chưa là bạn) `suggestFriends` ⇒
   `PhuTanThu/Lv1`; ĐạoHữu01 (Lv20, bạn duy nhất là BaoDo01 Lv1) ⇒ danh sách rỗng.
