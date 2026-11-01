# ADR-132: Bậc trang phục theo cảnh giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Bậc trang phục theo cảnh giới" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: B2 khi vào Trúc Cơ Cấp 19, B3 khi vào Tử Phủ Cấp 37). |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-045](ADR-045-Appearance-Changed-Event.md) (báo đổi ngoại hình) · [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) (model 3D) · ART-PIPELINE §2.4 |

---

## Decision

**D1 — Bậc theo cảnh giới, không theo trang bị**: B1 Luyện Khí (Cấp 1–18), **B2 từ Trúc Cơ (Cấp 19)**, **B3 từ Tử Phủ
(Cấp 37)** — `CharacterAppearance.tierFor(level)`. Trước đó server luôn gửi B1 (hằng số) dù art đã đủ 15 model B1/B2/B3.
Không đổi schema: bậc suy ra từ cột `character.level` đã có.

**D2 — Đổi ngay trong phiên**: hook lên cấp (`CharacterLevelSync`, sau sư đồ) gọi `CostumeTierHooks` ghi ngoại hình mới
vào registry; CHỈ khi bậc thật sự đổi (`updateAppearance` nay trả ngoại hình cũ) mới xếp `enqueueAppearanceChanged`
vào World đang chứa nhân vật ⇒ người đang nhìn thấy nhận gói đổi ngoại hình (ADR-045) và tải model mới. Lên cấp không
vượt mốc thì không phát gì.

**D3 — Giao thức**: không đổi — `CostumeTier` đã có trong `CharacterSpawnedEvent` / gói đổi ngoại hình từ trước.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | B1 < 19 ≤ B2 < 37 ≤ B3; đổi trong phiên khi vượt mốc, chỉ phát khi bậc đổi | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [ART-PIPELINE](../../14-art/ART-PIPELINE.md) §2.4

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: `CharacterAppearance.tierFor/of(faction, level)` (+ `CharacterAppearanceTest`),
   `CostumeTierHooks`, `FactionMapping.toProto`, ba chỗ dựng ngoại hình truyền cấp thật.
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01**: Kagemaru (Cấp 44) và Benkei (Cấp 19) cùng vào world ⇒ mỗi người thấy người kia đúng bậc (B3 / B2). Đặt Benkei về Cấp 18 đủ EXP ⇒ Kagemaru thấy B1; Benkei độ kiếp, qua 3 đợt, về world chính lên Cấp 19 ⇒ gói spawn tới Kagemaru mang **B2**. (Lên Cấp 19/37 chỉ xảy ra qua Thiên Kiếp vì 18/36 là trần — đường thắng kiếp đi qua sendExperience nên hook chạy.)
