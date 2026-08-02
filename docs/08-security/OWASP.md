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
| **GitHub Secret Scanning** | ⚠️ **Chỉ miễn phí cho repo PUBLIC** — repo private cần GitHub Advanced Security (trả phí, cấp Organization). **PHÁT HIỆN THẬT (2026-07-31)**: repo dự án là private, mục Secret Scanning không hề xuất hiện trong Settings → Security (không phải "chưa bật", là "không có tuỳ chọn"). Đã bật thay thế 2 mục miễn phí cùng trang: **Dependency graph** + **Dependabot alerts** (khác `dependabot.yml` — đó chỉ tạo PR version-bump, không cảnh báo CVE) |
| **gitleaks** | Pre-commit hook + CI — **CHƯA làm** (không có `.pre-commit-config.yaml`/git hook nào trong repo tính tới 2026-07-31, dù dự án đã qua nhiều trăm file/commit) |
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

## 4. DAST ⏳ (Phase 4+, mục 1-3 ✅ implemented 2026-08-02)

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
4. ⏳ Bộ test thoát script sandbox — CHƯA làm ở đợt này (ngoài scope yêu cầu 2026-08-02, cần harness riêng cho GraalJS/ADR-016).
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
| 3 | ✅ **implemented 2026-08-02** — Bộ test authorization/replay/rate-limit (§4) — `SecurityIntegrationTest` |
| 4 | ✅ **implemented 2026-08-02** — Fuzzing packet (§4) — `SecurityIntegrationTest#shouldNotCrashOrHang_whenReceivingMalformedOrRandomFrames` |
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
3. ~~Phase 3: viết bộ test §4 (authz, replay)~~ — ✅ **implemented 2026-08-02**, cùng fuzzing + rate-limit, xem §4.
4. ⏳ Bộ test thoát script sandbox (§4 TOC mục 4) — còn lại duy nhất trong danh sách §4, cần harness riêng cho GraalJS (ADR-016).
