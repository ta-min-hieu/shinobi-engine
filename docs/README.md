# Project Kage — Documentation

MMORPG **tu tiên** side-view 2.5D ([ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md), 2026-09-01 — trước đó là bối cảnh ninja, cơ chế không đổi) bằng **Java 25 + libGDX + HyperLap2D** (client) và **Netty + PostgreSQL + Redis** (server). Kiến trúc Hybrid: Clean/Hexagonal cho nghiệp vụ, ECS + Data-Oriented cho gameplay realtime, Event-Driven giữa các hệ thống.

> **Trạng thái hiện tại (2026-09-03)**: **Phase 0–9 đã đạt Exit Criteria**, dự án đang ở **Phase 10 — Release** (checklist thật ở [ROADMAP.md](11-process/ROADMAP.md)). Bộ docs này vẫn là nguồn thiết kế gốc ([Điều 35](00-governance/KAGE-CONSTITUTION.md)) — code phải khớp docs, lệch thì báo cáo mâu thuẫn (§14 [/CLAUDE.md](../CLAUDE.md)), không tự chọn bên nào.
>
> *(Ghi chú lịch sử — **Cập nhật 2026-07-22**: Phase 0–4 đã code xong, đạt Exit Criteria — foundation, engine core, network, character/map/movement, combat/skill/monster (kể cả script sandbox GraalJS thật, [ADR-016](01-architecture/adr/ADR-016-Scripting-Engine.md)). Lúc đó đang ở Phase 5 — Inventory · Equipment · Shop.)*

---

## Bắt đầu từ đâu

| Nếu bạn là | Đọc |
|---|---|
| **AI/Claude Code** sắp gen code | [AI-CODEGEN-GUIDE.md](AI-CODEGEN-GUIDE.md) — đọc ít, đúng chỗ, dùng template |
| **AI/Claude Code** tìm luật làm việc | [/CLAUDE.md](../CLAUDE.md) (tự nạp) → [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) |
| **Người mới** vào dự án | Theo thứ tự nhóm 00→13 bên dưới |
| **Muốn biết game là gì** | [GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| **Muốn biết vì sao chọn X** | [ADR Index](01-architecture/adr/README.md) |
| **Cần bắt đầu code** | [ROADMAP.md](11-process/ROADMAP.md) — nhưng xem "🔴 ĐANG CHỜ CHỦ DỰ ÁN" trước |

---

## Bản đồ tài liệu

### 00 · Governance — luật tối cao
| Tài liệu | Nội dung |
|---|---|
| [KAGE-CONSTITUTION.md](00-governance/KAGE-CONSTITUTION.md) | **Hiến pháp** — 49 Điều, nguyên tắc bất biến. Ưu tiên cao nhất |
| [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) | AI làm việc thế nào; chống trôi kiến trúc |

### 01 · Architecture
| Tài liệu | Nội dung |
|---|---|
| [ARCHITECTURE.md](01-architecture/ARCHITECTURE.md) | ⭐ Quan trọng nhất — C4, layer, luồng, scale |
| [REPOSITORY-STRUCTURE.md](01-architecture/REPOSITORY-STRUCTURE.md) | Module Gradle, dependency rule |
| [TECHNOLOGY-STACK.md](01-architecture/TECHNOLOGY-STACK.md) | Công nghệ nào, version, vì sao, khi nào thay |
| [ADR Index](01-architecture/adr/README.md) | **48 file ADR — 44 Accepted · 3 Proposed · 1 Superseded** (đếm lại 2026-09-12 bằng dòng `Status` của **từng file một**; ⚠️ ADR-034 khai Status bằng **gạch đầu dòng** chứ không phải bảng — script đếm nào cũng phải tính đến nó). **Chỉ mục HẾT ô "chưa viết"**, và nay có **BA** ADR chưa Accepted: **ADR-042** (giảm số gói gửi ra — chủ dự án **hoãn có chủ ý**, mở lại khi N≥1500), **ADR-047** (báo cho người bị kéo vào hầm ngục theo nhóm) và **ADR-048** (kéo boss vào bảng dữ liệu). ADR-023/043/044 đều Accepted 2026-09-05; 043 và 044 đã thi hành + kiểm chứng cùng ngày |

### 02 · Coding Standards
[CODING-STANDARDS.md](02-coding-standards/CODING-STANDARDS.md) · [JAVA-GUIDELINES.md](02-coding-standards/JAVA-GUIDELINES.md) · [CODE-REVIEW.md](02-coding-standards/CODE-REVIEW.md)

### 03 · Engine
[ENGINE.md](03-engine/ENGINE.md) · [ECS.md](03-engine/ECS.md) · [RENDERING.md](03-engine/RENDERING.md) · [PHYSICS.md](03-engine/PHYSICS.md) · [AI.md](03-engine/AI.md)

### 04 · Client
[CLIENT.md](04-client/CLIENT.md) · [UI.md](04-client/UI.md) · [RESOURCE.md](04-client/RESOURCE.md)

### 05 · Server
[SERVER.md](05-server/SERVER.md) · [NETWORK.md](05-server/NETWORK.md) · [DATABASE.md](05-server/DATABASE.md) · [GAMEPLAY.md](05-server/GAMEPLAY.md)

### 06 · Plugin
[PLUGIN.md](06-plugin/PLUGIN.md) · [SCRIPTING.md](06-plugin/SCRIPTING.md)

### 07 · Infrastructure
[DOCKER.md](07-infrastructure/DOCKER.md) · [CI-CD.md](07-infrastructure/CI-CD.md) · [OBSERVABILITY.md](07-infrastructure/OBSERVABILITY.md) · [KUBERNETES.md](07-infrastructure/KUBERNETES.md) 🚧

### 08 · Security
[SECURITY.md](08-security/SECURITY.md) · [OWASP.md](08-security/OWASP.md)

### 09 · Performance
[PERFORMANCE.md](09-performance/PERFORMANCE.md) · [JVM.md](09-performance/JVM.md) · [BENCHMARK.md](09-performance/BENCHMARK.md)

### 10 · Quality
[TESTING.md](10-quality/TESTING.md) · [STATIC-ANALYSIS.md](10-quality/STATIC-ANALYSIS.md)

### 11 · Process
[GIT-WORKFLOW.md](11-process/GIT-WORKFLOW.md) · [DOCUMENTATION.md](11-process/DOCUMENTATION.md) · [ROADMAP.md](11-process/ROADMAP.md)

### 12 · DevTools 🚧
[EDITOR.md](12-devtools/EDITOR.md) · [ADMIN.md](12-devtools/ADMIN.md) · [LAUNCHER.md](12-devtools/LAUNCHER.md) — *stub có chủ đích*

### 13 · Game Design
[GAME-DESIGN-DOCUMENT.md](13-game-design/GAME-DESIGN-DOCUMENT.md) — cơ chế (theme tu tiên từ [ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)) · [STORY.md](13-game-design/STORY.md) — cốt truyện & lore (SSOT tường thuật, **v1.0.0 duyệt nguyên trạng 2026-09-05**)

### 14 · Art
[ART-PIPELINE.md](14-art/ART-PIPELINE.md) — Blender → **model glTF cho nhân vật (2.5D, [ADR-031](01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md))** + pre-render sprite cho VFX/icon, art direction, 3 mức cấu hình đồ họa client · [BLENDER-SESSION-GUIDE.md](14-art/BLENDER-SESSION-GUIDE.md) — lệnh từng bước, checklist nghiệm thu, bảng lỗi · [ART-QUALITY-STANDARD.md](14-art/ART-QUALITY-STANDARD.md) — thước đo ĐẠT/KHÔNG ĐẠT (NSO là benchmark ĐỘ HOÀN THIỆN; đề tài là tu tiên) · [ART-AUDIT-TU-TIEN.md](14-art/ART-AUDIT-TU-TIEN.md) — rà asset cho Đợt 3: giữ gì / sửa gì / làm lại gì. **Bảng quyết định §5 đã tick đủ 7 dòng và đã thi hành xong 2026-09-03** (xem [ROADMAP mục 51](11-process/ROADMAP.md))

### Xuyên suốt
[GLOSSARY.md](GLOSSARY.md) — thuật ngữ Việt–Anh

### Lớp tối ưu cho AI gen code
| Tài liệu | Vai trò |
|---|---|
| [AI-CODEGEN-GUIDE.md](AI-CODEGEN-GUIDE.md) | Mô hình 3 lớp context, bản đồ task→doc, kỷ luật token |
| [templates/](templates/) | Skeleton `.java` copy-paste: feature-module, ecs, protocol |
| `shared/` · `engine/` · `client/` · `server/` **CLAUDE.md** | Thẻ luật vùng — Claude Code auto-load theo thư mục đang sửa (đặt ở thư mục module thật, kích hoạt từ Phase 0) |

🚧 = **stub có chủ đích** — chưa viết chi tiết vì phụ thuộc quyết định chưa có / Phase xa. Không phải thiếu sót ([DOCUMENTATION.md §3](11-process/DOCUMENTATION.md)).

---

## Nguyên tắc toàn cục

- **Hiến pháp có ưu tiên cao nhất.** Tài liệu khác mâu thuẫn → Hiến pháp thắng ([Điều 2](00-governance/KAGE-CONSTITUTION.md)).
- **Không lặp lại Hiến pháp** — tài liệu chuyên môn trích dẫn link, không copy.
- **Đổi kiến trúc bắt buộc ADR trước** ([ADR Index](01-architecture/adr/README.md)).
- **PR đổi kiến trúc không merge nếu chưa cập nhật tài liệu** (Điều 40).
- Mỗi tài liệu chuyên môn kết thúc bằng 4 mục: Technical Decisions · ADR cần tạo · Tài liệu liên quan · Việc cần làm tiếp theo.

---

## ⚠️ Quyết định đang treo — chốt trước khi code

Đây là danh sách tổng hợp toàn bộ thứ **chưa quyết** (chủ dự án quyết, không phải AI).

## 🔴 ĐANG CHỜ CHỦ DỰ ÁN — cập nhật 2026-09-12

| # | Việc | Đang chặn gì | Đọc ở đâu |
|---|---|---|---|
| ~~11~~ | ✅ **[ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) Accepted 2026-09-01 — game ĐỔI BỐI CẢNH sang TU TIÊN** (Phàm Nhân Tu Tiên-style): re-theme tường thuật + tên hiển thị, KHÔNG đổi cơ chế/protocol/DB/tên code. Chủ dự án duyệt trọn: D1-D9, thang **6 cảnh giới × 9 tầng** (Luyện Khí→Trúc Cơ→Tử Phủ→Kim Đan→Nguyên Anh→Hoá Thần), bố cục [STORY.md](13-game-design/STORY.md) §1–§8, và 2 tên riêng "Huyền Thương"/"Thanh Hư Tông". **Đợt 1 (docs) + Đợt 2 (chuỗi hiển thị, hội thoại, quest, cảnh giới) đều XONG 2026-09-01** — xem [ADR-038 D10](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md). **Đợt 3 (art) XONG 2026-09-03** — cả 7 dòng bảng §5 của [ART-AUDIT-TU-TIEN](14-art/ART-AUDIT-TU-TIEN.md) đã quyết và đã thi hành. ✅ **Nội dung STORY.md được duyệt NGUYÊN TRẠNG 2026-09-05** (chân tướng Đại Kiếp, twist Tô Vân Nhi, lời giải rebirth — không sửa mục nào) ⇒ tài liệu lên **v1.0.0**, hết treo | ✅ **Đã PLAYTEST thật 2026-09-01** (D11): cảnh giới, hội thoại Chu Hoành, nhận nhiệm vụ, "Nhận được Phi Đao x1.", Tàng Bảo Các, túi đồ — tất cả đã nhìn thấy chạy; **1099/1099 test xanh** gồm 197 test Postgres. D8 cũng xong phần thật sự lệch. ✅ **Đợt 3 xong**: băng trán ninja đã xoá (hoá ra **chỉ 1/15 nhân vật** từng có — `char_hoa-b1`), 2 icon đã sửa (mã vật phẩm giữ nguyên), `char_town-elder` dựng lại thành Chu Hoành (43 part, LOD 7.622/7.622/4.998), giáp `char_tho-b3` chủ dự án quyết GIỮ, ngân sách tam giác 57/57. **Còn treo đúng một thứ ⇒ dòng 12 dưới đây** | [ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) · [GDD v0.3.0](13-game-design/GAME-DESIGN-DOCUMENT.md) · [STORY.md](13-game-design/STORY.md) · [ROADMAP mục 50, 51](11-process/ROADMAP.md) |
| ~~1~~ | ✅ **ADR-032 Accepted 2026-08-06 và đã thi hành cùng ngày** — `gdx-gltf` 2.3.0 nằm trong `third-party/m2/`, đã vào `libs.versions.toml` và `client-core`, resolve + compile được với `--offline` (tức lấy thật từ repo, không qua mạng). `WorldScreen` **đã nối** sang nhánh 3D (`df5ace2`) và `RENDERING.md` **đã viết lại** cho hai nhánh render (2026-08-07). Còn lại: chạy pipeline art sinh `.glb` để **nhìn thấy** nó chạy → đo frame time → viết GLSL cel | Không còn chặn | [ADR-032](01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md) · [third-party/m2](../third-party/m2/README.md) |
| ~~2~~ | ✅ **V20 duyệt + thi hành 2026-08-07, bước kiểm mồ côi thi hành 2026-08-08 — ĐÓNG HOÀN TOÀN** — `V20__extend_art_asset_for_3d.sql` thêm `asset_kind`/`model_name`/`tri_counts` + `CHECK art_asset_kind_branch`; `ArtRegistryUpserter` upsert được nhánh `MODEL` (verify thật trên Postgres 17: 15 dòng / 45 file `.glb`, chạy 2 lần bất biến). `OrphanAssetChecker` soi **cả hai** thư mục `assets/exported/atlases/` và `assets/exported/models/`, fail build nếu đĩa có file mồ côi; dòng DB không còn file trên đĩa (stale) chỉ cảnh báo — verify thật qua Postgres 17 (Testcontainers) | Không còn chặn | [ART-PIPELINE §8.6](14-art/ART-PIPELINE.md) |
| ~~3~~ | ✅ **ĐÃ TRẢ LỜI 2026-09-05: DESKTOP-FIRST** — [ADR-023](01-architecture/adr/ADR-023-Desktop-First-Input.md) Accepted. Bàn phím + chuột là sơ đồ ĐIỀU KHIỂN CHÍNH; mobile là Post-MVP và sẽ cần ADR RIÊNG. Đây là **hợp thức hoá thực tế** (ADR-033/034 và toàn bộ HUD đã giả định bàn phím + chuột từ lâu) | Không còn chặn — gỡ luôn ngân sách tam giác của ADR-031, CLIENT.md §5/§12, và ADR-024/028 | [ADR-023](01-architecture/adr/ADR-023-Desktop-First-Input.md) |
| 4 | Font **Be Vietnam Pro** (OFL 1.1) — chỉ còn xác nhận hình thức, license đã kiểm tại nguồn + test glyph PASS | Không chặn gì | [RESOURCE.md §7](04-client/RESOURCE.md) |
| 5 | i18n: có tiếng Anh ở MVP không · Mô hình kinh doanh | Chưa chặn phase nào | [GDD](13-game-design/GAME-DESIGN-DOCUMENT.md) |
| 6 | **GM/admin tool**: in-game command hay web app? mấy cấp quyền? tách service hay nhúng? | Chưa chặn — Closed Beta người quen vận hành bằng SQL tay được. Trả lời thì mới viết được [ADMIN.md](12-devtools/ADMIN.md) | [ADMIN.md](12-devtools/ADMIN.md) |
| ~~7~~ | ✅ **ADR-036 Accepted 2026-08-30 và đã thi hành cùng ngày** — chủ dự án duyệt trọn D1-D5 và chấp nhận `maxHp = 100 + bonus_hp`. Migration **V22** thêm `bonus_def`/`bonus_res`/`bonus_hp`/`required_level` + cân bằng lại 16 món; `EnhancementTable.enhancedBonus` khiến cường hoá **cộng chỉ số thật** (×1,10 mỗi cấp, áp riêng từng món); `EquipItemUseCase` kiểm cấp (`error.equip_level_too_low`); 4 field mới `7..10` của `ItemCatalogEntry` (additive, `protocolVersion` KHÔNG tăng); client hiện "Chỉ số gốc"/"Cần Cấp N" và lấy máu tối đa **của server**. Vòng lặp cốt lõi đã KHÉP KÍN | Không còn chặn | [ADR-036](01-architecture/adr/ADR-036-Equipment-Stats-And-Enhancement-Effect.md) · [GAMEPLAY §7.0/§7.1](05-server/GAMEPLAY.md) |
| ~~9~~ | ✅ **ADR-037 Accepted 2026-08-30 và đã thi hành cùng ngày** — chủ dự án chọn **module `feature-monster`** và **duyệt field `model_key`**. Migration V23 (`monster_template` + `monster_spawn`, cột quan hệ **thay JSON** ⇒ đã sửa DATABASE.md §2.2 theo), `MonsterExperience` bị xoá, 3 loài quái (thêm `wandering_bandit` atk 22 và `stone_ogre` atk 40) để DEF/RES của V22 lần đầu **đo được**. Tham số AI cố ý hoãn (D7), đã ghi chú vào AI.md §10 | Không còn chặn | [ADR-037](01-architecture/adr/ADR-037-Monster-Template-And-Spawn-Tables.md) · [ROADMAP mục 43](11-process/ROADMAP.md) |
| ~~8~~ | ✅ **ĐÃ ĐÓNG 2026-09-06 — ADR-045 Accepted + thi hành cùng ngày** (phương án 3, thêm event riêng) — [ADR-045](01-architecture/adr/ADR-045-Appearance-Changed-Event.md) `CharacterAppearanceChangedEvent` opcode **307**, chỉ THÊM nên `protocolVersion` KHÔNG tăng. Đường đi: gateway → hàng đợi world (AoI chỉ đọc được trên tick thread) → `observersOf` → `ConnectedCharacterRegistry` tra ngoại hình → client `replaceCharacterModel` **thay model tại chỗ**, giữ hướng mặt + trạng thái chết, KHÔNG phá thế bất biến "spawn = tạo mới". Chính người đổi KHÔNG nhận event (đường của họ đã có: opcode 207 → `LocalCharacterAppearance`). **Đã thi hành 2026-09-06**, 5 test mới gồm một test HAI SOCKET thật (A nhìn, B đổi hệ phái, A nhận đúng opcode 307) | ✅ **Đã kiểm chứng sống 2026-09-06**: hai client song song, người đứng nhìn thấy model đổi TẠI CHỖ khi người kia chọn Thuỷ | [ADR-045](01-architecture/adr/ADR-045-Appearance-Changed-Event.md) · [ROADMAP mục 29](11-process/ROADMAP.md) |
| ~~10~~ | ✅ **ĐÃ TRẢ LỜI 2026-09-06: sửa tài liệu theo thực tế**, KHÔNG dựng module rỗng — §1 nay liệt kê đúng cây thật (thêm `feature-party`/`feature-trade`/`feature-pvp`/`feature-monster`, `quality/architecture-tests`, `third-party/m2/`, `tools/blender/`), 4 module dự kiến chuyển xuống **§1.1 "chưa tồn tại"** kèm điều kiện sinh ra. Lý do không dựng module rỗng: 5 thư mục code chết làm mọi lần build đắt thêm, và `client-android`/`client-ios` đi ngược ADR-023 desktop-first | ✅ **Ô Exit Criteria cuối cùng của Phase 0–3 đã tick** | [REPOSITORY-STRUCTURE §1](01-architecture/REPOSITORY-STRUCTURE.md) · [ROADMAP Phase 0](11-process/ROADMAP.md) |
| **12** | 🆕 **Khoá Hyper3D (Rodin) TRẢ PHÍ** — việc DUY NHẤT của Đợt 3 còn treo. Chủ dự án **đã đồng ý** tiêu hạn mức Rodin để dựng lại `char_town-elder`, nhưng khoá dùng thử `vibecoding` trả `API_INSUFFICIENT_FUNDS` — API từ chối **trước** khi sinh nên **không tốn credit nào**. Ông lão vì vậy đi đường `kage_mesh_kit` (offline, tất định) và **đã xong, đã nhìn thấy trong bộ dựng thật**. Muốn bản Rodin (chất lượng cao hơn) thì cần chủ dự án cấp khoá trả phí — **chỉ chủ dự án làm được** | Không chặn gì: bản `kage_mesh_kit` đang dùng được. Chỉ chặn nếu muốn nâng chất lượng ông lão bằng Rodin | [ART-AUDIT-TU-TIEN §5](14-art/ART-AUDIT-TU-TIEN.md) · [ROADMAP mục 51](11-process/ROADMAP.md) |
| ~~13~~ | ✅ **[ADR-043](01-architecture/adr/ADR-043-Push-Max-Hp-On-Equipment-Change.md) Accepted 2026-09-05, thi hành + kiểm chứng sống cùng ngày** — chủ dự án chọn cách (a). Gỡ áo giữa world: **118/118 → 110/110 ngay**, không cần chết. Kèm một lỗi SERVER bắt được trên đường: **tôi luyện chưa bao giờ gọi `applyEquipmentBonus`**, nên cường hoá món ĐANG MẶC không đổi chỉ số thật cho tới lần đăng nhập sau — đã sửa | Không còn chặn. ⚠️ Giới hạn có ý thức của cách (a): TỬ SỐ trễ một nhịp (`110 / 118`) tới gói mang HP tiếp theo; mẫu số — thứ sai trước đó — nay đúng ngay | [ADR-043](01-architecture/adr/ADR-043-Push-Max-Hp-On-Equipment-Change.md) |
| ~~14~~ | ✅ **[ADR-044](01-architecture/adr/ADR-044-Quest-Prerequisite-Enforcement.md) Accepted 2026-09-05, thi hành cùng ngày** — chủ dự án chốt mốc **`CLAIMED`** và **không** giấu lựa chọn hội thoại. Cột `prerequisite_quest_id` (có từ V11, chưa ai đọc) nay được thi hành thật: chưa lĩnh thưởng quest mộc nhân thì không nhận được quest trực đêm | Không còn chặn | [ADR-044](01-architecture/adr/ADR-044-Quest-Prerequisite-Enforcement.md) |
| ~~15~~ | ✅ **ĐÃ GIẢI QUYẾT 2026-09-05, và tiền đề của tôi thì SAI** — hỏi lại `CombatConstants` thay vì hỏi màn hình: trận tay đôi vốn đã cân (người chơi 3 nhát, tán tu 7 nhát, chung nhịp 1 đòn/giây), nên **hạ chỉ số quái là chữa nhầm bệnh**. Nguyên nhân thật nằm ở **hình học**: hai điểm spawn cách nhau đúng bằng `aggro_radius`, kéo hai con cùng lúc ⇒ sát thương gấp đôi. Chủ dự án chọn **giãn spawn**: **V29** xếp lại 21 / 34 / 47 theo luật `|A-B| > aggroA + aggroB`, kèm test duyệt MỌI cặp spawn có aggro | ✅ **Đã kiểm chứng TRONG GAME 2026-09-06** bằng client headless `gameProbe`: ở x=24 có hai con tán tu trong tầm nhìn nhưng **chỉ một con bị kéo aggro** (9/9 đòn đều từ nó), con kia đi tuần, 0 sát thương | [ROADMAP mục 56, 57, 58, 61](11-process/ROADMAP.md) |
| ~~16~~ | ✅ **ĐÃ TRẢ LỜI + THI HÀNH 2026-09-06: dời thạch vệ sang PHÍA TÂY** (V32, `-20`/`-33`) — khớp luôn lời thoại của Chu Hoành ("góc phía TÂY Tàng Thư Đường") mà V30 đặt ngược sang đông. Đo lại bằng `gameProbe`: đường đi nay **0 sát thương** (118/118 lúc chạm mặt, so với 53/118 đường cũ) và trận đấu thắng đúng như bảng V30 (4 nhát × 32, còn 38/118). ⚠️ Vẫn là trận **sát nút**: người chơi trúng 91%, hụt hai nhát là thua — có một lượt đo thua đúng vì vậy | ✅ Cao trào arc 1 nay qua được bằng đường chơi thường | [ROADMAP mục 62, 64](11-process/ROADMAP.md) |
| ~~17~~ | ✅ **ĐÃ TRẢ LỜI + THI HÀNH 2026-09-07: chủ dự án chọn CẢ BA hướng (a)+(b)+(c)** — [ADR-046](01-architecture/adr/ADR-046-Health-Recovery.md) Accepted nguyên trạng cùng ngày. ⚠️ **Và đánh giá cũ của tôi ở dòng này là SAI**: "(a) rẻ nhất, KHÔNG đổi protocol" không đúng — client KHÔNG có đường nào nhận HP ngoài `AttackResultEvent`/`SkillResultEvent` (javadoc `CombatHudController` nói thẳng), nên máu hồi mà vạch máu đứng im tới đòn đánh kế tiếp. Cả ba hướng đều cần protocol ⇒ gộp vào MỘT ADR. Đã thi hành: opcode **408** `HealthChangedEvent` (một đường ống cho cả ba nguồn), `HealthRegenSystem` (ngoài chiến đấu 8s → 3% máu tối đa/giây), bảng `safe_zone` + module mới **`feature-world`** (chủ dự án chọn) với `TriggerZoneSystem` lần đầu có consumer thật, quái lành lại khi về tới điểm spawn, và opcode **512/513** + đan dược `minor_healing_pill` (V36, hồi 50 máu / 80 vàng) | Không còn chặn gì | [ADR-046](01-architecture/adr/ADR-046-Health-Recovery.md) · [ROADMAP](11-process/ROADMAP.md) |
| **18** | 🆕 **ÁO CHOÀNG của ba bộ B3 (Hoả/Thuỷ/Lôi) ăn mất khuôn mặt.** Câu hỏi để ngỏ từ 2026-09-06 nay đã đo, và câu trả lời NGƯỢC: cloak **hiện rất rõ**, nhưng nó dính vào khối tóc thành một cái "mũ" tối bè ra hai vai và **vùi mất mặt**. `char_loi-b2` có mặt đọc được; `char_loi-b3` thì không. **Lần thứ BA của lỗi "khối tối che mặt"**. Số đo qua đúng camera game: silhouette Lôi 3.003 → 3.365 pixel (**+362**), **đối chứng cùng model cách 2 khung: −2**. ⚠️ **Và ba hướng sửa tôi đưa lúc đầu đều DỰA TRÊN TIỀN ĐỀ SAI** — tôi tưởng cloak là phụ kiện `_acc_cloak` gỡ ra được bằng cách sửa bảng `CHARACTERS`. Đọc thẳng `.glb` thì mỗi nhân vật là MỘT mesh thân duy nhất sinh bằng **Text→3D** (ADR-031, 2026-08-06); áo choàng nằm BÊN TRONG mesh đó, không có gì để gỡ. **Lựa chọn thật**: (i) chờ khoá Rodin trả phí (dòng #12) rồi dựng lại ba thân B3; (ii) nắn tay `.blend` — file "không sinh lại được", rủi ro cao; (iii) **chấp nhận tới khi có khoá** (tôi khuyên — B3 là trang phục cấp cao, chưa ai chạm tới) | Không chặn gì. Thực chất là một nhánh của dòng **#12** | [ROADMAP mục 68](11-process/ROADMAP.md) · [ASSET-LOG 2026-09-07 (2)](../assets/raw/ASSET-LOG.md) |
| **19** | 🆕 **Quãng Lv10–18 KHÔNG có nội dung nào.** [STORY.md §6](13-game-design/STORY.md) xếp arc 1 ở **Lv1–18** và arc 2 ở **Lv19–36**, nhưng trọn chuỗi arc 1 (4 quest) đưa người chơi tới **ĐÚNG Cấp 10** — và con số đó là ràng buộc CỐ Ý của V33 (Cấp 10 = mốc mở khoá chọn hệ phái). Tức là gần nửa sau của arc 1 hiện trống. Quest 5 (`report_to_outer_sect`, V37) **cố ý KHÔNG lấp** bằng một cục EXP to: một quest đi bộ 4 mét trong vùng an toàn mà thưởng bằng cả arc là nói dối về độ khó. Ba hướng: **(a)** nhiệm vụ ngày (§7 STORY đã có sẵn dòng lore "gánh nước, tuần sơn, hái linh thảo"); **(b)** thêm quest arc 1 giữa khảo hạch và ngoại môn; **(c)** kéo giãn/đổi đường cong EXP để 4 quest arc 1 về đích ở Lv18 thay vì Lv10 — nhưng (c) đụng mốc Cấp 10 của `SelectFactionUseCase` nên phải tách hai chuyện đó ra trước | Không chặn: arc 2 đã mở được, người chơi cày quái vẫn lên cấp. Chặn việc **viết tiếp nội dung arc 2** cho đúng dải cấp mà STORY khai | ✅ **Thu hẹp BA cấp 2026-09-12**: quest 6 `recover_outer_sect_tallies` (V38, 1.000 EXP), quest 7 `missing_disciple_trail` (V39, 1.300 EXP) rồi quest 8 `abandoned_cave` (V40, 1.600 EXP) đưa đường tối thiểu từ Cấp 10 lên **Cấp 13** — cả ba đã playtest trọn vòng, xem [ROADMAP mục 71, 72, 73](11-process/ROADMAP.md). Quãng còn lại (Lv13–18) vẫn chờ chủ dự án chọn hướng. 🆕 **Và arc 2 vừa chạm một trần khác**: bản đồ HẾT CHỖ đặt điểm spawn — đo bằng máy thì cả trục chơi cũ `[-55, 60]` chỉ còn hai khe hợp lệ, nên quest 8 phải nới nền đất 60 → 85 (chủ dự án đã chọn hướng này thay vì dựng hầm ngục thứ hai). 🆕 Kèm một câu hỏi nhỏ cùng họ: tên riêng **"Lý Tam"** (đệ tử mất tích, nay là tên hiển thị của quest 7) **chưa có trong STORY §5** — §8 quy tắc 3 đòi khai, nhưng §1–§6 là SSOT mà AI không tự sửa | [V38](../server/server-database/src/main/resources/db/migration/V38__seed_outer_sect_tally_quest.sql) · [STORY §6](13-game-design/STORY.md) · [recover_outer_sect_tallies.js](../server/server-features/feature-quest/src/main/resources/scripts/quest/recover_outer_sect_tallies.js) |
| **20** | 🆕 **`char_hoa-b1` không có `.blend` nguồn trong repo** — chẩn đoán thật của thứ bàn giao 2026-09-07 mới ghi là "hiện vật cục bộ". Sidecar khai model CÓ TEXTURE (2048/1024/512 px, 19 999 tris), còn `assets/raw/blender/characters/hoa-b1.blend` đang commit là bản **mesh-kit thế hệ cũ**, không texture và không `kage_base_hex` ⇒ **không xuất lại được bằng CẢ HAI đường** (đã thử cả hai, có thông báo lỗi cụ thể). Cùng loại phát hiện với mục áo choàng B3 2026-09-07: các script/`.blend` mesh-kit **không còn mô tả thứ đang chạy**. Ba hướng: **(a)** xuất lại cả 15 bộ khi có khoá Rodin và commit `.blend` nguồn cùng lượt; **(b)** tìm lại và commit các `.blend` Text→3D nếu còn; **(c)** chấp nhận | Không chặn game (`art_asset` là sổ sách pipeline, client đọc thẳng file dưới `models/<tier>/` — grep xác nhận không code nào đọc bảng lúc chạy). CHẶN bước upsert `art_asset` **nhánh MODEL** trên máy này; nhánh 2D vẫn chạy đủ 55 dòng | [ASSET-LOG mục 2026-09-09](../assets/raw/ASSET-LOG.md) |
| **21** | 🆕 **Người bị KÉO vào hầm ngục theo nhóm không nhận một gói nào** — nợ ghi từ bàn giao 2026-08-30, nay **đã tái hiện qua socket thật**: A bấm `EnterDungeonCommand`, server dời cả B sang World hầm ngục, nhưng chỉ A nhận `EnterDungeonResponse`; B nhận spawn của boss mà không có gói nào trong dải 1500–1599. Với B: không biết mình đang ở trong hầm ngục, cả cái làng còn đứng nguyên trên màn hình lẫn với boss, vị trí sai tới lần đi đầu tiên. Chỉ chiều VÀO bị — thoát bằng lệnh, mất kết nối, rời nhóm, boss chết đều đã kiểm và KHÔNG cần gói báo (mỗi đường một test). Không có đường nào sẵn có mà không đổi nghĩa một message đang chạy ⇒ **cần chủ dự án chọn**: **(B, khuyến nghị)** thêm event `DungeonEnteredEvent` opcode 1504 cùng khuôn `DuelStartedEvent`, `protocolVersion` KHÔNG tăng; **(E)** bỏ luật "kéo theo cả nhóm", ai muốn vào tự bấm — không đổi protocol nhưng đổi luật chơi. Kèm phát hiện phụ (sửa được KHÔNG đổi protocol, nên gộp vào lượt thi hành): ngay cả người GỬI cũng có khe thứ tự response-vs-spawn có thể làm họ không thấy boss | Không chặn phase nào; chặn việc chơi hầm ngục theo nhóm cho ra hồn. Test tái hiện đang `@Disabled` chờ ADR | [ADR-047](01-architecture/adr/ADR-047-Dungeon-Entered-Event.md) |
| **22** | 🆕 **[ADR-048](01-architecture/adr/ADR-048-Boss-Into-Data-Tables.md) — kéo BOSS vào bảng dữ liệu, chờ Accept.** **Điều kiện mở lại do chính [ADR-037](01-architecture/adr/ADR-037-Monster-Template-And-Spawn-Tables.md) D7 đặt ra đã xảy ra**: cao trào arc 2 (hộ pháp Huyết Sa cải trang) là **boss THỨ HAI**. Boss hôm nay hardcode ở BỐN chỗ (`BossConstants`, `CombatConstants.BOSS_STATS`, `WorldFacade.BOSS_SPAWN_X`, `spawnBossEntity`), và chỗ thật sự chặn là **`BossAiSystem` nạp ĐÚNG MỘT script lúc khởi tạo** ⇒ boss thứ hai không có đường khai hành vi riêng. Boss cũng là thực thể **duy nhất còn vẽ bằng hộp** vì `model_key` rỗng. Đề xuất: `is_boss` + `ai_script_ref` + 2 cột nhịp AI trên chính `monster_template`, `sample_boss` chuyển nguyên trạng (test Phase 8 làm hàng rào), không đổi protocol, không đụng client | ⏸️ **Chặn quest 9** (cao trào arc 2) — chủ dự án đã chọn HƯỚNG này khi được hỏi 2026-09-12, nhưng chọn hướng ≠ Accept ADR, và AI không tự Accept. ✅ Cắt cảnh đã quyết: **hoãn**, kể bằng cây hội thoại (D8) | [ADR-048](01-architecture/adr/ADR-048-Boss-Into-Data-Tables.md) · [STORY §6](13-game-design/STORY.md) |

> ⚠️ Câu "không còn ADR nào `Proposed`" đã sai **hai lần trong hai ngày** (ADR-036 rồi ADR-037), nên
> đừng tin chữ ở đây: kiểm dòng `Status` của từng file ADR.
> Đếm lại 2026-09-03 (đọc dòng `Status` của **từng file một**, không suy từ bảng index): **36 Accepted ·
> 1 Superseded (ADR-007) · 0 Proposed** trên tổng **37 file**; chỉ **ADR-023** là chưa viết. Số này khớp
> đúng lần đếm 2026-09-01 — không ADR nào đổi trạng thái giữa hai lần.
> *(Câu "chỉ ADR-023 chưa viết" là ghi chép của NGÀY 2026-09-03 và nay đã lỗi thời: ADR-023 được
> viết + Accepted 2026-09-05, desktop-first. Giữ nguyên dòng trên làm hồ sơ.)*
> ✅ **Đếm lại 2026-09-05 (mới nhất)**: **40 Accepted · 1 Proposed (HOÃN có chủ đích) · 1 Superseded**
> trên tổng **42 file ADR**. Chủ dự án Accept ADR-039/040/041 và chốt ADR-023 (desktop-first) cùng
> ngày; ADR-042 hoãn kèm điều kiện mở lại đo được. **Chỉ mục HẾT ô "chưa viết"** — lần đầu kể từ
> Phase 0. Luật vẫn đúng nguyên: nếu tài liệu nào còn viết "chờ chủ dự án
> Accept" cho một ADR thì **kiểm dòng `Status` của chính file ADR đó**, đừng tin chữ ở nơi khác —
> chính cái bẫy đó đã bắt được lỗi sổ sách ADR-032 ngày 2026-08-25.

---

## ✅ Đã chốt — lịch sử

### ✅ Đã chốt (2026-07-18) — không còn chặn Phase 0

| Quyết định | ADR |
|---|---|
| Package root `dev.kage` | ✅ [ADR-012](01-architecture/adr/ADR-012-Package-Root.md) |
| Single-realm + `realm_id` dự phòng | ✅ [ADR-010](01-architecture/adr/ADR-010-Realm-Model.md) |
| Tick rate **60Hz** | ✅ [ADR-018](01-architecture/adr/ADR-018-Tick-Rate.md) |

Chỉ còn **ADR-011** (ArchUnit enforce) — là việc kỹ thuật *trong* Phase 0, không chặn khởi động. **→ Dự án đủ điều kiện mở Phase 0.**

### ✅ Đã chốt (2026-07-19) — không còn chặn Phase 3

| Quyết định | ADR |
|---|---|
| **Redis** (Redis 8 quay lại AGPLv3, OSI-approved từ 5/2025) | ✅ [ADR-005](01-architecture/adr/ADR-005-Why-Redis.md) |
| **Flyway** (SQL tường minh, khớp ADR-004) | ✅ [ADR-013](01-architecture/adr/ADR-013-Flyway-vs-Liquibase.md) |
| **Auth token**: session token tự phát hành + Redis (không JWT) | ✅ [ADR-017](01-architecture/adr/ADR-017-Auth-Token.md) |
| **Idempotency** cho Command có side-effect (`idempotencyKey` + enforce DB) | ✅ [ADR-025](01-architecture/adr/ADR-025-Idempotency.md) |

Toàn bộ 4 ADR chặn Phase 3 đã Accepted — **Phase 3 đủ điều kiện mở**. Công thức damage/derived stats đã chốt (2026-07-19, [GDD §5–6](13-game-design/GAME-DESIGN-DOCUMENT.md#5-nhân-vật--tiến-trình)) — **Phase 4 hết bị chặn về game design**.

### ✅ Đã chốt (2026-07-20) — không còn chặn Phase 4 (script sandbox)

| Quyết định | ADR |
|---|---|
| **GraalJS** làm scripting engine (chạy trên Temurin JDK 25 hiện có, không đổi ADR-001) | ✅ [ADR-016](01-architecture/adr/ADR-016-Scripting-Engine.md) |

### ✅ Đã chốt (2026-07-24) — không còn chặn Phase 8

| Quyết định | ADR |
|---|---|
| **Dungeon instance model**: cùng process, World riêng trong 1 JVM (không phải OS process riêng) | ✅ [ADR-026](01-architecture/adr/ADR-026-Dungeon-Instance-Model.md) |

### ✅ Đã chốt (2026-07-26) — không còn chặn Phase 9

| Quyết định | ADR |
|---|---|
| **Micrometer cho Metrics, OpenTelemetry cho Tracing** | ✅ [ADR-015](01-architecture/adr/ADR-015-Micrometer-vs-OpenTelemetry.md) |
| **Docker Compose + VM đơn, không dùng Kubernetes** | ✅ [ADR-014](01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) |

### ✅ Đã chốt (2026-07-28) — không còn chặn Phase 9

| Quyết định | ADR |
|---|---|
| **ZGC cho server** — soak test 2h14 thật: pause max 0.052ms, không leak | ✅ [ADR-019](01-architecture/adr/ADR-019-GC-Choice.md) |

### ✅ Đã chốt (2026-07-31)

| Quyết định | ADR |
|---|---|
| **TLS**: in-process Netty (server) + JDK `SSLSocket` pin cert (client), self-signed cho Closed Beta | ✅ [ADR-027](01-architecture/adr/ADR-027-TLS-Transport-Security.md) |

### ✅ Đã chốt (2026-08-01)

| Quyết định | ADR |
|---|---|
| **ArchUnit** thực thi luật dependency (đã chạy thật từ Phase 0) | ✅ [ADR-011](01-architecture/adr/ADR-011-Enforce-Dependency-Rules.md) |
| **`FitViewport`** cho camera gameplay — công bằng PvP | ✅ [ADR-020](01-architecture/adr/ADR-020-Viewport-Strategy.md) |
| **Không dùng Box2D** — AABB tự viết cho va chạm địa hình | ✅ [ADR-021](01-architecture/adr/ADR-021-Box2D-vs-AABB.md) |
| **Tự viết FSM** cho AI quái, không dùng gdx-ai | ✅ [ADR-022](01-architecture/adr/ADR-022-Custom-FSM-No-Gdx-Ai.md) |
| **Không xây Plugin System** — xoá `engine-plugin-api` | ✅ [ADR-030](01-architecture/adr/ADR-030-No-Plugin-System.md) *(supersedes ADR-007)* |
| **Asset distribution**: không CDN/patch/OBB cho Closed Beta Desktop | ✅ [ADR-024](01-architecture/adr/ADR-024-Asset-Distribution-Strategy.md) |
| **Graphics tier delivery**: đóng gói 1 tier (MEDIUM), tier khác tải theo yêu cầu | ✅ [ADR-028](01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) |
| **`tools:asset-pipeline`** + `gdx-tools` TexturePacker | ✅ [ADR-029](01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md) |

### ✅ Đã chốt (2026-08-03)

| Quyết định | ADR |
|---|---|
| **Nhân vật/quái/NPC render 3D thời gian thực** qua camera ortho khoá góc (2.5D); VFX/icon/UI vẫn sprite 2D | ✅ [ADR-031](01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Trạng thái

Tất cả tài liệu ở `Status: Draft`. Chuyển `Approved` chỉ khi chủ dự án xác nhận — AI không tự xác nhận thay chủ dự án ([Điều 45](00-governance/KAGE-CONSTITUTION.md)).

**Việc cần làm tiếp theo của cả bộ docs:**
1. ✅ Phase 0–9 đã code xong + đạt Exit Criteria (checklist thật ở [ROADMAP.md](11-process/ROADMAP.md)) — dự án đang ở **Phase 10 Release**.
2. ✅ [ADR-011](01-architecture/adr/ADR-011-Enforce-Dependency-Rules.md) (ArchUnit) Accepted (2026-08-01).
3. ✅ [ADR-038](01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) (pivot tu tiên) Accepted 2026-09-01 — **Đợt 1 + Đợt 2 + playtest + D8 xong 2026-09-01, Đợt 3 (art) xong 2026-09-03** ([ROADMAP mục 50, 51](11-process/ROADMAP.md)).
4. Trả lời các dòng còn mở ở "🔴 ĐANG CHỜ CHỦ DỰ ÁN" (§ trên): dòng **3** (desktop-first hay mobile-first) là câu hỏi chiến lược để lâu nhất và đang chặn ADR-023; dòng **10** đang chặn ô Exit Criteria duy nhất còn trống của Phase 0; dòng **12** (khoá Hyper3D trả phí) không chặn gì, chỉ để nâng chất lượng `char_town-elder`. Dòng 1, 2, 7, 9, 11 đã đóng.
