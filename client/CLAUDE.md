# CLAUDE.md — vùng `client/`

> Thẻ luật vùng. Auto-load khi sửa `client/**`. Bản đầy đủ: [/CLAUDE.md](../CLAUDE.md).
> **Client chỉ hiển thị. Client không quyết định gameplay** (Điều 15).

## Ranh giới bắt buộc

❌ `client-desktop/android/ios` chứa **chỉ** launcher + PlatformAdapter — không gameplay (Điều 28)
❌ `Gdx.app.getType() == Android` trong `client-core` — dùng `PlatformAdapter`
❌ Tự tính damage/giá/tỉ lệ — server tính, client render kết quả
❌ Lưu giá trị gameplay ở client (sửa được = hack)
❌ Hardcode chuỗi hiển thị — dùng i18n `errorKey` ([UI.md §7](../docs/04-client/UI.md))
❌ Gameplay biết phím cụ thể — dùng `GameAction` (ý định)

## Đúng cách

✅ Gửi **ý định** (`UseSkillCommand`), không gửi **kết quả**
✅ Dự đoán **chỉ** di chuyển/animation của chính mình; server nói khác → sửa theo server
✅ Người chơi khác: nội suy, không dự đoán
✅ Network I/O thread riêng; packet xử lý đầu tick
✅ Widget nhận dữ liệu qua setter, không tự đọc ECS ([UI.md §4](../docs/04-client/UI.md))
✅ UI: nút mobile ≥ 48dp; không dùng màu làm thông tin duy nhất

## Rendering
Không sửa Component gameplay (chỉ đọc) · không `new`/load asset mỗi frame · 1 atlas/zone, UI atlas riêng.

## Đọc sâu khi cần
[CLIENT.md](../docs/04-client/CLIENT.md) · [UI.md](../docs/04-client/UI.md) · [RESOURCE.md](../docs/04-client/RESOURCE.md) · [RENDERING.md](../docs/03-engine/RENDERING.md)
