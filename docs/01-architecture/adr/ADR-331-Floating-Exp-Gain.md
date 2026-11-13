# ADR-331: Chữ +EXP khi nhận kinh nghiệm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chữ +EXP khi hạ quái" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: chữ '+N EXP' bay lên trên đầu nhân vật mỗi lần nhận kinh nghiệm (hiện chỉ có chữ lên cấp)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-034](ADR-034-Manual-Attack-And-Experience.md) · [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) |

---

## Decision

**D1 — Nguồn số:** hiệu `total_exp` giữa hai `ExperienceChangedEvent` liên tiếp (gói đầu lúc vào world chỉ ghi mốc). Không đổi protocol; client không tự tính EXP (Điều 15).

**D2 — Hiển thị:** chữ bay "+N EXP" màu tím nhạt qua đường chữ bay của bản thân (cùng lớp với chữ lên cấp); nhận EXP mà vừa lên cấp thì hiện cả hai.

**D3 — Công tắc:** `/chuexp` (`/expgain`) bật/tắt, mặc định bật, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hiệu `total_exp` hai event liên tiếp ⇒ chữ bay; `/chuexp`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `ExpGainCoordinator`, `FloatingCombatText.EXP_GAIN`, nối trong `ExpansionWindows.wireDisplay`.
2. ✅ Test: `Lot50ClientTest.expGainIgnoresFirstSnapshotAndDecreases`.
3. ⏳ Thuần client — cần chạy client thật để nhìn chữ bay (gameProbe không vẽ).
