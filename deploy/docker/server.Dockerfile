# server-gateway image (DOCKER.md §2-4) — gateway + world MVP: cùng 1 image.
# Build từ context = repo root (xem docker-compose.yml `build.context: ../..`), vì Gradle multi-module
# cần toàn bộ cây module để resolve project(":...") — không build được chỉ từ server/server-gateway/.

# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY build-logic/ build-logic/
# Windows checkout không giữ exec bit (git ls-files cho thấy gradlew ở mode 100644) — chmod lại
# tường minh, không dựa vào bit đã commit.
RUN chmod +x gradlew

# Tải dependency trước khi COPY source thật — tận dụng layer cache khi chỉ source đổi (không đổi
# bản build). Gradle configure TOÀN BỘ project graph trước khi chạy bất kỳ task nào (settings.
# gradle.kts include() hết, không configuration-on-demand) — thiếu build.gradle.kts của 1 module là
# lỗi "Configuring project ':x' without an existing directory is not allowed", nên phải COPY riêng
# build.gradle.kts của TỪNG module (không phải toàn bộ source) trước bước này.
# ⚠️ Trước đây bước này chạy `./gradlew dependencies` (task ở ROOT, root không khai báo dependency
# nào — build.gradle.kts root cố ý tối thiểu) kèm `|| true` nuốt lỗi — layer luôn fail (thiếu
# build.gradle.kts của các module con) VÀ dù không fail cũng không tải dependency thật nào của
# server-gateway, làm layer cache này vô nghĩa hoàn toàn, mọi build Docker phải tải lại từ đầu.
# Target thẳng `:server:server-gateway:dependencies` để Gradle resolve THẬT compile/runtime
# classpath của module cần build (kéo theo Netty, protobuf, GraalJS...), verify bằng docker build
# thật (không suy đoán).
COPY shared/shared-common/build.gradle.kts shared/shared-common/
COPY shared/shared-protocol/build.gradle.kts shared/shared-protocol/
COPY engine/engine-core/build.gradle.kts engine/engine-core/
COPY engine/engine-ecs/build.gradle.kts engine/engine-ecs/
COPY engine/engine-physics/build.gradle.kts engine/engine-physics/
COPY engine/engine-scripting/build.gradle.kts engine/engine-scripting/
COPY engine/engine-plugin-api/build.gradle.kts engine/engine-plugin-api/
COPY client/client-core/build.gradle.kts client/client-core/
COPY client/client-desktop/build.gradle.kts client/client-desktop/
COPY server/server-gateway/build.gradle.kts server/server-gateway/
COPY server/server-world/build.gradle.kts server/server-world/
COPY server/server-database/build.gradle.kts server/server-database/
COPY server/server-features/feature-account/build.gradle.kts server/server-features/feature-account/
COPY server/server-features/feature-character/build.gradle.kts server/server-features/feature-character/
COPY server/server-features/feature-inventory/build.gradle.kts server/server-features/feature-inventory/
COPY server/server-features/feature-shop/build.gradle.kts server/server-features/feature-shop/
COPY server/server-features/feature-dialogue/build.gradle.kts server/server-features/feature-dialogue/
COPY server/server-features/feature-quest/build.gradle.kts server/server-features/feature-quest/
COPY server/server-features/feature-friend/build.gradle.kts server/server-features/feature-friend/
COPY server/server-features/feature-mail/build.gradle.kts server/server-features/feature-mail/
COPY server/server-features/feature-chat/build.gradle.kts server/server-features/feature-chat/
COPY server/server-features/feature-guild/build.gradle.kts server/server-features/feature-guild/
COPY server/server-features/feature-party/build.gradle.kts server/server-features/feature-party/
COPY server/server-features/feature-trade/build.gradle.kts server/server-features/feature-trade/
COPY server/server-features/feature-pvp/build.gradle.kts server/server-features/feature-pvp/
COPY quality/architecture-tests/build.gradle.kts quality/architecture-tests/
COPY tools/load-test/build.gradle.kts tools/load-test/
RUN ./gradlew :server:server-gateway:dependencies --no-daemon
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :server:server-gateway:installDist --no-daemon

# ---- Stage 2: runtime ----
FROM eclipse-temurin:25-jre AS runtime
RUN useradd -r -u 1001 kage
WORKDIR /app
COPY --from=builder --chown=kage:kage /build/server/server-gateway/build/install/server-gateway/ ./
USER kage
EXPOSE 7777 9404
# JVM.md §3 (production flag set, verified via ADR-019's real 2h14 soak test) — ZGC + container-aware
# heap + JFR luôn bật (~1% overhead, đáng giá khi cần điều tra sự cố sau này) + GC log file. Gradle's
# generated start script appends $JAVA_OPTS AFTER its own hardcoded `-Dio.netty.leakDetection.level=
# paranoid` (dev default, build.gradle.kts) — JVM giữ giá trị `-D` xuất hiện SAU CÙNG trên command
# line, nên đặt `simple` ở đây ghi đè đúng ý, không cần sửa gì ở build.gradle.kts (giữ nguyên default
# dev cho `./gradlew run`). Không bật `-XX:NativeMemoryTracking` mặc định — JVM.md §3 để dành riêng
# lúc điều tra leak (có overhead), không phải cấu hình thường trực.
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError \
-Dio.netty.leakDetection.level=simple \
-XX:+FlightRecorder -XX:StartFlightRecording=disk=true,maxsize=1g,maxage=24h,dumponexit=true,filename=/tmp/kage.jfr \
-Xlog:gc*:file=/tmp/gc.log:time,uptime:filecount=5,filesize=10m"
# /health/live không chạm DB (MetricsHttpServer, Phase 9 lát cắt 8) — đúng ngữ nghĩa "process còn
# sống", không phải "sẵn sàng nhận traffic" (đó là /health/ready, orchestrator dùng riêng nếu cần).
# Dùng /dev/tcp của bash thay vì cài thêm curl/wget — image runtime không có sẵn 2 tool đó, thêm vào
# chỉ để healthcheck không đáng (Điều 9).
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/9404 && printf "GET /health/live HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && head -n 1 <&3 | grep -q "200 OK"' || exit 1
ENTRYPOINT ["./bin/server-gateway"]
