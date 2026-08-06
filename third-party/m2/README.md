# `third-party/m2` — kho Maven cục bộ nằm trong repo

Thi hành [ADR-032](../../docs/01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md).

Thư mục này là một **kho Maven đúng chuẩn bố cục**, được khai báo trong
`settings.gradle.kts` bên cạnh `mavenCentral()`. Toạ độ dependency giữ nguyên như
thư viện tải từ mạng — không `flatDir`, không `files(...)` — nên đổi sang tải từ
mạng (hoặc ngược lại) chỉ là đổi một dòng repository.

## Vì sao có thư mục này

`jitpack.io` **build theo yêu cầu từ GitHub**. Rủi ro có hai mặt, và chỉ một mặt
chặn được bằng checksum:

| Rủi ro | Checksum có chặn được không? |
|---|---|
| Cùng toạ độ nhưng artifact đổi (tag bị đẩy lại, tài khoản bị chiếm) | ✅ |
| Repo gốc bị xoá / đổi tên / JitPack ngừng phục vụ ⇒ build gãy vĩnh viễn | ❌ |

Dự án này một người bảo trì và sẽ còn build lại nhiều năm, nên **rủi ro sẵn có**
mới là thứ đáng sợ. Đóng gói jar vào repo chặn được cả hai.

## Đang chứa gì

| Artifact | Phiên bản | Kích thước | License | Nguồn | Ngày tải |
|---|---|---|---|---|---|
| `com.github.mgsx-dev.gdx-gltf:gltf` | 2.3.0 | 318 KB | Apache-2.0 | jitpack.io | 2026-08-06 |

SHA-256 của jar nằm ở `gltf-2.3.0.jar.sha256`, cạnh chính nó:

```
efbd4e7c351f1a23f70dfb46aea99516551ee4d2d55e4cc47fe100a2fbf4c874
```

Con số này đã được **đối chiếu độc lập hai lần**: lúc khảo sát viết ADR-032
(2026-08-05) và lúc tải về đóng gói (2026-08-06). Hai lần khớp nhau.

POM khai đúng **một** phụ thuộc bắc cầu — `com.badlogicgames.gdx:gdx:1.9.11` —
mà dự án vốn đã có gdx 1.14.2, nên đóng gói thư viện này **không kéo theo thứ gì
mới** vào graph.

## Cập nhật lên phiên bản mới

1. Tải `.jar` và `.pom` của phiên bản mới từ JitPack.
2. Tính lại SHA-256, ghi vào `<artifact>.jar.sha256`.
3. Đặt vào đúng đường dẫn theo groupId (dấu `.` thành thư mục con).
4. Cập nhật `gradle/libs.versions.toml` và bảng ở trên.
5. Chạy build thật để xác nhận resolve được trước khi commit.

⚠️ **Đừng sửa file trong này bằng tay.** Đây là artifact nhị phân đã ký bằng
checksum; sửa vào là chữ ký sai và build gãy — đúng như thiết kế.

## License

`LICENSE-gdx-gltf.txt` là bản Apache-2.0 lấy từ chính repo gốc
(`mgsx-dev/gdx-gltf`). Apache-2.0 **cho phép phát hành lại** kèm license, nên
việc đóng gói ở đây là hợp lệ.
