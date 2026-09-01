# CLAUDE.md — Quy tắc làm việc cho AI trên Project Kage

> File này được Claude Code **tự động nạp vào context mỗi phiên làm việc**. Đây là bản rút gọn ở mức "luôn nhớ".
> Bản đầy đủ: [docs/00-governance/AI-WORKING-RULES.md](docs/00-governance/AI-WORKING-RULES.md).
> Luật tối cao của dự án: [docs/00-governance/KAGE-CONSTITUTION.md](docs/00-governance/KAGE-CONSTITUTION.md).

---

## 0. Trạng thái dự án (đọc trước tiên)

**Phase 0-9 đã xong Exit Criteria** (Foundation → Engine Core → Network → Character/Map/Movement →
Combat/Skill/Monster → Inventory/Equip/Shop → Quest/NPC (**MVP**) → Guild/Party/Trade →
Dungeon/Boss/PvP → Optimization; checklist thật ở [ROADMAP.md](docs/11-process/ROADMAP.md)).
Đang ở **Phase 10 — Release** (Closed Beta → Open Beta → RC → Production). Luật phase vẫn nguyên:
không mở phase sau khi phase trước chưa đạt Exit Criteria.

Phase 9 đóng 2026-07-30 kèm **2 caveat còn mở, không chặn**: CPU không tuyến tính theo N nên **không
ngoại suy quá N=2000** (đo lại thật nếu vượt), và off-heap (~500-650KB/kết nối) chưa soak dài.

3 quyết định nền tảng chốt từ Phase 0 (không đổi trừ khi có ADR revisit):
- Package root: **`dev.kage`** ([ADR-012](docs/01-architecture/adr/ADR-012-Package-Root.md))
- **Single-realm** + cột `realm_id` dự phòng ([ADR-010](docs/01-architecture/adr/ADR-010-Realm-Model.md))
- Tick rate: **60Hz** — `FIXED_DT = 1/60s` ([ADR-018](docs/01-architecture/adr/ADR-018-Tick-Rate.md))

Quyết định mới nhất: **[ADR-038](docs/01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)** (✅ Accepted 2026-09-01) — **đổi bối cảnh từ ninja học viện sang TU TIÊN** (kiểu Phàm Nhân Tu Tiên): chỉ đổi tường thuật + tên hiển thị, KHÔNG đổi cơ chế/protocol/DB/tên code. Lv1–100 hiển thị thành **6 cảnh giới × 9 tầng** (Luyện Khí→Trúc Cơ→Tử Phủ→Kim Đan→Nguyên Anh→Hoá Thần). GDD v0.3.0 + GLOSSARY v1.1.0 + **[STORY.md](docs/13-game-design/STORY.md)** v0.2.0 (SSOT cốt truyện, §1–§8 viết đầy đủ — mọi quest/NPC/tên vùng phải map về một arc ở đó, luật viết quest ở §8) đã theo. Còn 🔶 TODO-OPUS: Đợt 2 (i18n + DB seed + quest bám STORY) → Đợt 3 (art audit) + D8 (quét "ninja" trong docs cũ). Trước đó: **[ADR-035](docs/01-architecture/adr/ADR-035-Client-State-Sync-Opcodes.md)** (Accepted 2026-08-27) — thêm **7 opcode** lấp 4 khoảng trống đồng bộ trạng thái client: liệt kê túi đồ (507/508), catalog vật phẩm (509/510, phương án **A′** — gửi qua mạng kèm `version` do server phán quyết), số vàng (`GOLD_CHANGED_EVENT` 209), nhật ký nhiệm vụ (610/611). Chỉ THÊM, không đổi message đang chạy, không đổi schema DB, không tăng `protocolVersion`. Mở khoá giao diện túi đồ/trang bị/cửa hàng/nhật ký nhiệm vụ và sửa bug thật "nhận nhiệm vụ → relog → mất thưởng vĩnh viễn".

Quyết định nền rendering: **[ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)** (Accepted 2026-08-03) — nhân vật/quái/NPC render **3D thời gian thực** qua camera orthographic khoá góc (2.5D), VFX/icon/UI vẫn sprite 2D. **Đã chạy thật, đã có ảnh chụp** (2026-08-07) và toàn bộ 9 mục "Việc cần làm tiếp theo" của ADR-031 đã ✅ đóng (2026-08-08) — `gdx-gltf` vendored vào `third-party/m2/` theo [ADR-032](docs/01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md) (**Accepted**), ngân sách tam giác 45/45 file đạt chuẩn §4.4. Client giờ có HUD combat thật đầu tiên (HP/mục tiêu/nút skill) — xem `client-core/ui/`. Lưu ý còn hiệu lực: sandbox GraalJS ([ADR-016](docs/01-architecture/adr/ADR-016-Scripting-Engine.md)) mới ở mức `CONSTRAINED`-equivalent, CHƯA phải `ISOLATED`/`UNTRUSTED` thật. Toàn bộ ADR: [adr/README.md](docs/01-architecture/adr/README.md).

⚠️ **Trước khi ghi `libs.versions.toml`: verify phiên bản thư viện thật** (Java 25, libGDX, Netty...), không copy số từ docs ([TECHNOLOGY-STACK](docs/01-architecture/TECHNOLOGY-STACK.md)).

---

## 0.5. Gen code hiệu quả (đọc ít, đúng chỗ)

Trước khi gen code, dùng lớp context tối thiểu — đừng đọc toàn bộ docs:

1. **Thẻ luật vùng** tự auto-load khi sửa file: `shared/`, `engine/`, `client/`, `server/` mỗi thư mục có `CLAUDE.md` riêng.
2. **Template** copy-paste: [docs/templates/](docs/templates/) — feature-module, ecs, protocol.
3. **Bản đồ task→doc**: [docs/AI-CODEGEN-GUIDE.md](docs/AI-CODEGEN-GUIDE.md) — tra "cần làm X → đọc đúng mục nào".
4. **Enforcement gánh luật**: từ Phase 0, ArchUnit + static analysis bắt vi phạm → gen theo hiểu biết tốt nhất, để build bắt lỗi còn lại, đừng nạp doc chỉ để "chắc chắn".

Nếu đang gen class bình thường mà mở > 2 file doc → đang đọc thừa.

---

## 1. Vai trò của AI

AI là **kỹ sư triển khai**, không phải kiến trúc sư trưởng.

- AI **đề xuất**. Con người **quyết định**.
- AI được tự do trong phạm vi đã có tài liệu chốt.
- AI **không được** tự quyết những thứ tài liệu ghi là "chưa chốt".

---

## 2. Bốn câu hỏi bắt buộc trước khi viết code

Trả lời trong đầu (hoặc ra màn hình nếu thay đổi lớn) trước mọi thay đổi:

1. **Module nào bị ảnh hưởng?** → tra [REPOSITORY-STRUCTURE.md](docs/01-architecture/REPOSITORY-STRUCTURE.md)
2. **Có phá dependency rule không?** → tra [ARCHITECTURE.md](docs/01-architecture/ARCHITECTURE.md)
3. **Có cần ADR không?** → xem §4 bên dưới
4. **Có phá backward compatibility không?** (packet/DB/public API)

---

## 3. Khi nào ĐƯỢC code ngay

✅ Sửa bug có test tái hiện được.
✅ Thêm code vào module đã có, đúng pattern đã có, không đổi public API.
✅ Thêm test.
✅ Refactor nội bộ module, không đổi behavior, không đổi API.
✅ Việc chủ dự án nói rõ "làm đi".

## 4. Khi nào PHẢI DỪNG và hỏi / tạo ADR

🛑 Thêm/xoá/đổi tên **module** hoặc **package root**.
🛑 Thêm **thư viện mới** (bất kỳ dependency nào không có trong [TECHNOLOGY-STACK.md](docs/01-architecture/TECHNOLOGY-STACK.md)).
🛑 Đổi **protocol** (packet, opcode, field protobuf).
🛑 Đổi **database schema**.
🛑 Đổi **kiến trúc** (thay ECS, thay DI, thay transport...).
🛑 Đụng vào bất kỳ mục nào đang liệt kê ở **"🔴 ĐANG CHỜ CHỦ DỰ ÁN"** trong [docs/README.md](docs/README.md).
🛑 Khi phát hiện thiết kế hiện tại có vấn đề → **mô tả vấn đề + đề xuất + chi phí refactor**, KHÔNG tự sửa.

---

## 5. Ranh giới kiến trúc không được vi phạm

```
Gameplay (engine-*, client-core, server-world)
   ❌ KHÔNG import: Netty, PostgreSQL, Redis, Spring, Docker, Kubernetes
   ✅ CHỈ phụ thuộc abstraction (Port)

domain/  ❌ không import  infrastructure/
```

- **Server Authority tuyệt đối**: client không bao giờ quyết định gameplay. Mọi Command từ client phải được server validate lại.
- **Component chỉ chứa dữ liệu.** Không method gameplay, không gọi service.
- **Entity chỉ là ID.** Không kế thừa, không override.
- **Mọi gameplay logic nằm trong System.**

---

## 6. Trong game loop — cấm tuyệt đối

❌ `new` object mỗi frame (dùng Pool) · ❌ Boxing · ❌ Reflection · ❌ Annotation scan runtime
❌ Dynamic proxy · ❌ Java Serialization · ❌ Stream tạo allocation đáng kể · ❌ Log mỗi frame
❌ Virtual Thread cho game loop (game loop luôn chạy Platform Thread)

---

## 7. Java 25 — mặc định

| Dùng | Cho |
|---|---|
| `record` | DTO, Packet, Command, Response, Value Object |
| `sealed` | Hierarchy đóng (Packet, GameState, SkillTarget) |
| Pattern matching | Thay `instanceof` truyền thống |
| Switch expression | Thay switch cũ |
| Text block | SQL dài |
| Virtual Thread | **Chỉ** blocking I/O, **không** game loop |

Chi tiết: [JAVA-GUIDELINES.md](docs/02-coding-standards/JAVA-GUIDELINES.md)

---

## 8. Thứ tự ưu tiên khi phải đánh đổi

```
Correctness → Readability → Maintainability → Testability → Performance → Elegance
```

**Không bao giờ đảo ngược.** Không đánh đổi correctness lấy performance.

---

## 9. Performance — quy tắc vàng

**Benchmark trước. Refactor sau.**
Mọi tối ưu phải có số liệu. Không chấp nhận "tôi nghĩ nhanh hơn".
Chi tiết: [BENCHMARK.md](docs/09-performance/BENCHMARK.md)

---

## 10. Định nghĩa HOÀN THÀNH

Một thay đổi chỉ xong khi: compile ✓ test ✓ không warning ✓ đúng coding standard ✓ không allocation thừa ✓ có JavaDoc nếu là Public API ✓ tài liệu đã cập nhật nếu đổi kiến trúc ✓

Chi tiết: [KAGE-CONSTITUTION.md — Definition of Done](docs/00-governance/KAGE-CONSTITUTION.md#chương-vii--definition-of-done--definition-of-ready)

---

## 11. Cấm kỵ khi viết code

❌ `catch (Exception e)` tổng quát · ❌ nuốt exception · ❌ `printStackTrace()` · ❌ TODO trong catch
❌ Magic number (dùng `CombatConstants`, không `speed *= 1.35`)
❌ `public static final int FIRE = 1` (dùng enum)
❌ Trả `null` (dùng Optional / empty collection / Null Object)
❌ Class tên `Manager`, `Helper`, `Util`, `Common`, `Data`, `Info` nếu không phản ánh đúng trách nhiệm
❌ Package theo loại (`service/`, `controller/`, `dto/`) — package theo **feature** (`inventory/`, `guild/`)

---

## 12. Ngôn ngữ

- **Tài liệu**: tiếng Việt.
- **Code, tên class, package, commit message, tên biến**: tiếng Anh.
- Tra thuật ngữ Việt–Anh: [GLOSSARY.md](docs/GLOSSARY.md) — không tự dịch khác đi.

---

## 13. Khi được giao viết tài liệu

Theo quy trình ở [DOCUMENTATION.md](docs/11-process/DOCUMENTATION.md):
1. Phân tích mục đích tài liệu.
2. Đề xuất bố cục (TOC) + giải thích ngắn từng mục.
3. **Chờ duyệt bố cục.**
4. Viết chi tiết.
5. Mỗi tài liệu kết thúc bằng 4 mục: Technical Decisions · ADR cần tạo · Tài liệu liên quan · Việc cần làm tiếp theo.

Không lặp lại nội dung Constitution trong tài liệu khác — **trích dẫn link**, không copy.

---

## 14. Nếu tài liệu mâu thuẫn nhau

Thứ tự thắng: **KAGE-CONSTITUTION.md** → **ADR (Accepted)** → tài liệu chuyên môn → code comment.

Phát hiện mâu thuẫn → báo cáo, **không tự chọn bên nào**.
