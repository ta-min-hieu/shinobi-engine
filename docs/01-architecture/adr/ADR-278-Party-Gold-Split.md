# ADR-278: Chia vàng cho nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Chia vàng cho nhóm" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /chiavang N: chia đều vàng cho thành viên nhóm cùng bản đồ (server, có audit)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (GoldLedger) · [ADR-115](ADR-115-Security-Pin.md) (khoá an toàn) · [ADR-169](ADR-169-Gold-Ledger-History.md) (sổ vàng) |

---

## Decision

**D1 —** `SplitPartyGoldCommand{amount}` → `SplitPartyGoldResponse{outcome, each, recipients, gold_after}` (1343/1344).
Người nhận = đồng đội ĐANG online ở CÙNG World với người chia (hầm ngục / đấu trường / world chính — `WorldRouteLookup`),
không gồm người chia. `each = amount / số người`; phần dư ở lại người chia. `amount` 1 – 1.000.000.000 và `each ≥ 1`
(`INVALID_AMOUNT`); không ai nhận ⇒ `NO_RECIPIENT`; chưa có nhóm ⇒ `NOT_IN_PARTY`.

**D2 — Một transaction, có audit.** `PartyGoldSplitPort` (feature-party) / `PostgresPartyGoldSplitAdapter`: trừ người chia
qua `GoldLedger.deduct` (không đủ ⇒ `NOT_ENOUGH_GOLD`, chưa ghi gì), cộng từng người nhận qua `GoldLedger.credit`
(sổ vàng `gold.reason.party_split` / `party_split_received`, `GoldChangedEvent` tự phát sau commit), audit
`PARTY_GOLD_SPLIT` + `PARTY_GOLD_RECEIVED`. Không bảng mới, không migration.

**D3 — Khoá an toàn.** Vàng rời túi ⇒ lệnh đi qua `pinGuarded` như giao dịch (ADR-115).

**D4 —** Người nhận thấy một dòng chat hệ thống; client `/chiavang <số>` (hoặc `/splitgold`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1343/1344; cùng World; một transaction + audit; khoá an toàn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `PartyGoldSplitFacade`, `PostgresPartyGoldSplitAdapter`, `Lot43Commands.splitGold`, `PartyGoldSplitCoordinator`.
2. ✅ Test: `PartyGoldSplitFacadeTest`, `Lot43IntegrationTest` (Postgres thật: bảo toàn tổng vàng, audit, không đủ thì không đổi gì).
