# ADR-002: Chọn libGDX làm nền tảng Client Engine

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [ADR-001](ADR-001-Why-Java25.md), [Hiến pháp Điều 4, 28](../../00-governance/KAGE-CONSTITUTION.md), [TECHNOLOGY-STACK.md §4](../TECHNOLOGY-STACK.md) |

---

## Context

Cần engine 2D chạy trên Java (ràng buộc từ [ADR-001](ADR-001-Why-Java25.md)), hỗ trợ đa nền tảng thật (Điều 28), đủ trưởng thành cho MMORPG 2D side-view: rendering, input, audio, asset management, và có công cụ dựng scene.

## Decision

Dùng **libGDX** làm client engine, backend **LWJGL3** cho Desktop, kèm **HyperLap2D** làm scene editor.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **libGDX** | Java-native; đa nền tảng thật (Desktop/Android/iOS); cộng đồng lớn, sống lâu (>10 năm); tích hợp Box2D/FreeType/Scene2D sẵn; HyperLap2D hỗ trợ trực tiếp | Không có editor tích hợp; API rendering low-level; UI (Scene2D) khá cũ | **Chọn** |
| Tự viết engine trên LWJGL/OpenGL thuần | Kiểm soát tuyệt đối, tối ưu đúng nhu cầu | Chi phí khổng lồ; phải tự làm asset loader, input, audio, đa nền tảng; vi phạm Điều 9 (Simplicity) và Điều 10 (Business First — engine phục vụ gameplay, không phải ngược lại) | Không chọn — sẽ tốn nhiều năm trước khi có gameplay |
| jMonkeyEngine | Java, engine đầy đủ hơn | Thiên về 3D; 2D là công dân hạng hai; nặng hơn cần thiết | Không chọn — sai thể loại |
| Unity / Godot | Editor mạnh, tooling tốt, asset store | Không phải Java → phá [ADR-001](ADR-001-Why-Java25.md) và Điều 4 ("Game Engine 2D bằng Java") | Không chọn — ngoài ràng buộc ngôn ngữ |
| LWJGL + thư viện rời (tự ghép) | Linh hoạt chọn từng phần | Phải tự ghép và tự bảo trì tích hợp; hiệu quả tương đương libGDX nhưng tốn công hơn nhiều | Không chọn |

## Consequences

**Tích cực:**
- Có ngay rendering + input + audio + asset + đa nền tảng.
- HyperLap2D bù được điểm yếu lớn nhất của libGDX (không có editor).
- `client-core` viết một lần, chạy Desktop/Android/iOS qua launcher riêng (Điều 28).

**Đánh đổi phải chấp nhận:**
- **libGDX là dependency nền tảng, thay rất đắt.** Đây là chấp nhận có ý thức — không phải mọi thứ đều đáng bọc sau abstraction. Bọc libGDX sau một lớp engine tự viết sẽ mất chính lợi ích của libGDX và vi phạm Điều 9.
- Rendering hiện đại (shader phức tạp, lighting) phải tự viết GLSL.
- Scene2D cũ → UI phức tạp sẽ tốn công ([UI.md](../../04-client/UI.md)).
- Phụ thuộc HyperLap2D — dự án nhỏ hơn libGDX, rủi ro bảo trì cao hơn.

**Module bị ảnh hưởng:** `engine-core`, `engine-physics`, `client-*`, `tools/asset-pipeline`.

## Revisit Trigger

- libGDX ngừng được bảo trì (không release >18 tháng, issue tồn đọng không xử lý).
- HyperLap2D bỏ tương thích với phiên bản libGDX ta cần → khi đó **thay HyperLap2D**, không thay libGDX (xem [RESOURCE.md](../../04-client/RESOURCE.md)).
- Không revisit vì lý do "engine khác có editor đẹp hơn".
