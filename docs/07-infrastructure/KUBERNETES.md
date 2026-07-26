# KUBERNETES.md

| | |
|---|---|
| Status | ⛔ **Deprecated** — [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) (2026-07-26) kết luận **không dùng Kubernetes**, dùng Docker Compose + VM đơn |
| Related | [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md), [DOCKER.md](DOCKER.md) — thứ đang dùng thật |

---

⚠️ **Kết luận cuối**: ADR-014 đã chốt **không dùng K8s** — lý do đầy đủ nằm ở ADR-014 (dự án cá nhân,
một người vận hành, single-realm theo ADR-010, chưa có số liệu tải nào chứng minh cần scale ngang).
Đúng như §"Kết quả hợp lệ" bên dưới đã dự đoán trước khi có kết luận — tài liệu này **không viết chi
tiết**, giữ nguyên STUB cho tới khi có lý do thật sự để revisit (xem Revisit Trigger ở ADR-014).

## Vì sao tài liệu này cố tình chưa viết (giữ lại — vẫn đúng bối cảnh lúc quyết định)

Viết chi tiết Kubernetes bây giờ sẽ là **bịa đặt**, vì:

1. **Chưa biết có cần K8s không.** [ARCHITECTURE.md §14](../01-architecture/ARCHITECTURE.md) quy định scale đi tuần tự bước 0→5. MVP ở **bước 0**: 1 process, 1 DB. K8s là công cụ cho bước 3+.

2. **Đã chốt single-realm** ([ADR-010](../01-architecture/adr/ADR-010-Realm-Model.md)) → deployment MVP chỉ 1 world instance, K8s (thiết kế cho scale ngang nhiều instance) chưa có lý do tồn tại. Chỉ xét lại nếu bật multi-realm sau Phase 9.

3. **Game server có trạng thái (stateful)** — không giống web service. K8s được thiết kế cho workload stateless dễ scale. Áp K8s lên game server cần suy nghĩ riêng, không copy pattern web:
   - Người chơi đang ở map nào gắn với instance nào → không thể load-balance tuỳ tiện.
   - Rolling update = đá người chơi ra giữa trận?
   - HPA scale down = instance nào chết? người chơi ở đó đi đâu?

4. **Điều 9 (Simplicity) + Điều 30 (đo trước)**: K8s cho 1 server nhỏ là over-engineering thuần tuý. [TECHNOLOGY-STACK.md §6](../01-architecture/TECHNOLOGY-STACK.md) đã ghi rõ điều này.

> Viết một tài liệu K8s đầy đủ ngay bây giờ sẽ vi phạm [AI-WORKING-RULES.md §14](../00-governance/AI-WORKING-RULES.md) (không bịa nội dung) và §13 (tài liệu chưa đủ dữ kiện → tạo stub, không bịa cho đủ file).

---

## Bố cục dự kiến (TOC) — chỉ dùng nếu ADR-014 bị supersede sau này

Nếu tương lai xuất hiện ADR mới `Supersedes ADR-014` kết luận **cần** K8s (theo Revisit Trigger ở
ADR-014), tài liệu này sẽ gồm:

1. **Vì sao K8s** — dẫn chiếu ADR supersede, số liệu chứng minh Docker Compose không đủ
2. **Deployment** — game server là stateful, dùng `StatefulSet` hay `Deployment`?
3. **Service** — làm sao route người chơi tới đúng world instance giữ map của họ
4. **ConfigMap** — cấu hình gameplay vs cấu hình hạ tầng
5. **Secret** — DB password, TLS cert (không bao giờ trong image — [DOCKER.md §4](DOCKER.md))
6. **Ingress** — TCP ingress cho game protocol (không phải HTTP → cần TCP LoadBalancer, không phải Ingress thường)
7. **HPA** — ⚠️ scale down một game server có người chơi bên trong nghĩa là gì?
8. **Rolling Update** — ⚠️ đá người chơi giữa trận? drain kết nối thế nào?
9. **Graceful shutdown** — `preStop` hook + `terminationGracePeriodSeconds` đủ dài cho [SERVER.md §10](../05-server/SERVER.md)
10. **PostgreSQL/Redis trong hay ngoài K8s?** — managed service thường tốt hơn
11. **Persistent Volume** — nếu tự host DB

---

## Câu hỏi phải trả lời trước khi viết (Definition of Ready) — đã trả lời, để lại làm hồ sơ

- [x] **ADR-010** đã chốt: single-realm (multi-realm chỉ khi tải Phase 9 cần)
- [x] **ADR-014** chốt: **không** cần K8s — Docker Compose + 1 VM là đủ
- [x] Không có số liệu tải nào chứng minh cần scale ngang tại thời điểm quyết định (chưa Phase 9 load test)
- [x] Không có ai ngoài chủ dự án vận hành hạ tầng → K8s vận hành sai còn tệ hơn không dùng
- [ ] PostgreSQL/Redis tự host hay dùng managed service — vẫn để ngỏ, không cần K8s để trả lời câu này
- [x] Ngân sách hạ tầng ưu tiên thấp nhất có thể ở giai đoạn chưa có người chơi thật
- [x] Rolling update qua Docker Compose chấp nhận cửa sổ bảo trì ngắn (không cần zero-downtime của K8s)

---

## Kết quả: "không dùng Kubernetes" — ✅ đã xảy ra đúng như dự đoán

Rất nhiều game server thành công chạy trên vài VM với Docker Compose + systemd. [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) kết luận:

> "Docker Compose trên 1 VM là đủ cho quy mô hiện tại — không dùng Kubernetes."

→ tài liệu này giữ **Deprecated**, đúng quyết định theo Điều 9 (Simplicity).

**Không** dùng K8s vì "công ty khác dùng" hay "nghe chuyên nghiệp hơn" — quyết định dựa trên: một người vận hành, single-realm, chưa có bằng chứng cần scale.

---

## Tài liệu liên quan

- [ARCHITECTURE.md §14](../01-architecture/ARCHITECTURE.md) — chiến lược scale bước 0→5
- [DOCKER.md](DOCKER.md) — thứ **đang dùng thật**
- [TECHNOLOGY-STACK.md §6](../01-architecture/TECHNOLOGY-STACK.md) — K8s được đánh dấu "chưa chốt"
- [SERVER.md §10](../05-server/SERVER.md) — graceful shutdown (ràng buộc cho §9 TOC)
- [ROADMAP.md](../11-process/ROADMAP.md) — Phase 9

## Việc cần làm tiếp theo

1. **Không làm gì với tài liệu này** trừ khi Revisit Trigger ở [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) xảy ra thật.
2. Nếu Revisit Trigger xảy ra (số liệu tải thật chứng minh 1 VM không đủ, hoặc multi-realm) → viết ADR mới `Supersedes ADR-014`, rồi mới viết tài liệu này theo TOC ở trên.
