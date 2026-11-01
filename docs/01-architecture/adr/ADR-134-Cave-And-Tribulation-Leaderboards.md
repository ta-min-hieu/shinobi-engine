# ADR-134: Bảng xếp hạng Động phủ + Thiên Kiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Bảng xếp hạng mới" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: bảng Động phủ theo cấp linh mạch và bảng Thiên Kiếp theo số kiếp đã qua, vào Bia Anh Danh). |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-057](ADR-057-Leaderboards.md) (Bia Anh Danh) · [ADR-129](ADR-129-Cave-Dwelling.md) · [ADR-126](ADR-126-Heavenly-Tribulation.md) |

---

## Decision

**D1 — Hai tab mới** trong Bia Anh Danh: **Động Phủ** (giá trị = cấp linh mạch, chỉ người đã dựng động phủ) và **Thiên
Kiếp** (giá trị = số kiếp đã qua, chỉ người đã qua ít nhất một kiếp). Hoà điểm thì giữ thứ tự cảnh giới (cấp, EXP, id),
sắp xếp ổn định.

**D2 — Cùng cơ chế ADR-057**: một bản dùng chung cả server, cache 60 giây (`LeaderboardCache`), 50 dòng + hạng của người
hỏi. Nguồn điểm: `CavePort.veinLevels()` / `TribulationPort.passedByCharacter()` (phương thức `default` rỗng trên port,
adapter Postgres cài thật — một truy vấn mỗi lần làm mới cache).

**D3 — Giao thức, chỉ thêm**: `LEADERBOARD_KIND_CAVE = 6`, `LEADERBOARD_KIND_TRIBULATION = 7`. Client cũ gửi giá trị lạ
⇒ server trả bảng cảnh giới như trước (không lỗi). Không đổi schema.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tab Động Phủ (cấp linh mạch) + Thiên Kiếp (số kiếp), cache chung 60 s, opcode cũ 1202/1203 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: proto, `LeaderboardCache` (`rankByScore`, `rowsFor`), `CaveCommands.veinLevels`,
   `TribulationHooks.passedByCharacter`, hai adapter, client 2 tab + 4 chuỗi.
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01** (`--leaderboards=cave,tribulation`, Benkei hỏi): Động Phủ 3 dòng — #1 Kagemaru linh mạch 2, #2 Benkei 1, #3 hieutm 1; Thiên Kiếp 3 dòng — #1 Kagemaru 2 kiếp, #2 Benkei 1, #3 tanthua44 1 (hoà điểm giữ thứ tự cảnh giới); hạng mình = 2 ở cả hai bảng.
