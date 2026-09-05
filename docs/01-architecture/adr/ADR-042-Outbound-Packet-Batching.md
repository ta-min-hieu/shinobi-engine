# ADR-042: Giảm SỐ LƯỢNG message gửi ra (gộp flush đã thử và bị số đo bác bỏ)

| | |
|---|---|
| Status | 🟡 **Proposed** (2026-09-05) — **D1 đã dựng thử và ĐO: gần như không ăn thua, xem §"Đã thử D1". Đề xuất nay xoay sang D3.** Chờ chủ dự án duyệt. AI **không** tự Accept. |
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

## Đã thử D1 và ĐO — kết quả: KHÔNG ăn thua (2026-09-05)

Thay vì đề xuất suông, D1 đã được **dựng thử thật** (`FlushConsolidationHandler(256, true)` đặt ngay
SAU `SslHandler`) và đo bằng đúng kịch bản/cấu hình của baseline:

| | Tổng CPU | `nioEventLoopGroup` | `world-tick-pool` |
|---|---|---|---|
| Baseline | 5,52 lõi | 5,09 lõi (92,3%) | 0,179 lõi |
| **+ gộp flush** | **5,36 lõi** | **4,87 lõi (91,0%)** | 0,198 lõi |

**Giảm ~2,9% tổng / ~4,3% Netty — nằm trong nhiễu** (biến thiên giữa các mốc đo là ±1 lõi). Và cơ cấu
bên trong Netty **không đổi**: TLS 39,5% → 39,7%, buffer 27,7% → 26,1%. Tức **số TLS record hầu như
không giảm**.

### Vì sao — con số mà bản đầu của ADR này đã bỏ sót

59.760 gói/giây ÷ 1000 kết nối = **~60 gói/giây MỖI kết nối**, tức trung bình **cách nhau ~16ms**.
Gộp flush chỉ ăn thua khi nhiều message tới **cùng một kết nối** sát nhau; ở đây **không có gì để
gộp**. Bản đầu của ADR này ngầm giả định message dồn cục theo kết nối — sai.

⇒ Chi phí là **THEO MESSAGE thật sự**, không phải theo lần flush. Muốn giảm thì phải **gửi ít
message hơn**, không phải gom chúng lại lúc ghi.

Prototype đã được **gỡ bỏ**, `master` không mang thay đổi nào của nó.

---

## Decision

**D1 — BÁC BỎ.** `FlushConsolidationHandler`: đã đo, ~3% và không tách được khỏi nhiễu. Không đáng
thêm một handler vào đường nóng để lấy ngần đó.

**D2 — BÁC BỎ theo D1.** Đổi `writeAndFlush` → `write` chỉ có nghĩa khi D1 có tác dụng.

**D3 — nay là đề xuất CHÍNH: giảm SỐ LƯỢNG message.** Ở AoI 20Hz, ~60 gói/giây mỗi kết nối là **~3
message mỗi lần đồng bộ AoI**. Gộp chúng thành **một** message mang nhiều thực thể ⇒ giảm ~2/3 số
gói, và vì TLS/buffer/syscall đều tính theo gói, đây là mức giảm áp thẳng vào ~79% CPU. Đây là đổi
protocol (message mới, theo luật additive NETWORK.md §6) nên **cần chủ dự án đồng ý trước khi làm**.

**D4 — câu hỏi CÒN MỞ, nên trả lời trước khi thi hành D3.** 2,13 lõi ÷ 59.760 gói/giây =
**~36 µs CPU cho TLS mỗi gói**. Con số đó **quá lớn** so với AES-GCM có AES-NI (48 byte lẽ ra tốn
nanogiây). Nếu có gì đó bệnh lý trong đường TLS (ví dụ mỗi record bị cấp phát/khởi tạo lại), thì sửa
đúng chỗ đó có thể rẻ hơn D3 rất nhiều. **Chưa điều tra.**

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| A. `FlushConsolidationHandler` (D1+D2) | Handler CÓ SẴN của Netty; không đụng protocol/DB/message | **ĐÃ ĐO: chỉ ~3%, trong nhiễu** — vì mỗi kết nối chỉ nhận ~60 gói/giây, cách nhau ~16ms, không có gì để gộp | **BÁC BỎ bằng số đo**, không phải bằng lập luận |
| **B. Gộp nội dung AoI vào 1 message nhiều thực thể** (chọn — D3) | Giảm ~2/3 số gói; áp thẳng vào ~79% CPU vì TLS/buffer/syscall đều tính theo gói | Đổi protocol ⇒ đụng client, test; phải giữ luật additive | **Chọn** — sau khi A bị số đo bác bỏ, đây là đường duy nhất còn lại tấn công đúng chi phí theo-gói |
| C. Tắt TLS trong mạng nội bộ | Xoá thẳng 39,5% CPU của Netty | Phá [ADR-027](ADR-027-TLS-Transport-Security.md); client kết nối qua Internet, không phải mạng nội bộ | Không chọn — đổi bảo mật lấy hiệu năng, đúng thứ Điều 30 cấm |
| D. Đổi cipher suite (ChaCha20 thay AES-GCM) | Có thể rẻ hơn trên máy không AES-NI | Máy đo CÓ AES-NI, và chi phí là **theo record** chứ không theo byte ⇒ đổi cipher gần như không đổi gì | Không chọn — không nhắm vào nguyên nhân thật |
| E. Không làm gì, tăng phần cứng | Không rủi ro kỹ thuật | Ở N=2000 server đã ăn ~65% một máy 32 luồng, và chi phí mỗi người chơi đang TĂNG theo N | Không chọn — mua thời gian bằng tiền cho một đường cong siêu tuyến tính |

---

## Consequences

**Tích cực:**
- Nhắm đúng ~79% CPU của server thay vì ~3% (mọi đích tối ưu ECS/AoI/AI từng nêu đều nằm trong 3%).
- Giảm số gói tác động cùng lúc lên cả TLS, cấp phát buffer và syscall — ba khoản chi lớn nhất.
- Một khoản đã được TRỪ khỏi bàn cân bằng số đo (D1), nên không ai còn phải thử lại nó.

**Đánh đổi phải chấp nhận:**
- D3 **đổi protocol**: thêm message mới, client phải hiểu; giữ message cũ để tương thích ⇒ hai đường
  cùng tồn tại một thời gian, tốn công bảo trì.
- Gộp nhiều thực thể vào một message làm gói to hơn; nếu một thực thể đổi trạng thái, cả gói phải
  gửi lại. Ở AoI 20Hz với ~3 thực thể mỗi lần thì rủi ro này nhỏ, nhưng **phải đo lại**.
- Chưa trả lời D4 thì vẫn có khả năng đang tối ưu triệu chứng: nếu TLS đắt vì một lỗi bệnh lý,
  giảm số gói chỉ che bớt nó.

**Module bị ảnh hưởng:**
- `shared/shared-protocol` — message AoI mới (D3).
- `server/server-gateway` — `ConnectedCharacterRegistry` (đường gửi AoI).
- `client/client-core` — phía nhận.

---

## Revisit Trigger

Đo lại sau khi thi hành D3: nếu **`nioEventLoopGroup` không giảm xuống dưới ~70% CPU** ở N=1000 thì
giảm số gói cũng chưa đủ, và D4 (chi phí TLS mỗi record bất thường) trở thành việc chính. Ngược lại,
nếu `world-tick-pool` vượt **20%** CPU trong một lần đo sau này thì thứ tự ưu tiên ở ADR này đã hết
hiệu lực, phải đo lại từ đầu.

---

## Technical Decisions

- **Dựng thử rồi mới đề xuất.** D1 nghe rất hợp lý (handler có sẵn, không đụng protocol) nhưng đo
  xong thì chỉ ~3%. Nếu ADR này được duyệt lúc chưa đo, dự án đã thêm một handler vào đường nóng để
  đổi lấy nhiễu. **Điều 30 áp cho cả ĐỀ XUẤT, không chỉ cho refactor.**
- Không đụng TLS lẫn cipher suite ở đây: chi phí tính theo **số record**, không phải thuật toán —
  nhưng D4 vẫn để ngỏ khả năng có lỗi bệnh lý trong chính đường TLS.
- D3 giữ message cũ, chỉ THÊM message mới (luật additive NETWORK.md §6) — không tăng
  `protocolVersion`, cùng khuôn ADR-035/ADR-036.

## ADR cần tạo

Nếu sau khi đo D1/D2 vẫn chưa đủ ⇒ cần một ADR riêng cho **gộp nội dung AoI** (đổi protocol, tăng
`protocolVersion` hoặc thêm message mới theo luật additive của NETWORK.md §6).

## Tài liệu liên quan

- [BENCHMARK.md §5.1](../../09-performance/BENCHMARK.md) — mục "Nút thắt nằm ở đâu", toàn bộ số liệu.
- [OBSERVABILITY.md](../../08-observability/OBSERVABILITY.md) — `kage.tick.duration` chỉ mô tả ~3% CPU
  của server; cần thêm chỉ số cho đường gửi.
- [SERVER.md §6](../../05-server/SERVER.md) — AoI, nguồn sinh ra phần lớn 59.760 gói/giây.

## Việc cần làm tiếp theo

1. **Điều tra D4 TRƯỚC** (không cần chủ dự án gật — chỉ là đo đạc): vì sao TLS tốn ~36µs mỗi gói
   48 byte? Nếu đó là một chỗ bệnh lý sửa được thì rẻ hơn D3 nhiều.
2. **Chủ dự án duyệt D3** (đổi protocol). Câu hỏi cụ thể: có đồng ý thêm một message AoI mang NHIỀU
   thực thể, giữ message cũ để tương thích, không tăng `protocolVersion` không?
3. Sau khi thi hành: **đo lại đúng cùng kịch bản** (N=1000, cấu hình production, `jdk.ThreadCPULoad`
   + `kage_packet_sent_total`) và ghi cạnh số hiện tại ở §5.1.
4. Cân nhắc thêm metric **số gói mỗi giây trên mỗi kết nối** — hôm nay phải tự cộng
   `kage_packet_sent_total` theo opcode mới ra được, mà chính con số đó là thứ bác bỏ D1.
