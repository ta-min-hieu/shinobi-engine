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
COPY tools/asset-pipeline/build.gradle.kts tools/asset-pipeline/
COPY tools/load-test/build.gradle.kts tools/load-test/
RUN ./gradlew :server:server-gateway:dependencies --no-daemon
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :server:server-gateway:installDist --no-daemon

# ---- Stage 2: jlink custom runtime (DOCKER.md §8 — "jlink custom runtime: thêm ~50MB tiết kiệm") ----
# jdeps quét THẬT bộ jar server-gateway vừa build (không đoán module list tay) — build.gradle.kts
# thêm/bớt dependency thì layer này tự tính lại module cần, Dockerfile không cần sửa theo.
FROM eclipse-temurin:25-jdk AS jlink-builder
WORKDIR /jlink
COPY --from=builder /build/server/server-gateway/build/install/server-gateway/lib/ ./lib/
# ⚠️ PHÁT HIỆN THẬT (verify bằng jdeps chạy thật, không suy đoán): server-gateway/lib chứa ~25 jar
#   là module JPMS THẬT (module-info.class ở root hoặc META-INF/versions/*, vd. GraalJS/Truffle,
#   Jackson 3.x, logback 1.5.x, HikariCP) xen giữa ~90 jar thường (Netty, protobuf...). Nếu đưa CẢ
#   117 jar vào `jdeps --module-path` cùng lúc: Netty tự split package thật giữa nhiều jar của nó
#   (`io.netty.handler.ssl.ocsp` nằm ở cả netty-handler lẫn netty-codec-http) → lỗi cứng "export
#   package ... to module" (Netty chưa từng thiết kế để chạy như module JPMS thật, chỉ chạy classpath
#   thật ngoài đời). Ngược lại nếu dùng `--class-path` cho TẤT CẢ (kể cả các jar module thật) thì các
#   jar module thật đó không tự suy ra được automatic module (đó là cơ chế riêng của --module-path)
#   → lỗi "Module okio/kotlin.stdlib/org.graalvm.truffle not found, required by ...".
#   → Giải pháp: tách 2 thư mục — modpath/ (jar có module-info thật) dùng --module-path, phần còn
#   lại dùng --class-path (đúng cách server-gateway chạy thật ngoài đời: classpath, không module-path).
RUN mkdir -p /jlink/modpath /jlink/classpath \
    && (cd /jlink/lib \
        && for f in *.jar; do \
            if jar tf "$f" 2>/dev/null | grep -qE '(^module-info\.class$|^META-INF/versions/[0-9]+/module-info\.class$)'; then \
                cp "$f" /jlink/modpath/; \
            else \
                cp "$f" /jlink/classpath/; \
            fi; \
        done)
# Vài jar module thật (vd. okhttp3, từ opentelemetry-exporter-sender-okhttp — optional OTLP
#   sender) `requires` một module TÊN CỤ THỂ (okio) mà bản thân jar đó lại KHÔNG có module-info
#   thật, chỉ có Automatic-Module-Name — automatic module chỉ được suy ra khi nằm trên
#   --module-path, nên "chuyển nhà" đúng đúng 1 jar đó sang modpath/ mới đủ. Vòng lặp dưới tự dò
#   (đọc thông báo lỗi thật của jdeps, không đoán tên jar) — tối đa 15 vòng, build FAIL cứng nếu
#   không hội tụ (không âm thầm bỏ qua, Điều 9/11 CLAUDE.md).
RUN cd /jlink && i=0 && while [ "$i" -lt 15 ]; do \
        i=$((i + 1)); \
        set +e; \
        OUT=$(jdeps --ignore-missing-deps --multi-release 25 --recursive --print-module-deps \
            --module-path modpath --class-path 'classpath/*' modpath/*.jar classpath/*.jar 2>&1); \
        STATUS=$?; \
        set -e; \
        if [ "$STATUS" -eq 0 ]; then \
            echo "$OUT" | tail -1 > /jlink/modules.txt; \
            echo "jdeps hội tụ sau $i vòng, module: $(cat /jlink/modules.txt)"; \
            break; \
        fi; \
        MISSING=$(echo "$OUT" | sed -n 's/^Error: Module \([^ ]*\) not found.*/\1/p' | head -1); \
        if [ -z "$MISSING" ]; then echo "$OUT" >&2; echo "jdeps lỗi không phải do thiếu module — không tự sửa được"; exit 1; fi; \
        FOUND=0; \
        for f in classpath/*.jar; do \
            n=$(jar --describe-module --file="$f" 2>/dev/null | grep -v '^No module descriptor' | grep -v '^$' | head -1 | awk '{print $1}' | cut -d@ -f1); \
            if [ "$n" = "$MISSING" ]; then echo "chuyển $f sang modpath/ (module $n, jdeps báo thiếu)"; mv "$f" modpath/; FOUND=1; break; fi; \
        done; \
        if [ "$FOUND" -eq 0 ]; then echo "không tìm thấy jar cung cấp module '$MISSING' trong classpath/ — dừng, không đoán"; exit 1; fi; \
    done \
    && if [ ! -s /jlink/modules.txt ]; then echo "jdeps không hội tụ sau 15 vòng"; exit 1; fi
# jdk.crypto.ec: ADR-027 TLS dùng EC certificate — provider này đăng ký qua java.security
#   (ServiceLoader), KHÔNG xuất hiện trong tham chiếu bytecode nên jdeps KHÔNG tự phát hiện được;
#   thiếu module này thì handshake TLS lỗi "no cipher suites in common" ở RUNTIME (không phải lúc
#   build) — thêm tay module này là biết trước lỗi jlink kinh điển, không phải suy đoán an toàn.
RUN jlink \
        --add-modules "$(cat /jlink/modules.txt),jdk.crypto.ec" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=zip-9 \
        --output /opt/kage-jre

# ---- Stage 3: runtime ----
# Base OS: Ubuntu (KHÔNG Alpine — musl libc phá native lib Netty, DOCKER.md §8 đã reject) — CÙNG
# distro với eclipse-temurin:25-jdk/-jre (xác nhận thật qua `/etc/os-release` cả 2 image: Ubuntu
# 26.04) để runtime jlink build ở stage trên khớp glibc ABI, không phải suy đoán tương thích.
FROM ubuntu:26.04 AS runtime
COPY --from=jlink-builder /opt/kage-jre /opt/kage-jre
ENV JAVA_HOME=/opt/kage-jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
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
