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
Phase 0 cố ý **chưa** bật 3 tool bên thứ ba này (chỉ Checkstyle + PMD + ArchUnit + `-Werror`) để build đầu chắc xanh. Sau khi Bước 2 xanh:
1. Bỏ comment 3 dòng `implementation(...)` trong `build-logic/build.gradle.kts`.
2. Thêm lại cấu hình Spotless/SpotBugs/Error Prone vào `kage.quality-conventions.gradle.kts` (bản mẫu đã có trong lịch sử git / docs STATIC-ANALYSIS.md). Dùng `configure<T>()` nếu accessor DSL báo lỗi trong precompiled script.
3. Bỏ comment các step tương ứng trong `.github/workflows/ci.yml`.
4. Chạy lại `./gradlew build` — sửa version nếu incompat.

## Bước 4 — Git & Dependabot
```bash
git init
git add .
git status          # ⚠️ kiểm tra KHÔNG có .env / secret nào (SECURITY.md §13)
git commit -m "Phase 0: foundation scaffold"
```
- Bật **Dependabot** (`.github/dependabot.yml` đã có) — nó sẽ chốt dần các version `⚠️`.
- Bật **Secret scanning** trong Settings → Security của repo (OWASP.md §2).
- Bật **branch protection** cho `main`/`develop` (GIT-WORKFLOW.md §5).

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
