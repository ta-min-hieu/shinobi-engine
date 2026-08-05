# ADR-020: FitViewport cho camera gameplay — công bằng PvP thắng tận dụng màn hình rộng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-08-01 |
| Deciders | AI (Claude) đề xuất; chủ dự án Accept 2026-08-01 qua chat |
| Related | [RENDERING.md §3](../../03-engine/RENDERING.md), [ADR-002](ADR-002-Why-libGDX.md) (libGDX), Điều 15 (Server Authority — tinh thần công bằng áp dụng cả phía client) |

---

## Context

`RENDERING.md §3` đánh dấu Viewport là **"Chưa chốt"**, tự mô tả đây là "quyết định gameplay, không
phải kỹ thuật" vì ảnh hưởng công bằng PvP (màn hình rộng thấy nhiều hơn = lợi thế thật nếu dùng
`ExtendViewport`/`ScreenViewport`).

**Xác nhận bằng code thật**: `WorldScreen.java` (client-core, code đang chạy) đã dùng
`new FitViewport(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS, camera)`
— `FitViewport` với **chiều cao cố định** (`VIEWPORT_HEIGHT_UNITS = 10f`), không phải
`ExtendViewport`/`ScreenViewport`. Quyết định đã được đưa ra và chạy thật từ Phase 1, chỉ chưa có ADR
ghi lại — cùng tình huống với ADR-011 (ArchUnit).

## Decision

**Dùng `FitViewport`** cho camera gameplay chính — mọi người chơi thấy đúng cùng kích thước vùng chơi
tính bằng world unit, bất kể tỉ lệ màn hình thật. Chấp nhận viền đen (letterbox/pillarbox) trên màn
hình không khớp tỉ lệ, đổi lấy công bằng PvP tuyệt đối.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. `FitViewport`** | Mọi người chơi thấy **đúng cùng vùng chơi** — không ai có lợi thế tầm nhìn do màn hình to hơn; đơn giản, libGDX có sẵn | Có viền đen trên tỉ lệ màn hình lệch (16:9 vs 20:9 vs 4:3) | **Chọn** — công bằng PvP là yêu cầu gameplay cứng (RENDERING.md tự ghi "ảnh hưởng cân bằng PvP"), viền đen là đánh đổi UX chấp nhận được, không phải lỗi |
| **B. `ExtendViewport`** | Không viền đen, tận dụng hết màn hình — trải nghiệm "đẹp" hơn trên máy lệch tỉ lệ | Màn hình rộng thấy **nhiều vùng chơi hơn** theo đúng nghĩa đen → phát hiện kẻ địch/né đòn sớm hơn người chơi màn hình hẹp, lợi thế PvP thật, không phải cảm giác | **Không chọn** — vi phạm trực tiếp yêu cầu công bằng đã ghi trong chính RENDERING.md |
| **C. `ScreenViewport`** | Sắc nét 1:1 pixel, không scale | Lợi thế màn hình lớn còn RÕ RỆT hơn cả ExtendViewport (thấy nhiều hơn theo tỉ lệ điểm ảnh thật) | **Không chọn** — cùng lý do B, nặng hơn |

## Consequences

**Tích cực:**
- Công bằng PvP được đảm bảo bằng kỹ thuật, không phụ thuộc thiết lập màn hình người chơi.
- Gỡ nợ tài liệu: RENDERING.md §3 không còn "Chưa chốt" dù code đã chọn `FitViewport` từ lâu.

**Đánh đổi phải chấp nhận:**
- Viền đen (letterbox) trên tỉ lệ màn hình lệch xa 1:1 world-viewport ratio — chấp nhận được, đây là
  đánh đổi phổ biến ở game 2D ưu tiên công bằng (nhiều MOBA/MMO 2D làm vậy).
- Nếu UI cần tràn hết màn hình (không bị giới hạn bởi viewport gameplay) thì phải render UI ở
  Viewport/Stage RIÊNG, không dùng chung camera gameplay — cần xác nhận `WorldScreen`/UI hiện tại đã
  tách đúng hay chưa (không kiểm tra trong ADR này, nêu ra như một điểm cần soát lại nếu chưa).

**Module bị ảnh hưởng:**
- Không đổi code — `WorldScreen` đã dùng đúng `FitViewport` từ trước, ADR này chỉ chính thức hoá.
- `docs/03-engine/RENDERING.md` §3 nên cập nhật bỏ "Chưa chốt", trỏ về ADR này (việc tài liệu nhỏ, có
  thể làm cùng lúc Accept).

## Revisit Trigger

- **Nếu GDD sau này định nghĩa lại "công bằng PvP" theo hướng khác** (ví dụ: PvP tách biệt hoàn toàn
  vùng chơi PvE, không còn ràng buộc tầm nhìn chung) → xem lại có cần `FitViewport` cho MỌI màn hình
  hay chỉ màn hình PvP.
- **Nếu tỉ lệ màn hình phổ biến của người chơi thật (đo được sau khi có Closed Beta) lệch quá xa
  `VIEWPORT_HEIGHT_UNITS` hiện tại khiến viền đen chiếm phần lớn màn hình, ảnh hưởng UX rõ rệt** →
  cân nhắc điều chỉnh tỉ lệ world-viewport (không phải đổi loại Viewport) trước khi đổi sang B/C.
- Không có trigger nào khác — nếu không rơi vào 2 trường hợp trên, `FitViewport` coi là ổn định lâu
  dài.
