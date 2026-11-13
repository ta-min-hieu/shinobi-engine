# ADR-339: Màu riêng cho kênh chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Màu riêng cho kênh chat" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: tự chọn màu chữ cho từng kênh (thế giới, bang, nhóm, riêng...) qua lệnh; lưu trên máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-307](ADR-307-Colorblind-Mode.md) · [ADR-294](ADR-294-Chat-Font-Size.md) |

---

## Decision

**D1 — Phạm vi:** theo đúng 6 loại dòng `ChatPanel.LineKind` client đang tô màu: thế giới, khu vực, riêng, hệ thống, loa, nhắc tên (`thegioi, khuvuc, rieng, hethong, loa, nhacten`). Bang, nhóm, kết nghĩa và kênh giao dịch hiện dùng chung màu thế giới nên đổi theo `thegioi` — tách màu riêng cho chúng cần thêm loại dòng trong `ChatPanel`, để sau (khác mô tả lúc hỏi).

**D2 — Màu:** 8 tên có sẵn hoặc mã hex 6 chữ số; `macdinh` trả màu gốc. Áp từ lần vẽ danh sách kế tiếp, nhớ trong Preferences. Thuần client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ghi đè màu theo `LineKind`, lưu Preferences; `/maukenh`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `ChatPanel.setLineColor`/`defaultColorOf`, `ChatColorCoordinator`.
2. ✅ Test: `Lot51ClientTest.parsesChatColors`.
3. ⏳ Thuần client — cần chạy client thật để nhìn màu.
