# ADR-053: Bản cài client desktop mang theo runtime Java 25 riêng (jlink + jpackage app-image)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn Accept trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi "ADR đang Proposed từ lâu — Accept để tôi thi hành?"). Các câu hỏi mở được trả lời theo đúng KHUYẾN NGHỊ ghi trong ADR — xem mục "Việc cần làm tiếp theo". _(Proposed 2026-10-01.)_ |
| Date | 2026-10-01 |
| Deciders | Chủ dự án quyết HƯỚNG (2026-10-01: **cho Closed Beta, client desktop đóng gói KÈM JRE 25 để ghim JDK của người chơi**); AI soạn phần cách làm |
| Related | [ROADMAP.md — v1 Exit Criteria, mục "Bản cài client mang theo asset" + rủi ro LWJGL](../../11-process/ROADMAP.md) · [ADR-001](ADR-001-Why-Java25.md) (Java 25 + Temurin) · [ADR-023](ADR-023-Desktop-First-Input.md) (desktop-first) · [ADR-024](ADR-024-Asset-Distribution-Strategy.md) / [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) (asset đóng trong bản cài, chưa patch) · [ADR-032](ADR-032-JitPack-Dependency-Defence.md) (luật thêm dependency) · [DOCKER.md §8](../../07-infrastructure/DOCKER.md) (jlink của image server — tiền lệ) · [LAUNCHER.md](../../12-devtools/LAUNCHER.md) (stub auto-update) · `client/client-desktop/build.gradle.kts` |

---

## Context

### Bản cài hôm nay chạy bằng `java` của máy người chơi

`./gradlew :client:client-desktop:installDist -PkageBundleAssets` ra `bin/client-desktop(.bat)` +
`lib/` (69 jar, **22 MB** không kể asset; `kage-assets.jar` **22,1 MB** đo 2026-09-30). Script khởi
động do plugin `application` sinh ra tìm `JAVA_HOME` rồi `PATH` — tức **JDK nào người chơi đang có
thì client chạy bằng JDK đó**. Các vá đã có trong `build.gradle.kts` đều chỉ là phòng thủ cho chuyện
đó: classpath ký tự đại diện `%APP_HOME%/lib/*` (trần 8.191 ký tự của `cmd.exe`), và
`--enable-native-access=ALL-UNNAMED` (JDK sau sẽ cấm `System::load` của gdx-jnigen).

### Rủi ro LWJGL phụ thuộc đúng vào phiên bản JDK (ROADMAP, kiểm nguồn 2026-10-01)

- Cảnh báo `[LWJGL] [ThreadLocalUtil] Unsupported JNI version detected` trên JDK 25 là **báo động giả**:
  LWJGL 3.3.3 chưa biết `JNI_VERSION_24` nên rơi vào nhánh `default` = 232 hàm, trùng đúng số hàm thật
  của JDK 25.
- **JDK 28 thêm hàm thứ 233 (`HasIdentity`)** ⇒ mọi bản LWJGL hiện có (kể cả 3.4.3) sẽ chép thiếu bảng
  `JNINativeInterface_`. Người chơi nào cài JDK 28+ làm mặc định là chạy client trên một bảng JNI chép
  thiếu — đúng loại lỗi không có dòng log nào trước khi sập.
- Dự phòng đã biết: `-Dorg.lwjgl.system.JNINativeInterfaceSize=233`. Nhưng con số đúng phụ thuộc JDK
  thật đang chạy — ghim một hằng vào script khởi động thì lại sai khi người chơi dùng JDK khác.

Gốc chung của cả ba vá: **không kiểm soát được JDK**. Chủ dự án đã quyết hướng (2026-10-01): mang theo
runtime. ADR này chọn **cách** mang.

### Đã điều tra thật (2026-10-01, không đoán)

Mọi thử nghiệm làm trong worktree + scratchpad, không giữ thay đổi build nào.

**1. JDK dùng để đóng gói.** Máy dev chỉ có JBR 25.0.3 đi kèm IntelliJ — và
**JBR KHÔNG có `jlink`, `jdeps`, `jpackage`, `jar`** (đã `ls jbr/bin`). Gradle toolchain hiện phân giải
`languageVersion 25` về chính JBR đó, và repo **không** khai `foojay-resolver` nên không tự tải JDK. ⇒
Task đóng gói KHÔNG thể giả định "toolchain đang dùng có jlink"; phải tìm JDK đủ công cụ và hỏng TO
TIẾNG nếu thiếu (D4).

Thử nghiệm dùng **Azul Zulu 25.0.4.1** (Windows x64, có `jmods/` — 70 module) chỉ vì CDN Adoptium tải
được ~11 KB/s lúc đo (bỏ cuộc sau 10 phút ở 8 MB). Đã kiểm thêm: image `eclipse-temurin:25-jdk`
**không có `jmods/`** (JEP 493 — jlink từ runtime image). jlink vẫn chạy được (image server dùng đúng
đường này từ `47293f7`) **nhưng chỉ cho CÙNG nền tảng**: không thể đứng trên Linux dựng runtime Windows
bằng Temurin 25. Nhà cung cấp chính thức của dự án vẫn là Temurin (ADR-001) — xem Q2.

**2. Module cần.** `jdeps --ignore-missing-deps --multi-release 25 --recursive --print-module-deps
--class-path "lib/*" lib/client-desktop.jar` (exit 0) ⇒

```
java.base,java.desktop,java.logging,jdk.unsupported
```

Nhưng **jdeps không thấy hết** — cùng họ bẫy `jdk.crypto.ec` của image server (provider nạp qua
ServiceLoader, không có tham chiếu bytecode). Đo thật bằng một chương trình nhỏ chạy trên runtime đã
link:

| Runtime | TLS 1.3/1.2 + EC | Số locale | `NumberFormat.getInstance(Locale.of("vi"))` của `1234567.5` |
|---|---|---|---|
| 4 module của jdeps | ✅ (31 cipher suite) | **5** | **`1,234,567.5`** ❌ (rơi về kiểu Anh) |
| + `jdk.localedata --include-locales=en,vi` | ✅ | 125 | `1.234.567,5` ✅ |

`KageI18n` dựng `I18NBundle` với `target.locale()` (= `Locale.of("vi")`, `ClientLanguage`) và **không** bật `simpleFormatter` ⇒ đi qua
`MessageFormat` ⇒ mọi số `{0}` trong chuỗi tiếng Việt (vàng, máu, EXP…) đổi dấu phân cách hàng nghìn
nếu thiếu `jdk.localedata`. JDK đầy đủ (JBR hôm nay) có module này, nên đó là một **thay đổi hiển thị
lặng lẽ** mà không test nào hiện có bắt được. ⇒ `jdk.localedata` là bắt buộc (D2).

Phát hiện phụ: ở JDK 25, `jdk.crypto.ec` là module RỖNG (SunEC đã nằm trong `java.base`:
`java --describe-module java.base` có `sun.security.ec`). Dòng `,jdk.crypto.ec` trong
`deploy/docker/server.Dockerfile` vì vậy vô hại nhưng không còn cần — ghi lại, không sửa trong ADR này.

**3. Kích thước (Windows x64, `--strip-debug --no-header-files --no-man-pages --compress=zip-9`).**

| Thứ | Kích thước |
|---|---|
| JDK Zulu 25 nguyên bản (đối chứng) | 381 MB |
| Runtime 4 module jdeps | 49 MB |
| Runtime 4 module + `jdk.localedata` (`--include-locales=en,vi`) | **49 MB** |
| Runtime + `jdk.localedata` ĐỦ locale + `jdk.charsets` + `jdk.crypto.ec` | 60 MB |
| `jpackage --type app-image` (runtime 49 MB + `app/` 22 MB), **chưa có asset** | 71 MB |
| Nén zip (`Compress-Archive`) cùng thư mục đó | **55,7 MB** |
| ⚠️ Ước tính, CHƯA đo: + `kage-assets.jar` 22,1 MB (đã nén sẵn) | ~93 MB giải nén · ~78 MB zip |

Native của nền tảng khác trong `lib/` (LWJGL linux/macos, windows-x86/arm64) chiếm ~7 MB — bỏ được khỏi
gói Windows (Q7), không bắt buộc.

**4. Khởi động thật.** Chép bản cài ra thư mục tạm (không phải gốc repo, không có cây `assets/`), chạy:

| Lệnh | Exit | Kết quả |
|---|---|---|
| `rt-min/bin/java --enable-native-access=ALL-UNNAMED -cp "lib/*" dev.kage.client.desktop.DesktopLauncher`, `timeout 20` | **124** (còn sống tới khi bị giết) | Log 5 dòng, giống hệt đối chứng JBR (cảnh báo `Unsafe` + JNI) |
| Cùng lệnh + `-Dorg.lwjgl.util.Debug=true`, so với JBR | 124 / 124 | 35 dòng mỗi bên; `diff` chỉ khác dòng `JRE:`/`JVM:`. Nạp đủ `lwjgl`, `jemalloc`, `glfw`, `OpenAL`, `lwjgl_opengl`, `opengl32` |
| `jpackage --type app-image … --runtime-image rt-vi` | **0** | `KageClient.exe` + `app/` + `runtime/`, KHÔNG cần WiX |
| `KageClient.exe` chạy từ thư mục khác, `timeout 20` | **124** | Log y như trên |

⚠️ **Phạm vi đã chứng minh**: tiến trình nạp đủ native LWJGL/OpenGL và trụ 20 giây trên runtime đã
link, cả qua `java` lẫn qua `.exe` của jpackage, chạy từ thư mục không phải gốc repo. **CHƯA chứng minh**:
màn đăng nhập vẽ đúng (không ảnh chụp), kết nối TLS tới server thật, vào world, asset trong jar
(worktree không có `assets/exported/`), máy thứ hai, máy KHÔNG cài Java nào. Runtime thử là Zulu, chưa
phải Temurin.

Cảnh báo JNI của LWJGL **vẫn in** trên runtime 25 — đúng như phân tích (báo động giả ở JDK 25). Ghim JDK
không làm nó im; nó làm nó **vô hại có bảo đảm**.

**5. Môi trường xung quanh.**
- **WiX**: máy dev không có (`where candle.exe` / `wix.exe` đều không thấy). `jpackage --type exe|msi`
  cần WiX; `--type app-image` thì không.
- **CI**: `.github/workflows/ci.yml` chỉ có job `ubuntu-latest` (build/test, gitleaks, docker push) —
  không job nào dựng client. Do điểm 1 (Temurin 25 không cross-link được, jpackage không bao giờ
  cross-build) ⇒ gói Windows phải dựng **trên Windows** (máy chủ dự án hoặc runner `windows-latest`).
- **Plugin Gradle** (Gradle Plugin Portal, đọc `maven-metadata.xml` 2026-10-01): `org.beryx.runtime`
  **2.0.1** (cập nhật 2025-11-06), `org.beryx.jlink` **4.1.1** (2026-07-31; dành cho app modular — client
  không có `module-info`). Cả hai là **dependency mới** (CLAUDE.md §4), chưa kiểm tương thích Gradle
  9.6.1 của repo.
- **Log**: client không có `logback.xml` ⇒ logback mặc định ghi ra console. Launcher `.exe` mặc định
  của jpackage là GUI (không console) ⇒ người chơi bấm đúp thì **mọi dòng log biến mất** — lặp lại đúng
  bài học playtest 2026-08-30 (client im lặng). Xem D6.

---

## Decision

**Đề xuất:** gói Closed Beta của client Windows là **một file zip chứa `jpackage --type app-image`**
(`KageClient.exe` + `app/` + `runtime/`), runtime dựng bằng **`jlink` của một JDK 25 đầy đủ**, gọi bằng
**task Gradle `Exec` trực tiếp — KHÔNG thêm plugin**.

**D1. Hình dạng gói: app-image trong zip, không installer.** Không WiX, không quyền admin, không mục
Start Menu/gỡ cài đặt. Người chơi giải nén, bấm `KageClient.exe`. Installer `.exe`/`.msi` để sau
(Revisit Trigger).

**D2. Danh sách module là KHAI BÁO TƯỜNG MINH, jdeps chỉ là cổng kiểm.**
`java.base,java.desktop,java.logging,jdk.unsupported,jdk.localedata` + `--include-locales=en,vi`
(theo `ClientLanguage` hiện có). Task đóng gói chạy jdeps trên `lib/` và **FAIL** nếu jdeps báo một
module KHÔNG có trong danh sách (dependency mới kéo module mới ⇒ build đỏ, không lặng lẽ ra runtime
thiếu). Không làm ngược lại (lấy thẳng output jdeps) vì jdeps không thấy `jdk.localedata` — đã đo.
Thêm ngôn ngữ thứ ba ⇒ sửa `--include-locales`.

**D3. Cờ JVM nhúng trong launcher** (`--java-options`): `--enable-native-access=ALL-UNNAMED` (giữ như
script hôm nay). **KHÔNG** ghim `-Dorg.lwjgl.system.JNINativeInterfaceSize` — runtime đã ghim ở 25 thì
nhánh `default` 232 của LWJGL đúng; cờ đó chỉ cần khi đổi runtime lên JDK 28+ mà LWJGL chưa theo kịp
(Revisit Trigger).

**D4. JDK đóng gói được chỉ định, không đoán.** Task đọc thuộc tính Gradle `kagePackagingJdk` (đường
dẫn JDK 25 đầy đủ); thiếu thuộc tính, hoặc JDK đó không có `bin/jlink(.exe)`/`bin/jpackage(.exe)`, hoặc
`release` không phải `JAVA_VERSION="25…"` ⇒ `GradleException` nói rõ cần gì. Không dùng mặc định
toolchain (đang là JBR, thiếu công cụ — đã kiểm).

**D5. Tách khỏi `build`/`check`.** Task `packageWindowsClient` chỉ chạy khi gọi tên (cùng tinh thần cờ
`-PkageBundleAssets`): CI ubuntu không có JDK Windows/asset, không được đỏ vì nó. `installDist` + script
`.bat` giữ nguyên cho dev và làm đường lui.

**D6. Log không được biến mất.** Chọn một trong hai (Q5): (a) `--win-console` — rẻ, có cửa sổ console
đi kèm cửa sổ game; (b) thêm `logback.xml` có file appender vào `%LOCALAPPDATA%\Kage\logs` — người chơi
gửi file log được, không cửa sổ thừa. Khuyến nghị (b) cho Closed Beta (người chơi thật cần gửi log),
nhưng đó là một thay đổi riêng của `client-desktop`.

**D7. Phiên bản gói = phiên bản client.** `--app-version` lấy từ version Gradle của dự án — ⚠️ hôm nay repo **chưa khai `version`** ở đâu (Gradle trả `unspecified`, mà jpackage đòi dạng số `x.y.z`) ⇒ thi hành phải đặt version trước; tên zip
`kage-client-<version>-windows-x64.zip`. Mỗi bản vá client/asset = tải lại cả zip (đúng ADR-024 A — chưa
patch); runtime chiếm ~49 MB trong đó dù hiếm khi đổi — chấp nhận cho Closed Beta.

**D8. Chỉ Windows x64 cho Closed Beta.** ADR-023 là desktop-first, không chốt hệ điều hành. Mỗi nền tảng
cần dựng trên chính nền tảng đó (jpackage) và một gói riêng; macOS còn thêm notarization. Không làm trước
khi có người chơi thật trên nền tảng đó (Q6).

Phác thảo (chưa code, để ước lượng chi phí):

```kotlin
// client/client-desktop/build.gradle.kts — phác thảo, KHÔNG phải mã cuối
val packagingJdk = providers.gradleProperty("kagePackagingJdk")
val clientModules = "java.base,java.desktop,java.logging,jdk.unsupported,jdk.localedata"

val clientRuntime = tasks.register<Exec>("clientRuntime") {
    // kiểm D4 trong doFirst; xoá build/jlink/runtime trước (jlink từ chối thư mục đã tồn tại)
    executable = "${packagingJdk.get()}/bin/jlink"
    args("--add-modules", clientModules, "--include-locales=en,vi",
         "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-9",
         "--output", layout.buildDirectory.dir("jlink/runtime").get().asFile.path)
}
val clientAppImage = tasks.register<Exec>("clientAppImage") {
    dependsOn(clientRuntime, "installDist")
    executable = "${packagingJdk.get()}/bin/jpackage"
    args("--type", "app-image", "--name", "KageClient", "--app-version", project.version.toString(),
         "--input", "build/install/client-desktop/lib", "--main-jar", "client-desktop.jar",
         "--main-class", "dev.kage.client.desktop.DesktopLauncher",
         "--java-options", "--enable-native-access=ALL-UNNAMED",
         "--runtime-image", "build/jlink/runtime", "--dest", "build/jpackage")
}
tasks.register<Zip>("packageWindowsClient") { dependsOn(clientAppImage) /* + cổng jdeps D2 */ }
```

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. jlink + `jpackage --type app-image` + zip, task `Exec` không plugin** — ADR này | Ghim JDK thật; runtime 49 MB thay vì JDK 381 MB; `.exe` thay `.bat` ⇒ không còn `cmd.exe` (trần 8.191 ký tự hết là chuyện của người chơi); 0 dependency mới; 0 WiX; đã chạy thử được (exit 0 / sống 20 s) | ~60–100 dòng Gradle tự viết; phải tự giữ cổng module (D2); cần JDK 25 đầy đủ trên máy dựng (D4); `.exe` chưa ký ⇒ SmartScreen; gói chỉ dựng được trên Windows | **Khuyến nghị** |
| **B. jlink + giữ `client-desktop.bat`, thư mục `runtime/` cạnh `lib/`** | Rẻ nhất có ghim JDK: chỉ thêm 1 task jlink + sửa `startScripts` cho script ưu tiên `%APP_HOME%\runtime\bin\java`; không jpackage | Vẫn chạy qua `cmd.exe` (cửa sổ console đen, trần dòng lệnh chỉ đang được né bằng mẹo wildcard); người chơi bấm `.bat` trông như phần mềm nửa vời; `JAVA_HOME` của người chơi vẫn có thể lấn nếu sửa script không cẩn thận | Hợp lệ nếu chủ dự án muốn tối thiểu — Q1 |
| **C. `jpackage --type exe`/`msi` (installer thật)** | Trải nghiệm cài đặt chuẩn Windows: Start Menu, gỡ cài đặt, nâng cấp tại chỗ (`--win-upgrade-uuid`) | Cần **WiX** (công cụ ngoài, máy dev không có, phải cài trên máy dựng/CI); installer chưa ký thì SmartScreen gắt hơn; thêm một lớp phải kiểm (đường cài, quyền ghi log/config) | Chưa — để khi bỏ zip (Revisit Trigger). App-image của A là đầu vào của C, không phí công |
| **D. Plugin `org.beryx.runtime` 2.0.1** | Đỡ viết tay jlink/jpackage/zip; có sẵn cấu hình đa nền tảng | **Thư viện mới** (CLAUDE.md §4 — cần duyệt, ADR-032 về rủi ro nguồn); bản mới nhất 2025-11, chưa kiểm với Gradle 9.6.1; vẫn phải tự khai `jdk.localedata` (vấn đề D2 không biến mất); giấu cờ jlink sau DSL của bên thứ ba | Không khuyến nghị — việc tiết kiệm được nhỏ hơn chi phí thêm dependency |
| **E. Giữ nguyên (`java` của người chơi) + ghim `-Dorg.lwjgl.system.JNINativeInterfaceSize=233` + dặn "cài JDK 25"** | 0 công đóng gói; gói nhỏ nhất (~44 MB) | Không ghim được JDK — người chơi không có Java thì không chạy được; số 233 SAI với JDK 25 (232) nếu đặt cố định ⇒ phải đặt có điều kiện trong script; đi ngược quyết định chủ dự án 2026-10-01 | Không — chỉ còn là đường lui (`installDist` vẫn dựng được, D5) |
| **F. Mang nguyên JRE/JDK 25 (không jlink)** | Không bao giờ thiếu module (hết bẫy D2) | JDK 381 MB (đo); JRE đầy đủ ⚠️ chưa đo; tải về nặng gấp nhiều lần chỉ để né một danh sách module 5 dòng đã kiểm được | Không |

---

## Consequences

**Tích cực:**
- JDK của người chơi hết là biến số: rủi ro JNI `HasIdentity` của JDK 28 và việc JDK sau cấm
  `System::load` không còn chạm tới bản phát hành cho tới khi CHÍNH dự án đổi runtime.
- Người chơi không cần cài Java.
- Không còn `cmd.exe` trong đường khởi động (A) ⇒ trần 8.191 ký tự và mẹo wildcard chỉ còn là chuyện
  của `installDist` dev.
- Format số tiếng Việt được KHÓA bằng danh sách module (D2) thay vì phụ thuộc may rủi vào JDK của máy.

**Đánh đổi phải chấp nhận:**
- Gói nặng thêm ~49 MB giải nén (~34 MB nén — suy từ 55,7 MB zip trừ 22 MB jar đã nén; ⚠️ chưa đo
  riêng). Mỗi bản vá tải lại cả gói (ADR-024 A).
- **Vá bảo mật JDK thành việc của dự án**: CPU/PSU của Java 25 ra mỗi quý ⇒ phải dựng lại + phát hành
  lại gói, người chơi không tự nhận vá qua JDK của họ nữa (client mở kết nối TLS ra mạng — ADR-027).
- `.exe` chưa ký số ⇒ Windows SmartScreen hiện "Windows protected your PC" với file tải từ mạng (Mark of
  the Web). Chứng chỉ ký mã tốn tiền hằng năm (Q4). ⚠️ Chưa kiểm hành vi SmartScreen thật trên máy
  khác.
- Máy dựng gói phải là Windows và có JDK 25 đầy đủ (D4) — hôm nay là máy chủ dự án sau khi cài một JDK
  25 (JBR không đủ).

**Module bị ảnh hưởng:** *(tra [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md))*
- `client-desktop` — chỉ `build.gradle.kts` (task mới) và, nếu chọn D6(b), `src/main/resources/logback.xml`.
- **Không đổi protocol, DB, code gameplay, `client-core`.** Không module mới, không dependency mới.
- CI (`ci.yml`) — chỉ khi Q3 chọn runner Windows.

**Chi phí ước tính:** nhỏ — một lượt: ~60–100 dòng Gradle + cổng jdeps + một lượt kiểm sống trên máy
KHÔNG cài Java. Lớn hơn nếu Q3 chọn CI Windows (job mới, cache JDK) hoặc Q4 có chứng chỉ ký số
(`signtool` vào pipeline).

---

## Revisit Trigger

- Đổi runtime lên JDK ≥ 28 (LTS kế tiếp hoặc vì vá) mà LWJGL chưa có bản biết `JNI_VERSION` mới ⇒ bật
  `-Dorg.lwjgl.system.JNINativeInterfaceSize` trong D3 với đúng số hàm của JDK đó.
- Có người chơi Closed Beta trên macOS/Linux ⇒ D8, một gói cho từng nền tảng.
- Bỏ zip, cần cài đặt/gỡ/nâng cấp chuẩn ⇒ phương án C (WiX).
- Auto-update launcher ([LAUNCHER.md](../../12-devtools/LAUNCHER.md)) được thiết kế ⇒ cân tách runtime
  khỏi gói vá (runtime ít đổi hơn app/asset).
- Thêm ngôn ngữ giao diện thứ ba ⇒ `--include-locales` (D2).

---

## Technical Decisions

| Quyết định | Căn cứ |
|---|---|
| Ghim JDK bằng runtime mang theo (hướng) | Chủ dự án 2026-10-01 |
| jlink + jpackage `app-image`, zip, không installer | Đã chạy thử: exit 0, sống 20 s; không cần WiX |
| Không plugin Gradle | CLAUDE.md §4 + ADR-032; plugin không gỡ được bẫy `jdk.localedata` |
| Module tường minh + jdeps làm cổng | jdeps bỏ sót `jdk.localedata` ⇒ đã đo `1,234,567.5` thay vì `1.234.567,5` |
| JDK đóng gói chỉ định qua `kagePackagingJdk` | JBR (toolchain hiện tại) không có jlink/jpackage/jdeps |
| Dựng trên Windows | Temurin 25 không có `jmods/` (JEP 493) ⇒ không cross-link; jpackage không cross-build |

## ADR cần tạo

Không. (Ký số mã và installer WiX, nếu tới, là quyết định chi phí/công cụ mới — cân bằng ADR riêng lúc đó.)

## Tài liệu liên quan

- [ROADMAP.md — v1 Exit Criteria](../../11-process/ROADMAP.md) — "Bản cài client mang theo asset và KHỞI ĐỘNG được", rủi ro LWJGL JNI.
- `client/client-desktop/build.gradle.kts` — `startScripts` wildcard, `assetsJar`, `applicationDefaultJvmArgs`.
- [DOCKER.md §8](../../07-infrastructure/DOCKER.md) + `deploy/docker/server.Dockerfile` — jlink của server, bẫy `jdk.crypto.ec` cùng họ.
- [ADR-024](ADR-024-Asset-Distribution-Strategy.md) / [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) — gói tải lại toàn bộ.
- [ADR-001](ADR-001-Why-Java25.md), [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) — Temurin JDK 25.
- [LAUNCHER.md](../../12-devtools/LAUNCHER.md) — stub auto-update.

## Việc cần làm tiếp theo

1. **Chủ dự án duyệt D1–D8 và trả lời:**
   - **Q1.** A (`.exe` qua jpackage app-image) hay B (giữ `.bat` + thư mục `runtime/`)? Khuyến nghị A.
   - **Q2.** Nhà cung cấp runtime: Temurin 25 (ADR-001, TECHNOLOGY-STACK) — xác nhận giữ Temurin? Thử
     nghiệm dùng Zulu 25.0.4.1 chỉ vì tốc độ tải; cần lặp lại kiểm sống với Temurin trước khi phát hành.
   - **Q3.** Ai dựng gói: máy chủ dự án (thủ công, cài một JDK 25 Temurin đầy đủ) hay thêm job
     `windows-latest` vào CI chạy khi push tag `v*` (asset `assets/exported/` không có trên CI ⇒ job đó
     cần Blender hoặc nguồn asset khác — vướng ART-PIPELINE §7)? Khuyến nghị: thủ công cho Closed Beta.
   - **Q4.** Ký số `.exe`: chấp nhận cảnh báo SmartScreen cho Closed Beta (hướng dẫn người chơi bấm "More
     info → Run anyway"), hay mua chứng chỉ ký mã?
   - **Q5.** Log: D6(a) `--win-console` hay D6(b) file log trong `%LOCALAPPDATA%`? Khuyến nghị (b).
   - **Q6.** Closed Beta có người chơi macOS/Linux không? Nếu không, giữ D8 (chỉ Windows x64).
   - **Q7.** Bỏ native nền tảng khác (~7 MB) khỏi gói Windows?
2. ✅ **ĐÃ THI HÀNH 2026-10-18 (lô 15)** — chủ dự án Accept, Q1–Q7 theo khuyến nghị: **Q1** A (`.exe` app-image) · **Q2** Temurin · **Q3** dựng tay trên máy chủ dự án · **Q4** chấp nhận SmartScreen cho Closed Beta · **Q5** D6(b) `logback.xml` ghi `%LOCALAPPDATA%\Kage\logs\client.log` (giữ console) · **Q6** chỉ Windows x64 · **Q7** bỏ jar native LWJGL của nền tảng khác. Task `packageWindowsClient` (+ `prepareClientPackagingInput`, cổng `checkClientRuntimeModules`, `clientRuntime`, `clientAppImage`) ở `client/client-desktop/build.gradle.kts`; phiên bản gói lấy từ `-PkageClientVersion` (mặc định 0.10.0) vì repo không khai `version`. **Đo thật** với Temurin 25.0.4.1+1 (không `jmods/`, jlink từ runtime image): zip **48,7 MB** chưa asset; `runtime/bin/java --list-modules` = 8 module (5 khai + `java.datatransfer`/`java.prefs`/`java.xml` kéo theo); `KageClient.exe` chạy từ thư mục ngoài repo **sống 20 s**, tạo `client.log`; `NumberFormat` vi trên runtime đã link ra **`1.234.567,5`**. Dựng: `./gradlew :client:client-desktop:packageWindowsClient -PkageBundleAssets -PkagePackagingJdk=<JDK 25>`.
3. ⏳ Kiểm sống trên **máy không cài Java** (hoặc user Windows sạch): giải nén zip → `KageClient.exe` →
   đăng nhập TLS → vào world → asset từ jar vẽ đúng → số vàng hiển thị `1.234` kiểu Việt. Ghi phạm vi đã
   chứng minh như ROADMAP đã làm cho `installDist`.
4. Cập nhật ROADMAP v1 Exit Criteria (rủi ro (2) "bản cài KHÔNG kèm JRE" đóng khi mục 3 xong) và
   [DOCKER.md §8](../../07-infrastructure/DOCKER.md)/`server.Dockerfile` ghi chú `jdk.crypto.ec` rỗng ở JDK 25 (dọn tuỳ chọn).
