# ADR-024: Chiến lược phát hành asset — đóng gói toàn bộ trong bản build, hoãn patch/CDN/Play Asset Delivery

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-08-01 |
| Deciders | AI (Claude) đề xuất; chờ chủ dự án chốt |
| Related | [RESOURCE.md §9, §10](../../04-client/RESOURCE.md), [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) (dòng "Android — cần chốt mobile-first hay desktop-first"), ADR-023 *(chưa viết — câu hỏi mobile input song song, xem [README.md](README.md) "ADR cần tạo")*, [GIT-WORKFLOW.md](../../11-process/GIT-WORKFLOW.md) (Closed Beta trước Production), [ROADMAP.md Phase 10](../../11-process/ROADMAP.md) |

---

## Context

RESOURCE.md §10 đánh dấu "Patch & CDN" là **chưa chốt**, và ADR Index gắn nhãn ADR-024 là
**🟢 Chặn Phase 10** (Release) — Phase hiện đang mở. Đây là lý do ADR này được soạn ngay bây giờ,
không phải vì có nhu cầu thật đã phát sinh.

**Sự thật quan trọng nhất khi viết ADR này**: repo hiện **không có thư mục `assets/` nào** —
`find . -iname "*asset*"` không ra kết quả, `assets/hyperlap2d-projects/` và `assets/raw/` (nhắc tới
trong `.gitattributes` cho Git LFS) chưa từng được tạo. Nghĩa là toàn bộ câu hỏi ở RESOURCE.md §10
("đóng gói hay tải về?", "CDN ai trả tiền?", "patch từng file hay nguyên atlas?", "APK hay OBB/Play
Asset Delivery?") hiện **không có số liệu thật nào để đo** — không biết tổng dung lượng asset cuối
cùng, không biết số lượng zone/atlas, không biết tần suất cập nhật asset sau khi phát hành.

Thêm nữa: **Android chưa phải target đã chốt** — TECHNOLOGY-STACK.md liệt Android backend là "cần
chốt câu hỏi mobile-first hay desktop-first", và hiện chỉ có `client-desktop` tồn tại (không có
module Android nào). Câu hỏi "OBB hay Play Asset Delivery, giới hạn 150MB Google Play" trong §10 phụ
thuộc hoàn toàn vào quyết định đó — chưa chốt nền tảng thì chưa thể chốt cơ chế phân phối riêng cho
nền tảng đó.

Điều 30 (đo trước khi tối ưu, xem [BENCHMARK.md](../../09-performance/BENCHMARK.md)) và bài học rút ra
ở Phase 9 (không ngoại suy một quyết định lớn từ dữ liệu chưa tồn tại — capacity-planning phải dựa số
liệu thật, không đoán) áp dụng trực tiếp ở đây: quyết định cơ chế CDN/patch dựa trên dung lượng asset
**chưa tồn tại** sẽ chỉ là đoán, không phải kỹ thuật.

## Decision

**Chính thức hoá (Accept) quyết định MVP đã có ở RESOURCE.md Technical Decision #8**: đóng gói toàn
bộ asset vào bản build/bản cài, không patch, không CDN, không Play Asset Delivery — cho suốt giai
đoạn Closed Beta trên Desktop. Không quyết cơ chế patch/CDN/OBB ngay bây giờ; giữ nguyên là câu hỏi mở,
chỉ mở lại khi chạm Revisit Trigger cụ thể bên dưới (không phải "khi thấy cần").

Điều này **gỡ nhãn "🟢 Chặn Phase 10"** khỏi ADR-024 mà không cần bịa số liệu — Phase 10 (Release) cho
Closed Beta Desktop không thực sự bị chặn bởi câu hỏi CDN/mobile, vì phạm vi Closed Beta hiện tại
(xem [ADR-027](ADR-027-TLS-Transport-Security.md), cùng tinh thần) chưa cần tới cơ chế đó.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Đóng gói toàn bộ asset trong bản build (embed-all)** | 0 hạ tầng mới (không CDN, không server phát hành riêng); đơn giản nhất (Điều 9); đã là quyết định MVP có sẵn từ RESOURCE.md, chỉ cần chính thức hoá; asset versioning (§9) vẫn hoạt động cho việc kiểm tra hash/rollback nội bộ dù không patch qua mạng | Mỗi bản cập nhật asset (dù nhỏ) bắt buộc build lại + phát hành lại toàn bộ client; không phù hợp khi asset đủ lớn hoặc cần cập nhật thường xuyên không kèm code | **Chọn** — đúng quy mô Closed Beta Desktop hiện tại, không có asset thật để biện minh cho hạ tầng phức tạp hơn |
| **B. Patch/delta qua manifest đã có (§9) + CDN tải asset tách khỏi bản cài** | Cập nhật asset không cần phát hành lại client; băng thông thấp hơn (chỉ tải phần đổi); manifest hash/rollback (§9) đã thiết kế sẵn cho việc này | Thêm 1 thành phần hạ tầng mới (CDN — ai vận hành, ai trả phí) ngoài TECHNOLOGY-STACK.md, tự nó cần dừng lại hỏi riêng (CLAUDE.md §4); chưa có số liệu dung lượng/tần suất cập nhật asset thật để biện minh chi phí này | **Không chọn ngay** — ứng viên mạnh khi có asset thật đủ lớn/cập nhật đủ thường xuyên; nên có ADR riêng lúc đó, không quyết trước khi có nhu cầu thật |
| **C. Play Asset Delivery (Android, install-time/fast-follow/on-demand pack)** | Giải đúng giới hạn 150MB Google Play nếu asset vượt ngưỡng; cơ chế chuẩn của Google, không tự viết | Chỉ áp dụng khi có target Android — **hiện chưa có module Android nào tồn tại**; quyết định trước khi chốt mobile-first/desktop-first là quyết định mù | **Không chọn — chưa tới lúc**, phụ thuộc quyết định nền tảng Android (câu hỏi mở riêng ở TECHNOLOGY-STACK.md) |
| **D. Tải toàn bộ asset lần đầu chạy (thin client, không nhúng asset trong bản cài)** | Bản cài rất nhỏ, tải nhanh | Cần hạ tầng phát hành + CDN ngay từ đầu; trải nghiệm lần đầu phụ thuộc mạng — rủi ro cho Closed Beta (audience nhỏ, ưu tiên chạy được ngay hơn tối ưu kích thước cài đặt) | **Không chọn** — chi phí hạ tầng không tương xứng lợi ích ở quy mô hiện tại |

## Consequences

**Tích cực:**
- Gỡ trạng thái "chặn Phase 10" mà không cần đoán số liệu chưa tồn tại.
- Không thêm dependency/hạ tầng mới — nhất quán với ADR-014 (VM đơn, tối thiểu) và Điều 9.
- Asset versioning/manifest (RESOURCE.md §9) vẫn được giữ nguyên, không lãng phí — vẫn dùng nội bộ cho
  hash-check/rollback dù chưa dùng cho patch qua mạng.

**Đánh đổi phải chấp nhận:**
- Mỗi thay đổi asset (kể cả nhỏ) bắt buộc phát hành lại toàn bộ client trong suốt Closed Beta — chấp
  nhận được vì Closed Beta vốn đã yêu cầu build mới thường xuyên (cùng lý do đã chấp nhận ở ADR-027 về
  rotate cert thủ công).
- Câu hỏi Android/CDN/Play Asset Delivery **vẫn treo**, không phải đã trả lời — ADR này chỉ hoãn có chủ
  đích, không lờ đi. Bất kỳ ai đọc RESOURCE.md §10 sau này cần biết đây là hoãn có ADR, không phải lỗ
  hổng bị quên.

**Module bị ảnh hưởng:**
- Không module code nào đổi — quyết định này giữ nguyên hành vi hiện tại (chưa có pipeline patch/CDN
  nào tồn tại để tắt).
- `docs/04-client/RESOURCE.md` §10 cần cập nhật: bỏ trạng thái "chưa chốt", trỏ về ADR này.
- `docs/01-architecture/adr/README.md`: cập nhật bảng "ADR cần tạo" → chuyển ADR-024 sang danh sách
  chính, trạng thái Proposed.

## Revisit Trigger

- **Khi Android được chốt là target thật** (câu hỏi mở ở TECHNOLOGY-STACK.md, không phải quyết định
  của ADR này) → bắt buộc đánh giá lại Phương án C (Play Asset Delivery) với số liệu dung lượng thật
  lúc đó, viết ADR riêng hoặc Superseded ADR này.
- **Khi tổng dung lượng asset đóng gói (đo thật, không ước tính) vượt quá mức làm bản cài/bản cập nhật
  trở thành trải nghiệm tệ cho Closed Beta tester thật** (chưa có ngưỡng cụ thể — phải đo trước khi
  định con số, Điều 30; ví dụ cách đo: theo dõi kích thước `assets/` sau khi pipeline HyperLap2D
  (RESOURCE.md §3) sản xuất ra ≥1 zone thật) → cân nhắc lại Phương án B.
- **Khi tần suất cần cập nhật asset độc lập với code trở thành gánh nặng vận hành thật** (không phải
  lý thuyết — ví dụ phải phát hành lại client nhiều lần/tuần chỉ để sửa asset) → cân nhắc lại Phương
  án B.
- Không có trigger nào khác — nếu không rơi vào 3 trường hợp trên, đóng gói toàn bộ (Phương án A) coi
  là ổn định cho suốt Closed Beta.
