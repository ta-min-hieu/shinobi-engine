# ADR-042: Gộp gói gửi ra — thôi `writeAndFlush` cho từng message

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-09-05) — chờ chủ dự án duyệt D1–D3. AI **không** tự Accept. |
| Date | 2026-09-05 |
| Deciders | Chủ dự án quyết; AI đo + soạn |
| Related | [BENCHMARK.md §5.1](../../09-performance/BENCHMARK.md) (toàn bộ số liệu dưới đây) · [ADR-027](ADR-027-TLS-Transport-Security.md) (TLS — nay là khoản chi CPU lớn nhất) · [ADR-019](ADR-019-Garbage-Collector.md) · [KAGE-CONSTITUTION Điều 30](../../00-governance/KAGE-CONSTITUTION.md) (benchmark trước, refactor sau) · [/CLAUDE.md §4](../../../CLAUDE.md) (đổi đường ghi gateway / protocol ⇒ dừng, viết ADR) · [/CLAUDE.md §9](../../../CLAUDE.md) |

---

## Context

### Số đo, không phải phỏng đoán

Đo lại capacity ngày 2026-09-05 (cấu hình production: ZGC + `leakDetection=simple`), N=1000, dùng
**`jdk.ThreadCPULoad`** — CPU **thật** theo từng luồng, không phải lấy mẫu:

| Nhóm luồng | CPU | Tỉ trọng |
|---|---|---|
| **`nioEventLoopGroup`** | **4,65-6,49 lõi** | **~90%** |
| `world-tick-pool` (toàn bộ mô phỏng game) | 0,13-0,25 lõi | ~3% |

Chia nhỏ CPU của Netty theo toàn bộ stack:

| Hạng mục | % của Netty | ~lõi |
|---|---|---|
| **TLS / mã hoá** | **39,5%** | ~2,13 |
| **Cấp phát buffer Netty** | 27,7% | ~1,49 |
| **Ghi socket / event loop** | 20,7% | ~1,12 |
| Protobuf | 0,9% | ~0,05 |

**Ba hạng mục đầu ≈ 88% CPU của Netty ≈ 79% CPU toàn server. Cả ba là chi phí THEO GÓI, không phải
theo byte.** Protobuf — thứ trực giác hay đổ lỗi — gần như bằng 0.

### Vì sao đắt đến vậy

`kage_packet_sent_total` ở N=1000, cửa sổ ổn định 100 giây: **5.976.044 gói ⇒ 59.760 gói/giây**.
Băng thông cùng lúc ~2870 KB/s ⇒ **trung bình 48 byte một gói**.

Cả hai đường gửi đều `writeAndFlush` cho **mỗi** message:
`ConnectedCharacterRegistry:531` và `GatewayChannelHandler:3174`.

Mỗi lần flush ⇒ **một TLS record riêng** (≈29 byte overhead, cộng dựng GCM + tính MAC) và **một
syscall `write()` riêng** — cho 48 byte dữ liệu.

Phép chia cho thấy bản chất: 2,13 lõi ÷ 59.760 gói/giây = **~36 µs CPU cho TLS mỗi gói**. AES-GCM có
AES-NI mã hoá ở mức GB/s, nên 48 byte lẽ ra tốn **nano**giây. ⇒ chi phí hầu như **toàn bộ là overhead
mỗi record**, không phải mã hoá dữ liệu. Cùng logic cho buffer (một `ByteBuf` pooled mỗi gói) và
syscall (một `write` mỗi gói).

### Vì sao bây giờ

Đây là **lần đầu dự án biết CPU thật sự đi đâu**. Trước đó §5.1 quy trách nhiệm nhầm hai lần, và
những đích tối ưu từng được đề xuất (tra Component qua `HashMap`, AoI O(n²), quét mục tiêu của
`AiSystem`) **đều nằm trong 3% của tick pool** — quy ra toàn server chỉ ~0,5% mỗi thứ. Làm chúng
không đổi được gì. Điều 30 nói "benchmark trước, refactor sau"; benchmark nay đã có và nó chỉ đúng
một chỗ.

---

## Decision

**D1.** Thêm `io.netty.handler.flush.FlushConsolidationHandler` vào pipeline gateway: giữ NGUYÊN mọi
message, chỉ **flush một lần mỗi vòng event loop** thay vì mỗi message. Nhiều message dồn vào **một**
TLS record và **một** syscall. Không đổi protocol, không đổi DB, không đổi message nào.

**D2.** Đổi hai chỗ gọi `ctx.writeAndFlush(...)` thành `ctx.write(...)` (D1 lo phần flush), và giữ
`writeAndFlush` cho những message **phải đi ngay** (phản hồi bắt tay, `ErrorResponse`, `PONG`).

**D3.** **Chưa** gộp nội dung AoI (một message mang vị trí của N thực thể). Đó là đổi protocol, lợi
ích lớn hơn (~19 lần số gói ở AoI 20Hz với ~19 hàng xóm) nhưng rủi ro cũng lớn hơn — tách thành đợt
sau, **sau khi** đo lại D1/D2 để biết còn cần bao nhiêu.

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. `FlushConsolidationHandler` (D1+D2)** (chọn) | Handler CÓ SẴN của Netty, không phải tự viết; không đụng protocol/DB/message; đánh cùng lúc cả ba khoản chi lớn nhất | Thêm độ trễ tối đa một vòng event loop; phải rà kỹ chỗ nào cần flush ngay | **Chọn.** Lợi ích lớn nhất trên mỗi đơn vị rủi ro, và đảo ngược được bằng cách gỡ 1 handler |
| B. Gộp nội dung AoI vào 1 message nhiều thực thể | Giảm số gói mạnh nhất (~19 lần) | Đổi protocol ⇒ đụng client, `protocolVersion`, test; rủi ro cao khi làm CÙNG LÚC với A | Không chọn **bây giờ** — D3 hoãn có chủ đích, làm sau khi đo A |
| C. Tắt TLS trong mạng nội bộ | Xoá thẳng 39,5% CPU của Netty | Phá [ADR-027](ADR-027-TLS-Transport-Security.md); client kết nối qua Internet, không phải mạng nội bộ | Không chọn — đổi bảo mật lấy hiệu năng, đúng thứ Điều 30 cấm |
| D. Đổi cipher suite (ChaCha20 thay AES-GCM) | Có thể rẻ hơn trên máy không AES-NI | Máy đo CÓ AES-NI, và chi phí là **theo record** chứ không theo byte ⇒ đổi cipher gần như không đổi gì | Không chọn — không nhắm vào nguyên nhân thật |
| E. Không làm gì, tăng phần cứng | Không rủi ro kỹ thuật | Ở N=2000 server đã ăn ~65% một máy 32 luồng, và chi phí mỗi người chơi đang TĂNG theo N | Không chọn — mua thời gian bằng tiền cho một đường cong siêu tuyến tính |

---

## Consequences

**Tích cực:**
- Nhắm đúng ~79% CPU của server thay vì ~3%.
- Không đổi protocol/DB/message ⇒ client cũ chạy nguyên.
- Gỡ ra dễ: bỏ một handler là về trạng thái cũ.

**Đánh đổi phải chấp nhận:**
- Thêm **tối đa một vòng event loop** độ trễ cho message không phải loại "gửi ngay". Ở N=1000 độ trễ
  gói p95 đang là 0,7ms nên biên còn rất rộng; nhưng **phải đo lại**, không được suy đoán.
- Phải quyết chỗ nào cần flush ngay. Sót một chỗ ⇒ message có thể nằm chờ lâu hơn mong đợi.

**Module bị ảnh hưởng:**
- `server/server-gateway` — pipeline + `ConnectedCharacterRegistry` + `GatewayChannelHandler`.

---

## Revisit Trigger

Đo lại sau khi thi hành: nếu **`nioEventLoopGroup` không giảm xuống dưới ~70% CPU** ở N=1000, thì
gộp flush là chưa đủ và phải mở D3 (gộp nội dung AoI). Ngược lại, nếu `world-tick-pool` vượt **20%**
CPU trong một lần đo sau này thì thứ tự ưu tiên ở ADR này đã hết hiệu lực, phải đo lại từ đầu.

---

## Technical Decisions

- Dùng handler có sẵn của Netty, **không tự viết cơ chế gộp flush** (Điều 9).
- Hoãn gộp nội dung AoI (D3) để không đổi hai thứ rủi ro cùng lúc — cùng tinh thần ADR-033 lát cắt 1.
- Không đụng TLS lẫn cipher suite: nguyên nhân là **số record**, không phải thuật toán.

## ADR cần tạo

Nếu sau khi đo D1/D2 vẫn chưa đủ ⇒ cần một ADR riêng cho **gộp nội dung AoI** (đổi protocol, tăng
`protocolVersion` hoặc thêm message mới theo luật additive của NETWORK.md §6).

## Tài liệu liên quan

- [BENCHMARK.md §5.1](../../09-performance/BENCHMARK.md) — mục "Nút thắt nằm ở đâu", toàn bộ số liệu.
- [OBSERVABILITY.md](../../08-observability/OBSERVABILITY.md) — `kage.tick.duration` chỉ mô tả ~3% CPU
  của server; cần thêm chỉ số cho đường gửi.
- [SERVER.md §6](../../05-server/SERVER.md) — AoI, nguồn sinh ra phần lớn 59.760 gói/giây.

## Việc cần làm tiếp theo

1. **Chủ dự án duyệt D1–D3.** Câu hỏi cụ thể: có chấp nhận đánh đổi "thêm tối đa một vòng event loop
   độ trễ" để lấy lại phần lớn của ~79% CPU không?
2. Sau khi Accept: thi hành D1+D2, rồi **đo lại đúng cùng kịch bản** (N=1000, cấu hình production,
   `jdk.ThreadCPULoad` + `kage_packet_sent_total`) và ghi kết quả vào §5.1 cạnh số hiện tại.
3. Cân nhắc thêm một metric cho **số gói mỗi giây trên mỗi kết nối** — hôm nay phải tự cộng
   `kage_packet_sent_total` theo opcode mới ra được con số này.
