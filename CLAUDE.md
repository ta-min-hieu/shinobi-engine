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

Phase 9 đóng 2026-07-30 kèm 2 caveat; **đo lại thật 2026-09-05** ([BENCHMARK §5.1](docs/09-performance/BENCHMARK.md),
mục "ĐO LẠI N=2000 THẬT") đã trả lời cả hai, và kết quả **xấu hơn tài liệu cũ ghi**:
- **CPU ở N=2000 = 20,9 lõi** (cấu hình production: ZGC + `leakDetection=simple`), không phải ~7,3
  như bảng cũ — **sai ~3 lần**. Số N=1000 cũ (6,12) thì tái lập tốt. Chi phí mỗi người chơi tăng
  **0,0056 → 0,0105 lõi** khi N gấp đôi ⇒ **tuyệt đối không ngoại suy tuyến tính cho N>2000**.
- **Off-heap KHÔNG phải trục chính**: `Other`/DirectByteBuffer đứng yên ~256→263MB từ N=1000 sang
  N=2000. Trục RAM thật là **Java heap ứ đọng**, và nó **trả lại hết khi tải rút — KHÔNG rò rỉ**.
- **Đo CPU thật theo luồng (`jdk.ThreadCPULoad`, N=1000, cấu hình production): `nioEventLoopGroup`
  ~90% CPU, `world-tick-pool` chỉ ~3%.** Mô phỏng gần như miễn phí. Trong Netty: **TLS ~39%**,
  cấp phát buffer ~28%, ghi socket ~21% — **cả ba là chi phí THEO GÓI**; protobuf chỉ 0,9%.
  Nguyên nhân: **59.760 gói/giây, trung bình 48 byte** ở N=1000, mỗi gói một TLS record + một
  syscall + một buffer. ⇒ **Đích duy nhất đáng làm là GIẢM SỐ GÓI** — **cần ADR** (§4). Tối ưu
  ECS/AoI/AI là vô nghĩa, chúng nằm trong 3%. **Gộp flush đã dựng thử và bị số đo bác bỏ** (~3%,
  trong nhiễu — mỗi kết nối chỉ ~60 gói/giây nên không có gì để gộp). TLS đắt vì SSLEngine của JDK
  gọi `Cipher.init` cho MỖI record (TLS 1.3) — đặc tính JDK, không phải lỗi dự án.
- ⚠️ **`jdk.ExecutionSample` đếm MẪU, KHÔNG đo CPU.** Dùng nó để so sánh giữa các pool đã cho ra
  kết luận NGƯỢC HẲN (và đã lỡ ghi vào tài liệu). Quy trách nhiệm CPU thì dùng `jdk.ThreadCPULoad`.
- ⚠️ **`kage.tick.duration` chỉ nhìn thấy ~4% CPU của server** (đo thẳng `_count`/`_sum`: 4509
  tick/giây × 49µs = 0,22 lõi, trong khi tiến trình ăn 5,7 lõi). ⇒ **"tick p99 thấp" KHÔNG phải
  bằng chứng còn dư sức chứa.**
- ⚠️ **Đo capacity PHẢI xác minh cờ JVM trước khi đo.** `./gradlew run`/`installDist` mặc định là
  cấu hình DEV (`leakDetection=paranoid` + G1) — tốn thêm ~32% CPU và làm p95 xấu đi hàng trăm lần
  so với cấu hình production. Lần chạy đầu của chính phép đo này đã dính đúng bẫy đó.
- ⚠️ **Mọi con số "N lỗi" của load test trước 2026-09-05 đều không tin được** — công cụ đọc frame
  theo thứ tự, không kiểm opcode, nên lỗi bắt tay bị trộn vào lỗi quá tải. Đã vá.

3 quyết định nền tảng chốt từ Phase 0 (không đổi trừ khi có ADR revisit):
- Package root: **`dev.kage`** ([ADR-012](docs/01-architecture/adr/ADR-012-Package-Root.md))
- **Single-realm** + cột `realm_id` dự phòng ([ADR-010](docs/01-architecture/adr/ADR-010-Realm-Model.md))
- Tick rate: **60Hz** — `FIXED_DT = 1/60s` ([ADR-018](docs/01-architecture/adr/ADR-018-Tick-Rate.md))

Lô 18–43 (**2026-10-23 → 2026-11-08**): mỗi lô chủ dự án chọn hết qua MỘT lượt hỏi multiSelect; mỗi tính năng một ADR
"Accepted (chủ dự án chọn …)", check toàn repo, kiểm sống bằng gameProbe, push. Dải ADR: lô 18 ADR-073..075 · lô 19
076..082 · lô 20 083..090 · lô 21 091..102 · lô 22 103..114 · lô 23 115..126 · lô 24 127..131 (arc 3, pháp bảo, trận
pháp, động phủ) · lô 25 132..140 (trang phục theo cảnh giới, PvP Hoang Vực, **arc 4 Kim Đan**, danh hiệu trên đầu) ·
lô 26 141..146 (biển vùng PvP, sát khí/Ma Đầu, …) · lô 27 147..151 (Tàng Bảo Đồ, Dịch Danh Phù, quẻ bói, xúc xắc chia đồ nhóm, vòng quay may mắn) · lô 28 152..158 (Truyền Âm Phù, quà mốc cảnh giới, nhiệm vụ tuần, cửa hàng vinh dự, tự uống đan, bảng sát thương boss trực tiếp, quẻ bói tăng rơi đồ) · lô 29 159..166 (khoá vật phẩm, sắp xếp túi, ghép Linh Ngọc, quét nhanh Bí Cảnh, túi phúc, đan x2 EXP, GM thư bồi thường, huyễn hình trang phục) · lô 30 167..174 (nhận hết thư, tháo Linh Ngọc, sổ vàng, bán nhanh đồ rác, soi trang bị, đổi tên bang, ghi chú bạn bè, nhắc nghỉ ngơi — XONG, mỗi món một commit lên master) · lô 31 175..182 (XONG — bộ trang bị, dùng nhiều vật phẩm, lịch sử phường thị, tự trả lời khi vắng, thông báo bang, nhật ký bang, xếp hạng bang, báo bạn bè đột phá) · lô 32 183..190 (XONG — nhường chức bang chủ, phó bang chủ, chiêu mộ bang, điểm danh bang, nhắn riêng khi offline, khoe vật phẩm vào chat, lịch sử giao dịch, đổi phím tắt) · lô 33 191..198 (XONG — nhường trưởng nhóm, lần cuối online, đổi tên linh thú, danh sách cừu nhân, tóm tắt khi chết, theo dõi phường thị, kho chung tài khoản, kho bang) · lô 34–35 199..214 (XONG — 16 món: so sánh trang bị, đếm ngược reset, cho linh thú ăn, mua lại đồ, nâng cấp phi kiếm, lọc nhặt đồ, xoá nhân vật, tự tìm đường; góp vật phẩm bang, lịch sử bang chiến, xếp hạng thành tựu, danh hiệu cộng chỉ số, gợi ý kết bạn, chặn thư người lạ, đánh dấu mục tiêu nhóm, tự ngắt khi treo máy). Lô 36–37 215..230 (XONG — 16 món: tách chồng, xoá thư hàng loạt, ghim thư, hồ sơ, mời vào bang, ký hiệu mục tiêu nhóm, nút đi tới trong nhiệm vụ, kênh giao dịch; thống kê cá nhân, lịch sự kiện tuần, nhắc boss thế giới, nhật ký chiến đấu, quà tân thủ, gợi ý nhiệm vụ, lọc kênh chat, khoe thành tựu vào bang). Lô 38–39 231..246 (XONG — 16 món: huỷ vật phẩm, phân giải hàng loạt, lịch sử cường hoá, giá gợi ý phường thị, thư toàn bang, không làm phiền, chặn lời mời người lạ, lịch sử đăng nhập; buff bang, kỷ lục cá nhân, xếp hạng giàu có, xem trước thưởng nhiệm vụ, cảnh báo máu thấp, chế độ chụp ảnh, tô sáng khi bị nhắc tên, cảnh báo sắp đầy túi). Lô 40 247..254 (XONG — trả lời nhanh /r, bình chọn bang, lọc chat theo từ khoá, vị trí yêu thích, bảng sát thương nhóm, nhắc việc ngày, lịch sử nhặt đồ, tìm trong túi). Lô 41 255..262 (XONG — xem thông tin quái, lịch hoạt động bang, dự tính cường hoá, xếp hạng bạn bè, hẹn giờ nhắc, đồng hồ giờ server, sổ ghi chép, gọi lại tin đã gõ). Lô 42–43 263..278 (XONG trên nhánh `uat`, chủ dự án tự merge vào master — kiểm tra sẵn sàng, đếm ngược, xúc xắc, báo bang online, /trogiup, giờ chat, /ping, /toado; tóm tắt phiên, EXP/giờ, /alias, giá trị túi, tìm thư, bạn yêu thích, thư nháp, chia vàng nhóm — không migration). Migration mới nhất V170. **Bảng thật luôn là
[adr/README.md](docs/01-architecture/adr/README.md)** — đoạn này chỉ là mục lục, có thể chậm một lô.

Lô 17 (**2026-10-21**, chủ dự án chọn hết 8 mục qua một lượt hỏi): **[ADR-065](docs/01-architecture/adr/ADR-065-Item-Sets.md)** bộ pháp khí (V67) · **[ADR-066](docs/01-architecture/adr/ADR-066-Faction-Change.md)** đổi linh căn + Tẩy Căn Đan (V68) · **[ADR-067](docs/01-architecture/adr/ADR-067-Double-Events.md)** sự kiện x2 `/gm event` (V69, opcode 309) · **[ADR-068](docs/01-architecture/adr/ADR-068-Guild-Level-And-Contribution.md)**/**[ADR-069](docs/01-architecture/adr/ADR-069-Guild-Weekly-Quest.md)** cấp bang + nhiệm vụ bang tuần (V70, 721/722) · **[ADR-070](docs/01-architecture/adr/ADR-070-Flying-Sword-Mount.md)** phi kiếm ô MOUNT (V71, 310) · **[ADR-071](docs/01-architecture/adr/ADR-071-Skill-Ranks.md)** điểm công pháp, phím R (V72, 409–412) · **[ADR-072](docs/01-architecture/adr/ADR-072-Spirit-Pets.md)** linh thú ô PET (V71). Tất cả Accepted + đã thi hành; **đã kiểm sống bằng gameProbe 2026-10-21** (cờ probe mới `--selectFaction`/`--upgradeSkills`/`--donateToGuild`).

Lô 16 (**2026-10-18**, chủ dự án chọn hết 7 mục qua một lượt hỏi): **[ADR-060](docs/01-architecture/adr/ADR-060-Daily-Check-In.md)** điểm danh (module `feature-checkin`, V62) · **[ADR-061](docs/01-architecture/adr/ADR-061-Sect-Storage.md)** kho đồ tông môn (V63, 514–519) · **[ADR-062](docs/01-architecture/adr/ADR-062-Achievements-And-Titles.md)** thành tựu + danh hiệu (`feature-achievement`, V64, dải MỚI 1700–1799) · **[ADR-063](docs/01-architecture/adr/ADR-063-Market.md)** phường thị (`feature-market`, V65, 1006–1014; chỉ món chưa cường hoá) · **[ADR-064](docs/01-architecture/adr/ADR-064-Spirit-Jade-Sockets.md)** Linh Ngọc khảm nạm (V66, 520/521) · dòng 32(b) click nhắm đối thủ · `/gm goto|summon|inspect`. Tất cả Accepted + đã thi hành; **đã kiểm sống 2026-10-21** (cờ probe `--depositItems/--withdrawItems/--socketGems/--setTitle/--listOnMarket/--buyFromMarket`) — tìm ra và vá lỗi khảm ngọc khoá nhân vật ngoài world.

Lô 15 (**2026-10-18**, chủ dự án chọn hết qua một lượt hỏi): **[ADR-054](docs/01-architecture/adr/ADR-054-Abandon-Quest.md)** bỏ nhiệm vụ (612/613) · **[ADR-055](docs/01-architecture/adr/ADR-055-Daily-Quests.md)** nhiệm vụ ngày (V59 `repeat_policy`, `ctx.give_gold`) · **[ADR-056](docs/01-architecture/adr/ADR-056-Character-Stat-Sheet-And-Combat-Power.md)** bảng nhân vật + Lực Chiến (210/211) · **[ADR-057](docs/01-architecture/adr/ADR-057-Leaderboards.md)** Bia Anh Danh (1202/1203) · **[ADR-058](docs/01-architecture/adr/ADR-058-Send-Mail-Idempotency.md)** idempotency thư · **[ADR-059](docs/01-architecture/adr/ADR-059-Party-And-Guild-Changed-Events.md)** event nhóm 1318 / bang 720 — tất cả Accepted + đã thi hành. **[ADR-052](docs/01-architecture/adr/ADR-052-In-Game-GM-Commands.md)** lệnh GM (V61, module MỚI `feature-gm`, mặc định TẮT — `KAGE_GM_ENABLED`; đủ 11 lệnh kể cả `goto`/`summon`) và **[ADR-053](docs/01-architecture/adr/ADR-053-Bundled-Client-Runtime.md)** gói client mang JRE 25 (`packageWindowsClient`) đã Accept + thi hành. Kiểm dòng Status của từng ADR, đừng tin đoạn này nếu nó cũ hơn bảng [adr/README.md](docs/01-architecture/adr/README.md).

Trước đó: **[ADR-051](docs/01-architecture/adr/ADR-051-World-Changed-Event.md)** (✅ 2026-09-24) — event chung **308** "bạn vừa đổi World" (chết trong hầm ngục ⇒ tự về world chính); ADR-047…050 (1504 kéo vào hầm ngục, boss vào `monster_template`, `retaliates`, vùng an toàn dọc trục).

Trước đó: **[ADR-046](docs/01-architecture/adr/ADR-046-Health-Recovery.md)** (✅ Accepted 2026-09-07) — **HỒI MÁU**, đóng việc chặn duy nhất còn lại. Trước nó cả arc 1 không có một nguồn hồi máu nào (playtest 2026-09-06 chết BA lần trong 15 phút vì đúng lý do đó). Chủ dự án chọn CẢ BA hướng: hồi máu ngoài chiến đấu (8 giây → 3% máu tối đa/giây), vùng an toàn tông môn (bảng `safe_zone`, module MỚI **`feature-world`**, `TriggerZoneSystem` lần đầu có consumer thật), và đan dược (`minor_healing_pill`, V36). Thêm **3 opcode** (408 `HealthChangedEvent` — một đường ống cho cả bốn nguồn kể cả quái lành lại khi về điểm spawn; 512/513 dùng vật phẩm), `protocolVersion` KHÔNG tăng. ⚠️ **Mọi số cân bằng đo trước 2026-09-07 đổi nghĩa**: "thắng còn 38/118" nay là "thắng còn 38/118 rồi hồi lại trong 23 giây".

Trước đó: **[ADR-038](docs/01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)** (✅ Accepted 2026-09-01) — **đổi bối cảnh từ ninja học viện sang TU TIÊN** (kiểu Phàm Nhân Tu Tiên): chỉ đổi tường thuật + tên hiển thị, KHÔNG đổi cơ chế/protocol/DB/tên code. Lv1–100 hiển thị thành **6 cảnh giới × 9 tầng** (Luyện Khí→Trúc Cơ→Tử Phủ→Kim Đan→Nguyên Anh→Hoá Thần). GDD v0.3.0 + GLOSSARY v1.1.0 + **[STORY.md](docs/13-game-design/STORY.md)** **v1.0.0 — nội dung duyệt NGUYÊN TRẠNG 2026-09-05** (SSOT cốt truyện, §1–§8; mọi quest/NPC/tên vùng phải map về một arc ở đó, luật viết quest ở §8). ⚠️ Từ nay đổi §1–§6 (tiền đề, bí mật trục chính, thế lực, nhân vật, arc) là **đổi SSOT tường thuật — phải hỏi lại chủ dự án**. **Đợt 2 XONG 2026-09-01** (D10): ~90 chuỗi i18n vi/en, hội thoại arc 1 của trưởng lão Chu Hoành, `CultivationRealm` hiển thị cấp→cảnh giới. Không migration nào — DB chỉ chứa `name_key`, chữ nằm hết ở bundle i18n. **Đã PLAYTEST thật + D8 xong** (D11): 1099/1099 test xanh gồm 197 test Postgres. **Đợt 3 (art) XONG 2026-09-03** — [ART-AUDIT-TU-TIEN.md](docs/14-art/ART-AUDIT-TU-TIEN.md) §0: cả 7 dòng bảng quyết định đã chốt và thi hành. Băng trán ninja đã xoá (chỉ 1/15 nhân vật có), `icon_genin_headband` → mũ vải Ngoại Môn, `icon_steel_katana` → trường kiếm thẳng, `char_town-elder` dựng lại thành trưởng lão Chu Hoành, giáp `char_tho-b3` GIỮ. **0 lượt Rodin** — khoá dùng thử hết quỹ, ông lão đi đường mesh kit; muốn bản Rodin thì cần **khoá Hyper3D trả tiền** (việc duy nhất còn treo của Đợt 3, chỉ chủ dự án làm được). Trước đó: **[ADR-035](docs/01-architecture/adr/ADR-035-Client-State-Sync-Opcodes.md)** (Accepted 2026-08-27) — thêm **7 opcode** lấp 4 khoảng trống đồng bộ trạng thái client: liệt kê túi đồ (507/508), catalog vật phẩm (509/510, phương án **A′** — gửi qua mạng kèm `version` do server phán quyết), số vàng (`GOLD_CHANGED_EVENT` 209), nhật ký nhiệm vụ (610/611). Chỉ THÊM, không đổi message đang chạy, không đổi schema DB, không tăng `protocolVersion`. Mở khoá giao diện túi đồ/trang bị/cửa hàng/nhật ký nhiệm vụ và sửa bug thật "nhận nhiệm vụ → relog → mất thưởng vĩnh viễn".

Quyết định nền rendering: **[ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)** (Accepted 2026-08-03) — nhân vật/quái/NPC render **3D thời gian thực** qua camera orthographic khoá góc (2.5D), VFX/icon/UI vẫn sprite 2D. **Đã chạy thật, đã có ảnh chụp** (2026-08-07) và toàn bộ 9 mục "Việc cần làm tiếp theo" của ADR-031 đã ✅ đóng (2026-08-08) — `gdx-gltf` vendored vào `third-party/m2/` theo [ADR-032](docs/01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md) (**Accepted**), ngân sách tam giác **96/96** file đạt chuẩn §4.4 (45 lúc đóng ADR-031; đo lần gần nhất 2026-10-01 bằng `:tools:asset-pipeline:run` — xem [ASSET-LOG](assets/raw/ASSET-LOG.md)). Client giờ có HUD combat thật đầu tiên (HP/mục tiêu/nút skill) — xem `client-core/ui/`. Lưu ý còn hiệu lực: sandbox GraalJS ([ADR-016](docs/01-architecture/adr/ADR-016-Scripting-Engine.md)) mới ở mức `CONSTRAINED`-equivalent, CHƯA phải `ISOLATED`/`UNTRUSTED` thật. Toàn bộ ADR: [adr/README.md](docs/01-architecture/adr/README.md).

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
