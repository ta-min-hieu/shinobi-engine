# STORY.md — Cốt truyện & Lore

**Nguồn chân lý duy nhất về TƯỜNG THUẬT — thế giới này là gì, chuyện gì đang xảy ra**

| | |
|---|---|
| Version | 0.1.0 |
| Status | Draft — **bố cục §1–§8 ĐÃ DUYỆT 2026-09-01** (cùng lúc Accept [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)); chi tiết §3–§6 chưa viết (🔶 TODO-OPUS) |
| Liên quan | [GDD](GAME-DESIGN-DOCUMENT.md) — cơ chế · [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) — quyết định re-theme tu tiên · [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ |

> [GDD](GAME-DESIGN-DOCUMENT.md) trả lời "chơi thế nào"; tài liệu này trả lời "**vì sao thế giới
> như vậy**". Từ Đợt 2 của ADR-038: **mọi quest, NPC, tên vùng, chuỗi thoại phải map ngược về một
> mục ở đây** — giống như mọi System phải map về GDD. Không bám được → không viết.
> ⚠️ Điều 5: thế giới/nhân vật/địa danh **nguyên bản 100%** — cảm hứng thể loại tu tiên, không lấy
> bất kỳ tên riêng/tình tiết nào của *Phàm Nhân Tu Tiên* hay IP khác.

---

## Bố cục đề xuất (chờ duyệt)

Mỗi mục dưới đây ghi **nội dung sẽ viết** + tình trạng. Duyệt bố cục xong mới viết chi tiết
(các mục 🔶 là phần dài, giao Opus viết sau khi duyệt).

### §1. Tiền đề (Premise) — ✍️ đã phác

Người chơi là **phàm nhân xuất thân bần hàn**, linh căn mỏng đến mức bị mọi tông môn lớn từ chối.
Sau **Thiên Địa Đại Kiếp**, linh khí đại lục Huyền Thương suy kiệt, các tông môn thiếu người,
**Thanh Hư Tông** hạ tiêu chuẩn thu nhận — người chơi được nhận vào làm **đệ tử tạp dịch ngoại
môn**: gánh nước, quét sân, nhặt linh thảo. Từ vị trí thấp nhất đó, bằng cần cù + cơ duyên (không
phải thiên tài), từng bước Luyện Khí → Trúc Cơ → ... → Hoá Thần → Độ Kiếp. Fantasy cốt lõi:
**"phàm nhân nghịch tập"** — kẻ bình thường đi xa hơn thiên tài nhờ kiên trì.

### §2. Vũ trụ quan — ✍️ đã phác, cần viết đầy đủ

Linh Khí là gì, linh căn là gì (vì sao có người tu được người không), tu luyện đổi lấy gì và **trả
giá gì** (nghịch thiên đoạt mệnh — càng lên cao thiên kiếp càng nặng), Độ Kiếp Chuyển Thế nghĩa là
gì trong lore (giải thích tường thuật cho cơ chế rebirth: độ kiếp thất bại thân tan nhưng đạo cơ
còn, chuyển thế mang theo Đạo Vận). Dị linh căn Phong/Lôi: hiếm, bị đồn là "trời phạt", thực là
"trời thử".

### §3. Lịch sử đại lục Huyền Thương — 🔶 viết sau khi duyệt

Timeline 3 thời kỳ: **Thượng Cổ** (linh khí sung mãn, tiên nhân phi thăng thường xuyên) → **Thiên
Địa Đại Kiếp** (~1000 năm trước, biến cố làm linh khí suy kiệt — nguyên nhân thật là **bí mật xuyên
suốt cốt truyện chính**, hé lộ dần theo arc) → **Hiện tại** (tông môn suy yếu, yêu thú tràn Hoang
Vực, tà tu trỗi dậy). Lịch sử là nền cho mọi twist — phải chốt trước khi viết arc.

### §4. Thế lực — 🔶 viết sau khi duyệt

- **Thanh Hư Tông** (tông môn người chơi): cơ cấu tạp dịch → ngoại môn → nội môn → trưởng lão →
  tông chủ; đường thăng tiến của người chơi bám cảnh giới. NPC hiện có trong game (trưởng lão
  ngoại môn — hiện là `town_elder`) sẽ định danh ở đây.
- **1 chính tông đối trọng** (quan hệ cạnh tranh, không thù địch — nguồn xung đột PvP "luận kiếm").
- **1 thế lực tà tu/ma đạo** (phản diện chính, liên quan bí mật Đại Kiếp).
- **Yêu tộc** ở Hoang Vực (không thuần ác — có lớp lang riêng, nguồn quest phức tạp về sau).

### §5. Nhân vật — 🔶 viết sau khi duyệt

Người chơi (blank slate có xuất thân chung), 5–8 NPC chủ chốt: sư phụ/trưởng lão dẫn dắt, sư
huynh/tỷ đồng hành (có thể phản bội — twist), đối thủ thiên tài (gương chiếu fantasy phàm nhân),
phản diện từng arc. Mỗi NPC: động cơ, quan hệ với người chơi, gắn hệ thống game nào (shop, quest,
dialogue).

### §6. Arc cốt truyện chính — 6 arc theo 6 cảnh giới — 🔶 phần nặng nhất, TODO-OPUS

Khung mỗi arc (viết theo template thống nhất): *mục tiêu người chơi · xung đột · twist · boss/cao
trào · mở khoá gì · diễn ra ở zone nào*. Phác thô để duyệt hướng:

| Arc | Cảnh giới (Lv) | Một dòng |
|---|---|---|
| 1. Tạp dịch | Luyện Khí (1–18) | Từ gánh nước đến thức tỉnh linh căn — bị khinh, gặp cơ duyên đầu tiên |
| 2. Ngoại môn | Trúc Cơ (19–36) | Thành đệ tử chính thức, va chạm thiên tài, dấu vết tà tu đầu tiên |
| 3. Nội môn | Tử Phủ (37–54) | Đại bỉ võ nội môn, đắc Thần Thông, phát hiện manh mối Đại Kiếp |
| 4. Hạ sơn | Kim Đan (55–72) | Rời tông môn lịch luyện Hoang Vực, đối đầu ma đạo trực diện |
| 5. Chân tướng | Nguyên Anh (73–90) | Bí mật Thiên Địa Đại Kiếp lộ diện, đồng minh/phản bội |
| 6. Nghịch thiên | Hoá Thần (91–100) | Quyết chiến cuối, Độ Kiếp — mở vòng Chuyển Thế |

### §7. Ánh xạ lore ↔ hệ thống game — ✍️ viết cùng §6

Bảng 2 cột bắt buộc: mỗi hệ thống/zone đang chạy trong game có **lý do tường thuật**. Ví dụ: 5
Linh Mạch = long mạch ngũ hành nuôi Thanh Hư Tông; Bí Cảnh = di tích Thượng Cổ mở lại sau Đại
Kiếp; nhiệm vụ ngày = công việc tông môn; cường hoá thất bại = "linh khí phản phệ". Chống trôi:
feature mới không có dòng trong bảng này → chưa được đặt tên/viết thoại.

### §8. Quy tắc viết quest bám cốt truyện — ✍️ đã phác

Tone trang trọng vừa phải, xưng hô đúng vai vế (đạo hữu/sư huynh/sư điệt/tiền bối); thuật ngữ tra
[GLOSSARY.md](../GLOSSARY.md), không tự chế; đặt tên nguyên bản (Điều 5); mỗi chuỗi quest ghi rõ
`arc + mục STORY.md` nó thuộc về ngay trong file script (comment header) — reviewer đối chiếu được.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | STORY.md là SSOT tường thuật — quest/NPC/tên vùng phải map về đây | ✅ Accepted (2026-09-01, cùng ADR-038) |
| 2 | Bí mật Thiên Địa Đại Kiếp là trục xuyên suốt 6 arc | ✅ Accepted (2026-09-01) |
| 3 | 6 arc = 6 cảnh giới, khớp [ADR-038 D2](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) | ✅ Accepted (2026-09-01) |

## ADR cần tạo

Không — tường thuật thuộc thẩm quyền GDD/STORY, không phải kiến trúc. [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) (Proposed) đã bao quyết định re-theme.

## Tài liệu liên quan

- [GAME-DESIGN-DOCUMENT.md](GAME-DESIGN-DOCUMENT.md) — cơ chế mà lore phải giải thích
- [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ bắt buộc
- [SCRIPTING.md](../06-plugin/SCRIPTING.md) — quest viết bằng script sandbox

## Việc cần làm tiếp theo

1. ✅ Bố cục §1–§8 + hướng 6 arc đã duyệt 2026-09-01.
2. 🔶 **TODO-OPUS**: viết chi tiết §3–§6 (phần creative dài nhất — lịch sử, thế lực, nhân vật, 6 arc đầy đủ).
3. Sau khi §6 viết xong → Đợt 2 ADR-038 (viết quest script) mới được chạy, từng quest map về arc.
