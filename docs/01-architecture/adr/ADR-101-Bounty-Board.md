# ADR-101: Bảng truy nã

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Nhiệm vụ truy nã" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: bảng truy nã đổi mỗi ngày: hạ N con quái chỉ định ⇒ thưởng; 5 lượt/ngày; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) · [ADR-069](ADR-069-Guild-Weekly-Quest.md) (đếm hạ quái qua bước phụ của thưởng hạ quái) |

---

## Decision

**D1 — Bảng của ngày**: `bounty_template(code, monster_code, kill_count, reward_gold, reward_exp, min_level)` là THAM SỐ.
Mỗi ngày (giờ Việt Nam) bảng có **3** truy nã chọn TẤT ĐỊNH từ ngày (băm ngày → chỉ số) trong các mẫu — mọi người cùng
thấy một bảng, không cần lưu bảng.

**D2 — Nhận và làm**: nhận một truy nã trên bảng hôm nay ⇒ dòng `character_bounty(character_id, day, bounty_code,
progress, status)`. Tối đa **5 lượt hoàn thành/ngày** (đếm dòng COMPLETED hôm nay); một lúc chỉ giữ MỘT truy nã đang làm;
cùng một truy nã làm lại được trong ngày (tới khi hết lượt). Hạ quái đúng loại ⇒ +1 tiến độ (bước phụ của đường thưởng
hạ quái — chỉ người ra đòn cuối, không chia nhóm). Đủ số ⇒ tự hoàn thành và trao thưởng ngay (vàng qua GoldLedger, EXP
cùng đường thưởng quest) trong cùng giao dịch.

**D3 — Giao thức, chỉ thêm**: 614/615 `GetBountyBoardRequest`/`Response{bounties[], active, completed_today}`, 616/617
`AcceptBountyCommand{bounty_code}`/`Response{outcome}`, 618 `BountyProgressEvent{bounty_code, progress, target,
completed}`.

**D4 — Client**: tab "Truy nã" của cửa sổ Hằng ngày (E).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 3 truy nã/ngày tất định theo ngày, 5 lần xong/ngày, một truy nã đang làm, tự trao thưởng | Accepted |

## ADR cần tạo

- Không.

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 3).
2. ⏳ Kiểm sống bằng gameProbe.
