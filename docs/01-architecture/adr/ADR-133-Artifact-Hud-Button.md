# ADR-133: Nút pháp bảo trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Nút pháp bảo trên HUD" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: icon + vòng hồi chiêu + phím tắt). Chi tiết do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-127](ADR-127-Artifacts.md) (pháp bảo, `/phapbao`) |

---

## Decision

**D1 — Nút thứ ba ở cụm hành động góc dưới-phải**, bên TRÁI nút chiêu, cùng cỡ nút chiêu (76): biển tên "Pháp bảo",
trong nút là số giây hồi chiêu còn lại (rỗng khi sẵn sàng), nút mờ khi đang hồi. Nút Đánh vẫn to nhất, ngoài cùng.

**D2 — Phím 1** (`GameAction.USE_ARTIFACT`, hàng số và bàn phím số): phím chữ đã chia hết cho các cửa sổ. Phím đi qua
`KeyPressEdge` như phím chiêu (giữ phím không spam, gõ trong ô chat không kích).

**D3 — Một đường duy nhất**: lệnh chat `/phapbao`, nút HUD và phím 1 đều gọi `ArtifactCoordinator.use()`. Đếm ngược
lấy từ chính gói trả lời của server (OK ⇒ hồi chiêu đầy, COOLDOWN ⇒ số giây còn lại); client không tự tính hồi chiêu
nào khác — server vẫn là bên quyết. Chưa biết mặc món gì thì nút vẫn bấm được; server trả `NO_ARTIFACT` thành dòng hệ
thống.

**D4 — Không đổi giao thức/schema.** "Icon" của nút là chữ + đếm ngược, như nút chiêu hiện có (bộ nút HUD chưa dùng
icon ảnh — đổi cả bộ là việc của "Khung UI tu tiên" cùng lô).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nút pháp bảo cạnh nút chiêu + phím 1; đếm ngược theo gói server | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: `HudActionButtons`/`CombatHud.setOnArtifactClicked/setArtifactCooldown`,
   `GameAction.USE_ARTIFACT` + `DesktopInputMapper`, `LocalIntentSender.setOnArtifact`,
   `ArtifactCoordinator.use/cooldownRemainingSeconds`.
2. ⏳ Xem bằng client thật (ảnh chụp HUD). Đường gửi lệnh đã kiểm sống ở ADR-127 (/phapbao) — nút và phím gọi đúng hàm đó.
