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
# Tải dependency trước — tận dụng layer cache khi chỉ source đổi, không đổi bản build.
RUN ./gradlew dependencies --no-daemon || true
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
