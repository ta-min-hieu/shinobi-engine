# BUILD.md — Chạy & hoàn tất Phase 0

✅ **Build ĐÃ được verify thành công** (2026-07-18, Gradle 9.1.0 trên JDK 25 = JBR của IntelliJ):
`./gradlew build` và `./gradlew test` đều **BUILD SUCCESSFUL** — compile + Checkstyle + PMD + 8 rule ArchUnit đều xanh. Gradle wrapper (jar + scripts) đã generate sẵn.
Mọi version `⚠️` trong catalog thực tế **đã resolve OK** (AssertJ 3.27.6, Checkstyle 10.21.0, PMD 7.10.0...) — nhưng vẫn để Dependabot theo dõi.

## Yêu cầu để chạy
- **JDK 25** — máy đã có (JBR 25.0.3 trong IntelliJ). `./gradlew` tự dùng JDK từ IntelliJ hoặc `JAVA_HOME`.

## Chạy build
```bash
./gradlew build          # compile + Checkstyle + PMD + test
./gradlew test           # gồm 8 rule ArchUnit (ADR-011)
./gradlew printModules   # liệt kê 13 module
```

⚠️ **Nếu về sau fail** — thường do:
1. Version `⚠️` trong `gradle/libs.versions.toml` bị Dependabot nâng lên bản incompat → xem PR.
2. Plugin ↔ Gradle incompat khi nâng cấp.

## Bước 3 — Wire nốt Spotless + SpotBugs + Error Prone

✅ **XONG (2026-07-30)** — cả 3 tool đã wire vào `kage.quality-conventions.gradle.kts` + `ci.yml`.
Toàn bộ `./gradlew build`+`test` xanh (trừ `pmdMain`/`pmdTest`, xem ghi chú PMD bên dưới). Verify
THẬT bằng build/test thật, không suy luận.

**Phát hiện thật đáng nhớ khi wire lần đầu trên codebase đã lớn** (không phải Phase 0 nữa — nhiều
lỗi Phase 0 tránh được nhờ "0 warning từ đầu" giờ phải dọn 1 lần):
- Spotless + convention-plugin đa module: lỗi classloader thật (`SpotlessTaskService` load bởi 2
  classloader khác nhau ở project có tập plugin khác nhau) — sửa bằng khai báo
  `alias(libs.plugins.spotless) apply false` ở ROOT `build.gradle.kts` (ngoại lệ có chủ đích cho
  nguyên tắc "root tối thiểu", xem comment tại đó).
- Spotless mặc định quét CẢ `build/generated/sources/proto/**` (protoc tự sinh) — phải
  `target("src/main/java/**/*.java", "src/test/java/**/*.java")` tường minh.
- SpotBugs cũng phân tích generated protobuf code (EI/MS/SING trên chính code Google tự sinh) —
  loại trừ bằng regex package trong `config/spotbugs/exclude.xml`.
- STATIC-ANALYSIS.md từng nhắc check tên "AutoBoxing" — check đó KHÔNG TỒN TẠI trong Error Prone
  thật, đã sửa tài liệu.
- 338 file cần `spotlessApply` 1 lần (định dạng lần đầu — "quyết định 1 lần" đúng nghĩa).
- SpotBugs bắt được 1 lỗ hổng thật đáng chú ý: **TLS chưa từng được code** dù NETWORK.md ghi "TLS
  bắt buộc ở Production" là Accepted — suppress tạm ở `exclude.xml` kèm cảnh báo rõ, CHƯA implement
  lúc đó (việc lớn riêng, cần ADR). ✅ **Đã code xong 2026-07-31** — cơ chế đề xuất+chốt ở
  [ADR-027](docs/01-architecture/adr/ADR-027-TLS-Transport-Security.md) (Accepted, chủ dự án xác
  nhận qua chat), implement thật: `TlsConfig`+`GatewayServer` (Netty `SslHandler`, server-gateway),
  `GameConnection`+`SimulatedPlayer` (JDK `SSLSocket` pin cert, client-core/tools:load-test). Build+
  test đầy đủ xanh (bao gồm 3 test TLS thật qua socket: handshake pin-cert, từ chối cert lạ, từ
  chối client bỏ qua TLS). CHƯA làm: chạy thật trên VM (chỉ verify qua test tự động), cert
  Production thật (operator phải tự tạo, xem cảnh báo đầu `docker-compose.prod.yml`).
- 2 bug thật khác được sửa nhân tiện: `NpcIdComponent.npcId` chết hoàn toàn (ghi nhưng không bao
  giờ đọc) → xoá cả field; ~8 dòng `.newBuilder().build();` chết trong test round-trip protobuf
  (kết quả không dùng vào đâu) → xoá.

⚠️ **PMD KHÔNG chạy được trên JDK 25** — `Unsupported class file major version 69` (lỗi nội bộ ASM
của PMD 7.10.0 chưa hỗ trợ bytecode JDK 25 khi resolve type) — không phải lỗi máy, sẽ crash y hệt
trên CI. Luôn `-x pmdMain -x pmdTest` cho tới khi PMD ra bản hỗ trợ JDK 25 (theo dõi qua Dependabot).

## Bước 4 — Git & Dependabot
```bash
git init
git add .
git status          # ⚠️ kiểm tra KHÔNG có .env / secret nào (SECURITY.md §13)
git commit -m "Phase 0: foundation scaffold"
```
- Bật **Dependabot** (`.github/dependabot.yml` đã có) — nó sẽ chốt dần các version `⚠️`.
- ⚠️ **Secret scanning KHÔNG khả dụng** — repo private cần GitHub Advanced Security trả phí, mục đó
  không xuất hiện trong Settings → Security (phát hiện thật 2026-07-31, không phải "chưa bật"). Đã
  bật thay thế **Dependency graph** + **Dependabot alerts** (2 mục miễn phí duy nhất cùng trang) —
  KHÔNG tương đương secret scanning, chỉ cảnh báo CVE dependency. `gitleaks` pre-commit hook (OWASP.md
  §2 đề xuất) vẫn CHƯA làm — repo chưa có `.pre-commit-config.yaml`/git hook nào.
- ⚠️ **Branch protection KHÔNG khả dụng** — thử cả "classic branch protection rule" lẫn "ruleset" mới
  đều báo *"Your rules won't be enforced on this private repository until you move to GitHub Team
  organization account"* (phát hiện thật 2026-07-31). Giới hạn nền tảng GitHub cho private repo cá
  nhân/free — không phải cấu hình sai, không có cách bật thật qua Settings UI ở gói hiện tại. Không
  tạo rule "rỗng tác dụng" (dễ hiểu lầm là đã được bảo vệ). Muốn bật thật cần nâng cấp GitHub Team.

## Bước 5 — Môi trường dev (DB + cache)
```bash
cp deploy/docker/.env.example deploy/docker/.env   # sửa password
docker compose -f deploy/docker/docker-compose.yml up -d
```

## Exit Criteria Phase 0 ([ROADMAP](docs/11-process/ROADMAP.md))
- [ ] `./gradlew build` xanh
- [ ] `./gradlew test` xanh (ArchUnit chạy)
- [ ] `docker compose up` dựng được PostgreSQL + cache
- [ ] Secret scanning hoạt động
- [ ] CI xanh trên push đầu tiên

Khi đủ các mục trên → Phase 0 hoàn thành, mở [Phase 1 — Engine Core](docs/11-process/ROADMAP.md).
