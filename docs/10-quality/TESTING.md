# TESTING.md

**Chiến lược kiểm thử**

| | |
|---|---|
| Version | 1.1.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 18, 34](../00-governance/KAGE-CONSTITUTION.md) |

> Điều 18: **Nếu code khó test thì thiết kế đang có vấn đề** — không phải test framework có vấn đề.

---

## 1. Triết lý

| Nguyên tắc | Nghĩa là |
|---|---|
| Test phục vụ **tự tin khi refactor** | Không phải để đạt % coverage |
| **Không test getter/setter** | Không giá trị |
| **Không test framework của người khác** | Netty/libGDX/Ashley tự test rồi |
| `domain/` test **không cần mock** | Cần mock DB để test domain = **thiết kế sai** |

⚠️ **Coverage không phải mục tiêu.** 100% coverage với test vô nghĩa còn tệ hơn 60% với test tốt — vì nó tạo cảm giác an toàn giả.

---

## 2. Kim tự tháp theo tầng kiến trúc

| Tầng | Loại test | Công cụ | Tốc độ |
|---|---|---|---|
| `domain/` | Unit thuần, **không mock** | JUnit 5 + AssertJ | Rất nhanh (ms) |
| `application/` | Unit với **Fake** Port | JUnit 5 | Nhanh |
| `infrastructure/` | Integration, DB thật | **Testcontainers** | Chậm — CI |
| **ECS System** | Headless: World + System thật | JUnit 5 | Nhanh |
| **Protocol** | Round-trip encode→decode | JUnit 5 | Rất nhanh |
| `server-gateway` | Netty `EmbeddedChannel` | JUnit 5 | Vừa |
| **Kiến trúc** | ⚠️ **ArchUnit** | ArchUnit | Rất nhanh |
| End-to-end | 2 client giả + server + DB | Testcontainers | Chậm — định kỳ |

---

## 3. ArchUnit — loại test quan trọng nhất

Chi tiết: [CI-CD.md §4](../07-infrastructure/CI-CD.md).

**Vì sao quan trọng nhất**: đây là thứ duy nhất ngăn kiến trúc bị bào mòn qua nhiều năm và nhiều AI. Test bình thường bắt bug hôm nay. ArchUnit bắt **quyết định kiến trúc bị phá** — thứ mà 2 năm sau mới thấy hậu quả và lúc đó không sửa nổi.

Bắt buộc kiểm tra:
- Gameplay không import Netty/JDBC/Redis (Điều 23)
- Không import `com.badlogic.ashley.*` ngoài `engine-ecs` ([ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md))
- `domain/` không import `infrastructure/`
- `ServiceRegistry` không xuất hiện trong constructor System ([ENGINE.md §6](../03-engine/ENGINE.md))
- `engine-ecs` không phụ thuộc `engine-core` ([ENGINE.md §9](../03-engine/ENGINE.md))

---

## 4. Fake > Mock

```java
// ✅ Fake — implementation in-memory THẬT
final class FakeInventoryPort implements InventoryPort {
    private final Map<Long, Inventory> store = new HashMap<>();
    public Optional<Inventory> findByCharacter(long id) { return Optional.ofNullable(store.get(id)); }
    public void save(Transaction tx, Inventory inv) { store.put(inv.characterId(), inv); }
}

// ❌ Mock — chỉ ghi lại lời hứa
when(port.findByCharacter(1L)).thenReturn(Optional.of(inv));
```

| | Fake | Mock |
|---|---|---|
| Phát hiện lỗi tương tác thật | ✅ | ❌ |
| Test còn đúng sau khi đổi implementation | ✅ | ⚠️ Phải sửa mock |
| Đọc dễ | ✅ | ⚠️ `when/thenReturn` rối |

**Quy tắc mock** ([AI-WORKING-RULES.md §10](../00-governance/AI-WORKING-RULES.md)):
- ❌ **Không mock Domain object** (record thuần — dùng trực tiếp).
- ❌ **Không mock thứ không sở hữu** (`Connection`, `Channel`).
- ✅ Chỉ mock Port khi Fake quá phức tạp.

---

## 5. Test ECS System

System **hoàn toàn test được** — nó chỉ là hàm xử lý dữ liệu.

```java
@Test
void movementSystem_diChuyenTheoVanToc() {
    var engine = new Engine();                          // headless
    engine.addSystem(new MovementSystem());             // CHỈ 1 system — cô lập

    var e = engine.createEntity();
    e.add(new PositionComponent(0, 0));
    e.add(new VelocityComponent(10, 0));
    engine.addEntity(e);

    engine.update(1.0f);                                // 1 giây

    assertThat(posMapper.get(e).x).isEqualTo(10f);
}
```

⚠️ **Chỉ add System cần test.** Add toàn bộ System = không biết cái nào gây ra kết quả.

---

## 6. Test Protocol — backward compatibility

[NETWORK.md §6](../05-server/NETWORK.md).

| Test | Vì sao |
|---|---|
| Round-trip: encode → decode → bằng nhau | Cơ bản |
| ⚠️ **Decode payload CŨ bằng code MỚI** | Client cũ phải chạy được |

```
shared-protocol/src/test/resources/legacy-payloads/
├── v1.0/move-command.bin
└── v1.1/move-command.bin       ← KHÔNG BAO GIỜ XOÁ
```

⚠️ Mỗi minor version giữ lại payload nhị phân mẫu. Thêm field mới → test decode payload cũ **không throw**, field mới nhận default hợp lý.

---

## 7. Test bảo mật (thay DAST)

[OWASP.md §4](../08-security/OWASP.md) — DAST tiêu chuẩn vô dụng với protocol nhị phân. Thay bằng test tự viết:

| Test | Kiểm tra |
|---|---|
| **Authorization** | Client A gửi Command với `characterId` của B → **phải bị từ chối** ([SECURITY.md §4](../08-security/SECURITY.md)) |
| **Replay** | Gửi lại packet cũ → phải bị từ chối |
| **Rate limit** | Flood → phải bị chặn |
| **Fuzzing** | Packet dị dạng → server **không crash** |
| **Sandbox escape** | Script gọi `getClass()` → phải bị chặn ([SCRIPTING.md §4](../06-plugin/SCRIPTING.md)) |
| **Số âm/tràn** | `quantity = -1` → phải bị từ chối |

⚠️ **Đây là test có giá trị cao nhất về bảo mật** với kiến trúc này — cao hơn mọi công cụ quét mua sẵn.

---

## 8. Test Script

[SCRIPTING.md §8](../06-plugin/SCRIPTING.md).

| Cách | Chi tiết |
|---|---|
| Unit test script | Chạy với `ctx` Fake, assert kết quả |
| **Validate lúc khởi động** | Parse mọi script → lỗi cú pháp = fail fast |
| CI | Chạy toàn bộ script test |

---

## 9. Load / Soak Test

[BENCHMARK.md §5](../09-performance/BENCHMARK.md). Từ Phase 9.

- Giả lập hành vi **thật** (move+combat+chat), không ping trống.
- **Soak ≥ 2 giờ** — leak chỉ lộ theo thời gian.
- Đo: tick p99, GC pause, **heap sau major GC** (tăng đều = leak).

---

## 10. Đặt tên test

```java
// ❌
void testAttack() { }

// ✅ mô tả HÀNH VI
void shouldRejectAttack_whenTargetOutOfRange() { }
void khongChoTanCong_khiMucTieuNgoaiTam() { }   // tiếng Việt cũng OK cho test
```

**Một test — một hành vi.** Không test 5 thứ trong 1 method.

---

## 11. Test cho bug fix

[AI-WORKING-RULES.md §9](../00-governance/AI-WORKING-RULES.md) — **bắt buộc**:

```
1. Viết test tái hiện bug → test FAIL
2. Tìm nguyên nhân gốc (không vá triệu chứng)
3. Sửa
4. Test PASS
5. Giữ test vĩnh viễn (regression)
```

⚠️ **Cấm sửa bug không có test tái hiện.** Không có cách chứng minh bug đã hết, và không có gì ngăn nó quay lại.

---

## 12. CI Gating

[CI-CD.md §3](../07-infrastructure/CI-CD.md).

| Test | Chặn PR? |
|---|---|
| Unit (domain, application, ECS, protocol) | ✅ |
| **ArchUnit** | ✅ |
| Integration (Testcontainers) | ✅ (job song song) |
| Security test (§7) | ✅ |
| Benchmark | ❌ nightly |
| Load/Soak | ❌ trước mốc Roadmap |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **Coverage không phải mục tiêu** | Accepted |
| 2 | `domain/` test **không mock** — cần mock = thiết kế sai | Accepted |
| 3 | **Fake > Mock** | Accepted |
| 4 | **ArchUnit là loại test quan trọng nhất** — bảo vệ kiến trúc qua nhiều năm | Accepted |
| 5 | Test ECS: **chỉ add System cần test** | Accepted |
| 6 | **Giữ legacy payload vĩnh viễn** cho backward compat test | Accepted |
| 7 | **Test bảo mật tự viết thay DAST** | Accepted |
| 8 | **Cấm sửa bug không có test tái hiện** | Accepted |
| 9 | Integration test dùng **Testcontainers**, không DB của dev | Accepted |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| **ADR-011** | ArchUnit rules (§3) | 🔴 Chặn Phase 0 |

## Tài liệu liên quan

- [CI-CD.md](../07-infrastructure/CI-CD.md) · [STATIC-ANALYSIS.md](STATIC-ANALYSIS.md)
- [BENCHMARK.md](../09-performance/BENCHMARK.md) — §9
- [SECURITY.md](../08-security/SECURITY.md) · [OWASP.md](../08-security/OWASP.md) — §7
- [ECS.md](../03-engine/ECS.md) — §5
- [AI-WORKING-RULES.md §10](../00-governance/AI-WORKING-RULES.md)

## Việc cần làm tiếp theo

1. 🔴 **Phase 0: ArchUnit** (§3) — hạng mục giá trị cao nhất.
2. Phase 3: bộ test bảo mật §7 cùng lúc với protocol.
3. Phase 3: lập `legacy-payloads/` (§6) từ version đầu tiên.
