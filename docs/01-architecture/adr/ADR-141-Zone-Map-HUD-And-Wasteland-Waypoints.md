# ADR-141: Biển vùng PvP trên HUD và trạm truyền tống Hoang Vực

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn hai mục lô 26: "Báo vào vùng PvP trên HUD" (phần D4 treo của ADR-137) và "Trạm dịch chuyển Hoang Vực". Cách làm do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) D4 · [ADR-100](ADR-100-Teleport-Talisman.md) · [ADR-102](ADR-102-Guild-War.md) D6 · [ADR-139](ADR-139-Arc-Four-Descend-The-Mountain.md) |

---

## Bối cảnh

ADR-137 mở dải PvP tự do ở Hoang Vực nhưng client không biết dải nằm đâu. Hai hậu quả: người chơi bước vào dải mà
không được báo; và **click nhắm người chơi khác chỉ bật trong giờ bang chiến** (ADR-102 D6), nên trong dải PvP chỉ
probe đánh được người (`--attackTarget=<id>`), client thật thì không. Cùng lúc, mạng truyền tống ADR-100 dừng ở Trạm Thí
Luyện (208) trong khi trục chính đã dài tới 417.

## Decision

**D1 — Protocol, chỉ THÊM**: cặp `ListZoneMapRequest` (338) / `ListZoneMapResponse` (339) — `repeated ZoneRange
pvp_ranges` (bảng `pvp_zone`) và `repeated ZoneRange safe_ranges` (bảng `safe_zone`) của world chính. `protocolVersion`
KHÔNG tăng. Request/response chứ không phải event đẩy: bản đồ là config tĩnh nạp lúc khởi động, client hỏi một lần khi
vào world là đủ (cùng kiểu danh sách trạm truyền tống 317/318).

**D2 — Server**: `AdventureCommands.zoneMap` đọc qua `WorldGeometryFacade` (thêm vào `AdventureFacades`), chạy trên
Virtual Thread như mọi `storageCommand`. Không đụng `WorldFacade`: luật đòn đánh giữ nguyên ADR-137.

**D3 — Client, chỉ hiển thị**: `ZoneMapCoordinator` hỏi bản đồ ở khung đầu, mỗi khung so hoành độ của chính mình:
- trong dải PvP và ngoài mọi vùng an toàn ⇒ biển đỏ son thường trực "Vùng PvP — tu sĩ khác ra tay được" (cột góc
  trên-phải của HUD, dưới dải hầm ngục) và một dòng hệ thống lúc bước vào/ra;
- trong dải PvP nhưng đứng ở trạm an toàn ⇒ biển ngọc "Trạm an toàn giữa vùng PvP";
- ngoài world chính (sau gói 308 sang hầm ngục/đấu trường) ⇒ không biển nào.

Màu không phải thông tin duy nhất: chữ nói đủ. Dải ngược (min > max) bị bỏ, tối đa 64 dải mỗi loại (luật gói repeated
mới). Không cấp phát mỗi khung.

**D4 — Click nhắm người trong dải PvP**: điều kiện cho `DuelTargeting` thành "bang mình đang chiến **hoặc** mình đứng
trong dải PvP" (`ZoneMapCoordinator.orInPvpZone`). Server vẫn phán quyết: đối phương ở ngoài dải hay trong trạm an toàn
thì đòn bị bỏ như cũ. Nhãn mục tiêu đổi từ "Đối thủ bang chiến" thành "Tu sĩ đối địch" vì nay dùng cho cả hai.

**D5 — Trạm truyền tống (V114)**: ba trạm mới, mở khi đi ngang (luật ADR-100), đặt đúng giữa ba vùng an toàn đã có:
`inner_sect_gate` @240 (Cổng Nội Môn, V109), `wasteland_descent` @304 (Trạm Hạ Sơn, V113), `deep_dead_vein` @368
(Trạm Tử Mạch Sâu, V113 — nằm giữa dải PvP nhưng là vùng an toàn). Mã đã grep toàn migration, không trùng.

## Kiểm chứng

- `ZoneMapCoordinatorTest` (hỏi một lần, báo vào/ra, trạm an toàn thắng dải PvP, ngoài world chính không báo, dải hỏng
  bị bỏ, click nhắm chỉ bật trong dải PvP).
- `SafeZoneIntegrationTest.shouldLandEveryWastelandWaypointInsideASafeZone` (Postgres thật).
- `InboundPacketFuzzTest`/`InboundPacketScenarioTest` giữ bất biến "client không tự gửi lệnh từ một khung vẽ": yêu cầu
  bản đồ gửi ở `WorldScreen.show()`, cùng chỗ nhật ký nhiệm vụ (bản đầu gửi ở khung đầu và bị hai test này bắt).
- **Kiểm sống 2026-11-01** (gameProbe, cờ mới `--lot21=zoneMap`): server trả `wasteland_rim[179,205]`,
  `wasteland_deep[307,417]` và 10 vùng an toàn. Kagemaru đi tới 304 ⇒ sau một nhịp quét `wasteland_descent mở=true`;
  `teleport:sect_gate` OK về −5; `teleport:deep_dead_vein` ⇒ `NOT_UNLOCKED` (chưa đi ngang); `teleport:wasteland_descent`
  OK, gói 308 đặt đúng x=304.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cặp request/response 338/339, không event đẩy | Accepted |
| 2 | Client tự so vị trí với dải, chỉ để hiển thị + bật click nhắm | Accepted |
| 3 | Ba trạm truyền tống mới tại 240/304/368 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) · [ADR-100](ADR-100-Teleport-Talisman.md) · [adr/README.md](README.md)

## Việc cần làm tiếp theo

- Phạt giết người trong dải PvP (sát khí) — mục riêng của lô 26.
