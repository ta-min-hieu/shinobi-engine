# OWASP.md

**Quét bảo mật tự động**

| | |
|---|---|
| Status | 🚧 **STUB một phần** — §1–§2 làm được ngay, §3–§4 chờ có code |
| Blocked by | Phần lớn nội dung cần **có code để quét** — dự án hiện chưa có dòng nào |
| Sẽ hoàn thiện ở | [Phase 0](../11-process/ROADMAP.md) (dependency scan + secret scan) → Phase 3 (SAST) → Phase 4+ (fuzzing/authz test thay DAST) |

> Tài liệu này về **công cụ quét tự động**. Nguyên tắc bảo mật thiết kế nằm ở [SECURITY.md](SECURITY.md).

---

## Vì sao một phần là stub

| Mục | Làm được ngay? | Vì sao |
|---|---|---|
| §1 Dependency Check | ✅ **Có** — chạy được từ Phase 0 | Quét `libs.versions.toml`, không cần code |
| §2 Secret Scan | ✅ **Có** — bật ngay | Quét repo, không cần code |
| §3 SAST | ⏳ Cần có code | Quét source — chưa có source |
| §4 DAST | ⏳ Cần server chạy được | Quét app đang chạy — chưa có app |

Viết chi tiết §3–§4 bây giờ = bịa cấu hình cho code không tồn tại ([AI-WORKING-RULES.md §13–14](../00-governance/AI-WORKING-RULES.md)).

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
| **GitHub Secret Scanning** | Bật ngay — miễn phí |
| **gitleaks** | Pre-commit hook + CI |
| **git-secrets** | Thay thế |

**Bắt buộc**: pre-commit hook — chặn secret **trước khi** vào git, vì:

⚠️ **Secret đã commit = đã lộ.** Xoá khỏi git history **không đủ** — phải **rotate** secret đó ([SECURITY.md §13](SECURITY.md)).

**Ưu tiên cao nhất**: bật §2 **trước khi** có commit đầu tiên. Phòng rẻ hơn chữa rất nhiều.

---

## 3. SAST ⏳ (Phase 3+)

Static Application Security Testing — quét source tìm lỗ hổng.

**Đã có sẵn một phần** qua [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md):

| Công cụ hiện có | Bắt được |
|---|---|
| **SpotBugs** + `find-sec-bugs` plugin | SQL injection, crypto yếu, path traversal |
| **Error Prone** | Một số lỗi bảo mật |
| **PMD** | Một số pattern nguy hiểm |

**Cần bổ sung khi có code:**
- `find-sec-bugs` plugin cho SpotBugs — **ưu tiên cao**, rẻ, bắt SQL injection (quan trọng vì [ADR-004](../01-architecture/adr/ADR-004-Why-PostgreSQL.md) không dùng ORM → ta tự viết SQL).
- CodeQL (GitHub) — miễn phí cho repo công khai.
- SonarQube — nếu có hạ tầng host.

### TOC khi viết đầy đủ
1. Cấu hình `find-sec-bugs`
2. Quy tắc gate (finding nào chặn PR)
3. Suppression policy
4. False positive đã biết

---

## 4. DAST ⏳ (Phase 4+)

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
1. Fuzzing harness cho packet
2. Bộ test authorization
3. Bộ test replay/rate-limit
4. Bộ test thoát script sandbox
5. DAST cho admin API (nếu có)

---

## 5. OWASP Top 10 — cái nào liên quan?

Trung thực: OWASP Top 10 viết cho **web app**. Không phải mục nào cũng áp dụng.

| OWASP Top 10 | Liên quan Kage? | Xử lý |
|---|---|---|
| **A01 Broken Access Control** | 🔴 **Rất liên quan** | IDOR — [SECURITY.md §4](SECURITY.md) |
| **A02 Cryptographic Failures** | 🔴 Liên quan | Mật khẩu, TLS — [SECURITY.md §3](SECURITY.md) |
| **A03 Injection** | 🔴 Liên quan | SQL injection — không có ORM che |
| A04 Insecure Design | 🟡 Liên quan | Toàn bộ [SECURITY.md](SECURITY.md) |
| A05 Security Misconfiguration | 🔴 Liên quan | ⚠️ Redis không password — [SECURITY.md §14](SECURITY.md) |
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
| **0** | ✅ Secret scan (§2) — **trước commit đầu tiên** · ✅ Dependabot (§1) |
| 0 | ✅ `find-sec-bugs` vào SpotBugs config |
| 3 | ⏳ Bộ test authorization/replay/rate-limit (§4) |
| 4 | ⏳ Fuzzing packet (§4) |
| 4+ | ⏳ CodeQL / SonarQube nếu cần |

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
| 8 | SAST/DAST chi tiết chờ có code | ⏳ Stub |

## ADR cần tạo

Không có — đây là công cụ, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [SECURITY.md](SECURITY.md) — **nguyên tắc bảo mật thiết kế**
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — SpotBugs đã có
- [CI-CD.md §10](../07-infrastructure/CI-CD.md) — secret trong pipeline
- [TESTING.md](../10-quality/TESTING.md) — test bảo mật tự viết
- [TECHNOLOGY-STACK.md §1](../01-architecture/TECHNOLOGY-STACK.md) — 7 câu hỏi dependency

## Việc cần làm tiếp theo

1. 🔴 **Bật secret scanning + pre-commit hook TRƯỚC khi có commit đầu tiên** (§2). Đây là việc rẻ nhất và giá trị cao nhất trong toàn bộ tài liệu này.
2. Phase 0: Dependabot + `find-sec-bugs`.
3. Phase 3: viết bộ test §4 (authz, replay) — **quan trọng hơn DAST rất nhiều** với kiến trúc này.
