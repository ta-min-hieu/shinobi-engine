# ADR-428: Tên kênh rút gọn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tên kênh rút gọn" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /kenhngan: khung chat hiện [TG]/[GD]/[KV]/[B]/[N] thay tên kênh đầy đủ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-229](ADR-229-Chat-Channel-Filter.md) · [ADR-371](ADR-371-Remember-Chat-Channel.md) |

---

## Decision

**D1:** khi bật, tiền tố kênh mỗi dòng dùng khoá i18n `chat.channel.<kênh>.short` (vi: TG, GD, KV, R, B, N, KN, Loa; en: W, T, A, P, G, Pt, Sw, Bc) — khung chat 420px hẹp, tiết kiệm chỗ cho nội dung.

**D2:** `/kenhngan` (`/shortchannels`), nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, khoá i18n `.short` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `ChatCoordinator.setShortChannels`, `ChatLensCoordinator`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
