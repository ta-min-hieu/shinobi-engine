# ADR-028: Bản cài đóng gói đúng 1 tier đồ họa mặc định, các tier còn lại tải theo yêu cầu

| | |
|---|---|
| Status | ⏳ **Proposed** — AI không tự Accept ([adr/README.md](README.md) quy tắc 5) |
| Date | 2026-08-01 |
| Deciders | Chủ dự án chọn phương án (2026-08-01); AI soạn |
| Related | **Amend [ADR-024](ADR-024-Asset-Distribution-Strategy.md)** · [ART-PIPELINE.md §5, §8.4](../../14-art/ART-PIPELINE.md) · [RESOURCE.md §9, §10](../../04-client/RESOURCE.md) · [RENDERING.md](../../03-engine/RENDERING.md) |

---

## Context

[ADR-024](ADR-024-Asset-Distribution-Strategy.md) (Proposed, 2026-08-01) khuyến nghị **đóng gói toàn bộ asset trong bản build** và hoãn patch/CDN, với lý do trung thực: lúc viết, repo **không có asset thật nào để đo dung lượng**, và Android chưa được chốt là target.

Cùng ngày, [ART-PIPELINE.md §5](../../14-art/ART-PIPELINE.md) chốt thiết kế **3 mức cấu hình đồ họa** (LOW `@0.5x` · MEDIUM `@1x` · HIGH `@1x` + hiệu ứng) — mỗi mức là một **bộ atlas riêng**, sinh từ cùng một bản master `@2x`.

Đây là **dữ kiện mới ADR-024 chưa cân nhắc**, và nó làm thay đổi phép tính: ADR-024 ngầm giả định *một* bộ asset. Với 3 bộ, "đóng gói toàn bộ" nghĩa là **bản cài phồng khoảng 3 lần trong khi mỗi thiết bị chỉ dùng đúng 1 bộ** — LOW cho máy yếu, HIGH cho desktop, không máy nào dùng cả ba.

Chủ dự án muốn giảm dung lượng bản cài ban đầu. Ràng buộc bổ sung khi cân nhắc:

- **Màn hình chờ tải ở lần mở game đầu tiên là điểm rơi người chơi lớn nhất** — người chơi chưa đầu tư gì vào game thì rất dễ bỏ.
- Lý do hoãn của ADR-024 **vẫn còn đúng một phần**: repo hiện vẫn có 0 asset thật, nên **con số dung lượng vẫn là ước lượng**, chưa đo được.
- Hạ tầng phát file đã chốt là **nginx đọc từ đĩa**, không object storage ([ART-PIPELINE §8.4](../../14-art/ART-PIPELINE.md)).
- `asset-manifest.json` (đường dẫn + sha256 + size) **đã có trong thiết kế** [RESOURCE.md §9](../../04-client/RESOURCE.md) — hạ tầng cho việc tải đã sẵn, không phải phát minh mới.

## Decision

**Bản cài đóng gói đúng một tier mặc định (MEDIUM); LOW và HIGH tải theo yêu cầu khi người chơi tự đổi mức đồ họa** — và client **luôn** đi qua một đường code duy nhất `ensureAssetsAvailable(tier)`, đường này thoả mãn tức thì với tier đã đóng gói sẵn.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Đóng gói cả 3 tier** (ADR-024 nguyên bản) | Đơn giản nhất (Điều 9); chơi ngay, không cần mạng; không có nhánh code nào có thể hỏng | Bản cài phồng ~3× trong khi mỗi máy dùng 1 bộ; lãng phí băng thông tải game lẫn dung lượng đĩa; chạm trần 150MB của Google Play sớm hơn nhiều nếu sau này ra Android | **Không chọn** — lãng phí có thể tránh được mà không đổi lại lợi ích tương xứng |
| **B. Bản nhẹ hoàn toàn, tải tất cả lúc chạy lần đầu** | Bản cài nhỏ nhất | **Màn hình chờ tải chắn ngay lần mở đầu tiên** — điểm rơi người chơi lớn nhất; game không chạy được nếu offline; hỏng mạng giữa chừng là hỏng trải nghiệm đầu tiên | **Không chọn** — tối ưu sai thứ: dung lượng cài đặt đổi lấy ấn tượng đầu |
| **C. Hai kiểu build (đầy đủ / nhẹ) chọn lúc build** | Linh hoạt; hoà giải được ADR-024 | Hai cấu hình phát hành = hai thứ phải kiểm thử; nếu Closed Beta chỉ dùng bản đầy đủ thì **nhánh "tải" sẽ mục vì không ai chạy** | **Không chọn làm phương án chính** — nhưng cờ build vẫn có thể thêm sau như biến thể của D (xem "Mở rộng") |
| **D. Đóng gói 1 tier mặc định + tải tier khác theo yêu cầu** ✅ | Giảm ~⅔ dung lượng asset so với A; **chơi được ngay, không có màn hình chờ lần đầu**; chỉ người chủ động chọn Ultra/Low mới phải chờ — và họ sẵn lòng chờ vì tự chọn; nhánh tải chạy trên đường code chung nên không mục | Vẫn phải viết code tải + xử lý lỗi mạng; người đổi tier phải chờ một lần | **Chọn** — đạt mục tiêu giảm dung lượng mà không đánh đổi ấn tượng đầu |

## Consequences

**Tích cực:**
- Bản cài giảm khoảng ⅔ phần asset so với đóng gói cả 3 tier.
- Người chơi mở game là chơi được ngay, **không có màn hình chờ tải ở lần đầu** — không đụng vào điểm rơi lớn nhất.
- Việc tải chỉ xảy ra khi người chơi **chủ động** đổi mức đồ họa, tức đã hiểu vì sao phải chờ.
- Gỡ được thế bí của ADR-024 mà không cần đo dung lượng thật trước: quyết định này **đúng ở mọi mức dung lượng**, vì nó dựa trên "mỗi máy chỉ dùng 1 tier", không dựa trên con số MB.
- Đường tải dùng chung `asset-manifest.json` đã thiết kế sẵn — không thêm hạ tầng, vẫn nginx + đĩa.

**Đánh đổi phải chấp nhận:**
- Phải viết **code tải thật kèm xử lý lỗi thật**: mất mạng giữa chừng, file tải dở, hash sai, hết dung lượng đĩa, tải tiếp sau khi thoát game. Đây là chi phí lớn nhất của quyết định này, **không phải** ở script build.
- Đổi tier tốn một lần chờ tải; UI phải nói rõ trước khi người chơi bấm.
- Người chơi offline hoàn toàn bị khoá ở tier mặc định — chấp nhận được với game online.
- **Bắt buộc**: mọi lần khởi động đều gọi `ensureAssetsAvailable(tier)`, kể cả khi tier đó đã đóng gói sẵn. Đây là điều **chống nhánh code mục** — bản mặc định chạy qua cùng đường code với bản phải tải, nên đường đó được kiểm thử liên tục. Nếu về sau ai đó "tối ưu" bằng cách bỏ qua lời gọi này khi asset có sẵn thì mất luôn tác dụng đó.
- Hash trong manifest phải được **kiểm tra thật**, không chỉ dùng để so version — nếu không thì file tải hỏng sẽ biểu hiện thành lỗi texture khó truy.

**Module bị ảnh hưởng:**
- `client-core` — `ResourceManager` thêm `ensureAssetsAvailable(tier)`; màn hình tải khi đổi tier; `GraphicsQuality` enum.
- `tools/asset-pipeline` (chưa tồn tại) — xuất atlas **tách theo tier** + `asset-manifest.json` cho từng tier.
- Hạ tầng phát hành — nginx serve `assets/exported/` ([ART-PIPELINE §8.4](../../14-art/ART-PIPELINE.md)).
- **Không** ảnh hưởng server/gameplay: tier chỉ tồn tại phía client, Server Authority không bị đụng.

**Quan hệ với ADR-024:** ADR này **amend** ADR-024 ở đúng một điểm — "đóng gói toàn bộ" đổi thành "đóng gói tier mặc định". Phần còn lại của ADR-024 (**hoãn CDN/OBB/Play Asset Delivery** tới khi Android được chốt) **vẫn giữ nguyên hiệu lực**. Nếu chủ dự án Accept ADR-028 thì ADR-024 cần cập nhật Status thành "Amended by ADR-028", không phải Superseded.

## Revisit Trigger

Xem lại quyết định này khi **bất kỳ** điều kiện nào sau đây xảy ra:

1. **Dung lượng 1 tier vượt 150MB** (đo trên `assets/exported/atlases/med/` sau khi có bộ asset thật của ≥3 zone) — chạm trần APK của Google Play, buộc phải tính OBB/Play Asset Delivery.
2. **Chênh lệch dung lượng giữa tier lớn nhất và nhỏ nhất < 20%** — lúc đó tách tier không còn tiết kiệm đáng kể, quay về đóng gói cả 3 cho đơn giản (Điều 9).
3. **Tỷ lệ người chơi đổi mức đồ họa > 50%** — nếu đa số phải tải thêm ngay, chọn sai tier mặc định; cân nhắc phát hiện cấu hình máy lúc cài thay vì cố định MEDIUM.
4. **Android được chốt là target thật** — kích hoạt lại phần ADR-024 đang hoãn (OBB/Play Asset Delivery), có thể thay thế hoàn toàn cơ chế tải tự viết.

## Mở rộng có thể làm sau (không thuộc phạm vi ADR này)

- Cờ build `-Pkage.assets=default|all|none` để ra bản đóng gói cả 3 tier (bản LAN/offline) hoặc bản rỗng hoàn toàn. Chỉ thêm khi có nhu cầu thật — mỗi cấu hình phát hành thêm là một thứ phải kiểm thử.
- Tự phát hiện cấu hình máy lúc chạy lần đầu để chọn tier mặc định thay vì cố định MEDIUM.
