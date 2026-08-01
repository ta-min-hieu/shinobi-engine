# GIT-WORKFLOW.md

**Quy trình Git, branch, release**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 39–40](../00-governance/KAGE-CONSTITUTION.md) |

> ⚠️ **Repo hiện CHƯA khởi tạo git.** Tài liệu này áp dụng từ commit đầu tiên ở [Phase 0](ROADMAP.md).

---

## 1. Trước commit đầu tiên

🔴 **Bắt buộc làm trước khi `git init`:**

| Việc | Vì sao | Nguồn |
|---|---|---|
| `.gitignore` đầy đủ | `build/`, `.gradle/`, `*.jar`, `assets/raw/`, `local.properties` | — |
| **Secret scanning + pre-commit hook** | ⚠️ Secret đã commit = **đã lộ**, xoá không đủ, phải rotate | [OWASP.md §2](../08-security/OWASP.md) |
| `.gitattributes` | ⚠️ LF/CRLF — dev Windows + CI Linux | §8 |

⚠️ **Đây là cửa sổ duy nhất làm rẻ.** Sau 1000 commit, dọn secret khỏi history là phẫu thuật.

---

## 2. Branch Strategy

```
main          ← Production. Luôn deploy được. Chỉ nhận từ release/* và hotfix/*
  ↑
develop       ← Tích hợp. Nhận từ feature/*
  ↑
feature/*     ← Công việc hàng ngày
release/*     ← Đóng băng chuẩn bị release
hotfix/*      ← Sửa gấp Production → merge vào CẢ main VÀ develop
```

| Nhánh | Đặt tên | Sống bao lâu |
|---|---|---|
| `feature/` | `feature/inventory-enhance`, `feature/ADR-011-archunit` | Ngắn — **≤ 1 tuần** |
| `release/` | `release/1.2.0` | Tới khi release xong |
| `hotfix/` | `hotfix/1.2.1-duplicate-item` | Vài giờ |

⚠️ **Nhánh sống lâu = merge hell.** Nhánh > 1 tuần → chia nhỏ công việc.

⚠️ **Hotfix phải merge vào cả `main` và `develop`** — quên `develop` = bug quay lại ở release sau.

---

## 3. Commit

**Conventional Commits:**

```
<type>(<scope>): <mô tả ngắn>

<thân — VÌ SAO, không phải LÀM GÌ>

<footer: refs #issue, ADR-XXX>
```

| Type | Dùng cho |
|---|---|
| `feat` | Tính năng mới |
| `fix` | Sửa bug |
| `refactor` | Không đổi behavior (Điều 39) |
| `perf` | Tối ưu — ⚠️ **bắt buộc kèm số liệu** |
| `test` | Thêm/sửa test |
| `docs` | Tài liệu |
| `build` / `ci` | Build, pipeline |
| `chore` | Vặt |

```
feat(inventory): thêm cường hoá trang bị

Cường hoá +0→+15 theo GDD §79.4. Dùng transaction vì trừ nguyên liệu
và ghi enhance_level phải nguyên tử — nếu tách, người chơi mất đồ khi
server crash giữa chừng.

Refs #42, DATABASE.md §5
```

**Ngôn ngữ**: commit message **tiếng Anh** hoặc **tiếng Việt** — chọn một, nhất quán. *(Đề xuất: tiếng Việt, vì đội ngũ tiếng Việt và tài liệu tiếng Việt.)*

⚠️ **Commit `perf` không có số liệu = không merge** (Điều 30).

---

## 4. Pull Request

[CODE-REVIEW.md §7](../02-coding-standards/CODE-REVIEW.md).

| Quy tắc | Chi tiết |
|---|---|
| **Một PR — một mục tiêu** | Điều 39 |
| Kích thước | ≤ ~400 dòng là dễ review nhất |
| ❌ **Refactor + feature cùng PR** | Điều 39 — không ai review nổi |
| ❌ Gộp thay đổi không liên quan | — |
| Liên kết Issue / ADR | Điều 39 |

**PR template:**

```markdown
## Mục tiêu
<giải quyết gì>

## Thay đổi
- ...

## Bốn câu hỏi (AI-WORKING-RULES §4)
- Module bị ảnh hưởng:
- Có phá dependency rule không:
- Có cần ADR không:
- Có phá backward compat không (packet/DB/API):

## Test
- [ ] Unit test
- [ ] ArchUnit pass
- [ ] Nếu fix bug: có test tái hiện (FAIL trước khi sửa)
- [ ] Nếu `perf`: có số liệu benchmark

## Tài liệu
- [ ] Đã cập nhật nếu đổi kiến trúc (Điều 40)

Refs: #issue / ADR-XXX
```

---

## 5. Merge

| Cách | Dùng cho | Vì sao |
|---|---|---|
| **Squash merge** | `feature/*` → `develop` | History sạch, 1 feature = 1 commit |
| **Merge commit** | `release/*` → `main` | Giữ dấu vết release |
| ❌ Rebase merge | — | Đổi SHA, rối khi nhiều người |

⚠️ **Không force-push lên `main`/`develop`.** Bật branch protection.

**Branch protection cho `main` và `develop`:**
- [ ] Bắt buộc PR (không push thẳng)
- [ ] Bắt buộc CI pass ([CI-CD.md §3](../07-infrastructure/CI-CD.md))
- [ ] Bắt buộc ≥1 review
- [ ] Cấm force-push

---

## 6. Tag & Versioning

Điều 40 — Semantic Versioning.

| Thành phần | Version riêng | Vì sao |
|---|---|---|
| Server/Client | `v1.2.3` | — |
| **Protocol** | riêng | [NETWORK.md §6](../05-server/NETWORK.md) — client cũ phải chạy |
| **Asset** | riêng | [RESOURCE.md §9](../04-client/RESOURCE.md) — patch không rebuild |
| ~~Plugin~~ | *(không áp dụng)* | Plugin System không được xây — [ADR-030](../01-architecture/adr/ADR-030-No-Plugin-System.md); xem [PLUGIN.md §7](../06-plugin/PLUGIN.md) cho quy ước versioning đã từng được đề xuất |

| Thay đổi | Bump |
|---|---|
| Phá backward compat (protocol/API) | **MAJOR** |
| Tính năng mới, tương thích | MINOR |
| Sửa bug | PATCH |

---

## 7. Release Process

Điều 40 + [ROADMAP.md Phase 10](ROADMAP.md):

```
develop
   ↓ tạo release/1.2.0 (đóng băng tính năng)
   ↓ chỉ nhận bug fix
Closed Beta → Open Beta → Release Candidate
   ↓ merge vào main + tag v1.2.0
Production
   ↓ nếu lỗi
hotfix/1.2.1 → main + develop
```

| Quy tắc | Vì sao |
|---|---|
| ❌ **Không deploy Production trực tiếp từ `develop`** | Điều 40 |
| ❌ **Không auto-deploy Production** | Deploy = đá người chơi ([CI-CD.md §8](../07-infrastructure/CI-CD.md)) |
| Không tính năng nào lên Production chưa qua ≥1 vòng Closed Beta | — |
| Release phải có **rollback plan** | ⚠️ Đặc biệt DB migration ([DATABASE.md §10](../05-server/DATABASE.md)) |

⚠️ **Rollback code dễ, rollback dữ liệu khó.** Migration xoá cột → rollback = dữ liệu **đã mất**.

---

## 8. Line Ending — bẫy Windows

⚠️ Đội ngũ dev **Windows**, CI chạy **Linux**. Không cấu hình → mọi file "đổi toàn bộ" trong diff.

```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.png binary
*.ogg binary
*.atlas text eol=lf
```

**Bắt buộc từ commit đầu tiên.** Sửa sau = một commit khổng lồ đổi mọi file.

---

## 9. File lớn

| Loại | Cách |
|---|---|
| `assets/raw/` (PSD) | ⚠️ Cân nhắc **Git LFS** — PSD 200MB × 100 commit = repo khổng lồ |
| `assets/exported/` | Sinh ra được → cân nhắc không commit? |
| Binary test fixture | Commit (nhỏ) |

⚠️ **Quyết định LFS phải làm sớm** — chuyển sang LFS sau khi đã có lịch sử là rewrite history.

**Đề xuất**: Git LFS cho `assets/raw/**` từ đầu.

---

## 10. Ai làm gì

| Việc | AI được tự làm? |
|---|---|
| Tạo branch, commit | ✅ |
| Mở PR | ✅ |
| **Merge PR** | ❌ Người quyết |
| **Push thẳng `main`/`develop`** | ❌ Không ai được |
| **Tag release** | ❌ Người |
| **Force push** | ❌ Không ai |
| `git reset --hard`, `git clean` | ⚠️ Chỉ khi được yêu cầu rõ, sau khi `git status` |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Git Flow rút gọn: `main` / `develop` / `feature` / `release` / `hotfix` | Accepted |
| 2 | Conventional Commits; **`perf` phải kèm số liệu** | Accepted |
| 3 | Squash merge cho feature; merge commit cho release | Accepted |
| 4 | **Branch protection**: cấm force-push, bắt buộc CI + review | Accepted |
| 5 | Protocol / Asset / Plugin có **version riêng** | Accepted |
| 6 | ❌ Không auto-deploy Production | Accepted |
| 7 | 🔴 **`.gitattributes` + LFS + secret hook TRƯỚC commit đầu** | Accepted |
| 8 | Nhánh feature ≤ 1 tuần | Accepted |
| 9 | Ngôn ngữ commit: **tiếng Việt** (đề xuất) | ⏳ Chờ chốt |
| 10 | Git LFS cho `assets/raw/**` | Accepted (bật 2026-08-01) |

## ADR cần tạo

Không có — quy trình, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [CODE-REVIEW.md](../02-coding-standards/CODE-REVIEW.md) — §4
- [CI-CD.md](../07-infrastructure/CI-CD.md) — gate
- [ROADMAP.md](ROADMAP.md) — Phase 10
- [DOCUMENTATION.md](DOCUMENTATION.md) — tài liệu trong PR
- [OWASP.md §2](../08-security/OWASP.md) — secret scanning

## Việc cần làm tiếp theo

1. 🔴 **Trước `git init`**: `.gitignore`, `.gitattributes` (§8), secret hook (§1), quyết định LFS (§9).
2. Phase 0: bật branch protection (§5).
3. Chốt ngôn ngữ commit (§3).
