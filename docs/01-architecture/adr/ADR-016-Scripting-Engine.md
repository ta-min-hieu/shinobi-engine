# ADR-016: Dùng GraalJS làm scripting engine

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-20 (quyết định) — 2026-07-22 (ghi ADR) |
| Deciders | Chủ dự án |
| Related | [SCRIPTING.md](../../06-plugin/SCRIPTING.md), [ADR-001](ADR-001-Why-Java25.md), [AI.md §4](../../03-engine/AI.md), [GAMEPLAY.md §4, §9](../../05-server/GAMEPLAY.md), Điều 27 |

---

## Context

Điều 27 ([KAGE-CONSTITUTION.md](../../00-governance/KAGE-CONSTITUTION.md)): Quest · NPC · Dialogue · Cutscene · Event · Festival · Boss (nhiều pha) **không được hardcode**, phải chạy qua script có **sandbox**. [SCRIPTING.md](../../06-plugin/SCRIPTING.md) đã phân tích 2 ứng viên (Lua/LuaJ vs GraalJS) nhưng để ngỏ, khuyến nghị Lua, chờ ADR-016 chốt trước khi Phase 4 (skill dùng script) và Phase 6 (quest) chạm vào.

Hai vấn đề kỹ thuật cần kiểm chứng trước khi chốt — không thể chọn chỉ bằng bảng so sánh lý thuyết trong SCRIPTING.md §3:

1. **LuaJ có còn được bảo trì không?** SCRIPTING.md liệt "bảo trì chậm" là nhược điểm nhưng không định lượng.
2. **GraalJS có thực sự bắt buộc đổi runtime sang GraalVM không?** SCRIPTING.md §3 nêu đây là "vấn đề then chốt": nếu đúng, chọn GraalJS đồng nghĩa phải **mở lại ADR-001** (Temurin JDK 25) — một quyết định lớn hơn nhiều so với chọn ngôn ngữ script.

## Decision

Dùng **GraalJS** (`org.graalvm.polyglot:js`) làm scripting engine, chạy trên **Temurin JDK 25 hiện có** (không đổi ADR-001) qua `SandboxPolicy.ISOLATED`/`UNTRUSTED`.

> **Cập nhật triển khai (2026-07-22, xem "Kiểm chứng thêm khi code" bên dưới):** `ISOLATED`/`UNTRUSTED` thật hiện **chưa dùng được** — artifact bắt buộc `js-isolate-community` bị lỗi đóng gói trên Maven Central. `engine-scripting` đang chạy tạm ở cấu hình tay tương đương `CONSTRAINED`. Quyết định engine (GraalJS) không đổi; chỉ mức sandbox policy đang thấp hơn dự tính ban đầu của ADR này.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Lua (LuaJ)** | Cú pháp đơn giản nhất cho designer; chuẩn ngành game (WoW, Roblox, Garry's Mod); nhẹ, khởi động nhanh; chạy mọi JVM không cần thêm gì | **Thực chất không còn được bảo trì**: bản MIT license gần nhất là **3.0.2, phát hành tháng 3/2019** (~7 năm tính tới 2026-07), nhắm Lua 5.2 cũ; **không có sandbox policy dựng sẵn** — phải tự giới hạn thủ công từng hàm thư viện chuẩn expose ra script (đúng điều Điều 27 coi là nguy hiểm nhất) | Không chọn |
| **GraalJS** | Bảo trì tích cực bởi Oracle (release tháng 7/2026 xác nhận vẫn đang phát triển); UPL-1.0/GPL2+Classpath; có sẵn `SandboxPolicy.ISOLATED`/`UNTRUSTED` + giới hạn CPU/memory — xây **đúng cho bài toán "chạy script không tin cậy an toàn"** mà ADR này cần giải; JIT thật qua Truffle framework của GraalVM | Nặng hơn LuaJ; JS có nhiều bẫy cú pháp hơn Lua cho designer không rành lập trình (`this`, prototype, async) | **Chọn** |

**Không có phương án C thật** — SCRIPTING.md §1 đã chốt nguyên tắc "chọn MỘT, không hỗ trợ cả hai" (hai sandbox = hai chỗ có lỗ hổng), nên bài toán chỉ còn nhị phân giữa hai ứng viên đã khảo sát kỹ ở SCRIPTING.md §3.

## Kiểm chứng 2 vấn đề kỹ thuật nêu ở Context

**1. LuaJ bảo trì chậm — đúng, và nghiêm trọng hơn SCRIPTING.md mô tả.** 3.0.2 (03/2019) là bản MIT ổn định gần nhất, không có sandbox policy sẵn — SCRIPTING.md §4 yêu cầu "whitelist thư viện + giới hạn instruction/memory/timeout" sẽ phải tự viết toàn bộ tay trên một engine ít người còn bảo trì.

**2. "GraalJS cần GraalVM" — SCRIPTING.md §3 nêu đúng vấn đề nhưng kết luận đã lỗi thời.** Kiểm tra lại 2026-07-22: GraalJS từ lâu đã publish thẳng lên Maven Central dưới coordinate `org.graalvm.polyglot:js`, chạy được trên **JVM tiêu chuẩn bất kỳ** (Oracle JDK, OpenJDK, Temurin) như một dependency thông thường — không cần cài GraalVM làm JDK distribution. Không có Graal Compiler (JIT chuyên biệt), GraalJS chạy ở chế độ interpreter chậm hơn "peak" GraalVM, nhưng SCRIPTING.md §2/§7 đã tự đặt nguyên tắc **script chạy theo sự kiện, không trong hot path mỗi tick, có timeout mỗi lần gọi** — đúng use case mà interpreter-mode "đủ nhanh", giống lý do LuaJ "đủ nhanh" từng được dùng để khuyến nghị Lua. Nếu sau này cần thêm tốc độ, có thể nạp thêm Graal Compiler như một JIT module (`--upgrade-module-path`) cho **cùng một Temurin JDK 25** — vẫn không cần đổi JDK distribution, ADR-001 không bị động tới.

Kết luận: rào cản lớn nhất khiến SCRIPTING.md nghiêng về Lua (không phá ADR-001) **không có thật** — GraalJS chạy trên Temurin bình thường. Khi rào cản đó bị loại, GraalJS thắng LuaJ ở đúng thứ Điều 27 quan tâm nhất: sandbox dựng sẵn, và một engine còn được bảo trì tích cực thay vì đã dừng 7 năm.

**3. Ngộ nhận hiệu năng cần làm rõ**: "Lua nhanh hơn GraalJS 2-3 lần" là nhận định phổ biến nhưng sai lệch nguồn — con số đó đến từ **LuaJIT** (trình biên dịch JIT viết bằng C của Lua) so với V8/JS thông dịch, KHÔNG phải từ **LuaJ** (một trình thông dịch cây cú pháp thuần Java, không có JIT riêng) so với GraalJS (có JIT thật qua Truffle framework của GraalVM). Không có benchmark trực tiếp LuaJ-vs-GraalJS đáng tin cậy nào; nếu có khác biệt, nhiều khả năng nghiêng về GraalJS vì LuaJ hoàn toàn không có JIT.

## Kiểm chứng thêm khi code (2026-07-22, `engine-scripting` lát cắt đầu)

Lúc viết `ScriptSandbox` (host binding + eval thật, xem `dev.kage.engine.scripting.ScriptSandbox`), gọi `.sandbox(SandboxPolicy.ISOLATED)`/`UNTRUSTED` như Decision ở trên ghi thất bại lúc chạy: `IllegalArgumentException: Polyglot isolate execution was requested... but polyglot isolate support is not available on the class path`. Cả hai policy này bắt buộc thêm artifact `org.graalvm.polyglot:js-isolate-community` để chạy script trong một JVM isolate riêng (heap/JIT tách biệt host).

Thêm artifact đó vào `libs.versions.toml` lộ ra một **lỗi đóng gói thật của GraalVM trên Maven Central ở bản `25.1.3`**, không phải lỗi cấu hình phía dự án:

1. POM của `js-isolate-community` không dùng Gradle variant-aware OS/arch selection — Gradle kéo **toàn bộ** biến thể nền tảng (linux-amd64, linux-aarch64, darwin-amd64, darwin-aarch64, windows-amd64...) thay vì chỉ nền tảng máy đang chạy.
2. Trong số đó, `js-isolate-darwin-amd64-community:25.1.3` **không tồn tại** trên Maven Central (404) — khiến resolve thất bại ngay cả trên máy Windows, không liên quan gì tới macOS.

Xác nhận qua build thật (`./gradlew :engine:engine-scripting:test`), không phải suy đoán từ tài liệu — [[kage_build_environment]] (memory) có cách bật JDK 25 để chạy build thật trong môi trường này.

**Quyết định tạm thời**: không ép dùng artifact đang lỗi, cũng không âm thầm hạ toàn bộ ADR. `ScriptSandbox` hiện tự khai báo tay các giới hạn tương đương `SandboxPolicy.CONSTRAINED` (chặn host access, I/O, thread/process mới, biến môi trường, polyglot access — cộng `statementLimit` chặn vòng lặp vô hạn), đủ để chặn toàn bộ danh sách "Script KHÔNG được" của SCRIPTING.md §4. Thiếu so với `ISOLATED`/`UNTRUSTED` thật: **không có heap JVM riêng** (script và host dùng chung heap — script cấp phát bộ nhớ lớn vẫn ảnh hưởng host tới khi `statementLimit` chặn) và không có JIT/Spectre mitigation riêng theo isolate.

## Kiểm chứng bằng script thù địch (2026-10-06)

Bộ `ScriptSandboxAdversarialTest` (engine-scripting) đánh vào từng bảo đảm ở trên. Kết quả, không suy đoán:

1. **"Tương đương CONSTRAINED" là SAI** ở hai điểm — chính GraalVM nói vậy: dựng context cũ với `.sandbox(SandboxPolicy.CONSTRAINED)` bị từ chối vì (a) `HostAccess.NONE` vẫn bật *mutable target mapping*, (b) `print`/`console.log` của script ghi thẳng ra `System.out` của server (một kênh I/O mà `IOAccess.NONE` không phủ). **Đã sửa**: `ScriptSandbox` nay khai `.sandbox(SandboxPolicy.CONSTRAINED)` THẬT (artifact hiện có, không cần isolate) — GraalVM tự validate cấu hình mỗi lần dựng context.
2. **`statementLimit` KHÔNG chặn được treo luồng.** Regex backtracking `/^(a+)+\1b$/` chỉ tốn vài câu lệnh nhưng treo luồng gọi > 15 giây; đệ quy `try/catch` bắt `RangeError` rồi gọi lại treo > 10 phút (statementLimit lẫn `close(true)` đều không dừng được). SCRIPTING.md §7 #7 "Timeout mỗi lần gọi" là Accepted nhưng chưa từng được làm. **Đã sửa**: timeout theo đồng hồ thật mỗi lần `eval` (`close(true)` từ luồng canh, mặc định 500ms — chốt chặn, chưa phải ngân sách) + `sandbox.MaxStackFrames=128` (vượt là cạn tài nguyên, script không `catch` được).
3. **Câu "ảnh hưởng host tới khi `statementLimit` chặn" ở đoạn trên là sai**: MỘT câu lệnh (`new Array(5e7).fill(1.5)`) đủ đòi hàng trăm MB heap chung. `sandbox.MaxHeapMemory` báo "not supported on the current Truffle runtime" khi không có isolate — lỗ này chỉ ISOLATED đóng được.
4. Builtin không có safepoint (VD `2n ** 20000000n`, ~5 giây) không bị `close(true)` ngắt giữa chừng — cũng chỉ ISOLATED (CPU limit theo isolate) mới xử lý triệt để.

## Consequences

**Tích cực:**
- Sandbox (`SandboxPolicy.ISOLATED`/`UNTRUSTED`, khi artifact isolate dùng được — xem "Kiểm chứng thêm khi code" ở trên) là tính năng dựng sẵn của GraalJS — khớp trực tiếp yêu cầu "cứng" của Điều 27, giảm bề mặt tự viết so với phải tự giới hạn LuaJ thủ công.
- Engine còn được bảo trì tích cực (Oracle) — giảm rủi ro phải di trú engine giữa chừng dự án như LuaJ đang đối mặt.
- **ADR-001 không cần mở lại** — vẫn Temurin JDK 25, GraalJS chỉ là một Maven dependency (`org.graalvm.polyglot:js`) thêm vào `engine-scripting`.
- Debug tốt hơn (Chrome DevTools protocol) — hữu ích khi tự làm một mình, không có team debug hộ.

**Đánh đổi phải chấp nhận:**
- Cú pháp JS phức tạp hơn Lua cho một game designer không rành lập trình — nếu tương lai có designer không-code tham gia, cần cân nhắc lớp cú pháp thân thiện hơn (không phải MVP).
- Không chạy Graal Compiler (để giữ nguyên Temurin, không đổi ADR-001) → hiệu năng interpreter-mode, không đạt "peak" GraalVM. Chấp nhận được vì SCRIPTING.md §2/§7 đã thiết kế script chạy theo sự kiện + có timeout, không phải hot path.
- Nặng hơn LuaJ về footprint bộ nhớ/khởi động — cần đo thật khi có nhiều script instance đồng thời (JMH, theo TECHNOLOGY-STACK.md §8), không giả định.
- **Sandbox thật đang thấp hơn Decision đã ghi**: `js-isolate-community:25.1.3` lỗi đóng gói trên Maven Central (không variant-aware, thiếu classifier darwin-amd64) → chưa dùng được `ISOLATED`/`UNTRUSTED`. Đang chạy tạm ở cấu hình tay tương đương `CONSTRAINED` (chặn đủ theo SCRIPTING.md §4 nhưng KHÔNG có heap JVM riêng cho script) — chấp nhận được cho lát cắt đầu vì chưa có script không tin cậy từ người dùng cuối chạy thật (chỉ nội dung do chủ dự án viết), nhưng **phải nâng lên `ISOLATED`/`UNTRUSTED` thật trước khi cho phép script từ nguồn không tin cậy** (VD: nếu sau này mở workshop/mod cộng đồng).

**Module bị ảnh hưởng:** `engine-scripting` (script sandbox, binding API); `TECHNOLOGY-STACK.md` §5 (thêm GraalJS vào bảng Server); `libs.versions.toml` (thêm `org.graalvm.polyglot:js` — verify version thật trước khi ghi, theo CLAUDE.md §"gen code hiệu quả").

## Revisit Trigger

- Graal Compiler/GraalJS đổi license theo hướng không còn miễn phí dùng cho dự án cá nhân (giống rủi ro Redis/SSPL ở ADR-005, Liquibase/FSL ở ADR-013) → xem lại toàn bộ ADR này.
- Benchmark thật (JMH, theo TECHNOLOGY-STACK.md §8) đo được thời gian khởi động/memory GraalJS interpreter-mode vượt ngưỡng chấp nhận được cho số lượng script instance thực tế (VD: quest instance đồng thời) → cân nhắc nạp Graal Compiler làm JIT module hoặc, nếu vẫn không đủ, xem lại lựa chọn engine.
- Phát hiện lỗ hổng sandbox escape thật trong `SandboxPolicy.ISOLATED`/`UNTRUSTED` (CVE hoặc tự phát hiện) → dừng dùng script chưa qua bản vá, đánh giá lại.
- `js-isolate-community` ra bản mới sửa lỗi đóng gói (variant-aware OS/arch selection, đủ classifier), HOẶC dự án chốt nền tảng server mục tiêu (nhiều khả năng chỉ Linux/Docker, xem ROADMAP) đủ để ghim đúng 1 classifier thay vì cần Gradle tự chọn → nâng `ScriptSandbox` từ cấu hình tay CONSTRAINED-equivalent lên `SandboxPolicy.ISOLATED`/`UNTRUSTED` thật.
- Có kế hoạch cho script từ nguồn không tin cậy (không phải chủ dự án tự viết) chạy trên server production → **bắt buộc** giải quyết mục trên trước, không được ship cấu hình CONSTRAINED-equivalent hiện tại cho trường hợp đó.
