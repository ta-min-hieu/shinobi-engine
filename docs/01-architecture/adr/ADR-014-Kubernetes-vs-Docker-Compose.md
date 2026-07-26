# ADR-014: Dùng Docker Compose + VM đơn, không dùng Kubernetes

| | |
|---|---|
| Status | ✅ **Accepted** |
| Date | 2026-07-26 |
| Deciders | Chủ dự án |
| Related | [ADR-010](ADR-010-Realm-Model.md), [KUBERNETES.md](../../07-infrastructure/KUBERNETES.md), [DOCKER.md](../../07-infrastructure/DOCKER.md), [CI-CD.md §8](../../07-infrastructure/CI-CD.md), [ROADMAP.md — Phase 9](../../11-process/ROADMAP.md) |

---

## Context

Phase 9 (Optimization) cần chốt deploy target production trước khi viết pipeline CI-CD §8 và trước khi
`KUBERNETES.md` (hiện là STUB có chủ đích) có thể được viết chi tiết hoặc bị bỏ hẳn.

KUBERNETES.md tự đặt sẵn Definition of Ready cho quyết định này:
- Có số liệu tải thật chứng minh cần scale ngang không?
- Đội ngũ có ai vận hành được K8s không?
- PostgreSQL/Redis tự host hay managed service?

Dự án là **cá nhân, một người vận hành duy nhất** (xác nhận lại ở ADR-013 — cùng tinh thần ưu tiên
đơn giản/miễn phí hơn "chuẩn" ngành khi không có đội ngũ vận hành chuyên trách). ADR-010 đã chốt
**single-realm** — không có nhu cầu route người chơi qua nhiều cụm/nhiều world instance phân tán theo
địa lý, giảm hẳn lý do kỹ thuật cần orchestration tự động của K8s. Tính tới thời điểm này (trước khi
load test Phase 9 chạy) **chưa có số liệu tải nào** cho thấy 1 VM không đủ sức phục vụ.

## Decision

Deploy production bằng **Docker Compose trên 1 VM** (có thể thêm VM riêng cho PostgreSQL/Redis nếu
cần tách biệt sau, vẫn không qua K8s). Không dùng Kubernetes (tự host hay managed).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **Docker Compose + VM đơn** | Đơn giản, đúng thứ đã dùng từ Dev/CI (`docker-compose.yml`/`docker-compose.ci.yml`, DOCKER.md §155); không cần học K8s; chi phí hạ tầng thấp nhất; khớp single-realm (ADR-010) — không có nhiều world instance cần orchestrate | Không tự động scale ngang, không tự phục hồi (self-healing) khi VM chết — phải tự dựng giám sát + restart | **Chọn** |
| Kubernetes tự host (vd. k3s) | Scale ngang tự động, self-healing, chuẩn ngành cho game server lớn | Vận hành sai còn tệ hơn không dùng (KUBERNETES.md tự cảnh báo); một người vận hành 1 cluster K8s solo là rủi ro vận hành cao, không tương xứng lợi ích khi tải thấp | Không chọn |
| Managed Kubernetes (GKE/EKS Autopilot...) | Giảm gánh vận hành so với tự host | Tốn phí hàng tháng ngay cả khi tải thấp/không tải — đi ngược tinh thần ưu tiên miễn phí đã xác lập ở ADR-005/ADR-013; vẫn chưa có bằng chứng cần scale | Không chọn |

## Consequences

**Tích cực:**
- Không phải học/vận hành thêm một hệ thống phức tạp (K8s) chỉ có một người dùng.
- Giữ nguyên hạ tầng Docker Compose đã dùng xuyên suốt Dev/CI — production chỉ thêm file compose riêng, không đổi công cụ.
- Chi phí hạ tầng thấp nhất có thể ở giai đoạn chưa có người chơi thật.

**Đánh đổi phải chấp nhận:**
- Không có scale ngang tự động — nếu tải vượt 1 VM, phải tự tay thêm VM + tự định tuyến (không có Service/Ingress của K8s lo hộ).
- Không có self-healing tự động — cần tự dựng giám sát (Phase 9 — Prometheus/Grafana/Alerts) + quy trình restart thủ công hoặc systemd.
- Nếu sau này cần multi-realm hoặc scale ngang thật sự, phải quay lại đánh giá (xem Revisit Trigger).

**Module bị ảnh hưởng:** `deploy/` (mới, khi viết), [CI-CD.md §8](../../07-infrastructure/CI-CD.md), [DOCKER.md](../../07-infrastructure/DOCKER.md) — `KUBERNETES.md` giữ nguyên trạng thái STUB, không viết chi tiết.

## Revisit Trigger

- Số liệu tải thật (Phase 9 load/soak test, hoặc tải người chơi thật sau Release) cho thấy 1 VM không đủ phục vụ dưới tải mục tiêu (tick p99 vượt ngân sách dù đã tối ưu code).
- ADR-010 (single-realm) bị xét lại và dự án chuyển sang multi-realm thật.
- Có thêm người vận hành hạ tầng chuyên trách tham gia dự án, thay đổi cân bằng chi phí/lợi ích vận hành K8s.
