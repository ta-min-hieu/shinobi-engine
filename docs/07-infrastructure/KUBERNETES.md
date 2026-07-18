# KUBERNETES.md

| | |
|---|---|
| Status | 🚧 **STUB — chưa viết chi tiết, CÓ CHỦ ĐÍCH** |
| Blocked by | [ADR-014](../01-architecture/adr/README.md) (chưa tạo). *(ADR-010 đã chốt single-realm → càng ít lý do cần K8s ở MVP)* |
| Sẽ viết khi | [Roadmap Phase 9](../11-process/ROADMAP.md) — và **chỉ khi** ADR-014 kết luận là cần K8s |

---

## Vì sao tài liệu này cố tình chưa viết

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

## Bố cục dự kiến (TOC)

Khi ADR-014 kết luận **cần** K8s, tài liệu này sẽ gồm:

1. **Vì sao K8s** — dẫn chiếu ADR-014, số liệu chứng minh Docker Compose không đủ
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

## Câu hỏi phải trả lời trước khi viết (Definition of Ready)

- [x] **ADR-010** đã chốt: single-realm (multi-realm chỉ khi tải Phase 9 cần)
- [ ] **ADR-014** chốt: có thật sự cần K8s, hay Docker Compose + 1 VM là đủ?
- [ ] Có số liệu tải thật từ [Phase 9 — Optimization](../11-process/ROADMAP.md) chứng minh cần scale?
- [ ] Đội ngũ có ai vận hành được K8s không? (K8s vận hành sai còn tệ hơn không dùng)
- [ ] PostgreSQL/Redis tự host hay dùng managed service?
- [ ] Ngân sách hạ tầng bao nhiêu? (K8s tốn hơn 1 VM đáng kể)
- [ ] Rolling update chấp nhận đá người chơi không? Hay cần cửa sổ bảo trì?

---

## Kết quả hợp lệ: "không dùng Kubernetes"

⚠️ **Đây không phải thất bại.**

Rất nhiều game server thành công chạy trên vài VM với Docker Compose + systemd. Nếu ADR-014 kết luận:

> "1 VM + Docker Compose + backup tự động là đủ cho quy mô hiện tại"

→ thì tài liệu này sẽ được đánh dấu **Deprecated**, và đó là quyết định đúng theo Điều 9.

**Không** dùng K8s vì "công ty khác dùng" hay "nghe chuyên nghiệp hơn".

---

## Tài liệu liên quan

- [ARCHITECTURE.md §14](../01-architecture/ARCHITECTURE.md) — chiến lược scale bước 0→5
- [DOCKER.md](DOCKER.md) — thứ **đang dùng thật**
- [TECHNOLOGY-STACK.md §6](../01-architecture/TECHNOLOGY-STACK.md) — K8s được đánh dấu "chưa chốt"
- [SERVER.md §10](../05-server/SERVER.md) — graceful shutdown (ràng buộc cho §9 TOC)
- [ROADMAP.md](../11-process/ROADMAP.md) — Phase 9

## Việc cần làm tiếp theo

1. **Không làm gì với tài liệu này cho tới Phase 9 bắt đầu.**
2. Phase 9: thu thập số liệu tải thật.
3. Dựa trên số liệu → viết ADR-014 → nếu Accepted thì mới viết tài liệu này theo TOC ở trên.
