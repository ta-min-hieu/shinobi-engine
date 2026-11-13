# ADR-341: Nhấp đúp dùng/mặc đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Nhấp đúp dùng/mặc đồ" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: nhấp đúp vào món trong túi để dùng (đan dược) hoặc mặc (trang bị) ngay). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) |

---

## Decision

**D1 — Hành vi:** bấm vào tên món trong túi = chọn món đó (như nút Chọn); nhấp đúp = bấm nút hành động chính đang hiện (Dùng cho đan dược, Mặc cho trang bị — theo catalog, ADR-035). Món không dùng/mặc được thì nhấp đúp chỉ chọn. Mọi kiểm tra vẫn ở server.

**D2 — Thuần client**, không công tắc, không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | ClickListener trên tên món: chọn; tapCount ≥ 2 ⇒ hành động chính; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `InventoryPanel.addItemRow` (ClickListener gọi `onSelect` rồi `firePrimaryAction`).
2. ✅ Test: đường dùng/mặc giữ nguyên test sẵn có (`InventoryController`); phần nhấp đúp là sự kiện scene2d, kiểm bằng client thật.
3. ⏳ Thuần client — cần chạy client thật để nhấp.
