# ADR-365: Màn hình xám khi chết

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Màn hình xám khi chết" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: khi nhân vật bị hạ, màn hình phủ một lớp tối/xám cho tới khi hồi sinh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-243](ADR-243-Low-Health-Warning.md) · [ADR-117](ADR-117-Revive-In-Place.md) |

---

## Decision

**D1 — Khi nào:** máu của chính mình trên HUD về 0 (máu tối đa đã biết) ⇒ mỗi khung phủ một lớp xám tối (alpha 0,55) cả màn hình, thay cho viền đỏ máu thấp (ADR-243); hồi sinh (máu > 0) ⇒ tự hết. Bảng hồi sinh vẫn vẽ phía trên.

**D2 — Không cấp phát:** màu và ma trận dựng sẵn trong `LowHealthWarning` (cùng chỗ đọc máu mỗi khung). Thuần client, không công tắc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Máu 0 ⇒ phủ xám cả màn hình trong `LowHealthWarning`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `LowHealthWarning.update`/`drawDeathVeil`/`isDead`.
2. ✅ Test: đường vẽ libGDX — kiểm bằng client thật; ngưỡng máu dùng chung `LowHealthWarning.update`.
3. ⏳ Thuần client — cần chạy client thật để nhìn.
