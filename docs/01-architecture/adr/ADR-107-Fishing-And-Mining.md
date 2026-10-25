# ADR-107: Câu cá và khai khoáng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Câu cá / khai khoáng" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: mở rộng cơ chế hái linh thảo: điểm câu cá (bờ nước) và mạch khoáng, đứng 3–5s, ra cá/quặng dùng cho luyện đan & phân giải; chủ yếu dữ liệu, ít opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-098](ADR-098-Herb-Gathering.md) (hái linh thảo) · [ADR-079](ADR-079-Alchemy-Crafting.md) (luyện đan) |

---

## Decision

**D1 — Một cơ chế, ba loại điểm (V90)**: bảng `herb_node` thêm `kind` (`HERB`/`FISH`/`ORE`, mặc định `HERB`) và
`gather_seconds` (mặc định 3). Không bảng mới, không luồng mới: `HerbNodes`/`HerbCommands` (ADR-098) dùng đúng
`gather_seconds` của điểm thay cho hằng 3 giây. Seed: 2 bờ câu (5 giây, `spirit_fish`), 2 mạch khoáng (4 giây,
`spirit_ore`), hồi 240 giây. Số là PLACEHOLDER.

**D2 — Vật phẩm mới**: `spirit_fish` (Linh Ngư) và `spirit_ore` (Linh Khoáng Thạch) — nguyên liệu xếp chồng 99, bán được
cho cửa hàng. Thêm 2 công thức luyện đan: Hồi Khí Đan cũ (`minor_healing_pill`) thêm cách luyện từ 2 Linh Ngư + 1 Linh
Thảo; Bùa Hộ Linh (`enhance_protection_charm`) từ 5 Linh Khoáng + 2 Linh Thảo.

**D3 — Giao thức, chỉ thêm field**: `HerbNodeView` thêm `kind` (string) và `gather_seconds`; không opcode mới,
`protocolVersion` không tăng.

**D4 — Client**: tab "Linh thảo" đổi nhãn thành "Thu thập", mỗi dòng ghi loại (Linh thảo/Bờ câu/Mạch khoáng) và số giây.
Hái/câu/đào đều tính vào việc `herb_gather` của lịch hoạt động (ADR-106) — đổi nhãn việc thành "Thu thập".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mở rộng herb_node bằng kind + gather_seconds; Linh Ngư, Linh Khoáng; 2 công thức | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 2).
2. ⏳ Kiểm sống bằng gameProbe.
