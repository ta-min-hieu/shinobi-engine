# ADR-127: Pháp bảo

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-31) — chủ dự án chọn "Pháp bảo" trong lượt hỏi lô 24 (AskUserQuestion; mô tả kèm: ô trang bị mới, có chiêu chủ động, luyện ở lò luyện khí). Chi tiết (ba món, con số, hiệu ứng) do AI chọn theo lời chủ dự án "làm tiếp đi" — mọi con số là ĐỀ XUẤT CÂN BẰNG. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-120](ADR-120-Forging.md) (lò luyện khí, phẩm chất) · [ADR-070](ADR-070-Flying-Sword-Mount.md) / [ADR-072](ADR-072-Spirit-Pets.md) (tiền lệ ô MOUNT/PET không cần CHECK) · [ADR-046](ADR-046-Health-Recovery.md) (408 `HealthChangedEvent`) |

---

## Decision

**D1 — Ô trang bị mới `ARTIFACT`** (`EquipmentSlotType.ARTIFACT`, proto `EQUIPMENT_SLOT_ARTIFACT = 10`). Cột
`item_template.equip_slot` là chữ không CHECK — thêm ô không cần đổi schema bảng cũ, như MOUNT/PET (V71). Pháp bảo cộng
chỉ số phẳng như mọi trang bị, qua đúng đường tính chỉ số đang có.

**D2 — Ba pháp bảo đầu (V107)**, luyện ở lò luyện khí (công thức `FORGE`, có tung phẩm chất như ADR-120):

| Món | Chỉ số | Chiêu | Hồi chiêu | Cấp | Nguyên liệu |
|---|---|---|---|---|---|
| Kim Chung Tráo `golden_bell_artifact` | thủ 8, kháng 8, máu 20 | **SHIELD** khiên 25% máu tối đa, 10 giây | 60 giây | 20 | 1500 vàng, linh khoáng ×10, Linh Ngọc Thổ ×1 |
| Thanh Phong Phiến `wind_fan_artifact` | công 10, kháng 4 | **SPEED** +40% tốc chạy, 6 giây | 45 giây | 25 | 1800 vàng, linh thảo ×12, Linh Ngọc Mộc ×1 |
| Hồi Xuân Kính `spring_mirror_artifact` | thủ 4, kháng 10, máu 30 | **HEAL** hồi ngay 30% máu tối đa | 90 giây | 30 | 2500 vàng, yêu hạch ×6, Linh Ngọc Thuỷ ×1 |

Bảng `artifact_skill` (một chiêu mỗi mẫu món) là nội dung — thêm pháp bảo = thêm dòng, không đổi Java (Điều 27).

**D3 — Kích hoạt**: `/phapbao` (opcode 333). Server đọc ô ARTIFACT từ DB — không tin client nói mình mặc gì — tra chiêu,
giữ hồi chiêu theo nhân vật (RAM, `ArtifactFacade`; khởi động lại server ⇒ hồi chiêu về 0, như hồi chiêu skill), rồi
gửi `ArtifactEffectCommand` vào World. Hiệu ứng áp trên tick thread qua đúng ống có sẵn: khiên/tốc chạy là
`StatusEffect` (SHIELD, MOVE_SPEED_MOD), hồi máu đi qua `handleHealCharacter` ⇒ client nhận 408 như mọi nguồn hồi máu.

**D4 — Giao thức, chỉ thêm**: 333/334 `UseArtifactCommand/Response` (outcome OK / NO_ARTIFACT / COOLDOWN /
NOT_IN_WORLD kèm hiệu ứng, độ lớn, thời lượng, hồi chiêu). `protocolVersion` KHÔNG tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ô `ARTIFACT`; 3 món SHIELD/SPEED/HEAL luyện ở lò; chiêu đọc từ ô đã mặc ở DB; hồi chiêu RAM; opcode 333/334 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 24 phần 1): `ArtifactPort`/`ArtifactFacade` (feature-world, có test), `PostgresArtifactAdapter`
   (V107, có test Postgres trong `Lot23IntegrationTest`), `ArtifactEffect` + `WorldCommand.ArtifactEffectCommand`,
   gateway `ArtifactCommands`, client `ArtifactCoordinator` (`/phapbao`) + ô pháp bảo trong bảng trang bị, bước probe
   `artifact`, ba icon (`tools/blender/build_item_icon.py`).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V107; món cấp qua SQL thẳng vào ô ARTIFACT): Kagemaru mặc Kim
   Chung Tráo, `/phapbao` hai lần ⇒ `OK SHIELD 25% cd=60` rồi `COOLDOWN cd=59`. Benkei mặc Hồi Xuân Kính, vào Thiên Kiếp,
   chịu đợt sét 1 (164 → 132) rồi `/phapbao` ⇒ `OK HEAL 30% cd=90` và gói 408 báo 164/164 ngay. Chưa kiểm sống khiên
   hấp thụ đòn thật và tốc chạy của quạt (cùng đường `StatusEffect` mà skill đang dùng).
3. ⚠️ Chủ dự án xem: toàn bộ con số ở D2 do AI chọn; chưa có nút chiêu pháp bảo trên HUD (mới có lệnh chat).
