# server-gateway image (DOCKER.md §2-4) — gateway + world MVP: cùng 1 image.
# Build từ context = repo root (xem docker-compose.yml `build.context: ../..`), vì Gradle multi-module
# cần toàn bộ cây module để resolve project(":...") — không build được chỉ từ server/server-gateway/.

# Base image PIN theo digest (README dòng 61, chủ dự án duyệt 2026-10-17) — tag giữ lại chỉ để người đọc
# biết dòng nào; digest mới là thứ được kéo. Đọc thật từ image đã kéo về máy dev ngày 2026-10-17
# (`docker image inspect`): temurin JAVA_VERSION=jdk-25.0.3+9, dựng 2026-07-16; ubuntu 26.04 LTS
# (resolute), dựng 2026-07-13. Dependabot (ecosystem `docker`, .github/dependabot.yml) mở PR khi digest mới
# ra — ba dòng FROM phải đổi CÙNG nhau (jlink-builder và builder cùng JDK, runtime cùng glibc).

# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk@sha256:201fbb8886b2d273218aa3a192f0afbf7b5ff65ee8cc6ef47f5dce2171f013ea AS builder
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
# ⚠️ 2 dòng dưới THIẾU từ 2026-08-30 (feature-monster, ADR-037) / 2026-09-07 (feature-world,
# ADR-046) tới 2026-10-03 — Gradle 9 từ chối configure project không có thư mục ⇒ bước
# `dependencies` ngay dưới fail cứng, image không build được (job CI docker-build-push đỏ). Thêm
# module vào settings.gradle.kts thì PHẢI thêm 1 dòng COPY ở đây.
COPY server/server-features/feature-monster/build.gradle.kts server/server-features/feature-monster/
COPY server/server-features/feature-world/build.gradle.kts server/server-features/feature-world/
COPY server/server-features/feature-achievement/build.gradle.kts server/server-features/feature-achievement/
COPY server/server-features/feature-checkin/build.gradle.kts server/server-features/feature-checkin/
COPY server/server-features/feature-crafting/build.gradle.kts server/server-features/feature-crafting/
COPY server/server-features/feature-market/build.gradle.kts server/server-features/feature-market/
COPY server/server-features/feature-gm/build.gradle.kts server/server-features/feature-gm/
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
FROM eclipse-temurin:25-jdk@sha256:201fbb8886b2d273218aa3a192f0afbf7b5ff65ee8cc6ef47f5dce2171f013ea AS jlink-builder
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
FROM ubuntu:26.04@sha256:3131b4cc82a783df6c9df078f86e01819a13594b865c2cad47bd1bca2b7063bb AS runtime
# ⚠️ PHÁT HIỆN THẬT (verify bằng `docker run ... locale`, không suy đoán): `eclipse-temurin:25-jre`
# (base cũ) tự cấu hình sẵn `LANG=en_US.UTF-8`, nhưng `ubuntu:26.04` trần mặc định locale `POSIX`
# (không UTF-8) — mọi log/exception tiếng Việt của server (khắp `server-world`/`server-database`...)
# sẽ ra mojibake (`Thi?u bi?n m?i tr??ng b?t bu?c` thay vì `Thiếu biến môi trường bắt buộc`, verify
# thật bằng `docker run` container này thiếu `DB_PASSWORD`). `C.utf8` có sẵn trong glibc của Ubuntu
# (`locale -a` liệt kê, không cần cài `locales` package/`locale-gen`) — đủ để UTF-8 hoạt động đúng,
# không cần dữ liệu vùng miền đầy đủ của `en_US.UTF-8` (server không hiển thị ngày/giờ/số theo locale
# hệ điều hành, dùng `TIMESTAMPTZ` + code tự format).
ENV LANG=C.utf8
ENV LC_ALL=C.utf8
COPY --from=jlink-builder /opt/kage-jre /opt/kage-jre
ENV JAVA_HOME=/opt/kage-jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN useradd -r -u 1001 kage
WORKDIR /app
COPY --from=builder --chown=kage:kage /build/server/server-gateway/build/install/server-gateway/ ./
# JFR + GC log ra thư mục riêng (dòng 63(iii), 2026-10-17) — compose prod gắn volume vào đây để bản ghi
# sống qua lần tạo lại container; trước đó nằm ở /tmp và mất đúng lúc cần đọc nhất (sau sự cố).
# Volume có tên được khởi tạo từ nội dung image, nên chủ sở hữu kage đi theo.
RUN mkdir -p /app/diagnostics && chown kage:kage /app/diagnostics
USER kage
EXPOSE 7777 9404
# JVM.md §3 (production flag set, verified via ADR-019's real 2h14 soak test) — ZGC + container-aware
# heap + JFR luôn bật (~1% overhead, đáng giá khi cần điều tra sự cố sau này) + GC log file. Gradle's
# generated start script appends $JAVA_OPTS AFTER its own hardcoded `-Dio.netty.leakDetection.level=
# paranoid` (dev default, build.gradle.kts) — JVM giữ giá trị `-D` xuất hiện SAU CÙNG trên command
# line, nên đặt `simple` ở đây ghi đè đúng ý, không cần sửa gì ở build.gradle.kts (giữ nguyên default
# dev cho `./gradlew run`). Không bật `-XX:NativeMemoryTracking` mặc định — JVM.md §3 để dành riêng
# lúc điều tra leak (có overhead), không phải cấu hình thường trực.
# -XX:MaxDirectMemorySize (dòng 59, 2026-10-17): trước đó không đặt ⇒ mặc định bằng Xmx, off-heap của Netty
# không có trần riêng. 512m là số TẠM — BENCHMARK §5.1 đo `Other`/DirectByteBuffer ~256→263MB ở
# N=1000→2000, nên 512m gấp ~2 lần mức đo được; chưa đo lại ở cấu hình prod sau khi đặt trần.
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:MaxDirectMemorySize=512m -XX:+ExitOnOutOfMemoryError \
-Dio.netty.leakDetection.level=simple \
-XX:+FlightRecorder -XX:StartFlightRecording=disk=true,maxsize=1g,maxage=24h,dumponexit=true,filename=/app/diagnostics/kage.jfr \
-Xlog:gc*:file=/app/diagnostics/gc.log:time,uptime:filecount=5,filesize=10m"
# /health/ready (dòng 62(i), 2026-10-17 — trước đó /health/live, luôn 200 khi tiến trình còn sống): DB kết
# nối được VÀ cổng game còn mở VÀ mọi event loop Netty còn chạy việc (GatewayServer#isServingGameTraffic).
# Docker thuần KHÔNG tự restart container unhealthy — compose prod có sidecar `autoheal` làm việc đó
# (dòng 62(ii), 2026-10-17). DB sập cũng làm unhealthy: đúng, khi đó không ai chơi được (autoheal sẽ
# restart lặp lại tới khi DB về — vô hại, chỉ ồn log).
# retries=9 (không phải 3): tắt server êm ĐÓNG cổng game ngay bước đầu ⇒ ready đỏ suốt quá trình tắt (đếm
# ngược 30 s + các bước dọn, xấu nhất ~75 s, stop_grace_period 120 s). 3 × 15 s = 45 s là container thành
# unhealthy GIỮA lúc đang tắt, và autoheal (đọc thẳng script của nó: lọc health=unhealthy, chỉ bỏ qua
# trạng thái "restarting") gọi restart — một lệnh `docker stop` có chủ đích bị dựng dậy lại. 9 × 15 s =
# 135 s > 120 s: container luôn thoát (hoặc bị SIGKILL) trước khi kịp unhealthy. Đổi một trong các số đó
# thì giữ retries × interval > stop_grace_period. Đánh đổi: server treo thật bị phát hiện chậm ~2 phút.
# Dùng /dev/tcp của bash thay vì cài thêm curl/wget — image runtime không có sẵn 2 tool đó, thêm vào
# chỉ để healthcheck không đáng (Điều 9).
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=9 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/9404 && printf "GET /health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && head -n 1 <&3 | grep -q "200 OK"' || exit 1
ENTRYPOINT ["./bin/server-gateway"]
