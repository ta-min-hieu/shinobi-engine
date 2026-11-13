# ADR-328: Cảnh báo đăng nhập từ vùng mạng lạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Cảnh báo mạng lạ" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: khi vào game từ vùng IP (đã che) chưa từng thấy trong lịch sử đăng nhập ⇒ dòng cảnh báo hệ thống). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-238](ADR-238-Login-History.md) · [ADR-115](ADR-115-Security-Pin.md) |

---

## Decision

**D1 — Không thêm bảng.** Dùng lại `login_history` (ADR-238, IP đã che 2 khối). Lúc vào world: lấy tối đa 50 lần gần nhất của tài khoản; lần mới nhất có IP che KHÔNG trùng lần nào trước đó ⇒ một dòng cảnh báo hệ thống, gợi ý `/lichsudangnhap`. Đây là phần ADR-238 D4 để ngoài phạm vi.

**D2 — Không báo động giả:** lần đăng nhập đầu tiên của tài khoản và IP không rõ (`?`) không tính là lạ. Không chặn đăng nhập, không gửi thư — chỉ thông tin, vì IP che 2 khối đổi theo nhà mạng di động là chuyện thường.

**D3 — Dây:** không opcode mới (dòng `ChatMessageEvent` hệ thống).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | So IP che của lần mới nhất với 50 lần trước; khác hết ⇒ dòng cảnh báo; không opcode, không bảng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `LoginHistoryFacade.latestIsNewNetwork`/`isNewNetwork`, `Lot50Commands.onEnteredWorld`.
2. ✅ Test: `Lot50ServerTest.newNetworkOnlyWhenUnseenBefore`/`inspectedNoticeNamesViewer`.
3. ✅ Kiểm sống gameProbe 2026-11-13: tạm đổi 23 dòng lịch sử của tài khoản sang `203.113.x.x`, đăng nhập từ `127.0.x.x` ⇒ dòng cảnh báo vùng mạng lạ; tài khoản khác (lịch sử cùng IP) không bị báo. Đã trả lịch sử về như cũ.
