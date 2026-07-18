# DOCUMENTATION.md

**Quy trình viết và bảo trì tài liệu**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 40](../00-governance/KAGE-CONSTITUTION.md#điều-40-backward-compatibility) |

> **Code không thay thế tài liệu.** Code nói *làm gì*. Tài liệu nói *vì sao*.
> **Không merge PR thay đổi kiến trúc nếu chưa cập nhật tài liệu.**

---

## 1. Bốn loại tài liệu

| Loại | Trả lời | Ví dụ | Đổi thường xuyên? |
|---|---|---|---|
| **Hiến pháp** | Nguyên tắc bất biến | [KAGE-CONSTITUTION.md](../00-governance/KAGE-CONSTITUTION.md) | ❌ Hiếm |
| **ADR** | Vì sao quyết định vậy | [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) | ❌ **Không bao giờ sửa** — chỉ supersede |
| **Chuyên môn** | Thiết kế hiện tại | [ARCHITECTURE.md](../01-architecture/ARCHITECTURE.md) | ✅ Theo code |
| **JavaDoc** | Dùng API này thế nào | Trong code | ✅ Theo code |

**Nguyên tắc phân định**: câu nào **có thể sai sau 6 tháng vì công nghệ đổi** → không thuộc Hiến pháp.

---

## 2. Quy trình viết tài liệu mới

Bắt buộc ([AI-WORKING-RULES.md §13](../00-governance/AI-WORKING-RULES.md)):

```
1. Phân tích MỤC ĐÍCH tài liệu
2. Đề xuất BỐ CỤC (TOC) + giải thích ngắn từng mục
3. ⚠️ CHỜ DUYỆT BỐ CỤC
4. Viết chi tiết
5. Kết thúc bằng 4 mục bắt buộc (§4)
```

⚠️ **Bước 3 tồn tại vì**: viết 300 dòng rồi phát hiện sai bố cục = vứt cả 300 dòng. Duyệt TOC tốn 5 phút, tiết kiệm hàng giờ.

---

## 3. Nguyên tắc nội dung

| Nguyên tắc | Vì sao |
|---|---|
| **Không lặp lại Hiến pháp** — trích dẫn link | Copy = hai nguồn chân lý = mâu thuẫn về sau |
| **Trung thực về giới hạn** | "Ashley không cache-friendly như DOD hứa" — không tự lừa |
| **Đánh dấu thứ chưa verify** | ⏳ / ⚠️ — số liệu chưa đo phải nói rõ |
| **Chưa đủ dữ kiện → stub, không bịa** | [KUBERNETES.md](../07-infrastructure/KUBERNETES.md) là ví dụ |
| **Ghi cả thứ đã TỪ CHỐI** | Không ai đề xuất lại sau 6 tháng |

⚠️ **Bịa nội dung cho đủ file là vi phạm nghiêm trọng** ([AI-WORKING-RULES.md §14](../00-governance/AI-WORKING-RULES.md)). Tài liệu bịa tệ hơn không có tài liệu — vì người ta **tin** nó.

---

## 4. Bốn mục bắt buộc cuối tài liệu

Mọi tài liệu chuyên môn **phải** kết thúc bằng:

```markdown
## Technical Decisions
| # | Quyết định | Lý do | Trạng thái |

## ADR cần tạo
| ADR | Nội dung | Ưu tiên |

## Tài liệu liên quan
- link...

## Việc cần làm tiếp theo
1. ...
```

| Mục | Vì sao |
|---|---|
| **Technical Decisions** | Người đọc thấy ngay cái gì đã chốt, cái gì còn treo |
| **ADR cần tạo** | Không để quyết định trôi vào quên lãng |
| **Tài liệu liên quan** | Chống tài liệu mồ côi |
| **Việc cần làm tiếp theo** | Tài liệu không phải điểm cuối |

---

## 5. Cấu trúc thư mục

```
docs/
├── README.md              ← index, thứ tự đọc, quyết định đang treo
├── GLOSSARY.md            ← xuyên suốt
├── 00-governance/         ← luật
├── 01-architecture/       ← kiến trúc + adr/
├── 02-coding-standards/
├── 03-engine/
├── 04-client/
├── 05-server/
├── 06-plugin/
├── 07-infrastructure/
├── 08-security/
├── 09-performance/
├── 10-quality/
├── 11-process/
├── 12-devtools/
└── 13-game-design/        ← GDD (bổ sung ngoài taxonomy gốc)
```

**Thêm tài liệu mới**: `NN-category/TÊN-VIẾT-HOA.md` + thêm dòng vào [README.md](../README.md).

---

## 6. Diagram

| Loại | Công cụ |
|---|---|
| Kiến trúc, luồng, ERD, state | **Mermaid** trong markdown |
| Ảnh chụp | `docs/images/` |

⚠️ **Mermaid > ảnh** vì: render trực tiếp trên GitHub/IDE · **diff được** trong git · sửa không cần công cụ ngoài · không bao giờ lệch với tài liệu.

❌ **Không dùng**: draw.io export PNG (không diff được, sẽ lỗi thời), ảnh chụp whiteboard.

---

## 7. Tài liệu và Code — đồng bộ

| Thay đổi | Tài liệu phải |
|---|---|
| Đổi kiến trúc | ✅ Cập nhật **cùng PR** — Điều 40 |
| Thêm module | ✅ [REPOSITORY-STRUCTURE.md §2](../01-architecture/REPOSITORY-STRUCTURE.md) |
| Thêm dependency | ✅ [TECHNOLOGY-STACK.md §11](../01-architecture/TECHNOLOGY-STACK.md) |
| Đổi protocol | ✅ [NETWORK.md](../05-server/NETWORK.md) |
| Đổi schema | ✅ [DATABASE.md](../05-server/DATABASE.md) |
| Thêm thuật ngữ | ✅ [GLOSSARY.md](../GLOSSARY.md) |
| Sửa bug nội bộ | ❌ Không cần |

⚠️ **Cùng PR, không phải "PR sau"**. "Sẽ cập nhật tài liệu sau" = không bao giờ.

---

## 8. JavaDoc

[CODING-STANDARDS.md §16](../02-coding-standards/CODING-STANDARDS.md).

| Bắt buộc | Không bắt buộc |
|---|---|
| Mọi thứ trong `api/` | Private method |
| Public method module khác gọi | Method hiển nhiên |
| Plugin API | Getter/setter |

**JavaDoc nói TẠI SAO và HỢP ĐỒNG**, không nói lại code:

```java
// ❌
/** Thêm item. @param stack item */

// ✅
/**
 * Thêm item vào túi. Nếu túi đầy, item KHÔNG được thêm và
 * trả về {@link AddItemResult#full()} — không ném exception,
 * vì túi đầy là trạng thái bình thường của gameplay.
 */
```

---

## 9. RFC — chưa dùng

| | ADR | RFC |
|---|---|---|
| Khi nào | Quyết định **đã đưa ra** | Đề xuất **đang thảo luận** |
| Trạng thái | Accepted/Proposed | Draft → thảo luận → ADR |

**Quyết định**: **chưa dùng RFC.** Đội ngũ nhỏ → thảo luận trực tiếp rồi viết ADR đủ. Thêm RFC = thêm quy trình cho nhu cầu chưa có (Điều 9).

Dùng RFC nếu: đội ngũ lớn nhiều bên liên quan, hoặc cần thảo luận công khai với cộng đồng.

---

## 10. Bảo trì

| Việc | Khi nào |
|---|---|
| Rà tài liệu vs code | Cuối mỗi Phase ([ROADMAP.md](ROADMAP.md)) |
| Cập nhật số liệu chưa verify | Sau khi đo (client: Phase 3; server: Phase 9) |
| Rà "quyết định đang treo" | Mỗi Phase |
| ⚠️ **Đổi ROADMAP (đánh số Phase)** | **Cùng PR đó**: grep toàn bộ docs tìm `Phase [0-9]` và đối soát mọi gate với [ADR Index](../01-architecture/adr/README.md) — bài học từ lần drift 2026-07 |
| Đánh dấu Deprecated | Khi tài liệu lỗi thời |

⚠️ **Tài liệu sai tệ hơn không có tài liệu.** Tài liệu lỗi thời → đánh dấu `Status: Deprecated` + lý do, **không xoá** (người ta có thể còn link tới).

---

## 11. Ngôn ngữ

| | Ngôn ngữ |
|---|---|
| Tài liệu | **Tiếng Việt** |
| Code, class, package, biến | **Tiếng Anh** |
| Commit message | Tiếng Việt (⏳ đề xuất — [GIT-WORKFLOW.md §3](GIT-WORKFLOW.md)) |
| Thuật ngữ | Tra [GLOSSARY.md](../GLOSSARY.md) — **không tự dịch khác đi** |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **TOC duyệt trước, viết chi tiết sau** | Accepted |
| 2 | **Không lặp Hiến pháp** — trích dẫn link | Accepted |
| 3 | **4 mục bắt buộc** cuối mỗi tài liệu | Accepted |
| 4 | **Mermaid, không ảnh** | Accepted |
| 5 | Tài liệu cập nhật **cùng PR** đổi kiến trúc | Accepted |
| 6 | **Chưa đủ dữ kiện → stub, không bịa** | Accepted |
| 7 | **Ghi cả thứ đã từ chối** | Accepted |
| 8 | **Chưa dùng RFC** — ADR đủ cho đội ngũ nhỏ | Accepted |
| 9 | Tài liệu lỗi thời → Deprecated, **không xoá** | Accepted |
| 10 | Tài liệu tiếng Việt, code tiếng Anh | Accepted |

## ADR cần tạo

Không có.

## Tài liệu liên quan

- [AI-WORKING-RULES.md §13](../00-governance/AI-WORKING-RULES.md) — AI viết tài liệu
- [KAGE-CONSTITUTION.md](../00-governance/KAGE-CONSTITUTION.md) — Điều 40
- [ADR Index](../01-architecture/adr/README.md) — quy tắc ADR
- [GIT-WORKFLOW.md](GIT-WORKFLOW.md) — PR template
- [GLOSSARY.md](../GLOSSARY.md)

## Việc cần làm tiếp theo

1. Cuối mỗi Phase: rà §10.
2. Phase 9: cập nhật **mọi số liệu đang đánh dấu ⏳ chưa verify** (client đo sớm hơn ở Phase 3).
3. Thêm gate CI: PR đổi `**/api/**` mà không đổi `docs/**` → cảnh báo.
