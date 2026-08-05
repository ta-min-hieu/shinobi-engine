# OWASP.md

**Quét bảo mật tự động**

| | |
|---|---|
| Status | ✅ **Đầy đủ** — §1–§4 đều đã triển khai và verify thật (cập nhật 2026-08-05) |
| Lịch sử | Viết dưới dạng stub một phần khi dự án chưa có code; §1–§2 làm 2026-07-31/08-02, §3 wired 2026-07-30, §4 implement 2026-08-02 |

> Tài liệu này về **công cụ quét tự động**. Nguyên tắc bảo mật thiết kế nằm ở [SECURITY.md](SECURITY.md).

---

## Trạng thái từng mục

| Mục | Trạng thái | Ở đâu |
|---|---|---|
| §1 Dependency Check | ✅ Dependency graph + Dependabot alerts bật 2026-07-31 | GitHub Settings → Security |
| §2 Secret Scan | ✅ gitleaks: pre-commit hook + CI job `secret-scan` (2026-08-02) | `.githooks/pre-commit`, `.github/workflows/ci.yml` |
| §3 SAST | ✅ SpotBugs+find-sec-bugs / Error Prone / PMD / Checkstyle (2026-07-30) | `build-logic/.../kage.quality-conventions.gradle.kts` |
| §4 DAST → test tự viết | ✅ 4/5 mục implement 2026-08-02, mục 5 không áp dụng | `SecurityIntegrationTest`, `ScriptSandboxSecurityTest` |

> Tài liệu này từng là stub một phần vì §3–§4 cần code để quét mà lúc đó chưa có dòng nào. Điều kiện
> đó đã hết từ lâu (Phase 0–9 xong) — nội dung dưới đây mô tả **cấu hình đang chạy thật**, không phải
> dự định.

---

## 1. Dependency Check ✅ (làm từ Phase 0)

Quét CVE trong thư viện bên thứ ba — thực thi Điều 36 câu hỏi "Rủi ro bảo mật?".

| Công cụ | Ghi chú |
|---|---|
| **OWASP Dependency-Check** | Gradle plugin; quét NVD database |
| **GitHub Dependabot** | Miễn phí cho repo GitHub; tự tạo PR nâng version |
| Snyk / OSS Index | Thương mại; tuỳ chọn |

**Đề xuất**: **Dependabot** (miễn phí, tự động) + **OWASP Dependency-Check** trong CI.

```
Quy tắc gate:
  CVE nghiêm trọng (Critical/High) → build FAIL
  CVE trung bình (Medium)          → cảnh báo, tạo issue
  CVE thấp                          → báo cáo
```

⚠️ **Cạm bẫy false positive**: Dependency-Check hay báo CVE của thư viện ta không dùng đường code có lỗ hổng. → Cần **suppression file** có ghi lý do, review định kỳ.

⚠️ **Đừng để CVE gate thành nhiễu**: nếu build fail suốt vì CVE không liên quan, đội ngũ sẽ tắt gate. Tệ hơn là không có gate.

---

## 2. Secret Scan ✅ (bật ngay từ commit đầu)

| Công cụ | Khi nào |
|---|---|
| **GitHub Secret Scanning** | ⚠️ **Chỉ miễn phí cho repo PUBLIC** — repo private cần GitHub Advanced Security (trả phí, cấp Organization). **PHÁT HIỆN THẬT (2026-07-31)**: repo dự án là private, mục Secret Scanning không hề xuất hiện trong Settings → Security (không phải "chưa bật", là "không có tuỳ chọn"). Đã bật thay thế 2 mục miễn phí cùng trang: **Dependency graph** + **Dependabot alerts** (khác `dependabot.yml` — đó chỉ tạo PR version-bump, không cảnh báo CVE) |
| **gitleaks** | Pre-commit hook + CI — ✅ **implemented 2026-08-02**. Pre-commit hook: [`.githooks/pre-commit`](../../.githooks/pre-commit) (cài 1 lần/máy: `git config core.hooksPath .githooks`, xem [`.githooks/README.md`](../../.githooks/README.md)) — quét `gitleaks git --staged`, chặn commit nếu thấy secret; nếu máy chưa cài `gitleaks` thì chỉ cảnh báo, không chặn (fast pre-check, không phải hàng rào chặn thật). CI: job `secret-scan` trong [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) — tải gitleaks v8.30.1 (verify SHA-256 checksum từ GitHub Releases thật, không dùng `gitleaks-action` để tránh phụ thuộc license bên thứ 3), quét **toàn bộ git history** mỗi lần chạy — đây mới là hàng rào chặn thật, không phụ thuộc máy dev nào có cài `gitleaks` hay không. Verify thật: đã stage 1 fake secret dạng AWS key, xác nhận hook + CLI chặn commit (exit 1), rồi xoá. |
| **git-secrets** | Thay thế |

**Bắt buộc**: pre-commit hook — chặn secret **trước khi** vào git, vì:

⚠️ **Secret đã commit = đã lộ.** Xoá khỏi git history **không đủ** — phải **rotate** secret đó ([SECURITY.md §13](SECURITY.md)).

**Ưu tiên cao nhất**: bật §2 **trước khi** có commit đầu tiên. Phòng rẻ hơn chữa rất nhiều.

---

## 3. SAST ✅ (đang chạy từ 2026-07-30)

Static Application Security Testing — quét source tìm lỗ hổng. **Không còn là stub**: toàn bộ chuỗi
đã wired trong `build-logic/src/main/kotlin/kage.quality-conventions.gradle.kts` và chạy mỗi lần
`./gradlew build`. Chi tiết cấu hình chung: [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md).

### 3.1 Cấu hình thật

| Công cụ | Phiên bản | Cấu hình | Bắt được |
|---|---|---|---|
| **SpotBugs** + **`find-sec-bugs`** | `findsecbugs-plugin:1.14.0` | `effort = MAX`, `reportLevel = MEDIUM`, exclude ở `config/spotbugs/exclude.xml` | SQL injection, crypto yếu, path traversal, deserialization |
| **Error Prone** | `error_prone_core:2.50.0` | severity mặc định của tool | Bug lúc compile, vài lớp lỗi bảo mật |
| **PMD** | 7.26.0 | `config/pmd/ruleset.xml` | Pattern nguy hiểm |
| **Checkstyle** | `config/checkstyle/checkstyle.xml` (test dùng `checkstyle-test.xml`, khác đúng 1 rule) | | Chuẩn code |

⚠️ **`find-sec-bugs` là BẮT BUỘC, không phải tuỳ chọn.** [ADR-004](../01-architecture/adr/ADR-004-Why-PostgreSQL.md)
chọn không dùng ORM ⇒ SQL do người viết tay ⇒ **không có framework nào che SQL injection**
([SECURITY.md §11](SECURITY.md)). Đây là công cụ duy nhất trong chuỗi bắt được lớp lỗi đó.

### 3.2 Quy tắc gate

Build **fail** khi có finding — không có mức "cảnh báo rồi bỏ qua" cho SAST. Lý do ghi ở
[STATIC-ANALYSIS.md §11](../10-quality/STATIC-ANALYSIS.md): gate mà không chặn thì sau vài tuần thành
5000 warning không ai đọc, tệ hơn là không có gate.

### 3.3 Suppression policy

Chỉ qua `config/spotbugs/exclude.xml`, **mỗi mục phải kèm lý do bằng comment**. Không dùng
`@SuppressFBWarnings` rải rác trong code — suppression tập trung một chỗ thì review được, rải rác thì không.

### 3.4 Bài học thật khi wired (2026-07-30 → 2026-08-02)

- **Error Prone không có check tên `AutoBoxing`.** STATIC-ANALYSIS.md từng ghi tên đó; build thật báo
  `AutoBoxing is not a valid checker name`. Không tồn tại check chung bắt **mọi** autoboxing — chỉ có
  vài check hẹp (`BoxedPrimitiveEquality`, `LockOnBoxedPrimitive`, `IdentityHashMapBoxing`...) và
  chúng đã bật sẵn. Boxing trong hot path ([Điều 31](../00-governance/KAGE-CONSTITUTION.md)) vẫn phải
  dựa vào review + JMH, **không công cụ tĩnh nào bắt hết**.
- **Spotless phải khai ở root project** dù root không có code, và phải `targetExclude` thư mục
  `build/generated/sources/proto/**` — nếu không nó format cả code protoc tự sinh.
- **PMD 7.10.0 không hiểu JDK 25**, phải nâng lên 7.26.0 (`8087b61`, 2026-08-02). Trước đó CI phải
  chạy `-x pmdMain -x pmdTest`; workaround đó nay đã gỡ hẳn.

### 3.5 Đã cân nhắc và loại

| Công cụ | Vì sao không dùng |
|---|---|
| **CodeQL** | Miễn phí cho repo **công khai**; repo này private trên gói free — cùng lý do khiến GitHub Secret Scanning không khả dụng (§2) |
| **SonarQube** | Cần hạ tầng tự host; chuỗi hiện tại đã phủ các lớp lỗi chính, thêm SonarQube là thêm một server phải nuôi |

---

## 4. DAST — thay bằng test tự viết, mục 1-4 ✅ implemented 2026-08-02 (chỉ mục 5 không áp dụng)

Dynamic Application Security Testing — quét app **đang chạy**.

⚠️ **Vấn đề: DAST tiêu chuẩn không hợp game server.**

| DAST thường quét | Kage có? |
|---|---|
| HTTP endpoint | ❌ Game dùng **TCP + Protobuf**, không phải HTTP |
| Form, cookie, XSS web | ❌ Không có web UI |
| REST API | ⚠️ Chỉ nếu có admin API |

→ **OWASP ZAP / Burp Suite gần như vô dụng** với game protocol nhị phân.

**Thứ thật sự cần thay thế DAST:**

| Việc | Cách |
|---|---|
| **Fuzzing packet** | Gửi packet ngẫu nhiên/dị dạng → server phải không crash |
| **Test authorization** | Client A gửi Command với `characterId` của B → phải bị từ chối ([SECURITY.md §4](SECURITY.md)) |
| **Test replay** | Gửi lại packet cũ → phải bị từ chối |
| **Test rate limit** | Flood → phải bị chặn |
| **Test script sandbox thoát** | Script cố `getClass()` → phải bị chặn |

→ **Đây là test tự viết, không phải công cụ mua sẵn.** Thuộc [TESTING.md](../10-quality/TESTING.md) hơn là DAST.

### TOC khi viết đầy đủ

1. ✅ **implemented 2026-08-02** — Fuzzing harness cho packet. `SecurityIntegrationTest#shouldNotCrashOrHang_whenReceivingMalformedOrRandomFrames` (`server/server-gateway/src/test/.../SecurityIntegrationTest.java`) — 36 lần lặp, 6 biến thể xoay vòng (payload protobuf rác, opcode giả mạo, frame ngắn hơn header, frame rỗng, khai length > `MAX_FRAME_LENGTH`, frame cắt cụt giữa chừng), rồi sanity-check server còn sống bằng 1 kết nối Ping/Pong sạch. Không dùng thư viện fuzzing riêng (CLAUDE.md §4 — không thêm dependency).
2. ✅ **implemented 2026-08-02** — Bộ test authorization. `SecurityIntegrationTest` 4 test đầu (nhóm "AUTHORIZATION / IDOR"). **Phát hiện quan trọng**: `characterId` KHÔNG BAO GIỜ nằm trong bất kỳ Command nào khác ngoài `SelectCharacterRequest` (mọi Command gameplay khác dùng `boundCharacterId` từ session — đúng thiết kế SECURITY.md §4) — IDOR kiểu "client A gửi Command mang `characterId` của B" **không tồn tại được** cho các Command đó. `SelectCharacterRequest` LÀ packet duy nhất client tự khai `characterId`, nên đó chính là bề mặt IDOR thật: test `shouldRejectSelectCharacter_whenCharacterBelongsToAnotherAccount` verify server chặn đúng (404 `error.character_not_found`, fail-secure — cùng lỗi dù id không tồn tại hay thuộc account khác) + session KHÔNG bị bind. Cũng verify ranh giới "channel chưa đăng nhập / đã đăng nhập nhưng chưa chọn nhân vật" không gửi được Command gameplay nào (401 `error.not_authenticated`).
3. ✅ **implemented 2026-08-02** — Bộ test replay/rate-limit. Cùng file, 2 nhóm "REPLAY" và "RATE LIMIT": `idempotencyKey` (ADR-025) verify THẬT qua `BuyItemCommand` (gold trừ đúng 1 lần/item cấp đúng 1 lần dù gửi lại) và `CreateCharacterCommand`; rate limit verify THẬT qua flood socket thật cho `MoveCommand` (30/giây) và chat WORLD (5/10s), xác nhận `kage.ratelimit.rejected` tăng thật + kết nối bị ngắt sau 3 window liên tiếp vượt ngưỡng. **Phát hiện quan trọng**: [SECURITY.md §6](SECURITY.md) liệt "`sequenceId` tăng dần — server từ chối sequenceId ≤ cái đã thấy" như 1 cơ chế chống replay, nhưng code hiện tại KHÔNG hề implement việc này — `sequenceId` chỉ dùng để khớp request/response ([NETWORK.md §5](../05-server/NETWORK.md)), không có state nào lưu "sequenceId cuối đã thấy" cho bất kỳ Command nào. Test `moveCommand_sequenceIdIsNotEnforcedAsReplayGuard_documentedGap` xác nhận trung thực khoảng trống này (gửi sequenceId thấp hơn vẫn được xử lý bình thường) thay vì giả vờ nó đã được chặn. Không phải lỗ hổng khai thác được thật (Command không mang side-effect tài chính dùng cơ chế này) — Command CÓ side-effect được `idempotencyKey` bảo vệ riêng, đã verify OK ở trên. SECURITY.md §6 cần sửa lại mô tả cho khớp thực tế (ngoài scope thay đổi của lần này — chỉ ghi nhận, không tự sửa tài liệu thiết kế).
4. ✅ **implemented 2026-08-02** — Bộ test thoát script sandbox. `ScriptSandboxSecurityTest` (`engine/engine-scripting/src/test/.../ScriptSandboxSecurityTest.java`, làm song song cùng ngày, luồng riêng) — 15 test verify sandbox `CONSTRAINED`-equivalent (ADR-016) chặn đúng host access/file I/O/thread/process/env access/runaway script/cross-script leak/Polyglot global. Không phát hiện gap thật — mọi vector đều bị chặn đúng như javadoc `ScriptSandbox` claim.
5. ⏳ DAST cho admin API — chưa có admin API nào tồn tại trong dự án, không áp dụng.

---

## 5. OWASP Top 10 — cái nào liên quan?

Trung thực: OWASP Top 10 viết cho **web app**. Không phải mục nào cũng áp dụng.

| OWASP Top 10 | Liên quan Kage? | Xử lý |
|---|---|---|
| **A01 Broken Access Control** | 🔴 **Rất liên quan** | IDOR — [SECURITY.md §4](SECURITY.md) |
| **A02 Cryptographic Failures** | 🔴 Liên quan | Mật khẩu, TLS — [SECURITY.md §3](SECURITY.md) |
| **A03 Injection** | 🔴 Liên quan | SQL injection — không có ORM che |
| A04 Insecure Design | 🟡 Liên quan | Toàn bộ [SECURITY.md](SECURITY.md) |
| A05 Security Misconfiguration | 🔴 Liên quan | ✅ Redis đã có password bắt buộc (`requirepass` qua `CACHE_PASSWORD`, verify 2026-08-02) — [SECURITY.md §14](SECURITY.md); ⚠️ còn thiếu test runtime xác nhận `requirepass` chặn kết nối không auth |
| A06 Vulnerable Components | 🔴 Liên quan | §1 |
| A07 Auth Failures | 🔴 Liên quan | Brute-force, session |
| A08 Data Integrity Failures | 🟡 Liên quan | Java Serialization (đã cấm) |
| A09 Logging Failures | 🔴 Liên quan | Audit log — [SECURITY.md §9](SECURITY.md) |
| A10 SSRF | 🟢 Ít liên quan | Server không gọi URL do người dùng cung cấp |

**Bổ sung — rủi ro của game mà OWASP không có:**
- Duplicate item / gold (⚠️ nghiêm trọng nhất về mặt kinh tế)
- Bot / automation
- Speed/damage hack
- Script sandbox escape

---

## 6. Lộ trình

| Phase | Việc |
|---|---|
| **0** | ✅ Secret scan (§2) · ✅ Dependabot (§1). **Trung thực**: §2 bật 2026-08-02, tức **sau** vài trăm commit chứ không phải trước commit đầu — muộn vẫn hơn không |
| 0 | ✅ `find-sec-bugs` vào SpotBugs config (2026-07-30) |
| 3 | ✅ **implemented 2026-08-02** — Bộ test authorization/replay/rate-limit (§4) — `SecurityIntegrationTest` |
| 4 | ✅ **implemented 2026-08-02** — Fuzzing packet (§4) — `SecurityIntegrationTest#shouldNotCrashOrHang_whenReceivingMalformedOrRandomFrames` |
| 4+ | ❌ CodeQL / SonarQube — **đã loại**, lý do ở §3.5 |

**Còn mở** (không phải stub, là việc thật chưa làm):

| Việc | Vì sao còn mở |
|---|---|
| Test runtime xác nhận Redis `requirepass` chặn kết nối không auth | §5 dòng A05 — cấu hình đã có và verify bằng mắt, chưa có test tự động |
| `sequenceId` **không** phải cơ chế chống replay dù [SECURITY.md §6](SECURITY.md) từng mô tả vậy | Đã sửa mô tả trong SECURITY.md (2026-08-02); Command có side-effect được `idempotencyKey` bảo vệ riêng nên **không phải lỗ hổng khai thác được** |
| Chưa có xác thực mật khẩu | `ConnectRequest` chỉ có username — cắt giảm MVP có chủ đích. **Chỉ chấp nhận được cho Closed Beta người quen**; mở cho người lạ thì phải làm trước |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **Secret scan bật trước commit đầu tiên** | Accepted — ưu tiên cao nhất |
| 2 | Dependabot + OWASP Dependency-Check; CVE Critical/High → build fail | Accepted |
| 3 | Suppression file **phải có lý do**, review định kỳ | Accepted |
| 4 | `find-sec-bugs` cho SpotBugs — ưu tiên cao vì tự viết SQL | Accepted |
| 5 | **DAST tiêu chuẩn (ZAP/Burp) không dùng** — game protocol nhị phân | Accepted — trung thực |
| 6 | Thay DAST bằng **test tự viết**: fuzzing, authz, replay, sandbox escape | Accepted |
| 7 | OWASP Top 10 **không áp dụng toàn bộ** — chỉ mục liên quan (§5) | Accepted |
| 8 | ~~SAST/DAST chi tiết chờ có code~~ | ✅ Hết treo — §3 wired 2026-07-30, §4 implement 2026-08-02 |
| 9 | **CodeQL và SonarQube đều loại** (§3.5) | Accepted — CodeQL chỉ free cho repo public, SonarQube cần server tự nuôi |
| 10 | SAST gate **fail build**, không có mức "cảnh báo rồi bỏ qua" | Accepted — gate không chặn thì thành 5000 warning không ai đọc |

## ADR cần tạo

Không có — đây là công cụ, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [SECURITY.md](SECURITY.md) — **nguyên tắc bảo mật thiết kế**
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — SpotBugs đã có
- [CI-CD.md §10](../07-infrastructure/CI-CD.md) — secret trong pipeline
- [TESTING.md](../10-quality/TESTING.md) — test bảo mật tự viết
- [TECHNOLOGY-STACK.md §1](../01-architecture/TECHNOLOGY-STACK.md) — 7 câu hỏi dependency

## Việc cần làm tiếp theo

1. ~~🔴 Bật secret scanning + pre-commit hook TRƯỚC khi có commit đầu tiên~~ (§2) — ✅ **implemented 2026-08-02**. Trung thực: KHÔNG còn kịp "trước commit đầu tiên" (repo đã qua nhiều trăm commit) — nhưng bật muộn vẫn tốt hơn không bật. Pre-commit hook `.githooks/pre-commit` + CI job `secret-scan` (`.github/workflows/ci.yml`), xem §2.
2. ~~Phase 0: Dependabot + `find-sec-bugs`~~ — ✅ **done, verify 2026-08-02** — Dependabot alerts bật (§2, thay thế Secret Scanning không có trên private tier); `find-sec-bugs` wired bắt buộc vào SpotBugs qua `kage.quality-conventions.gradle.kts` (`spotbugsPlugins` dependency, không điều kiện tắt) — build+test thật đã xanh với static analysis này (BUILD.md "Bước 3", 2026-07-30).
3. ~~Phase 3: viết bộ test §4 (authz, replay)~~ — ✅ **implemented 2026-08-02**, cùng fuzzing + rate-limit, xem §4.
4. ~~Bộ test thoát script sandbox (§4 TOC mục 4)~~ — ✅ **implemented 2026-08-02**, xem §4. Toàn bộ §4 nay chỉ còn mục 5 (DAST admin API) không áp dụng.
