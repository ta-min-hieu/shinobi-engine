# DOCKER.md

**Container hoá**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Liên quan | [TECHNOLOGY-STACK.md §6](../01-architecture/TECHNOLOGY-STACK.md), [CI-CD.md](CI-CD.md) |

---

## 1. Vì sao Docker

| Vấn đề | Docker giải |
|---|---|
| "Chạy được trên máy tôi" | Cùng image = cùng môi trường dev/CI/prod |
| Cài PostgreSQL + Redis thủ công mỗi máy dev | `docker compose up` |
| Test integration cần DB thật | Testcontainers ([TESTING.md](../10-quality/TESTING.md)) |

---

## 2. Cấu trúc

```
deploy/docker/
├── server.Dockerfile          # gateway + world (MVP: cùng 1 image)
├── docker-compose.yml         # môi trường dev đầy đủ
├── docker-compose.ci.yml      # tối giản cho CI
└── .dockerignore
```

⚠️ **Client không container hoá** — client là ứng dụng desktop/mobile, không phải service.

---

## 3. Multi-stage Build

```dockerfile
# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY build-logic/ build-logic/
# Tải dependency trước — tận dụng layer cache khi source đổi
RUN ./gradlew dependencies --no-daemon || true
COPY . .
RUN ./gradlew :server:server-gateway:installDist --no-daemon

# ---- Stage 2: runtime ----
FROM eclipse-temurin:25-jre AS runtime
RUN useradd -r -u 1001 kage
WORKDIR /app
COPY --from=builder /build/server/server-gateway/build/install/ ./
USER kage
EXPOSE 7777
ENTRYPOINT ["./bin/server-gateway"]
```

**Vì sao multi-stage**: image cuối chỉ chứa JRE + app (~200MB), không chứa JDK + Gradle + source (~1.5GB).

---

## 4. Quy tắc bắt buộc

| Quy tắc | Vì sao |
|---|---|
| **Không chạy bằng `root`** | Bảo mật cơ bản ([SECURITY.md](../08-security/SECURITY.md)) |
| **Pin version base image** (`temurin:25-jre`, không `latest`) | `latest` = build hôm nay khác hôm qua |
| `.dockerignore` loại `.git`, `build/`, `assets/raw/` | Image nhỏ, build nhanh |
| **Không copy secret vào image** | Secret qua env var / secret manager, không nằm trong layer |
| Layer ổn định trước, hay đổi sau | Tận dụng cache |
| Healthcheck | Orchestrator biết container sống hay chết |

⚠️ **Secret trong Dockerfile là lỗ hổng vĩnh viễn** — kể cả khi xoá ở layer sau, nó vẫn nằm trong lịch sử layer và ai có image đều đọc được.

---

## 5. Docker Compose (dev)

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: kage
      POSTGRES_USER: kage
      POSTGRES_PASSWORD: ${DB_PASSWORD:?bắt buộc set}
    # ⚠️ UTF-8 collation từ đầu (DATABASE.md §1)
    command: >
      postgres -c 'lc_collate=en_US.UTF-8' -c 'lc_ctype=en_US.UTF-8'
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U kage"]
      interval: 5s
    volumes:
      - pgdata:/var/lib/postgresql/data

  cache:
    # Redis 8.x — AGPLv3, OSI-approved (ADR-005 ✅ Accepted)
    image: redis:8
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  server:
    build:
      context: ../..
      dockerfile: deploy/docker/server.Dockerfile
    depends_on:
      postgres: { condition: service_healthy }
      cache:    { condition: service_healthy }
    environment:
      KAGE_DB_URL: jdbc:postgresql://postgres:5432/kage
      KAGE_CACHE_HOST: cache
      KAGE_ENV: development
    ports:
      - "7777:7777"

volumes:
  pgdata:
```

⚠️ **`depends_on` + `condition: service_healthy` là bắt buộc** — không có nó, server khởi động trước PostgreSQL và crash.

---

## 5.5. Testcontainers trên Windows — 2 lỗi thật đã gặp (Phase 3)

⚠️ Khi chạy `./gradlew test` cho `server-database` trên máy dev Windows + Docker Desktop, hai lỗi
thật đã gặp và cách sửa:

**1. `Could not find a valid Docker environment`** — Testcontainers không tự nhận named pipe của
Docker Desktop hiện đại. Sửa: tạo `~/.testcontainers.properties`:
```properties
docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
docker.host=npipe\:////./pipe/dockerDesktopLinuxEngine
```
⚠️ Đúng **4 dấu `/`** sau `npipe\:` — thiếu sẽ ra lỗi khác (`socket file does not exist`). Lấy đúng
tên pipe bằng `docker context inspect desktop-linux` nếu tên khác trên máy khác.

**2. `BadRequestException (Status 400)` khi gọi `/v1.32/info`** — Testcontainers **1.21.3** dùng
API version cũ (1.32), Docker 29+ yêu cầu tối thiểu 1.40, trả 400 với JSON gần như rỗng (dễ nhầm
là lỗi kết nối). Sửa triệt để: nâng `testcontainers = "2.0.5"` (`gradle/libs.versions.toml`) — bản
2.x tự thương lượng API version. **Đổi tên artifact kèm theo** (2.0 thêm tiền tố `testcontainers-`
cho mọi module con): `org.testcontainers:junit-jupiter` → `testcontainers-junit-jupiter`,
`org.testcontainers:postgresql` → `testcontainers-postgresql`; import `PostgreSQLContainer` cũng
đổi package `org.testcontainers.containers` → `org.testcontainers.postgresql`, và class không còn
generic (`PostgreSQLContainer`, không phải `PostgreSQLContainer<?>`).

---

## 6. Môi trường

| Môi trường | Compose file | Đặc điểm |
|---|---|---|
| **Development** | `docker-compose.yml` | Volume persist; log DEBUG; TLS tắt (tường minh) |
| **Test/CI** | `docker-compose.ci.yml` | Không volume (sạch mỗi lần); nhanh nhất có thể |
| **Production** | `docker-compose.prod.yml` (mới, khi viết) | [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) ✅ — không K8s, chạy trên VM đơn cùng cơ chế Compose |

---

## 7. JVM trong Container

⚠️ Bẫy kinh điển: JVM không biết giới hạn memory của container → cấp phát vượt → **container bị OOM kill**.

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```

| Flag | Vì sao |
|---|---|
| `-XX:MaxRAMPercentage=75` | JVM dùng tối đa 75% RAM container, chừa chỗ cho native (Netty direct buffer, Box2D) |
| `-XX:+ExitOnOutOfMemoryError` | OOM → chết hẳn để orchestrator restart, không sống dở |

⚠️ **Không dùng `-Xmx` cố định trong image** — image chạy ở nhiều cấu hình RAM khác nhau.

**GC flags**: ✅ chốt — [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) (ZGC, Accepted
2026-07-28), flag set đầy đủ ở [JVM.md §3](../09-performance/JVM.md), đã đưa vào
`deploy/docker/server.Dockerfile` (`ENV JAVA_OPTS`, 2026-07-30).

---

## 8. Image size

| Kỹ thuật | Hiệu quả |
|---|---|
| Multi-stage (§3) | ~1.5GB → ~200MB |
| `temurin:25-jre` thay `jdk` | ~150MB tiết kiệm |
| `jlink` custom runtime | Thêm ~50MB tiết kiệm |
| Alpine base | ⚠️ **Cẩn thận** — musl libc có thể gây vấn đề với native lib (Netty, Box2D) |

**Đề xuất**: multi-stage + JRE là đủ. **Không dùng Alpine** cho app có native dependency — rủi ro không đáng với vài chục MB.

---

## 9. Logging trong container

| Quy tắc | Vì sao |
|---|---|
| Log ra **stdout/stderr**, không ra file | Container ephemeral; file log mất khi restart |
| **JSON structured log** ở production | Máy parse được ([OBSERVABILITY.md](OBSERVABILITY.md)) |
| Không log rotate trong app | Docker/orchestrator lo |

---

## 10. Asset trong image?

| Cách | Ưu | Nhược |
|---|---|---|
| Asset trong image server | Đơn giản | Image to; đổi asset = build lại image |
| Asset ở volume/CDN | Image nhỏ; patch asset không rebuild | Phức tạp hơn |

⚠️ **Server có cần asset không?** Server không render, nhưng **cần map data** (collision, spawn point — [RESOURCE.md](../04-client/RESOURCE.md)).

**MVP**: map data vào image. Đơn giản (Điều 9).

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Multi-stage build | Image 200MB thay 1.5GB | Accepted |
| 2 | **Không chạy root** | Bảo mật | Accepted |
| 3 | **Pin version base image**, không `latest` | Build tái lập được | Accepted |
| 4 | **Không secret trong image** | Layer history đọc được vĩnh viễn | Accepted |
| 5 | `MaxRAMPercentage=75` + `ExitOnOutOfMemoryError` | JVM không biết giới hạn container | Accepted |
| 6 | **Không `-Xmx` cố định** | Image chạy nhiều cấu hình RAM | Accepted |
| 7 | **Không dùng Alpine** | musl libc + native lib (Netty/Box2D) = rủi ro | Accepted |
| 8 | Log ra stdout, JSON ở production | Container ephemeral | Accepted |
| 9 | `depends_on` + healthcheck bắt buộc | Server crash nếu DB chưa sẵn sàng | Accepted |
| 10 | **MVP: gateway + world cùng 1 image** | Điều 9 — tách khi cần ([ARCHITECTURE.md §14](../01-architecture/ARCHITECTURE.md)) | Accepted |
| 11 | MVP: map data vào image | Đơn giản | Accepted |
| 12 | Image cache: Redis | [ADR-005](../01-architecture/adr/ADR-005-Why-Redis.md) | ✅ |

## ADR cần tạo

Không còn — ADR-019 (GC flags cho container, §7) đã Accepted 2026-07-28.

## Tài liệu liên quan

- [CI-CD.md](CI-CD.md) — build image trong pipeline
- [KUBERNETES.md](KUBERNETES.md) — ⏳ stub
- [OBSERVABILITY.md](OBSERVABILITY.md) — log/metrics từ container
- [JVM.md](../09-performance/JVM.md) — GC flags
- [SECURITY.md](../08-security/SECURITY.md) — không root, không secret

## Việc cần làm tiếp theo

1. ✅ Phase 0: dựng `docker-compose.yml` — dev environment 1 lệnh (postgres + cache).
2. ⚠️ **Đảm bảo UTF-8 collation ở §5** — dễ quên, rất khó sửa sau ([DATABASE.md §1](../05-server/DATABASE.md)).
3. Phase 9: chốt GC flags §7 sau khi đo (✅ ADR-019 Accepted, xem JVM.md §3).
4. ✅ **Phase 9 lát cắt 10 (2026-07-28)** — thêm 4 service vào `docker-compose.yml`: `prometheus`,
   `alertmanager`, `grafana`, `jaeger` (chi tiết đầy đủ ở [OBSERVABILITY.md](OBSERVABILITY.md) §6-10).
   `server-gateway` vẫn CHƯA có `server.Dockerfile` thật lúc đó (§1 sơ đồ chỉ là kế hoạch) — Prometheus
   scrape server chạy native trên host qua `host.docker.internal:9404`.
5. ✅ **Phase 10 (2026-07-30)** — `deploy/docker/server.Dockerfile` viết xong theo đúng cấu trúc multi-
   stage §3, verify THẬT bằng `docker build` + chạy container nối `postgres`/`cache` container thật
   (không phải suy luận từ đọc code): image build thành công (623MB, không tối ưu size — §8 chưa làm,
   ngoài phạm vi lần này), `docker`-native HEALTHCHECK báo `healthy`, `/health/live`+`/health/ready`+
   `/metrics` (cổng 9404) đều trả về đúng qua container, JVM flags JVM.md §3 (ZGC/MaxRAMPercentage/
   JFR/GC log) xác nhận áp dụng đúng qua log thật (không phải chỉ đọc `ENV` trong Dockerfile).
6. ✅ **Phase 10, cùng ngày (2026-07-30, theo yêu cầu chủ dự án)** — wire service `server` vào
   `docker-compose.yml` dev (dùng `server.Dockerfile`), `prometheus.yml` scrape CẢ `server:9404` LẪN
   `host.docker.internal:9404` (2 target cùng job) để dashboard vẫn thấy server dù chạy container hay
   native. Verify THẬT bằng `docker compose up -d --build` full stack (7 container, không phải chỉ
   đọc YAML): `server` container `healthy`, `/api/v1/targets` Prometheus xác nhận cả 2 target
   `health:"up"`, Grafana `/api/health` OK. **Phát hiện thật khi verify**: nếu chạy container VÀ
   container publish cổng 9404 ra host, 2 target Prometheus đều "up" nhưng là **CÙNG 1 tiến trình**
   (`host.docker.internal:9404` vòng qua cổng host publish, trỏ lại đúng container "server") — dashboard
   nào `sum` theo `instance` sẽ đếm gấp đôi trong tình huống này, ghi rõ trong comment
   `docker-compose.yml`. Cách chạy an toàn: dùng `docker compose up` (full container) HOẶC
   `docker compose up postgres cache prometheus alertmanager grafana jaeger` (loại "server", chạy
   native) — không chạy song song cả 2. Chưa làm (ngoài phạm vi, không phải thiếu sót):
   `docker-compose.prod.yml` (§6), CI chưa build/push image (`ci.yml` không có bước Docker), chưa tối
   ưu size image (`jlink`, §8).
