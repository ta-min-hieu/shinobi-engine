# ADR-023: Desktop-first — bàn phím + chuột là sơ đồ điều khiển chính

| | |
|---|---|
| Status | ✅ **Accepted** (2026-09-05) — chủ dự án chốt **desktop-first**. |
| Date | 2026-09-05 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [CLIENT.md §5, §12](../../04-client/CLIENT.md) · [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) (dòng "Android — cần chốt mobile-first hay desktop-first") · [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) (ngân sách tam giác chờ câu trả lời này) · [ADR-024](ADR-024-Asset-Distribution-Strategy.md) / [ADR-028](ADR-028-Asset-Delivery-Tiers.md) (giao asset theo tầng) · [ADR-033](ADR-033-Ninja-School-Style-Movement.md) (di chuyển khoá vào trục X) · [ADR-034](ADR-034-Manual-Attack-And-Click-Target.md) (chọn mục tiêu bằng chuột) |

---

## Context

### Câu hỏi này chặn đúng ba thứ, suốt từ Phase 2

Chỉ mục ADR ghi ADR-023 là ô **duy nhất** chưa viết, và lý do luôn là một: *"bị chặn bởi câu hỏi
Android chưa chốt, không phải bị bỏ quên"*. Nó kéo theo:

- [CLIENT.md §5, §12](../../04-client/CLIENT.md) — sơ đồ điều khiển và bố cục HUD không viết dứt được.
- [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) Revisit Trigger #2 — **ngân sách tam giác** không
  chốt được: máy để bàn và điện thoại chịu được số tam giác khác nhau một bậc.
- [ADR-024](ADR-024-Asset-Distribution-Strategy.md)/[ADR-028](ADR-028-Asset-Delivery-Tiers.md) —
  chiến lược giao asset phụ thuộc băng thông và dung lượng cài đặt của nền tảng.

### Thực tế đã chạy: mọi thứ hôm nay đều là desktop

Không phải quyết định trên giấy — đây là hợp thức hoá thứ đã tồn tại và đã được nghiệm thu:

- Client duy nhất chạy được là `client-desktop` (LWJGL3), và mọi buổi playtest từ 2026-08-08 tới nay
  đều qua nó.
- [ADR-033](ADR-033-Ninja-School-Style-Movement.md): đi lại bằng **A/D**, nhảy bằng **W**.
- [ADR-034](ADR-034-Manual-Attack-And-Click-Target.md): đánh thường bằng **Z**, **chọn mục tiêu bằng
  CHUỘT** — cơ chế này không có tương đương một-đổi-một trên cảm ứng.
- Chín cửa sổ HUD mở bằng chín phím chữ (T/I/B/N/J/M/P/F/G/K/H) — một sơ đồ chỉ có nghĩa khi có bàn phím.
- Ô nhập chat, `/w <tên> <nội dung>` — bàn phím vật lý.

Chọn mobile-first bây giờ nghĩa là **vứt bỏ và làm lại** phần lớn tầng nhập liệu của client, trong
khi Phase 10 (Release) đã bắt đầu.

### Vì sao không phải "cứ để mở"

Để mở là thứ đắt nhất: nó **chặn** ba việc trên vô thời hạn, và mỗi tính năng client viết thêm lại
làm giả định desktop lún sâu hơn mà không ai ghi nhận. Chốt desktop-first không cấm mobile — nó chỉ
nói rõ ai là bản chính, để những thứ đang chờ được đi tiếp.

---

## Decision

**D1.** **Desktop-first.** Bàn phím + chuột là sơ đồ điều khiển CHÍNH và là thứ mọi tính năng mới
phải chạy được. `client-desktop` là bản duy nhất được coi là "đủ tính năng".

**D2.** **Không hoãn tính năng nào chỉ vì nó khó đưa lên cảm ứng.** Chọn mục tiêu bằng chuột, chín
phím mở cửa sổ, gõ chat — đều hợp lệ, không cần tìm phương án trung lập nền tảng trước.

**D3.** **Mobile là Post-MVP và sẽ cần ADR RIÊNG khi tới lúc**, phạm vi tối thiểu gồm: sơ đồ nhập
liệu cảm ứng (joystick ảo / tap-to-move / D-pad), bố cục lại HUD theo vùng chạm tối thiểu, và xem
lại ADR-024/ADR-028 cho băng thông + dung lượng cài đặt. **Không** thiết kế trước cho nó bây giờ
(Điều 9 — YAGNI).

**D4.** **Gỡ chặn ngay ba việc đang treo**: ngân sách tam giác của ADR-031 nay tính theo mục tiêu
desktop; CLIENT.md §5/§12 viết dứt được; ADR-024/028 chốt được theo giả định desktop.

**D5.** Vẫn **giữ tầng trừu tượng `GameAction`/`InputMapper` đang có** — nó đã tồn tại và đang trả
công (đổi phím không phải sờ vào gameplay). Nhưng **không** mở rộng nó để đón cảm ứng khi chưa có
mobile thật.

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Desktop-first** (chọn) | Khớp 100% thứ đang chạy và đã nghiệm thu; gỡ chặn ADR-031/024/028 và CLIENT.md ngay; không phải viết lại gì | Lên mobile sau sẽ tốn một đợt làm lại tầng nhập liệu + HUD thật sự | **Chọn.** Chi phí duy nhất là một đợt làm lại TRONG TƯƠNG LAI, có thể không bao giờ tới; chi phí của mọi phương án khác là **ngay bây giờ** |
| B. Mobile-first | Thị trường game nhập vai Việt Nam nghiêng mạnh về mobile | Vứt bỏ sơ đồ điều khiển đã nghiệm thu (ADR-033/034), thiết kế lại toàn bộ HUD 9 cửa sổ, và làm việc đó **trong Phase 10** | Không chọn — đổi hướng lúc đang chuẩn bị phát hành là đắt nhất trong mọi thời điểm |
| C. Cả hai ngang nhau | Không phải làm lại lần hai nếu chắc chắn ra mobile | Mọi tính năng phải qua mẫu số chung của hai nền tảng; "chọn mục tiêu bằng chuột" (ADR-034, đã nghiệm thu) sẽ phải bỏ. Trả giá NGAY cho một lợi ích CÓ THỂ không bao giờ dùng | Không chọn — đây chính là thiết kế trước cho nhu cầu suy đoán mà Điều 9 cấm |
| D. Tiếp tục để mở | Không phải quyết | Đã để mở từ Phase 2 và **chặn ba việc suốt từ đó**. Không quyết cũng là một quyết định, chỉ là loại không ai chịu trách nhiệm | Không chọn |

---

## Consequences

**Tích cực:**
- Ba việc treo lâu nhất được gỡ chặn cùng lúc (ngân sách tam giác, CLIENT.md §5/§12, ADR-024/028).
- Chỉ mục ADR hết ô "chưa viết" — mọi quyết định kiến trúc nay đều có hồ sơ.
- Tính năng client mới thôi phải cân nhắc một nền tảng chưa tồn tại.

**Đánh đổi phải chấp nhận:**
- Nếu sau này làm mobile, tầng nhập liệu và bố cục HUD **sẽ phải làm lại thật** — không phải chỉnh
  sửa nhỏ. Chấp nhận có ý thức, không phải bỏ sót.
- Vài cơ chế đã nghiệm thu (chọn mục tiêu bằng chuột) không có tương đương cảm ứng tự nhiên; bản
  mobile sẽ phải thiết kế lại chúng chứ không port thẳng.

**Module bị ảnh hưởng:** không đổi code nào. Đây là quyết định định hướng — nó **gỡ** ràng buộc chứ
không thêm.

---

## Revisit Trigger

Khi chủ dự án quyết đưa game lên Android/iOS thật (có mốc thời gian, không phải "có thể sau này").
Lúc đó ADR này **không bị Superseded** — nó vẫn đúng cho bản desktop; bản mobile cần một ADR riêng
theo D3.

---

## Technical Decisions

- Desktop-first là **hợp thức hoá thực tế**, không phải đổi hướng: ADR-033/034 và toàn bộ HUD đã
  giả định bàn phím + chuột từ lâu, chỉ là chưa ai ghi nó xuống.
- Không thiết kế trước cho cảm ứng (Điều 9). Giữ `GameAction`/`InputMapper` vì nó đã trả công sẵn,
  không phải vì mobile.
- Không đụng một dòng code nào trong ADR này.

## ADR cần tạo

Khi làm mobile: một ADR **sơ đồ nhập liệu cảm ứng** (theo D3) và một lượt xem lại ADR-024/ADR-028.

## Tài liệu liên quan

- [CLIENT.md §5, §12](../../04-client/CLIENT.md) — nay viết dứt được.
- [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) — ngân sách tam giác nay tính theo desktop.
- [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) — xoá được dòng "Android — chưa chốt".

## Việc cần làm tiếp theo

1. Cập nhật [CLIENT.md §5/§12](../../04-client/CLIENT.md): sơ đồ điều khiển desktop là bản chính,
   gỡ mọi chỗ ghi "chờ chốt nền tảng".
2. Cập nhật [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md): Android là Post-MVP, không còn là câu hỏi mở.
3. Đóng Revisit Trigger #2 của [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) — ngân sách tam giác
   nay có mục tiêu rõ.
