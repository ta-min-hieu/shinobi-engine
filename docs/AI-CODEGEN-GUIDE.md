# AI-CODEGEN-GUIDE.md

**Cách AI gen code nhanh và ít token trên Project Kage**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Đối tượng | AI (Claude Code) sinh code — không phải người |

> Tài liệu này **không** dạy nghiệp vụ. Nó dạy **cách nạp đúng context tối thiểu** để gen code đúng mà không đọc toàn bộ docs.
> Nguyên tắc gốc: **đọc ít, dựa vào enforcement, copy template thay vì suy luận từ prose.**

---

## 1. Mô hình 3 lớp context

Đừng nạp toàn bộ docs để viết một class. Nạp theo lớp, dừng ngay khi đủ:

```
Lớp 1 — LUÔN có sẵn (0 token thêm)
  /CLAUDE.md (auto-load) + <thư mục>/CLAUDE.md (auto-load theo vùng đang sửa)
        ↓  đủ để gen 80% code thường ngày
Lớp 2 — TEMPLATE (rẻ, copy-paste)
  docs/templates/*  →  pattern-match, điền chỗ trống
        ↓  đủ để gen code có cấu trúc chuẩn
Lớp 3 — DOC SÂU (chỉ khi bị thách thức / làm việc lạ)
  Mục cụ thể trong docs/NN-*/  — đọc ĐÚNG mục, không đọc cả file
        ↓
Lớp 4 — HIẾN PHÁP (chỉ khi tranh chấp nguyên tắc)
  docs/00-governance/KAGE-CONSTITUTION.md — điều khoản cụ thể
```

⚠️ **Quy tắc token**: nếu đang gen một class bình thường mà thấy mình mở > 2 file doc → **dừng, bạn đang đọc thừa**. Lớp 1 + template gần như luôn đủ.

---

## 2. Enforcement gánh phần lớn — bạn không cần nhớ hết luật

Đây là lý do bộ này tiết kiệm token: **bạn không phải giữ mọi quy tắc trong đầu.**

| Việc | Ai bắt lỗi | Bạn cần nhớ? |
|---|---|---|
| Import Netty/JDBC vào gameplay | ArchUnit → build fail | ❌ Không |
| `domain/` import `infrastructure/` | ArchUnit | ❌ |
| Import Ashley ngoài `engine-ecs` | ArchUnit | ❌ |
| Format, import order | Spotless auto-fix | ❌ |
| Tên `*Manager/*Helper` | Checkstyle | ❌ |
| SQL injection | find-sec-bugs | ❌ |
| Method quá dài, complexity | PMD | ❌ |

→ **Gen code theo hiểu biết tốt nhất, để build bắt lỗi còn lại.** Đừng nạp doc chỉ để "chắc chắn không phạm luật X" — enforcement lo (xem [STATIC-ANALYSIS.md](10-quality/STATIC-ANALYSIS.md), [CI-CD.md §4](07-infrastructure/CI-CD.md)).

⚠️ *Điều kiện*: enforcement chỉ hoạt động từ **Phase 0** khi ArchUnit + static analysis đã cấu hình. Trước đó, dựa vào scope cards.

---

## 3. Bản đồ Task → đọc đúng cái gì

Tra bảng này **trước khi** mở file. Cột "Đọc" là **tối thiểu đủ** — đừng đọc thêm.

| Tôi cần... | Lớp 1 (scope card) | Template | Doc sâu (chỉ mục cần) |
|---|---|---|---|
| **Thêm feature nghiệp vụ** (inventory, guild, shop...) | `server/CLAUDE.md` | `templates/feature-module/` | [REPOSITORY-STRUCTURE §4](01-architecture/REPOSITORY-STRUCTURE.md) · [DATABASE](05-server/DATABASE.md) (bảng liên quan) |
| **Thêm ECS Component/System** | `engine/CLAUDE.md` | `templates/ecs/` | [ECS.md §3–6](03-engine/ECS.md) |
| **Thêm packet** (Command/Response/Event) | `shared/CLAUDE.md` | `templates/protocol/` | [NETWORK.md §3–4](05-server/NETWORK.md) |
| **Sửa combat/skill** | `server/CLAUDE.md` | `templates/ecs/` | [GAMEPLAY.md §3–4](05-server/GAMEPLAY.md) · [SCRIPTING](06-plugin/SCRIPTING.md) nếu là hiệu ứng |
| **Thêm AI quái** | `engine/CLAUDE.md` | `templates/ecs/` | [AI.md §3](03-engine/AI.md) |
| **Thêm UI/HUD** | `client/CLAUDE.md` | — | [UI.md](04-client/UI.md) |
| **Rendering** | `client/CLAUDE.md` | — | [RENDERING.md](03-engine/RENDERING.md) |
| **Truy vấn DB / adapter** | `server/CLAUDE.md` | `templates/feature-module/infrastructure` | [DATABASE.md §5,§11](05-server/DATABASE.md) |
| **Viết test** | (theo vùng) | `templates/*` có test kèm | [TESTING.md](10-quality/TESTING.md) |
| **Sửa bug** | — | — | [AI-WORKING-RULES §9](00-governance/AI-WORKING-RULES.md) (test tái hiện trước!) |
| **Làm art nhân vật/quái/NPC** | — | `tools/blender/kage_text3d_kit.py` | [ART-PIPELINE §6.4](14-art/ART-PIPELINE.md) + [BLENDER-SESSION-GUIDE §1.5](14-art/BLENDER-SESSION-GUIDE.md) + [ART-QUALITY-STANDARD](14-art/ART-QUALITY-STANDARD.md) — **chỉ** 3 doc này |
| **Làm art VFX/icon/prop (ra sprite)** | — | `tools/blender/kage_sprite_render.py` | [ART-PIPELINE.md](14-art/ART-PIPELINE.md) — **chỉ** doc này, không đọc thêm |
| **Tạo ADR** | — | `01-architecture/adr/ADR-TEMPLATE.md` | [adr/README](01-architecture/adr/README.md) |
| **Thêm module** | — | — | 🛑 **DỪNG — cần ADR** ([AI-WORKING-RULES §6.2](00-governance/AI-WORKING-RULES.md)) |

---

## 4. Trước khi gen — 30 giây tự hỏi

Từ [AI-WORKING-RULES §4](00-governance/AI-WORKING-RULES.md), nhưng bản nhanh:

1. **Vùng nào?** → scope card của vùng đó đã auto-load chưa? (nếu chưa: đang sửa file ngoài module → đọc scope card thủ công)
2. **Có template không?** → có thì copy, đừng viết từ đầu.
3. **Có thuộc danh sách 🛑 phải dừng không?** (thêm module/dep/protocol/schema/kiến trúc → cần ADR)
4. **Có phá backward compat không?** (packet/DB/public API)

Nếu cả 4 đều "an toàn" → **gen ngay**, để enforcement bắt lỗi còn lại. Đừng đọc thêm doc.

---

## 5. Kỷ luật token khi báo cáo

- Đừng dán lại nội dung doc vào câu trả lời — link tới nó.
- Đừng giải thích lại nguyên tắc đã có trong doc — trích Điều/§.
- Khi gen code: viết code, nêu 1–2 câu quyết định quan trọng, xong. Không kể lể.

---

## 6. Khi nào ĐƯỢC phá "đọc ít"

Đọc sâu (Lớp 3–4) là **đúng** khi:
- Làm việc lần đầu ở một vùng lạ.
- Bị người dùng thách thức một quyết định.
- Sắp làm thứ ảnh hưởng nhiều module.
- Nghi ngờ template/scope card đã lỗi thời.

Trong các ca đó, token bỏ ra để đọc đúng là **đầu tư**, không phải lãng phí. Kỷ luật token là "đọc đúng cái cần", không phải "đọc càng ít càng tốt bất chấp".

---

## 7. Danh sách file lớp Codegen

| File | Vai trò | Kích hoạt |
|---|---|---|
| [/CLAUDE.md](../CLAUDE.md) | Luật luôn-nhớ | Auto-load mọi phiên |
| `shared/CLAUDE.md` | Thẻ luật vùng shared | Auto-load khi sửa `shared/**` |
| `engine/CLAUDE.md` | Thẻ luật vùng engine | Auto-load khi sửa `engine/**` |
| `client/CLAUDE.md` | Thẻ luật vùng client | Auto-load khi sửa `client/**` |
| `server/CLAUDE.md` | Thẻ luật vùng server | Auto-load khi sửa `server/**` |
| `templates/**` | Skeleton copy-paste | Đọc khi tạo file cùng loại |
| `docs/templates/feature-module/CLAUDE.md` | Thẻ mẫu cho feature mới | Copy vào mỗi feature |

⚠️ Scope card đặt ở thư mục thật (`server/CLAUDE.md`...). Các thư mục này tạo ở [Phase 0](11-process/ROADMAP.md); scope card đã sẵn để Claude Code auto-load ngay khi có file code trong đó.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mô hình 3 lớp context; dừng ngay khi đủ | Accepted |
| 2 | **Enforcement (ArchUnit/lint) gánh luật** — AI không cần nhớ hết | Accepted |
| 3 | Scope card đặt ở **thư mục thật** để auto-load theo vùng | Accepted |
| 4 | Template để pattern-match thay vì suy luận từ prose | Accepted |
| 5 | Bảng routing task→doc để tránh over-read | Accepted |

## ADR cần tạo

Không có — đây là hướng dẫn quy trình.

## Tài liệu liên quan

- [/CLAUDE.md](../CLAUDE.md) — lớp 1
- [AI-WORKING-RULES.md](00-governance/AI-WORKING-RULES.md) — quy tắc đầy đủ
- [STATIC-ANALYSIS.md](10-quality/STATIC-ANALYSIS.md) · [CI-CD.md §4](07-infrastructure/CI-CD.md) — enforcement §2
- [README.md](README.md) — bản đồ toàn bộ docs

## Việc cần làm tiếp theo

1. Phase 0: cấu hình ArchUnit + static analysis để §2 (enforcement gánh luật) thật sự hoạt động.
2. Khi module thật xuất hiện, kiểm tra scope card auto-load đúng.
3. Cập nhật template khi pattern code thực tế ổn định (sau Phase 2).
