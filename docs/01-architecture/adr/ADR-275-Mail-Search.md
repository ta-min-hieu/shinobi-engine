# ADR-275: Tìm thư theo từ khoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tìm thư theo từ khoá" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /timthu: lọc hộp thư theo người gửi/tiêu đề). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-217](ADR-217-Pin-Mail.md) |

---

## Decision

**D1 — Thuần client, dùng lại gói danh sách thư (902/903).** `/timthu <từ khoá>` (hoặc `/findmail`) hỏi lại hộp thư rồi
in tối đa 10 thư có **tiêu đề hoặc nội dung** chứa từ khoá (so không dấu), kèm đoạn đầu nội dung. Từ khoá `hethong` /
`system` khớp thư hệ thống.

**D2 — Không lọc được theo TÊN người gửi.** Mô tả lúc chọn ghi "người gửi/tiêu đề", nhưng `MailView` chỉ mang
`sender_id`, không mang tên — lọc theo tên cần thêm field `sender_name` vào `MailView` (đổi protocol, server tra tên). Chưa
làm; ghi ở "Việc cần làm tiếp theo".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | dùng lại 902/903; lọc tiêu đề + nội dung + thư hệ thống | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `MailSearchCoordinator`.
2. ✅ Test: `Lot43ClientTest.mailSearchMatches`.

3. ⏳ Chưa làm: lọc theo tên người gửi (cần `sender_name` trong `MailView` — đổi protocol). Chờ chủ dự án quyết.
