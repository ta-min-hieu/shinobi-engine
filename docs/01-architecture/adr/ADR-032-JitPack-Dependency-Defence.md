# ADR-032: Phòng vệ JitPack — đóng gói `gdx-gltf` vào repo dưới dạng kho Maven cục bộ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-06, chủ dự án xác nhận qua chat) — **đã thi hành cùng ngày**, xem [third-party/m2/README.md](../../../third-party/m2/README.md) |
| Date | 2026-08-05 |
| Deciders | Chủ dự án quyết; AI soạn + đo |
| Related | Thi hành việc treo #2 của [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) · [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) · [/CLAUDE.md §4](../../../CLAUDE.md) (thêm thư viện phải dừng hỏi) · Điều 36 |

---

## Context

[ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) (Accepted 2026-08-03) chọn `gdx-gltf` để nạp model
glTF, và **tự đặt điều kiện chặn cho chính nó**: *"Phải chốt cách phòng vệ [JitPack] trước khi phụ
thuộc thật"* — nên tới giờ thư viện **chưa được thêm vào `libs.versions.toml`**, và bước nối
`WorldScreen` sang `ModelBatch` không đi tiếp được.

Kiểm chứng thật ngày 2026-08-05 (không lấy từ tài liệu):

- **Không có trên Maven Central.** Tra API Central với từ khoá `gltf`: không có artifact nào thuộc
  `com.github.mgsx-dev` hay `gdx-gltf`. Các kết quả `gltf` khác đều của dự án khác.
- **Toạ độ đúng là `com.github.mgsx-dev.gdx-gltf:gltf:2.3.0`** (khớp ADR-031). Không có artifact
  `core` riêng như một số trang index gợi ý.
- **Tải thật từ JitPack**: jar **325.604 byte (318 KB)**, SHA-256
  `EFBD4E7C351F1A23F70DFB46AEA99516551EE4D2D55E4CC47FE100A2FBF4C874`.
- **POM khai đúng một phụ thuộc bắc cầu**: `com.badlogicgames.gdx:gdx:1.9.11` — dự án **vốn đã có**
  gdx (1.14.2, xem `libs.versions.toml`), nên đóng gói thư viện này **không kéo theo thứ gì mới**.
- License **Apache-2.0** ghi trong POM ⇒ **được phép phát hành lại**, kèm LICENSE/NOTICE.

Bản chất rủi ro JitPack có **hai** mặt, và trộn chúng lại là lý do người ta hay chọn nhầm cách chống:

| Rủi ro | Nghĩa là gì | Checksum có chặn được không? |
|---|---|---|
| **Toàn vẹn** | JitPack **build theo yêu cầu từ GitHub**. Cùng một toạ độ, artifact có thể khác đi nếu tag bị đẩy lại hoặc tài khoản bị chiếm | ✅ Có |
| **Sẵn có** | Repo gốc bị xoá / đổi tên / JitPack ngừng phục vụ ⇒ **build gãy vĩnh viễn**, kể cả trên máy đã từng build được | ❌ Không |

Rủi ro sẵn có là rủi ro thật với dự án này: chỉ một người bảo trì, một dịch vụ build-on-demand miễn
phí, và một game client sẽ còn phải build lại nhiều năm sau.

## Decision

**Đóng gói `gdx-gltf` vào repo dưới dạng một kho Maven cục bộ**, và **bỏ hẳn `jitpack.io` khỏi danh
sách repository của Gradle**.

```
third-party/m2/com/github/mgsx-dev/gdx-gltf/gltf/2.3.0/
├── gltf-2.3.0.jar          (318 KB)
├── gltf-2.3.0.pom
└── gltf-2.3.0.jar.sha256
third-party/m2/LICENSE-gdx-gltf.txt   (Apache-2.0 + NOTICE gốc)
third-party/m2/README.md              (nguồn, ngày tải, checksum, cách cập nhật)
```

Khai báo trong Gradle **giữ nguyên toạ độ Maven bình thường** — không dùng `flatDir`, không
`files(...)`:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri(rootProject.layout.projectDirectory.dir("third-party/m2")) }
}
```

Kèm theo: bật **Gradle dependency verification** (`gradle/verification-metadata.xml`) cho artifact
này, để một lần thay file trong repo cũng bị build chặn chứ không lặng lẽ trôi qua.

## Alternatives Considered

| Phương án | Chặn được toàn vẹn | Chặn được sẵn có | Vì sao chọn / không |
|---|---|---|---|
| **A. Giữ JitPack + chỉ pin checksum** | ✅ | ❌ | **Không chọn** — giải đúng một nửa vấn đề, mà nửa còn lại mới là nửa giết dự án. Repo biến mất thì checksum chỉ giúp biết chắc là mình không tải được gì |
| **B. Kho Maven cục bộ trong repo** ✅ | ✅ (kèm verification) | ✅ | **Chọn** — 318 KB, một phụ thuộc bắc cầu đã có sẵn, Apache-2.0 cho phép; build **offline được** và tái lập được vô thời hạn |
| **C. Mirror lên GitHub Packages** | ✅ | ⚠️ một phần | **Không chọn** — vẫn phụ thuộc một dịch vụ bên ngoài (cùng nhà cung cấp với repo gốc, tức **cùng một điểm hỏng**), lại thêm token vào CI. Đổi một dependency mạng lấy một dependency mạng khác |
| **D. Build từ source (submodule / included build)** | ✅ | ✅ | **Không chọn** — kéo cả toolchain build của dự án khác vào CI mỗi lần build, để đổi lấy đúng cái mà 318 KB jar đã cho. Chỉ đáng làm nếu cần **sửa** thư viện |
| **E. `flatDir` + `files(...)`** | ✅ | ✅ | **Không chọn** — `flatDir` không đọc POM nên mất metadata phụ thuộc bắc cầu, và Gradle cảnh báo đúng về điều đó. Bố cục thư mục Maven cho lại metadata mà không mất gì |

## Consequences

**Tích cực:**

- Build **tái lập được và chạy offline** — không phụ thuộc JitPack lúc build, kể cả lần build đầu.
- Bỏ được một repository lạ khỏi cấu hình Gradle. `jitpack.io` phục vụ artifact build từ **mã nguồn
  tuỳ ý trên GitHub**; không khai nó là thu hẹp bề mặt tấn công supply-chain của toàn dự án, không
  riêng thư viện này.
- Điều kiện Revisit Trigger #3 của ADR-031 ("JitPack ngừng phục vụ artifact") **tự vô hiệu**.
- Mở khoá việc treo #2 của ADR-031 ⇒ thêm được `gdx-gltf` vào `libs.versions.toml` và bắt đầu đường
  render 3D.

**Đánh đổi phải chấp nhận:**

- **Một binary 318 KB nằm trong git.** Đi ngược nguyên tắc chung "không commit thứ tải lại được" —
  nhưng chính "tải lại được" là giả định đang bị nghi ngờ. Không dùng Git LFS: LFS dành cho
  `assets/raw/**` và có quota; 318 KB đi git thường là rẻ hơn.
- **Cập nhật phiên bản thành việc thủ công**: tải, kiểm SHA-256, thay thư mục, cập nhật
  `verification-metadata.xml`. Với thư viện phát hành ~1 lần/năm thì đây là chi phí nhỏ, và
  `third-party/m2/README.md` ghi sẵn quy trình.
- Dependabot **không theo dõi** được artifact này. Phải tự kiểm bản mới — ghi vào cùng README.

**Module bị ảnh hưởng:** `client:client-desktop` (khai dependency), `client:client-core` (dùng),
`gradle/libs.versions.toml`, `settings.gradle.kts`/`build.gradle.kts` (repository). **Không** đụng
`server:*`.

## Revisit Trigger

1. `gdx-gltf` được phát hành lên **Maven Central** ⇒ bỏ kho cục bộ, quay lại toạ độ bình thường.
2. Cần **sửa** mã nguồn gdx-gltf ⇒ chuyển sang phương án D (build từ source).
3. Có thêm thư viện thứ hai chỉ phát hành qua JitPack ⇒ xem lại xem kho cục bộ có còn là cách rẻ nhất.

## Technical Decisions

- Kho cục bộ đặt ở `third-party/m2/`, đúng bố cục thư mục Maven, khai bằng `maven { url = ... }`.
- Ghim `gltf:2.3.0`, SHA-256 như trên, kèm bản Apache-2.0 và NOTICE gốc.
- Bật Gradle dependency verification cho artifact này.
- **Không** khai `jitpack.io` ở bất kỳ đâu trong build.

## ADR cần tạo

Không. ADR này chính là ADR mà [ADR-031 §"ADR cần tạo"](ADR-031-25D-Realtime-3D-Rendering.md) yêu cầu.

## Tài liệu liên quan

- [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) — việc treo #2 mà ADR này gỡ
- [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) — thêm `gdx-gltf` sau khi Accepted
- [ART-PIPELINE.md §4.4](../../14-art/ART-PIPELINE.md) — ngân sách tam giác của model mà thư viện này nạp

## Việc cần làm tiếp theo

1. ✅ **Chủ dự án đã Accept** (2026-08-06).
2. ✅ `third-party/m2/...` đã có: jar + pom + LICENSE + README + checksum (`38daea2`).
3. ✅ `gdx-gltf` đã vào `libs.versions.toml` + repository cục bộ; resolve + compile được với
   `--offline`, tức lấy thật từ repo chứ không qua mạng.
4. Sinh `gradle/verification-metadata.xml` — **chưa làm**.
5. ✅ `WorldScreen` đã nối sang nhánh 3D (`df5ace2`). ⛔ Còn thiếu **phép đo frame time** để điền
   dòng trống ở ART-PIPELINE §4.4 — chưa đo được vì chưa có `.glb` thật trong bản chạy, xem
   [RENDERING.md §6](../../03-engine/RENDERING.md).
