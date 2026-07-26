# ADR-015: Micrometer cho Metrics, OpenTelemetry cho Tracing

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-26 |
| Deciders | Chủ dự án |
| Related | [ADR-009](ADR-009-Why-No-Spring-Boot.md), [OBSERVABILITY.md §2](../../07-infrastructure/OBSERVABILITY.md), [ROADMAP.md — Phase 9](../../11-process/ROADMAP.md), Điều 44 |

---

## Context

Phase 9 yêu cầu observability đầy đủ (Điều 44 — không có observability thì Production không hoàn
chỉnh): metrics (`kage.tick.duration`, `kage.gold.total`...), tracing, và log có cấu trúc. Cả
Micrometer và OpenTelemetry (OTel) đều làm được metrics lẫn tracing, nhưng dùng **cả hai framework
đầy đủ cho cùng một loại tín hiệu** (vd. cả hai đều export metrics) tạo ra hai bộ số liệu có thể lệch
nhau — không ai biết tin bộ nào (OBSERVABILITY.md §2). Cần một ranh giới rõ ràng: framework nào lo
loại tín hiệu nào.

Dự án không dùng Spring Boot (ADR-009) — Micrometer vẫn dùng độc lập được (không cần Spring), nhưng
Micrometer Tracing thực chất là lớp facade mỏng bọc quanh Brave hoặc OTel, không phải native.
Ngược lại, OTel Metrics SDK cho JVM hiện chưa trưởng thành/ổn định bằng Micrometer (ít registry
export sẵn cho Prometheus, ít nhà cung cấp JVM metrics binder có sẵn hơn).

## Decision

**Micrometer cho Metrics** (export Prometheus), **OpenTelemetry cho Tracing** (distributed trace).
Không dùng OpenTelemetry Metrics. Không dùng Micrometer Tracing.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Micrometer (Metrics) + OTel (Tracing), tách ranh giới rõ** | Mỗi framework dùng đúng thế mạnh đã trưởng thành (Micrometer: JVM/Prometheus metrics; OTel: tracing chuẩn ngành, tương thích nhiều backend); không có hai bộ số liệu chồng lấn gây lệch nhau | Phải tích hợp/học 2 API riêng biệt thay vì một, code instrumentation có 2 loại import khác nhau | **Chọn** |
| OpenTelemetry toàn bộ (Metrics + Tracing + Log) | Một vendor duy nhất, đơn giản hoá khái niệm, chuẩn hoá 1 SDK | OTel Metrics Java SDK hiện chưa trưởng thành bằng Micrometer cho nhu cầu JVM/Prometheus (OBSERVABILITY.md §2) — đánh đổi chất lượng số liệu lấy sự đơn giản không đáng | Không chọn |
| Micrometer toàn bộ (Metrics + Tracing qua Micrometer Tracing) | Một API duy nhất phía ứng dụng | Micrometer Tracing chỉ là facade bọc Brave/OTel bên dưới — thêm lớp gián tiếp không cần thiết, không tránh được việc vẫn phải có OTel (hoặc Brave) chạy thật phía sau | Không chọn |

## Consequences

**Tích cực:**
- Mỗi loại tín hiệu (metrics vs tracing) dùng đúng công cụ mạnh nhất cho việc đó, giảm rủi ro số liệu sai lệch giữa hai nguồn.
- Tích hợp thẳng với Prometheus + Grafana đã pre-approved sẵn ở TECHNOLOGY-STACK.md §7, không cần đánh giá dependency mới cho phần Metrics.

**Đánh đổi phải chấp nhận:**
- Hai API riêng biệt trong code (`io.micrometer.*` cho metrics, `io.opentelemetry.*` cho tracing) — engineer (hoặc AI) phải nhớ đúng ranh giới, không lẫn lộn dùng nhầm framework cho sai loại tín hiệu.
- Nếu muốn correlate metrics ↔ trace (vd. xem trace của request có tick duration cao), cần tự nối exemplar/context giữa hai hệ thay vì có sẵn trong một SDK.

**Module bị ảnh hưởng:** cross-cutting toàn bộ `feature-*`/`server-*` khi thêm metric hoặc span mới — theo đúng ranh giới đã nêu ở [OBSERVABILITY.md](../../07-infrastructure/OBSERVABILITY.md).

## Revisit Trigger

Khi OTel Metrics Java SDK đạt stable + feature-parity thật sự với Micrometer cho JVM metrics/Prometheus export (theo release notes chính thức của OpenTelemetry Java, không phải suy đoán) → cân nhắc gộp về một vendor duy nhất để giảm số API phải học.
