# ADR-027: TLS cho kết nối game (server-gateway ↔ client) — self-signed + pinning cho Closed Beta

| | |
|---|---|
| Status | ✅ **Accepted** (2026-07-31, chủ dự án xác nhận qua chat) |
| Date | 2026-07-31 |
| Deciders | AI (Claude) đề xuất; chủ dự án chốt |
| Related | [NETWORK.md §2, §14](../../05-server/NETWORK.md), [SECURITY.md §3, §13](../../08-security/SECURITY.md), [ADR-003](ADR-003-Why-Netty.md) (Netty, server-side), [client/CLAUDE.md](../../../client/CLAUDE.md) (Điều 23 — client không import Netty), [GIT-WORKFLOW.md](../../11-process/GIT-WORKFLOW.md) (Closed Beta trước Production), [DOCKER.md §6](../../07-infrastructure/DOCKER.md) (`docker-compose.prod.yml`), [BUILD.md](../../../BUILD.md) (phát hiện gốc) |

---

## Context

NETWORK.md và SECURITY.md đã **Accepted** nguyên tắc "TLS chuẩn, bắt buộc ở Production, không tự viết
mã hoá" từ Phase 2 (NETWORK.md Technical Decision #8, SECURITY.md §13) — nhưng đó là quyết định Ở MỨC
NGUYÊN TẮC ("dùng TLS chuẩn"), chưa từng có ADR quyết định CƠ CHẾ CỤ THỂ (cấp chứng chỉ bằng cách nào,
thư viện nào, client tin chứng chỉ ra sao). Khoảng trống này bị phát hiện thật khi wire SpotBugs
(BUILD.md Bước 3, 2026-07-30): "TLS chưa từng được code dù NETWORK.md ghi 'TLS bắt buộc ở Production'
là Accepted — suppress tạm, CHƯA implement (việc lớn riêng, cần ADR)". Khoảng trống này chặn thật:
`docker-compose.prod.yml` (viết 2026-07-31) phải cảnh báo tường minh "KHÔNG mở cổng 7777 ra Internet
công khai cho tới khi TLS xong".

**Ràng buộc kiến trúc quan trọng nhất khi chọn cơ chế**: `client/CLAUDE.md` (Điều 23, ArchUnit thực
thi) cấm client import Netty — `GameConnection` (client-core) dùng `java.net.Socket` THUẦN, không
Netty. Server-gateway thì ngược lại — Netty là nền tảng I/O đã chọn (ADR-003). Nghĩa là 2 phía kết
nối BẮT BUỘC dùng 2 thư viện TLS khác nhau (Netty `SslHandler` phía server, JDK `SSLSocket` phía
client) — đây không phải vấn đề, TLS là giao thức chuẩn ở tầng dây, 2 implementation khác nhau vẫn
bắt tay được bình thường, nhưng là điều dễ bị hiểu lầm/làm sai nếu không ghi rõ (vd. ai đó thử thêm
Netty vào client cho "đồng bộ" sẽ vi phạm Điều 23).

GIT-WORKFLOW.md: "Không tính năng nào lên Production chưa qua ≥1 vòng Closed Beta". Closed Beta của
dự án cá nhân này chạy được trong mạng riêng/VPN (không cần domain public, xem cảnh báo trong
`docker-compose.prod.yml`) — nghĩa là **không bắt buộc chứng chỉ ký bởi CA công khai (Let's Encrypt)
ngay bây giờ** để mở Closed Beta, nhưng TLS vẫn phải THẬT SỰ chạy được (không phải giữ nguyên
plaintext rồi "để dành Production mới làm" — làm vậy thì TLS chưa từng được test thật trước khi cần,
đúng loại rủi ro Closed Beta được thiết kế để tránh).

## Decision

Dùng **TLS in-process trong chính `server-gateway`/`GameConnection`** (không reverse-proxy), chứng
chỉ **self-signed + client pin chính chứng chỉ đó**, cho tới khi cần Production công khai thật thì
đổi sang CA-signed (xem Revisit Trigger).

Cụ thể:

1. **Server-gateway**: `SslHandler` (từ `netty-all` đã có sẵn — KHÔNG thêm dependency mới) là handler
   ĐẦU TIÊN trong pipeline (trước `LengthFieldBasedFrameDecoder`/`LengthFieldPrepender`). Dùng JDK SSL
   provider mặc định (KHÔNG `netty-tcnative`/OpenSSL — xem Alternative C). Cert+key nạp từ file (path
   qua env var `KAGE_TLS_CERT_PATH`/`KAGE_TLS_KEY_PATH`), KHÔNG bake vào image (DOCKER.md Technical
   Decision #4 "không secret trong image" — mount qua volume, giống cách `.env` không vào image).
2. **Mặc định BẬT** — cần flag tường minh để tắt (`KAGE_TLS_ENABLED=false`), đúng NETWORK.md §2 đã
   Accepted "Dev có thể tắt qua flag tường minh, không mặc định tắt".
3. **Client** (`GameConnection`): đổi `Socket` → `SSLSocket` (`javax.net.ssl`, JDK có sẵn, KHÔNG
   Netty — tôn trọng Điều 23). `TrustManager` PIN đúng chứng chỉ server (bundle 1 file `.pem` làm
   resource client, KHÔNG dùng truststore hệ điều hành — self-signed cert vốn dĩ fail validate mặc
   định). Tắt hostname verification MỘT CÁCH CÓ CHỦ ĐÍCH — ghi rõ trong code: pin đúng public key/cert
   là điều kiện MẠNH HƠN hostname+CA chain cho 1 cặp client-server đóng (không phải browser nói chuyện
   với domain bất kỳ), miễn pin đúng full cert hoặc SPKI hash, không phải chỉ tắt verify rồi không bù
   lại bằng gì.
4. **Cấp chứng chỉ**: self-signed, tạo thủ công/script 1 lần mỗi môi trường (dev/Closed Beta), rotate
   thủ công (chưa có ACME/auto-renew). Chấp nhận được vì Closed Beta audience nhỏ, đã biết trước, và
   client vốn đã cần build mới mỗi lần cập nhật ở giai đoạn beta.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. In-process Netty TLS (server) + JDK `SSLSocket` pin cert (client), self-signed** | 0 dependency mới (`netty-all` đã có TLS handler sẵn, JDK có `SSLSocket` sẵn); không thêm process/component nào (đúng tinh thần ADR-014 "VM đơn, tối thiểu"); chạy được ngay, không cần domain public — đủ mở Closed Beta trong VPN/mạng riêng ngay | Rotate cert cần build lại client (chấp nhận được ở nhịp beta); chưa có hot-reload `SslContext` (rotate cần restart server) | **Chọn** — unblock nhanh nhất, đúng phạm vi Closed Beta, không vay mượn phức tạp của giai đoạn Production công khai |
| **B. Reverse-proxy TLS termination (nginx/HAProxy `stream{}` trước cổng 7777, ACME/certbot tự renew, forward TCP thuần vào Netty)** | Cert automation trưởng thành (certbot là chuẩn công nghiệp); JVM không cần biết gì về TLS, tách bạch lifecycle cert khỏi deploy app | Thêm 1 **component hạ tầng mới** ngoài TECHNOLOGY-STACK.md → tự nó cần dừng lại hỏi/ADR riêng (CLAUDE.md §4); thêm 1 hop mạng + 1 container phải vận hành/giám sát trên "VM đơn" mà ADR-014 cố tình chọn để tối giản; đòi domain public ngay (certbot HTTP-01/DNS-01 challenge) — Closed Beta hiện chưa cần | **Không chọn cho lát cắt này** — ứng viên MẠNH cho lúc cần CA-signed công khai thật (xem Revisit Trigger), lúc đó nên có ADR riêng đánh giá lại, không quyết trước khi có nhu cầu thật (Điều 9) |
| **C. `netty-tcnative` (OpenSSL native engine) thay JDK SSL provider** | Throughput TLS cao hơn JDK SSLEngine ở tải rất lớn (benchmark công khai của Netty) | Thêm 1 dependency **native** mới ngoài TECHNOLOGY-STACK.md (cần hỏi/ADR riêng); rủi ro tương thích binary đa nền tảng (máy dev Windows hiện tại + container Linux production) cao hơn hẳn JDK provider thuần Java; TLS chỉ tốn CPU lúc HANDSHAKE (1 lần/kết nối, không phải mỗi tick) — capacity hiện tại (N≤2000, BENCHMARK.md §5) chưa có bằng chứng cần thêm throughput mã hoá đó | **Không chọn** — Điều 30 (đo trước khi tối ưu), chưa có số liệu cho thấy JDK provider là nút thắt |
| **D. Giữ nguyên plaintext, hoãn TLS tới lúc deploy Production công khai thật** | 0 effort ngay bây giờ | Mâu thuẫn trực tiếp SECURITY.md §13 "TLS bắt buộc Production" (Accepted) VÀ GIT-WORKFLOW.md "không tính năng nào lên Production chưa qua ≥1 vòng Closed Beta" — nếu Closed Beta chạy plaintext, TLS chưa từng được kiểm chứng thật (hiệu năng, tương thích client-server) trước lúc BẮT BUỘC cần cho Production, rủi ro phát hiện vấn đề quá trễ | **Không chọn** |

## Consequences

**Tích cực:**
- Mở khoá `docker-compose.prod.yml` cho Closed Beta trong mạng riêng/VPN (cảnh báo hiện tại trong file
  đó sẽ được gỡ khi implement xong ADR này).
- 0 dependency mới — cả `netty-all` (server) lẫn `javax.net.ssl` (client, JDK chuẩn) đều đã sẵn có
  trong stack hiện tại.
- TLS được kiểm chứng THẬT trong Closed Beta trước khi bắt buộc cho Production (đúng tinh thần
  GIT-WORKFLOW.md, tránh phát hiện vấn đề tương thích/hiệu năng quá trễ).
- Tôn trọng đúng ranh giới Điều 23 (client không Netty) — không có server-only dependency nào bị kéo
  nhầm sang client.

**Đánh đổi phải chấp nhận:**
- Rotate cert cần build lại + phát hành lại client (KHÔNG chấp nhận được lâu dài ở quy mô Production
  công khai — ghi rõ ở Revisit Trigger, không phải thiếu sót bị bỏ quên).
- Chưa có hot-reload `SslContext` phía server — rotate cert cần restart (chấp nhận được ở nhịp rotate
  thủ công hiếm của beta).
- Pinning bảo vệ khỏi MITM tầng mạng, KHÔNG bảo vệ khỏi client bị reverse-engineer hoàn toàn (kẻ tấn
  công trích xuất được logic pin + tự dựng MITM riêng nếu cũng có private key server) — chấp nhận
  được, cùng mô hình threat đã có sẵn: server không bao giờ tin client hoàn toàn (Điều 15), đây không
  phải lớp bảo vệ MỚI chống client bị xâm phạm, chỉ chống nghe lén/giả mạo trên đường truyền.

**Module bị ảnh hưởng:**
- `server/server-gateway` — thêm `SslHandler` vào pipeline, đọc `KAGE_TLS_CERT_PATH`/`KAGE_TLS_KEY_PATH`/`KAGE_TLS_ENABLED`.
- `client/client-core` (`GameConnection`) — `Socket` → `SSLSocket`, `TrustManager` pin cert, bundle resource cert.
- `deploy/docker` — `server.Dockerfile`/`docker-compose.prod.yml` cần volume mount cho cert/key (KHÔNG vào image), gỡ cảnh báo TLS sau khi implement xong.

## Revisit Trigger

- **Trước khi mở Production công khai thật** (ngoài VPN/mạng riêng, người chơi không phải danh sách
  đóng đã biết trước) → bắt buộc đổi cert sang CA-signed (Let's Encrypt/ACME), bỏ pinning phía client
  (dùng trust-store mặc định JDK + hostname verification chuẩn). Lúc đó đánh giá lại có cần Phương án
  B (reverse-proxy) không dựa trên kinh nghiệm vận hành thật từ Closed Beta — nên viết ADR riêng, không
  quyết trước.
- Nếu benchmark THẬT đo được TLS handshake hoặc mã hoá record chiếm phần đáng kể CPU ở N mục tiêu
  (chưa có ngưỡng cụ thể — phải đo trước khi định con số, Điều 30) → cân nhắc lại Phương án C
  (`netty-tcnative`).
- Nếu tần suất rotate cert thủ công trở thành gánh nặng vận hành THẬT (không chỉ lý thuyết) → làm cơ
  chế reload `SslContext` nóng, không cần restart server.
- Không có trigger nào khác — nếu không rơi vào 3 trường hợp trên, cơ chế self-signed + pinning này
  coi là ổn định cho suốt giai đoạn Closed/Open Beta.
