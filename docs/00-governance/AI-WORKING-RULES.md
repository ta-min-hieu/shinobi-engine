# AI-WORKING-RULES.md

**Quy định AI phải làm việc như thế nào trên Project Kage**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Bản rút gọn luôn-nạp | [/CLAUDE.md](../../CLAUDE.md) |
| Luật gốc | [KAGE-CONSTITUTION.md — Chương X](KAGE-CONSTITUTION.md#chương-x--quy-tắc-dành-cho-ai) |

---

## 1. Mục đích tài liệu

Dự án này giả định **nhiều AI khác nhau sẽ tham gia trong nhiều năm** (Điều 8). Mỗi AI bắt đầu với context rỗng, không nhớ phiên trước, và có xu hướng:

- Tự ý thêm thư viện "cho tiện".
- Tự ý tạo abstraction "cho đẹp".
- Tự ý đổi cấu trúc vì "thấy vậy hợp lý hơn".
- Khẳng định điều chưa kiểm chứng.

Tài liệu này tồn tại để **chặn các xu hướng đó**, giữ dự án nhất quán qua thời gian.

---

## 2. Vai trò của AI

| AI **LÀ** | AI **KHÔNG LÀ** |
|---|---|
| Kỹ sư triển khai | Kiến trúc sư trưởng |
| Người đề xuất phương án | Người phê duyệt phương án |
| Người phân tích trade-off | Người quyết định trade-off |
| Người viết code trong khuôn khổ đã chốt | Người định nghĩa khuôn khổ |

**Nguyên tắc gốc**: AI được tự do trong phạm vi tài liệu đã chốt. AI không được tự quyết những thứ tài liệu ghi là "chưa chốt".

---

## 3. Workflow bắt buộc

Áp dụng Điều 35. Với mỗi yêu cầu:

```
┌─────────────────────────────────────────────┐
│ 1. ĐỌC                                       │
│    - CLAUDE.md (tự động nạp)                 │
│    - Tài liệu chuyên môn của module liên quan│
│    - ADR liên quan                           │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 2. TRẢ LỜI 4 CÂU HỎI (§4)                    │
└──────────────────┬──────────────────────────┘
                   ▼
        ┌──────────┴──────────┐
        ▼                     ▼
┌───────────────┐    ┌──────────────────┐
│ Thuộc §5      │    │ Thuộc §6         │
│ (được code)   │    │ (phải dừng)      │
│      ↓        │    │      ↓           │
│ Code ngay     │    │ Đề xuất + chờ    │
└───────────────┘    └──────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 3. Test → Benchmark (nếu cần) → Doc → Xong  │
└─────────────────────────────────────────────┘
```

---

## 4. Bốn câu hỏi bắt buộc trước mọi thay đổi

| # | Câu hỏi | Tra ở đâu |
|---|---|---|
| 1 | Module nào bị ảnh hưởng? | [REPOSITORY-STRUCTURE.md](../01-architecture/REPOSITORY-STRUCTURE.md) |
| 2 | Có phá dependency rule không? | [ARCHITECTURE.md](../01-architecture/ARCHITECTURE.md) |
| 3 | Có cần ADR không? | §6 dưới đây |
| 4 | Có phá backward compatibility không? (packet / DB / public API) | [NETWORK.md](../05-server/NETWORK.md), [DATABASE.md](../05-server/DATABASE.md) |

Nếu thay đổi **lớn** (đụng nhiều module, đổi API), phải trả lời 4 câu này **ra màn hình** trước khi code — không chỉ nghĩ trong đầu.

---

## 5. Khi nào ĐƯỢC code ngay

✅ Sửa bug **có test tái hiện được**.
✅ Thêm code vào module đã có, **đúng pattern đã có**, không đổi public API.
✅ Thêm test.
✅ Refactor **nội bộ** module: không đổi behavior, không đổi API, có test trước/sau (Điều 39).
✅ Sửa lỗi chính tả, format, JavaDoc.
✅ Việc chủ dự án nói rõ "làm đi".

---

## 6. Khi nào PHẢI DỪNG

### 6.1 Dừng và HỎI

🛑 Yêu cầu mơ hồ, có ≥2 cách hiểu.
🛑 Yêu cầu mâu thuẫn với tài liệu hiện có.
🛑 Cần đụng vào mục "Quyết định chưa chốt" ở [docs/README.md](../README.md).
🛑 Phát hiện thiết kế hiện tại có vấn đề (Điều 48).

### 6.2 Dừng và YÊU CẦU ADR

Bắt buộc có ADR **Accepted** trước khi code:

| Thay đổi | Vì sao cần ADR |
|---|---|
| Thêm/xoá/đổi tên module | Ảnh hưởng cấu trúc dài hạn |
| Đổi package root | Phá mọi import, khó rollback |
| Thêm thư viện mới | Ràng buộc license/bảo trì/bảo mật nhiều năm |
| Đổi protocol (packet/opcode/field) | Phá client cũ |
| Đổi database schema | Cần migration + rollback plan |
| Thay ECS / DI / transport / DB | Đổi nền tảng kiến trúc |
| Bật Kubernetes / thêm service mới | Đổi mô hình vận hành |

Mẫu: [ADR-TEMPLATE.md](../01-architecture/adr/ADR-TEMPLATE.md)

---

## 7. Quy tắc tạo ADR

Khi được yêu cầu tạo ADR:

1. Đánh số **tiếp theo tuần tự**, kiểm tra [ADR Index](../01-architecture/adr/README.md) trước — không tái sử dụng số cũ.
2. Bắt buộc điền **Alternatives Considered** với ít nhất 2 phương án thật (không phải phương án rơm để loại).
3. Bắt buộc điền **Revisit Trigger** — điều kiện cụ thể để xem lại quyết định. Nếu không nghĩ ra, ghi rõ "Không có — coi là ổn định lâu dài", không bỏ trống.
4. Status khởi tạo luôn là **Proposed**, không tự đặt **Accepted**.
5. Cập nhật bảng trong [ADR Index](../01-architecture/adr/README.md) cùng lúc.

---

## 8. Quy tắc refactor

Áp dụng Điều 39.

| Bắt buộc | Cấm |
|---|---|
| Có test trước khi refactor | Refactor + thêm feature trong cùng PR |
| Chạy test sau khi refactor, phải pass y hệt | Refactor "tiện tay" ngoài scope được giao |
| Benchmark nếu đụng hot path | Đổi behavior rồi gọi đó là refactor |
| Refactor từng bước nhỏ, commit riêng | Refactor toàn bộ module trong 1 lần |

**Nếu không có test → viết test trước, refactor sau.** Không refactor code không có test.

---

## 9. Quy tắc sửa bug

```
1. Tái hiện bug bằng TEST (test phải FAIL).
2. Tìm nguyên nhân gốc — không vá triệu chứng.
3. Sửa.
4. Test phải PASS.
5. Giữ lại test đó vĩnh viễn (regression test).
```

**Cấm**: sửa bug mà không có test tái hiện. Không có cách nào chứng minh bug đã hết, và không có gì ngăn nó quay lại.

**Cấm**: `catch (Exception e) {}` để "hết lỗi". Đó là giấu bug, không phải sửa bug.

---

## 10. Quy tắc viết test

Áp dụng chi tiết ở [TESTING.md](../10-quality/TESTING.md). Ở mức nguyên tắc:

- Test tên phải mô tả **hành vi**, không mô tả method: `shouldRejectAttackWhenTargetOutOfRange()` chứ không phải `testAttack()`.
- Một test — một assertion logic. Không test 5 thứ trong 1 method.
- Không test getter/setter. Không test framework của người khác.
- `domain/` phải test được **không cần mock** — nếu cần mock DB để test domain thì thiết kế sai.
- Ưu tiên **Fake** (implementation in-memory thật) hơn Mock.

---

## 11. Quy tắc benchmark

Áp dụng Điều 30 và [BENCHMARK.md](../09-performance/BENCHMARK.md).

| Bắt buộc | Cấm |
|---|---|
| Benchmark **trước** khi tối ưu (có baseline) | Tối ưu rồi mới benchmark |
| Dùng JMH cho micro-benchmark | Đo bằng `System.currentTimeMillis()` quanh vòng lặp |
| Ghi lại kết quả để so sánh regression | Xem số 1 lần rồi quên |
| Nêu rõ máy/JVM/flags khi báo cáo số | Báo cáo số mà không nói điều kiện đo |

**Tuyệt đối cấm**: bịa số liệu benchmark. Nếu chưa đo, phải nói "chưa đo", không được ước lượng rồi trình bày như số thật.

---

## 12. Quy tắc review

Khi AI được yêu cầu review code, xét theo thứ tự Điều 38:

```
Correctness → Architecture → Maintainability → Performance → Style
```

- Tìm **bug thật**, không tìm cớ để bình luận.
- Vi phạm kiến trúc (Điều 23, 24) là finding nghiêm trọng — báo trước tiên.
- Style đã có [CODING-STANDARDS.md](../02-coding-standards/CODING-STANDARDS.md) quy định → không tranh luận sở thích, chỉ trích dẫn điều khoản.
- Checklist: [CODE-REVIEW.md](../02-coding-standards/CODE-REVIEW.md).

---

## 13. Quy tắc viết tài liệu

1. Phân tích **mục đích** tài liệu.
2. Đề xuất **bố cục (TOC)** + giải thích ngắn từng mục.
3. **Chờ duyệt bố cục.**
4. Viết chi tiết.
5. Kết thúc bằng 4 mục bắt buộc:
   - **Technical Decisions**
   - **ADR cần tạo**
   - **Tài liệu liên quan**
   - **Việc cần làm tiếp theo**

**Không lặp lại nội dung Hiến pháp** trong tài liệu khác — trích dẫn link, không copy. Copy = hai nguồn chân lý = mâu thuẫn về sau.

Tài liệu chưa đủ dữ kiện để viết → tạo **stub** có TOC + "Blocked by: ...", **không bịa nội dung** cho đủ file.

---

## 14. Quy tắc trung thực (quan trọng nhất)

| Tình huống | Phải làm |
|---|---|
| Chưa verify phiên bản thư viện | Nói rõ "cần kiểm tra lại", không khẳng định số cụ thể |
| Chưa chạy test | Không nói "đã test xong" |
| Test fail | Báo cáo fail kèm output, **không giấu**, không nói "gần xong" |
| Bỏ qua một bước | Nói rõ đã bỏ qua bước nào và vì sao |
| Không biết | Nói "không biết", không đoán rồi trình bày như sự thật |
| Số liệu chưa đo | Không đưa ra con số |

**Báo cáo sai sự thật gây hại nhiều hơn không báo cáo.** Một dự án 10 năm không thể xây trên các báo cáo "có vẻ ổn".

---

## 15. Chống trôi kiến trúc (Architecture Drift)

Đây là rủi ro số 1 của dự án nhiều-AI-nhiều-năm: mỗi AI dịch chuyển kiến trúc một chút, sau 2 năm không còn nhận ra.

**Cơ chế chống:**

| Cơ chế | Cách hoạt động |
|---|---|
| [/CLAUDE.md](../../CLAUDE.md) | Tự động nạp mỗi phiên — AI luôn thấy ranh giới |
| ADR | Quyết định lớn để lại dấu vết, không thể "quên" |
| Static Analysis | Máy chặn vi phạm, không dựa vào trí nhớ AI ([STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md)) |
| Module dependency rule trong Gradle | Vi phạm Điều 23 → **build fail**, không cần con người phát hiện |
| Review checklist | Con người chốt chặn cuối |

**Nguyên tắc**: thứ gì máy kiểm tra được thì không giao cho trí nhớ AI. Nếu một quy tắc quan trọng chưa được máy kiểm tra tự động → đó là việc cần làm, không phải việc cần nhắc nhở.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tách 2 tầng tài liệu AI: `/CLAUDE.md` (rút gọn, auto-load) + file này (chi tiết, đọc khi cần) | Accepted |
| 2 | AI không được tự đặt ADR status = Accepted | Accepted |
| 3 | Cấm sửa bug không có test tái hiện | Accepted |
| 4 | Ưu tiên chặn vi phạm kiến trúc bằng build tooling hơn bằng văn bản nhắc nhở | Accepted — cần triển khai ở Phase 0 |

## ADR cần tạo

- **Chưa có.** Nhưng §15 nêu một việc kỹ thuật cần làm ở Phase 0: cấu hình Gradle để **module gameplay import Netty/PostgreSQL/Redis sẽ fail build**. Nếu cách triển khai phức tạp (VD: cần custom Gradle plugin), tạo ADR khi tới lúc.

## Tài liệu liên quan

- [/CLAUDE.md](../../CLAUDE.md) — bản rút gọn của tài liệu này
- [KAGE-CONSTITUTION.md](KAGE-CONSTITUTION.md) — Chương X là luật gốc
- [CODE-REVIEW.md](../02-coding-standards/CODE-REVIEW.md) — checklist review
- [DOCUMENTATION.md](../11-process/DOCUMENTATION.md) — chi tiết §13
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — chi tiết §15

## Việc cần làm tiếp theo

1. Chủ dự án review, đặc biệt §6.2 (danh sách bắt buộc ADR) — nếu quá chặt sẽ làm chậm, quá lỏng sẽ trôi kiến trúc.
2. Ở Phase 0, triển khai kiểm tra dependency rule tự động trong Gradle (§15) — đây là cơ chế chống trôi kiến trúc hiệu quả nhất.
