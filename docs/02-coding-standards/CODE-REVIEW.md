# CODE-REVIEW.md

**Checklist và nguyên tắc review code**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 38](../00-governance/KAGE-CONSTITUTION.md#điều-38-review-rules) |

---

## 1. Thứ tự ưu tiên khi review

Điều 38:

```
Correctness → Architecture → Maintainability → Performance → Style
```

**Không review chỉ để tranh luận sở thích cá nhân.**

Nếu ý kiến của bạn là về **style** và [CODING-STANDARDS.md](CODING-STANDARDS.md) đã quy định → trích dẫn điều khoản, không tranh luận. Nếu tài liệu **chưa** quy định → đó là việc **sửa tài liệu**, không phải chặn PR.

---

## 2. Checklist

### 2.1 Correctness (quan trọng nhất)

- [ ] Logic đúng với yêu cầu?
- [ ] Edge case: empty, null, 0, số âm, giá trị max?
- [ ] Off-by-one trong vòng lặp / index?
- [ ] Chia cho 0? Overflow `int`?
- [ ] Race condition nếu chạy đa luồng?
- [ ] Có test tái hiện được hành vi mới không?
- [ ] Nếu là fix bug: **có test FAIL trước khi fix** không?

### 2.2 Architecture 🔴 (nghiêm trọng nhất — báo trước)

- [ ] Có vi phạm dependency rule không? (gameplay import Netty/PostgreSQL/Redis → **chặn PR**)
- [ ] `domain/` có import `infrastructure/` không? → **chặn PR**
- [ ] Có import `com.badlogic.ashley.*` ngoài `engine-ecs` không? → **chặn PR** ([ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md))
- [ ] Feature có đọc bảng DB của feature khác không? → **chặn PR**
- [ ] Có thêm dependency mới không có trong [TECHNOLOGY-STACK.md](../01-architecture/TECHNOLOGY-STACK.md)? → cần ADR
- [ ] Thay đổi này có cần ADR không? ([AI-WORKING-RULES.md §6.2](../00-governance/AI-WORKING-RULES.md))
- [ ] **Client có tự quyết định gameplay không?** (Điều 15) → **chặn PR**
- [ ] Package theo feature hay theo loại? (Điều 24)

### 2.3 Maintainability

- [ ] Tên mô tả đúng vai trò? Không có `Manager`/`Helper`/`Util` vô nghĩa?
- [ ] Class có **một** trách nhiệm?
- [ ] Method < 50 dòng, < 3 indent, ≤ 4 tham số?
- [ ] Có duplicate code không?
- [ ] Comment giải thích **tại sao**, không phải **làm gì**?
- [ ] Magic number đã thành constant theo domain?
- [ ] Có abstraction thừa không? (interface chỉ có 1 impl và sẽ mãi như vậy → bỏ)

### 2.4 Performance (chỉ ở hot path)

- [ ] Có `new` object trong game loop không?
- [ ] Có boxing không? (`Map<Integer,...>`, `Integer` trong loop)
- [ ] Có reflection / annotation scan runtime không?
- [ ] Có stream tạo allocation trong hot path không?
- [ ] Có log mỗi frame không?
- [ ] Nếu tối ưu: **có số liệu benchmark không?** (Điều 30 — không có số = không merge)

⚠️ **Không review performance cho code ngoài hot path.** Yêu cầu tối ưu code khởi tạo/tool/test là làm hại Điều 17.

### 2.5 Thread Safety

- [ ] Có mutable state chia sẻ giữa thread không?
- [ ] Có block game tick không? (I/O, lock chờ lâu, `sleep`)
- [ ] Có block Netty EventLoop không?
- [ ] `synchronized` trên code chạy Virtual Thread? (pin — dùng `ReentrantLock`)
- [ ] Static mutable field? → gần như luôn sai

### 2.6 Memory

- [ ] `ByteBuf` (Netty) có được release không?
- [ ] Pool object có `free()` không? Có nguy cơ free hai lần không?
- [ ] Cache có giới hạn kích thước / TTL không? (cache không giới hạn = leak)
- [ ] Listener/callback có được huỷ đăng ký không?

### 2.7 Exception & Logging

- [ ] Có `catch (Exception)` tổng quát không?
- [ ] Có catch rỗng / nuốt exception không?
- [ ] Có `printStackTrace()` không?
- [ ] Exception có đủ context không?
- [ ] Log có context (characterId, sequenceId...) không?
- [ ] Log có chứa mật khẩu/token không? → **chặn PR**
- [ ] Dùng parameterized logging `{}`, không nối chuỗi?

### 2.8 Security

- [ ] Input từ client đã validate ở **server** chưa? (Điều 43)
- [ ] Có SQL injection không? (dùng PreparedStatement, không nối chuỗi SQL)
- [ ] Có rate-limit cho Command mới không?
- [ ] Có ghi audit log cho hành động nhạy cảm (trade, gold) không?

### 2.9 Backward Compatibility

- [ ] Có đổi field protobuf đang dùng không? → **chặn PR** (chỉ được thêm field mới)
- [ ] Có xoá/đổi public API không? → cần quy trình Deprecated → Migration → Removal (Điều 40)
- [ ] DB migration có rollback plan không?
- [ ] Client cũ còn chạy được không?

### 2.10 Documentation

- [ ] Public API có JavaDoc không?
- [ ] Nếu đổi kiến trúc: tài liệu đã cập nhật chưa? → **không merge nếu chưa** (Điều 40)
- [ ] Nếu có ADR: đã cập nhật [ADR Index](../01-architecture/adr/README.md) chưa?

---

## 3. Anti-pattern — thấy là chặn

| Anti-pattern | Vì sao tệ | Thay bằng |
|---|---|---|
| **God Class** (`PlayerManager` làm mọi thứ) | Không test được, không ai dám sửa | Tách theo trách nhiệm |
| **Anemic Domain Model** (domain chỉ getter/setter, logic ở service) | Mất lợi ích của Clean Architecture | Đặt quy tắc nghiệp vụ vào domain |
| **Service Locator** (`Registry.get(Foo.class)`) | Phụ thuộc ẩn, không thấy ở constructor | Constructor Injection |
| **Singleton mutable** | Global state, không test được | Truyền qua constructor |
| **Primitive Obsession** (`long characterId` khắp nơi) | Truyền nhầm id không ai phát hiện | Value Object (`record CharacterId(long value)`) — ở ngoài hot path |
| **Boolean parameter** (`spawn(true, false)`) | Đọc không hiểu gì | Enum hoặc tách 2 method |
| **Exception làm control flow** | Chậm, khó đọc | Trả về kết quả tường minh |
| **Comment code chết** | Nhiễu, git đã nhớ | Xoá |
| **Premature abstraction** (interface 1 impl "cho sau này") | YAGNI, thêm gián tiếp vô ích | Tạo interface khi có impl thứ 2 |
| **Premature optimization** (tối ưu không benchmark) | Làm code khó đọc, thường không nhanh hơn | Benchmark trước (Điều 30) |
| **Event dây chuyền >2 tầng** | Không ai debug nổi | Gọi Facade trực tiếp ([ADR-008](../01-architecture/adr/ADR-008-Why-EventDriven.md)) |
| **Component có method gameplay** | Phá ECS (Điều 14) | Chuyển logic vào System |

---

## 4. Code Smell — cảnh báo, không tự động chặn

| Smell | Ngưỡng | Hành động |
|---|---|---|
| Method dài | > 50 dòng | Hỏi vì sao, yêu cầu tách hoặc giải thích |
| Class lớn | > 400 dòng | Xem có nhiều trách nhiệm không |
| Nhiều tham số | > 4 | Yêu cầu Request Object |
| Indent sâu | > 3 | Yêu cầu early return / tách method |
| Nhiều điều kiện | `if` có > 3 `&&`/`\|\|` | Đặt tên cho điều kiện |
| Duplicate | Lặp 3 lần | Trích xuất — **2 lần thì chưa vội** (rule of three) |
| Comment quá nhiều | Cần comment giải thích từng dòng | Code chưa đủ rõ → đặt tên lại |

---

## 5. Complexity

| Chỉ số | Ngưỡng | Công cụ |
|---|---|---|
| Cyclomatic complexity | ≤ 10 mỗi method | PMD |
| Cognitive complexity | ≤ 15 mỗi method | SonarQube (nếu có) |
| Số field mỗi class | ≤ 10 | Checkstyle |
| Độ sâu kế thừa | ≤ 3 | PMD |

Vượt ngưỡng **không tự động sai** — nhưng phải giải thích được.

---

## 6. Cách viết comment review

| ✅ Tốt | ❌ Tệ |
|---|---|
| "Dòng 42: `list.get(i+1)` sẽ IndexOutOfBounds khi i = size-1. Test case: list 1 phần tử." | "Chỗ này sai." |
| "Vi phạm Điều 23: `server-world` không được import `io.netty.*`. Chuyển việc decode sang `server-gateway`." | "Không thích cách này." |
| "Tối ưu này cần benchmark theo Điều 30. Có số liệu không?" | "Cái này chậm." |
| "Cân nhắc đặt tên `remainingCooldownMs` — `t` không rõ đơn vị." | "Tên biến xấu." |

**Nguyên tắc**: nêu **vấn đề cụ thể** + **vì sao** + **gợi ý sửa**. Trích dẫn điều khoản khi có.

**Phân loại comment**:
- 🔴 **Blocking** — phải sửa mới merge (correctness, vi phạm kiến trúc, bảo mật).
- 🟡 **Should fix** — nên sửa, thảo luận được.
- 🟢 **Nit** — góp ý nhỏ, không chặn merge. Ghi rõ "nit:" để người viết biết không bắt buộc.

---

## 7. Pull Request

Điều 39.

**Một PR nên:**
- Giải quyết **một** mục tiêu cụ thể.
- Kích thước hợp lý (< ~400 dòng thay đổi là dễ review nhất).
- Có mô tả rõ ràng.
- Liên kết Issue hoặc ADR.

**Cấm:**
- Gộp nhiều thay đổi không liên quan.
- **Refactor + thêm feature trong cùng PR** (Điều 39) — không ai review nổi.
- PR 3000 dòng "vì làm luôn cho tiện".

---

## 8. Điều gì KHÔNG review

Để reviewer tập trung vào thứ quan trọng, **những thứ sau do máy lo**, không comment trong PR:

| Việc | Công cụ lo |
|---|---|
| Format, indent, whitespace | Spotless |
| Import order, unused import | Spotless / Checkstyle |
| Line length | Checkstyle |
| Bug pattern phổ biến | SpotBugs, Error Prone |
| Code smell cơ bản | PMD |

Nếu thấy mình đang comment về format → **cấu hình sai công cụ**, sửa công cụ chứ đừng comment.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Vi phạm kiến trúc (§2.2) là finding **chặn PR**, ưu tiên báo trước tiên | Accepted |
| 2 | Không review performance cho code ngoài hot path | Accepted |
| 3 | Tối ưu không có benchmark = không merge | Accepted |
| 4 | Rule of three cho duplicate — lặp 2 lần chưa vội trích xuất | Accepted |
| 5 | Phân loại comment 🔴/🟡/🟢 để người viết biết cái gì bắt buộc | Accepted |
| 6 | Style do máy lo, không comment trong PR | Accepted |
| 7 | PR ≤ ~400 dòng là mục tiêu, không phải luật cứng | Accepted |

## ADR cần tạo

Không có.

## Tài liệu liên quan

- [CODING-STANDARDS.md](CODING-STANDARDS.md) — quy tắc được checklist này kiểm tra
- [JAVA-GUIDELINES.md](JAVA-GUIDELINES.md) — chi tiết §2.4, §2.5, §2.6
- [AI-WORKING-RULES.md §12](../00-governance/AI-WORKING-RULES.md) — AI review thế nào
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — chi tiết §8
- [SECURITY.md](../08-security/SECURITY.md) — chi tiết §2.8

## Việc cần làm tiếp theo

1. Phase 0: cấu hình công cụ ở §8 — mỗi thứ máy bắt được là một thứ reviewer không phải nhớ.
2. Cân nhắc **ArchUnit test** để biến §2.2 từ "reviewer phải nhớ" thành "build fail tự động" — đây là mục có giá trị cao nhất trong toàn bộ checklist.
3. Tạo PR template trên GitHub gắn checklist §2.
