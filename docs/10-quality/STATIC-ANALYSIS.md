# STATIC-ANALYSIS.md

**Phân tích tĩnh — máy chặn thay người nhớ**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 41](../00-governance/KAGE-CONSTITUTION.md#điều-41-automation) |

> **Thứ gì máy kiểm tra được thì không giao cho trí nhớ AI** ([AI-WORKING-RULES.md §15](../00-governance/AI-WORKING-RULES.md)).

---

## 1. Vì sao

Dự án giả định **nhiều AI, nhiều năm** (Điều 8). Mỗi AI khởi đầu context rỗng. Văn bản "hãy nhớ đừng làm X" sẽ **thất bại theo thời gian**.

Công cụ phân tích tĩnh biến quy tắc thành **build fail** — không phụ thuộc ai nhớ gì.

---

## 2. Bộ công cụ

| Công cụ | Bắt gì | Gate |
|---|---|---|
| **Spotless** | Format, import order | ✅ auto-fix |
| **Checkstyle** | Style, naming, độ dài | ✅ fail |
| **Error Prone** | Bug pattern lúc compile | ✅ fail |
| **SpotBugs** + `find-sec-bugs` | Bug + **lỗ hổng bảo mật** | ✅ fail (High) |
| **PMD** | Code smell, complexity | ✅ fail (nghiêm trọng) |
| **ArchUnit** | ⚠️ **Vi phạm kiến trúc** | ✅ fail |
| SonarQube | Tổng hợp | ⏳ tuỳ chọn |

---

## 3. Thứ tự chạy

```
Spotless (< 30s)  →  Compile + Error Prone (~2 phút)  →  Checkstyle/SpotBugs/PMD (~2 phút)  →  ArchUnit (< 30s)
```

Nhanh và hay fail nhất trước ([CI-CD.md §2](../07-infrastructure/CI-CD.md)).

---

## 4. Spotless — không tranh luận format

```kotlin
spotless {
    java {
        palantirJavaFormat()      // hoặc googleJavaFormat
        importOrder("java", "javax", "", "dev.kage")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

**Quy tắc**: format là **quyết định một lần**, không phải chủ đề tranh luận PR. `./gradlew spotlessApply` sửa hết.

⚠️ Nếu thấy mình đang comment về format trong PR → **cấu hình sai Spotless**, sửa công cụ ([CODE-REVIEW.md §8](../02-coding-standards/CODE-REVIEW.md)).

---

## 5. Checkstyle

Thực thi [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md):

| Rule | Nguồn |
|---|---|
| Line length 120 | §23 |
| Không wildcard import | §23 |
| Method ≤ 50 dòng | §4 |
| Tham số ≤ 4 | §4 |
| Field ≤ 10/class | [CODE-REVIEW.md §5](../02-coding-standards/CODE-REVIEW.md) |
| **Cấm tên `*Manager`, `*Helper`, `*Util`** | §2 — custom regex rule |
| JavaDoc bắt buộc cho `api/` | §16 |

⚠️ Rule "cấm `*Manager/*Helper/*Util`" cần custom `TypeName` regex — đây là rule **đặc thù dự án**, giá trị cao vì nó bắt đúng thứ [CODING-STANDARDS.md §2](../02-coding-standards/CODING-STANDARDS.md) cấm.

---

## 6. Error Prone

Bắt bug **lúc compile** — Google đúc kết từ codebase khổng lồ.

Bắt được: `equals()` sai kiểu · `Optional.get()` không check · so sánh reference thay `equals` · format string sai · **boxing ngầm**...

⚠️ **PHÁT HIỆN THẬT (2026-07-30, lúc wire thật)**: check tên `AutoBoxing` **KHÔNG TỒN TẠI** trong
Error Prone thật (`AutoBoxing is not a valid checker name` — build fail thật, không phải suy đoán).
Không có 1 check chung nào phát hiện MỌI autoboxing; chỉ có vài check hẹp hơn LIÊN QUAN đến boxed
primitive, đã bật sẵn ở severity mặc định (không cần cấu hình thêm): `BoxedPrimitiveEquality`/
`LockOnBoxedPrimitive`/`IdentityHashMapBoxing` (ERROR), `BoxedPrimitiveConstructor`/
`BoxingComparator`/`ObjectEqualsForPrimitives` (WARNING). Boxing trong hot path (Điều 31) vẫn chủ
yếu dựa vào review + JMH benchmark ([BENCHMARK.md](../09-performance/BENCHMARK.md)), không có 1
check tĩnh nào bắt được toàn bộ trường hợp — xem `build-logic/src/main/kotlin/kage.quality-conventions.gradle.kts`.

---

## 7. SpotBugs + find-sec-bugs

| Plugin | Bắt |
|---|---|
| SpotBugs core | NPE, resource leak, so sánh sai |
| **`find-sec-bugs`** | ⚠️ **SQL injection**, crypto yếu, path traversal |

⚠️ **`find-sec-bugs` là bắt buộc** vì [ADR-004](../01-architecture/adr/ADR-004-Why-PostgreSQL.md) không dùng ORM → ta tự viết SQL → **không có framework che SQL injection** ([SECURITY.md §11](../08-security/SECURITY.md)).

```kotlin
spotbugs {
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
}
dependencies {
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:<version>")
}
```

---

## 8. PMD

Bắt: complexity, duplicate, unused, deep nesting.

| Ngưỡng | Giá trị |
|---|---|
| Cyclomatic complexity | ≤ 10 |
| Độ sâu kế thừa | ≤ 3 |
| Duplicate | ≥ 3 lần ([CODE-REVIEW.md §4](../02-coding-standards/CODE-REVIEW.md) — rule of three) |

---

## 9. ArchUnit — quan trọng nhất

Chi tiết: [CI-CD.md §4](../07-infrastructure/CI-CD.md), [TESTING.md §3](TESTING.md).

Các công cụ trên bắt **lỗi code**. ArchUnit bắt **lỗi kiến trúc** — thứ mà hậu quả xuất hiện sau 2 năm và lúc đó không sửa nổi.

→ Nội dung của **ADR-011**.

---

## 10. Suppression

Đôi khi phải bỏ qua. Quy tắc:

| ✅ | ❌ |
|---|---|
| Suppress **hẹp nhất** (dòng/method) | Suppress cả class/module |
| **Có comment lý do** | Suppress không giải thích |
| Review định kỳ | Suppress rồi quên |

```java
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")   // Pool chưa hợp lý ở đây,
                                                            // đo ở PR #123: không phải hot path
```

⚠️ **Suppress không lý do = tắt kiểm tra vĩnh viễn.** Suppress nhiều = công cụ cấu hình sai → sửa cấu hình, đừng suppress hàng loạt.

---

## 11. Cân bằng — cạm bẫy thật

⚠️ **Static analysis quá nghiêm = đội ngũ tắt nó.**

| Triệu chứng | Sửa |
|---|---|
| Build fail vì lỗi vụn vặt suốt | Hạ mức rule không quan trọng xuống WARN |
| 500 warning tồn đọng | Warning bị bỏ qua → vô dụng. Fix hết hoặc suppress có lý do |
| Đội ngũ dùng `-x check` | Rule sai, không phải người sai |

**Nguyên tắc**: rule **fail** phải là thứ **thật sự quan trọng** (bug, bảo mật, kiến trúc). Style vụn vặt → Spotless auto-fix, không fail.

---

## 12. Lộ trình

| Phase | Việc |
|---|---|
| **0** | Spotless + Checkstyle + Error Prone + SpotBugs(+find-sec-bugs) + PMD + **ArchUnit** |
| 0 | Baseline: 0 warning từ commit đầu |
| 3+ | Bổ sung rule theo bug thật gặp phải |

⚠️ **Phải làm ở Phase 0.** Thêm static analysis vào codebase 100k dòng = 5000 warning = không ai fix = tắt luôn.

⚠️ **Thực tế khác dự đoán (2026-07-30)**: Phase 0 CHƯA bật Spotless/SpotBugs/Error Prone (chỉ
Checkstyle+PMD+ArchUnit), ~10 phase sau mới wire nốt (BUILD.md "Bước 3") — codebase lúc đó đã hàng
chục nghìn dòng qua nhiều feature. Kết quả KHÔNG phải "5000 warning không ai fix": 338 file cần
format lại (auto-fix, không tốn công người), và chỉ ~30 SpotBugs finding thật cần xử lý (2 bug thật
sửa, còn lại suppress có lý do rõ — xem `config/spotbugs/exclude.xml`). Dự đoán "trễ = thảm hoạ" ở
trên đúng tinh thần nhưng phóng đại quy mô — có thể vì đa số finding rơi vào vài pattern lặp lại
nhiều lần (Port/DataSource/JedisPool held-by-reference, TLS chưa làm) chứ không phải rải rác ngẫu
nhiên khắp nơi.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Format do Spotless auto-fix, **không tranh luận PR** | Accepted |
| 2 | **`find-sec-bugs` bắt buộc** — không có ORM che SQL injection | Accepted |
| 3 | **ArchUnit là công cụ quan trọng nhất** | Accepted — ADR-011 |
| 4 | Custom Checkstyle rule cấm `*Manager/*Helper/*Util` | Accepted |
| 5 | ~~Error Prone `AutoBoxing` check~~ — check này KHÔNG TỒN TẠI (phát hiện 2026-07-30), xem §6 | Superseded |
| 6 | **Suppress phải hẹp + có lý do** | Accepted |
| 7 | Rule **fail** chỉ cho bug/bảo mật/kiến trúc; style → auto-fix | Accepted |
| 8 | **Toàn bộ ở Phase 0** — thêm sau = 5000 warning = bị tắt | Accepted |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| ~~**ADR-011**~~ | ✅ **Accepted 2026-08-01** — [ADR-011](../01-architecture/adr/ADR-011-Enforce-Dependency-Rules.md) | Hết chặn |

## Tài liệu liên quan

- [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) — rule được thực thi ở đây
- [CODE-REVIEW.md §8](../02-coding-standards/CODE-REVIEW.md) — máy lo gì, người lo gì
- [CI-CD.md](../07-infrastructure/CI-CD.md) · [TESTING.md](TESTING.md)
- [OWASP.md §3](../08-security/OWASP.md) — SAST
- [AI-WORKING-RULES.md §15](../00-governance/AI-WORKING-RULES.md) — chống trôi kiến trúc

## Việc cần làm tiếp theo

1. 🔴 **Phase 0: cấu hình toàn bộ §2** — đây là cửa sổ duy nhất để làm rẻ.
2. Viết `checkstyle.xml` với custom rule §5.
3. ADR-011 + ArchUnit **trước dòng code gameplay đầu tiên**.
4. ✅ **BUILD.md "Bước 3" xong (2026-07-30)** — Spotless + SpotBugs(+find-sec-bugs) + Error Prone
   wire xong, `./gradlew build`+`test` xanh. Chi tiết đầy đủ + gotcha thật: BUILD.md "Bước 3".
5. **Chưa làm**: TLS thật cho GameConnection/GatewayServer (SpotBugs phát hiện, đang suppress tạm
   trong `config/spotbugs/exclude.xml` — NETWORK.md ghi "TLS bắt buộc ở Production" Accepted nhưng
   chưa 1 dòng code nào, cần ADR riêng trước khi làm).
6. ✅ **PMD thật sự chạy được (2026-08-02)** — root cause KHÔNG phải "chưa hỗ trợ JDK 25" như ghi
   trước đây, mà là bug thật trong PMD 7.10.0: `StackOverflowError` ở
   `TypeOps.isSubClassOfNoInterface` (type-inference engine crash khi resolve type trên bytecode
   JDK 25/class file version 69 — PMD 7.10.0 phát hành trước khi PMD có hỗ trợ Java 25 thật, tức
   PMD 7.16.0 trở đi). Bump `pmd = "7.26.0"` (`gradle/libs.versions.toml` + toolVersion trong
   `kage.quality-conventions.gradle.kts`) — verify bằng build thật, không suy luận: PMD chạy xong
   không crash, và bắt được 1 loạt vi phạm thật (chưa từng chạy được để bắt trước đây) — đã sửa hết,
   xem BUILD.md "Bước 3" để biết chi tiết từng vi phạm.
