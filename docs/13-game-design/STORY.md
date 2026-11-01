# STORY.md — Cốt truyện & Lore

**Nguồn chân lý duy nhất về TƯỜNG THUẬT — thế giới này là gì, chuyện gì đang xảy ra**

| | |
|---|---|
| Version | **1.0.1** — §1–§8, nội dung duyệt nguyên trạng 2026-09-05; 2026-10-01 thêm nhân vật **Lý Tam** vào §5 (chủ dự án duyệt) |
| Status | ✅ **Approved** (2026-09-05) — chủ dự án đọc và **duyệt nguyên trạng**: chân tướng Đại Kiếp §3.2, twist Tô Vân Nhi §5, lời giải rebirth §6 arc 6, và cả ba điểm được nêu ra để soi kỹ (xuất thân cố định của người chơi ở §5; twist Tô Vân Nhi không rẽ nhánh cơ chế ở MVP; mới arc 1 tồn tại trong code). Bố cục đã duyệt trước đó cùng [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) 2026-09-01. |
| Liên quan | [GDD](GAME-DESIGN-DOCUMENT.md) — cơ chế · [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) — quyết định re-theme tu tiên · [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ |

> [GDD](GAME-DESIGN-DOCUMENT.md) trả lời "chơi thế nào"; tài liệu này trả lời "**vì sao thế giới
> như vậy**". **Mọi quest, NPC, tên vùng, chuỗi thoại phải map ngược về một mục ở đây** — giống
> như mọi System phải map về GDD. Không bám được → không viết.
> ⚠️ Điều 5: thế giới/nhân vật/địa danh **nguyên bản 100%** — cảm hứng thể loại tu tiên, không lấy
> bất kỳ tên riêng/tình tiết nào của *Phàm Nhân Tu Tiên* hay IP khác.

---

## §1. Tiền đề (Premise)

Người chơi là **phàm nhân xuất thân bần hàn**, linh căn mỏng đến mức bị mọi tông môn lớn từ chối.
Sau **Thiên Địa Đại Kiếp**, linh khí đại lục Huyền Thương suy kiệt, các tông môn thiếu người,
**Thanh Hư Tông** hạ tiêu chuẩn thu nhận — người chơi được nhận vào làm **đệ tử tạp dịch ngoại
môn**: gánh nước, quét sân, nhặt linh thảo. Từ vị trí thấp nhất đó, bằng cần cù + cơ duyên (không
phải thiên tài), từng bước Luyện Khí → Trúc Cơ → Tử Phủ → Kim Đan → Nguyên Anh → Hoá Thần → Độ
Kiếp. Fantasy cốt lõi: **"phàm nhân nghịch tập"** — kẻ bình thường đi xa hơn thiên tài nhờ kiên trì.

Một câu tóm tắt toàn truyện (dùng làm la bàn khi viết quest):

> *Một đứa gánh nước phát hiện ra rằng con đường thành tiên mà cả đại lục tôn thờ được xây trên
> một lời nói dối ngàn năm — và nó là kẻ duy nhất đủ "bình thường" để bước qua lời nói dối đó.*

---

## §2. Vũ trụ quan

**Linh Khí** là hơi thở của trời đất, đọng đậm nhất ở các **linh mạch** — mạch ngầm chạy dưới đại
lục như mạch máu. Người có **linh căn** mới hấp thu được linh khí; linh căn là "rễ" bẩm sinh, có
thuộc tính ngũ hành. Kẻ không linh căn là phàm nhân trọn đời.

- **Ngũ hành linh căn** (Hỏa/Thủy/Thổ — phổ biến): thuận thiên, tu ổn định.
- **Dị linh căn** (Phong/Lôi — hiếm): dân gian đồn là "trời phạt", tu sĩ già biết đó là "trời
  thử" — khó nhập môn hơn nhưng không hề yếu hơn. (Cân bằng gameplay: 5 hệ ngang nhau — GDD §4.)
- Linh căn **mỏng/dày** quyết định tốc độ hấp thu, KHÔNG quyết định giới hạn — đây là điểm tựa
  tường thuật của cả game: người chơi linh căn mỏng nhưng không bị khoá trần.

**Tu luyện là nghịch thiên đoạt mệnh.** Mỗi lần đột phá đại cảnh giới là một lần "vay" của trời
đất; càng lên cao món nợ càng lớn, nên **thiên kiếp** (sét trời khảo nghiệm) càng nặng. Cái giá
thường trực: tuổi thọ kéo dài nhưng tình cảm phàm tục phai dần, và **tâm ma** — chấp niệm của
chính mình phản phệ trong lúc đột phá.

**Độ Kiếp Chuyển Thế** (lore của cơ chế rebirth — GDD §5): tu sĩ Hoá Thần Viên Mãn bước vào thiên
kiếp cuối để phi thăng. Từ sau Đại Kiếp, **chưa một ai phi thăng thành công** — kẻ độ kiếp thân
tan, nhưng nếu **đạo cơ** đủ vững thì thần hồn giữ được một phần **Đạo Vận**, chuyển thế đầu thai
tu lại từ đầu. Người đời gọi họ là **Trùng Tu Giả** — mỗi kiếp một mạnh hơn. (Vì sao không ai phi
thăng được là **bí mật trục chính** — §3.)

---

## §3. Lịch sử đại lục Huyền Thương

Ba thời kỳ. Phần in nghiêng là **sự thật bị che giấu** — người chơi chỉ biết dần theo arc (§6),
tuyệt đối không để NPC nói ra sớm hơn arc quy định.

### 3.1. Thượng Cổ — thời Linh Triều (kết thúc ~1.000 năm trước)

Linh khí sung mãn, tu sĩ đông như sao, phi thăng là chuyện trăm năm đôi lần. Đỉnh cao là **Linh
Triều** — liên minh các đại tông môn cai trị toàn đại lục. Di sản còn lại: các **Bí Cảnh** (động
phủ, cấm địa, tàng bảo được phong ấn), pháp khí cổ, và những tấm bia đá ghi công pháp thất truyền.

### 3.2. Thiên Địa Đại Kiếp (~1.000 năm trước)

Chính sử của mọi tông môn chép giống nhau: *"Trời giáng đại kiếp, linh khí đứt mạch, Linh Triều
sụp đổ trong một đêm."* Từ đó linh khí suy kiệt, tu sĩ thưa thớt, không còn ai phi thăng.

*Sự thật (hé lộ ở arc 5): không phải trời giáng. Tầng lãnh đạo Linh Triều — trong đó có **tổ sư
của chính các chính tông ngày nay** — đã hợp lực ép mở **Thiên Môn**, cánh cửa phi thăng, để đưa
toàn bộ tầng cao nhất phi thăng cùng lúc thay vì chờ cơ duyên từng người. Thiên Môn không chịu
nổi, vỡ. Vụ vỡ rút cạn các linh mạch lớn (đó là "linh khí đứt mạch"), và cánh cửa từ đó **kẹt ở
trạng thái nứt** — không mở đủ cho ai phi thăng, không đóng đủ để trời đất tự lành. Mọi tu sĩ độ
kiếp từ đó đến nay đều đang lao vào một cánh cửa hỏng — vì thế chỉ có Chuyển Thế, không có phi
thăng. Các chính tông đời sau biết một phần, chọn im lặng để giữ đạo thống.*

### 3.3. Hiện tại — thời Tàn Linh

Linh khí chỉ còn quanh các linh mạch nhỏ. Tông môn suy yếu, phải thu cả phàm nhân linh căn mỏng
(→ tiền đề §1). **Hoang Vực** — vùng linh mạch chết — yêu thú biến dị ngày càng hung. **Huyết Sa
Giáo** trỗi dậy, truy lùng di vật Thượng Cổ. Vết nứt Thiên Môn (không ai biết vị trí) thỉnh
thoảng "rỉ" ra linh khí hỗn loạn — nguồn gốc các Bí Cảnh đột nhiên mở lại.

---

## §4. Thế lực

### 4.1. Thanh Hư Tông — tông môn của người chơi

Chính tông hạng trung, tọa lạc trên núi Thanh Hư nơi 5 linh mạch nhỏ (ngũ hành) giao nhau — vì
thế là tông hiếm hoi đào tạo được cả 5 hệ linh căn (lý do tường thuật cho GDD §4). Tông phong:
**"Tu chân trước tu tâm"** — trọng căn cơ, không trọng xuất thân. Đường thăng tiến gắn cảnh giới:

| Bậc | Điều kiện (lore — khớp mốc GDD) |
|---|---|
| Đệ tử tạp dịch | Nhập môn (Lv1) |
| Đệ tử ngoại môn | Thức tỉnh linh căn (Lv10 — Luyện Khí tầng 5) |
| Đệ tử nội môn | Trúc Cơ thành công + qua khảo hạch (arc 3, ~Lv37) |
| Chân truyền / Trưởng lão / Tông chủ | NPC — người chơi không giữ chức trong MVP |

Cơ cấu quyền lực: **Tông chủ Huyền Cơ chân nhân** (bế quan gần như toàn truyện — lý do tường
thuật để người chơi tự giải quyết mọi việc), hội đồng trưởng lão, dưới là các **Đường** (Tàng Thư
Đường, Luyện Khí Đường, Hình Đường, Tàng Bảo Các...).

### 4.2. Bách Luyện Kiếm Phái — chính tông đối trọng

Kiếm tu, tôn sùng "một kiếm phá vạn pháp", coi lối tu ngũ hành của Thanh Hư là tạp học. Quan hệ
**cạnh tranh nhưng không thù địch** — hai tông có minh ước luận bàn võ nghệ định kỳ. Đây là lý do
tường thuật cho PvP: **Luận Kiếm Đài** (Arena 1v1) và **Chiến trường 5v5** đều là "giao lưu" giữa
đệ tử hai tông. Đánh thua không chết — chỉ mất mặt.

### 4.3. Huyết Sa Giáo — phản diện chính

Tà tu, tu bằng cách **hút tu vi kẻ khác** (lore giải thích vì sao chúng tấn công người chơi vô
cớ). Giáo chủ **Sa Y Nhân** (không lộ mặt tới arc 6) tin rằng Thiên Môn hỏng có thể **mở lại bằng
máu** — đủ tu vi tế vào vết nứt thì cửa mở, và kẻ đứng mở cửa sẽ phi thăng một mình. Mọi hoạt
động của giáo (bắt đệ tử, đào bới Bí Cảnh, khuấy Hoang Vực) đều phục vụ việc: tìm vị trí vết nứt
+ gom "nhiên liệu".

### 4.4. Yêu tộc Hoang Vực

Yêu thú khai linh trí, sống ở Hoang Vực từ trước Đại Kiếp. **Không thuần ác** — chúng là nạn nhân
lớn nhất của vụ vỡ Thiên Môn (linh mạch chết trước tiên ở đất của chúng, đồng loại biến dị thành
yêu thú cuồng loạn mà người chơi vẫn đánh hằng ngày). Arc 4 mở góc nhìn này: đánh nhau trước, hiểu
nhau sau, liên thủ chống Huyết Sa. Nguồn quest phe phái về sau (Post-MVP).

---

## §5. Nhân vật

Quy ước: tên nguyên bản, không trùng nhân vật nổi tiếng của thể loại. Mỗi NPC ghi rõ **gắn hệ
thống game nào** — NPC không gắn hệ thống thì chưa cần tồn tại.

| Nhân vật | Vai | Động cơ & quan hệ | Gắn hệ thống |
|---|---|---|---|
| **Người chơi** | Phàm nhân linh căn mỏng | Blank slate; xuất thân chung: trẻ mồ côi sau một trận yêu thú tràn làng, được Chu Hoành nhặt về | Toàn bộ |
| **Trưởng lão Chu Hoành** | Trưởng lão ngoại môn, người dẫn dắt | Già, cảnh giới kẹt ở Kim Đan, cả đời không đột phá nổi — tin rằng "cần cù thắng linh căn" và đặt niềm tin đó vào người chơi. Biết nhiều hơn những gì ông nói (từng đọc cấm thư về Đại Kiếp — nguồn hé lộ arc 3) | NPC hub hiện có (`town_elder` — đổi định danh hiển thị), giao quest chính tuyến sớm, dạy tân thủ |
| **Tô Vân Nhi** | Sư tỷ đồng hành | Đệ tử ngoại môn vào trước người chơi 3 năm, xuất thân thương gia sa sút; ấm áp, che chở đàn em. **Twist arc 5: phản bội** — gia tộc cô bị Huyết Sa nắm sinh mạng từ đầu, cô là tai mắt bị ép buộc; người chơi chọn thái độ qua dialogue (tha thứ/lạnh nhạt — không rẽ nhánh cơ chế ở MVP) | Quest đồng hành, dạy hệ thống social (bạn bè/thư/trade) |
| **Bạch Kính Chi** | Thiên tài đối thủ | Linh căn dày hạng nhất một thế hệ, kiêu nhưng **không hèn** — gương chiếu của fantasy phàm nhân: khinh người chơi ở arc 1-2, bị vượt ở arc 3, thành chiến hữu miễn cưỡng từ arc 4 | Đối thủ khảo hạch/luận kiếm (PvE scripted), mồi giới thiệu PvP |
| **Tông chủ Huyền Cơ chân nhân** | Tông chủ Thanh Hư | Bế quan trị thương gần như toàn truyện (*thật ra đang đè nén vết thương cũ liên quan vết nứt Thiên Môn — hé lộ arc 6*) | Xuất hiện ở cắt cảnh mốc lớn |
| **Sa Y Nhân** | Giáo chủ Huyết Sa — phản diện cuối | *Chính là một Trùng Tu Giả đã chuyển thế nhiều lần, tuyệt vọng vì cửa hỏng, chọn con đường máu* — đối lập trực tiếp với người chơi (cũng sẽ thành Trùng Tu Giả nhưng không bán rẻ đạo tâm) | Boss arc 6 |
| **Lý Tam** | Đệ tử ngoại môn mất tích | Đứa thứ ba mất tích trong nửa năm; Hình Đường ghi sổ đúng một chữ "trốn" rồi đóng hồ sơ, dù cả ba đều bỏ lại đồ đạc. Thật ra là nạn nhân của chuỗi mất tích arc 2 (§6 — Huyết Sa hút tu vi). Là mồi đầu tiên dẫn người chơi vào twist arc 2. *(Thêm 2026-10-01, chủ dự án duyệt)* | Quest 6 `recover_outer_sect_tallies` (một trong hai thẻ bài là của Lý Tam) · quest 7 `missing_disciple_trail` (tên hiển thị "Dấu Vết Lý Tam") |
| Hộ pháp/đàn chủ Huyết Sa (nhiều) | Boss giữa các arc | Tay chân Sa Y Nhân | Boss quest chính arc 2-5 |

---

## §6. Arc cốt truyện chính — 6 arc theo 6 cảnh giới

Template mỗi arc: **Mục tiêu · Xung đột · Twist · Cao trào/Boss · Mở khoá · Zone**. Mỗi chuỗi
quest trong game phải ghi rõ nó thuộc arc nào (§8). Nhịp hé lộ bí mật Đại Kiếp: arc 1 gieo → arc 3
nghi ngờ → arc 5 chân tướng → arc 6 lựa chọn.

### Arc 1 — Tạp Dịch (Luyện Khí, Lv1–18)

- **Mục tiêu**: sống sót qua kiếp tạp dịch; được thừa nhận là người của tông môn.
- **Xung đột**: bị đệ tử ngoại môn khinh rẻ, việc nặng dồn hết; kỳ khảo hạch thức tỉnh linh căn
  sắp tới mà linh căn người chơi mỏng tới mức đo không ra.
- **Gieo mầm**: khi dọn dẹp một góc Tàng Thư Đường bỏ hoang, người chơi nhặt được **mảnh ngọc cổ**
  không ai thèm lấy (di vật Thượng Cổ — McGuffin xuyên truyện, arc này chỉ là "vật may mắn").
- **Cao trào/Boss**: một **tán tu cường đạo** (quái `wandering_bandit`) lẻn vào trộm linh thảo,
  đúng phiên người chơi trực đêm. Đánh đuổi được hắn → Chu Hoành để ý → được đặc cách dự khảo
  hạch → **thức tỉnh linh căn (Lv10)**.
- **Mở khoá**: chọn hệ (Lv10), công pháp đầu tiên (Lv15).
- **Zone**: Thanh Hư Tông (hub) + Linh Mạch sơ cấp.

### Arc 2 — Ngoại Môn (Trúc Cơ, Lv19–36)

- **Mục tiêu**: đứng vững ở ngoại môn, Trúc Cơ thành công.
- **Xung đột**: Bạch Kính Chi và vây cánh chèn ép "đứa tạp dịch đi cửa sau"; tài nguyên tu luyện
  ngoại môn phân phát theo thứ hạng — người chơi phải cày từ đáy bảng (lý do tường thuật cho
  nhiệm vụ ngày + ranking).
- **Twist**: chuỗi đệ tử ngoại môn **mất tích** không phải bỏ trốn như Hình Đường kết luận — người
  chơi và Tô Vân Nhi lần ra một hang luyện công bỏ hoang, phát hiện dấu vết **hút tu vi** — cái
  tên "Huyết Sa" lần đầu xuất hiện.
- **Cao trào/Boss**: **hộ pháp Huyết Sa cải trang làm chấp sự tông môn** — kẻ đứng sau các vụ mất
  tích. Đánh bại (quest boss đầu tiên có cắt cảnh), nhưng hắn kịp tự tan tu vi, manh mối đứt.
- **Mở khoá**: linh thú (Lv20), bang hội (Lv25), PvP (Lv30 — Bách Luyện Kiếm Phái đến giao lưu,
  mở Luận Kiếm Đài).
- **Zone**: Linh Mạch trung cấp; lần đầu thấy rìa Hoang Vực.

### Arc 3 — Nội Môn (Tử Phủ, Lv37–54)

- **Mục tiêu**: qua khảo hạch nội môn; đại bỉ võ toàn tông; đắc Thần Thông.
- **Xung đột**: nội môn là thế giới khác — chân truyền, trưởng lão, phe phái. Người chơi vượt
  Bạch Kính Chi ở đại bỉ võ (scripted — mốc "phàm nhân vượt thiên tài" đầu tiên, phải làm cho đã).
- **Twist kép**: (1) **mảnh ngọc cổ phát sáng** khi người chơi vào Tàng Thư Đường tầng cấm — Chu
  Hoành thấy, biến sắc, lần đầu nhắc tới hai chữ **"Thiên Môn"** rồi lảng tránh; (2) tin khảo
  hạch bị lộ cho Huyết Sa — **trong tông có nội gián cấp trưởng lão** (danh tính giữ đến arc 5).
- **Cao trào/Boss**: Huyết Sa tập kích lễ đại bỉ võ để bắt các hạt giống Tử Phủ. Người chơi lần
  đầu dùng **Thần Thông (Lv50)** đẩy lui **đàn chủ Huyết Sa**.
- **Mở khoá**: danh phận nội môn; Bí Cảnh mở màn (lore: di tích Thượng Cổ quanh núi Thanh Hư
  "thức dậy" — ROADMAP xếp dungeon Phase 8, quest Bí Cảnh chỉ trỏ tới, không bắt buộc).
- **Zone**: khu nội môn (mở rộng hub), Linh Mạch cao cấp.

### Arc 4 — Hạ Sơn (Kim Đan, Lv55–72)

- **Mục tiêu**: lịch luyện Hoang Vực theo lệnh tông (truyền thống: Kim Đan phải "luyện tâm trong
  hồng trần"); truy dấu Huyết Sa.
- **Xung đột**: Hoang Vực không có luật; tán tu, tà tu, yêu thú biến dị. Người chơi lần đầu **rời
  vòng tay tông môn** — tự lo chỗ ăn ngủ, tự chọn phe (lý do tường thuật cho trade/auction/kết
  giao rộng).
- **Twist**: cứu một **yêu tộc khai trí** khỏi tay Huyết Sa → biết yêu tộc cũng là nạn nhân Đại
  Kiếp, và Huyết Sa đang **đào linh mạch chết** tìm thứ gì đó (chính là dò vị trí vết nứt).
- **Cao trào/Boss**: Huyết Sa đánh thức một **cổ thạch quái** trấn giữ linh mạch chết (nâng cấp
  loài `stone_ogre` — lore: máy móc hộ mạch của Linh Triều mất kiểm soát). Người chơi + Bạch Kính
  Chi + yêu tộc liên thủ hạ nó.
- **Mở khoá**: toàn bộ Hoang Vực; thiện cảm yêu tộc (biến số cho Post-MVP).
- **Zone**: Hoang Vực (PvP mở — lore: nơi không luật lệ, đệ tử các tông va nhau).

### Arc 5 — Chân Tướng (Nguyên Anh, Lv73–90)

- **Mục tiêu**: về tông báo tin → lật nội gián → tìm sự thật Đại Kiếp.
- **Xung đột**: lời tố cáo của người chơi động tới **cấm kỵ ngàn năm của mọi chính tông**; một
  nửa hội đồng trưởng lão muốn bịt miệng thay vì điều tra.
- **Twist chồng twist**: (1) nội gián cấp trưởng lão bị lật — nhưng hắn chỉ là con rối; (2) **Tô
  Vân Nhi chính là tai mắt thật của Huyết Sa** từ arc 2 — bị ép bằng sinh mạng cả gia tộc; cô tự
  thú với người chơi trước khi bị phát hiện; (3) mảnh ngọc cổ + cấm thư của Chu Hoành ghép lại
  thành **chân tướng §3.2**: Đại Kiếp là do Linh Triều ép mở Thiên Môn — trong danh sách chủ mưu
  có tổ sư Thanh Hư.
- **Cao trào/Boss**: Huyết Sa tổng tấn công Thanh Hư Tông để cướp mảnh ngọc (chìa khoá định vị
  vết nứt). **Chu Hoành chết chặn hậu** — cái chết định hình đạo tâm người chơi (kẻ cả đời không
  đột phá nổi lại là người "thành tiên" nhất truyện). Mảnh ngọc vẫn bị cướp.
- **Mở khoá**: (tường thuật) toàn tông môn chuyển trạng thái chiến tranh; danh phận "người vạch
  trần chân tướng".
- **Zone**: toàn bản đồ.

### Arc 6 — Nghịch Thiên (Hoá Thần, Lv91–100)

- **Mục tiêu**: chặn Sa Y Nhân mở lại Thiên Môn bằng máu; chính người chơi Độ Kiếp.
- **Xung đột**: Huyền Cơ chân nhân xuất quan, thừa nhận tông môn ngàn năm nay **biết mà im lặng**;
  liên minh Thanh Hư + Bách Luyện + yêu tộc thành hình nhưng đầy rạn nứt.
- **Twist cuối**: Sa Y Nhân là **Trùng Tu Giả** — hắn từng độ kiếp thất bại nhiều lần, và tuyên
  bố với người chơi: *"Cửa hỏng rồi. Ngươi có tu sạch đến đâu cũng chỉ chuyển thế như ta thôi.
  Khác nhau là ta dám trả giá."* — đặt câu hỏi trung tâm của game vào miệng phản diện.
- **Cao trào/Boss**: đại chiến tại vết nứt Thiên Môn. Hạ Sa Y Nhân trước khi lễ tế máu hoàn tất.
- **Kết vòng đời (ăn khớp cơ chế rebirth)**: người chơi Lv100 bước vào thiên kiếp tại chính vết
  nứt. Phi thăng — như mọi kẻ ngàn năm nay — **thất bại**, nhưng nhờ đạo tâm (và mọi thứ tích
  luỹ trong truyện) giữ được **Đạo Vận**, chuyển thế trùng tu. Vòng lặp Chuyển Thế của người chơi
  = hành trình nhiều kiếp **tự sửa cánh cửa mà tiền nhân làm hỏng** — mở khung tường thuật cho
   5 lần rebirth của MVP và nội dung post-MVP.
- **Zone**: khu vực mới quanh vết nứt (nội dung endgame — thiết kế chi tiết khi tới Phase nội
  dung tương ứng).

---

## §7. Ánh xạ lore ↔ hệ thống game

Bảng bắt buộc — **feature không có dòng ở đây thì chưa được đặt tên/viết thoại**; thêm feature
mới = thêm dòng cùng PR (như luật GLOSSARY §4):

| Hệ thống trong game | Lý do tường thuật |
|---|---|
| Hub an toàn (Thanh Hư Tông) | Trận pháp hộ sơn ngàn năm — trong tông không thể động thủ |
| Trạm tiếp ứng dọc đường (`waypost_*`, 6 chỗ) | Tông môn chia trận pháp hộ sơn thành từng góc nhỏ, dựng dọc đường ra rìa Hoang Vực cho đệ tử ngoại môn đi lĩnh phần tài nguyên có chỗ nghỉ chân. Trong vòng trận không ai động thủ được — nhưng trận chỉ che người đang NGHỈ: hễ đệ tử rút kiếm đánh ra thì trận thôi nhận, đó là lý do nó không thành chỗ bắn lén. Trận nhỏ nên KHÔNG hồi được tu vi như trong tông; người ta phải tự ngồi lại sức |
| 5 Linh Mạch (zone luyện cấp theo hệ) | 5 linh mạch ngũ hành giao dưới núi Thanh Hư; yêu thú tụ theo thuộc tính |
| Hoang Vực (PvP mở) | Vùng linh mạch chết, ngoài vòng luật lệ mọi tông môn. _Trạng thái code (2026-09-24, chủ dự án quyết): code CHƯA có vùng Hoang Vực nên PvP đang TẠM CẤM ở mọi World trừ Arena (`WorldFacade#allowPlayerVersusPlayer`); khi dựng Hoang Vực, mở PvP bằng một loại zone mới — thiết kế ở dòng này không đổi._ |
| Bí Cảnh (dungeon instance) | Di tích Linh Triều bị phong ấn, "thức dậy" từ arc 3 do vết nứt Thiên Môn rỉ linh khí |
| Yêu Vương (world boss theo lịch) | Yêu thú biến dị đứng đầu một vùng, định kỳ tràn xuống |
| Hình nộm luyện công (`training_dummy`) | Bãi luyện công ngoại môn của tông |
| Tán tu cường đạo (`wandering_bandit`) | Tu sĩ không tông môn, sống bằng cướp ở rìa linh mạch |
| Thạch quái (`stone_ogre`) | Máy móc hộ mạch của Linh Triều mất kiểm soát sau Đại Kiếp |
| Thạch vệ Tàng Thư (`archive_warden`) | Cùng dòng máy móc Linh Triều, bản nhỏ dựng để canh thư khố; chủ chết từ ngàn năm trước, chúng vẫn canh — nên đánh cả người của tông |
| Trắc Linh Thạch Nhân (`trial_construct`) | Cũng dòng máy móc Linh Triều, nhưng là cỗ tông môn NHẶT VỀ VÀ SỬA LẠI để làm bạn đánh trong kỳ khảo hạch: nó đo linh khí đệ tử duy trì được, nên đánh nhẹ mà rất dai. Đứng ở sân khảo hạch sau lưng Tàng Thư Đường cũ |
| Tán Tu Đoạt Mạch (`vein_raider`) | Cùng dòng với tán tu cường đạo, một bậc trên: thay vì cướp dọc đường thì CHIẾM hẳn một linh mạch sơ cấp của tông môn và hút linh khí ở đó. Đệ tử ngoại môn đi lấy phần khoáng hàng tháng phải đi vòng — đó là lý do tông môn phải dẹp, và là việc đầu tiên giao cho người vừa thức tỉnh linh căn |
| Thám Tử Huyết Sa (`blood_sand_scout`) | Tai mắt Huyết Sa cắm ở rìa linh mạch của tông — không đánh chính diện, việc của chúng là đếm người ra vào và đánh dấu ai đi một mình. Đệ tử ngoại môn gặp chúng trước khi gặp bất cứ thứ gì mang tên Huyết Sa |
| Chấp sự Hình Vân Sơn (`blood_sand_enforcer`) | Hộ pháp Huyết Sa cải trang làm chấp sự Hình Đường suốt nhiều năm — kẻ ký đóng hồ sơ "bỏ trốn" cho chính những đệ tử hắn bắt. Cao trào arc 2; hạ được thì hắn tự tan tu vi nên manh mối đứt |
| Tế Đồ Huyết Sa (`blood_sand_acolyte`) | Môn đồ thường của giáo, tu bằng cách hút tu vi kẻ khác (§4.3). Sau khi chấp sự tự tan tu vi, chúng quay lại vùng mạch chết phía đông tìm thứ hắn bỏ dở — và bắt người dọc đường làm "nhiên liệu" |
| Đàn Chủ Huyết Sa (`blood_sand_ringleader`) | Kẻ cầm đầu toán tế đồ trên, một bậc trên chúng. Không phải hộ pháp — chỉ là đàn chủ một toán nhỏ, nên hạ được hắn thì toán tan chứ giáo không suy suyển gì |
| Đầu Lĩnh Đoạt Mạch (`raider_chieftain`) | Kẻ cầm đầu đám trên, đóng ở chỗ mạch cạn phía đông. Một tán tu có bản lĩnh thật nhưng không tông không môn — hạ được hắn thì đám kia tự tan, và thân phận tạp dịch của người chơi chấm dứt (nhịp đóng arc 1) |
| Yêu Nhân Hoang Vực (`wasteland_beast`) | Tu sĩ bị mạch chết làm biến dị ở rìa Hoang Vực — vùng linh mạch chết ngoài vòng luật lệ. Đệ tử ngoại môn bị cắt phần tài nguyên theo thứ hạng phải tự ra đây kiếm, và đó là lý do tường thuật của quãng Lv28–36 |
| Trắc Trúc Cơ Thạch Nhân (`foundation_warden`) | Cùng dòng máy móc Linh Triều với `trial_construct` của khảo hạch Lv10, nhưng là cỗ tông môn dựng riêng cho kỳ khảo hạch Trúc Cơ — một bậc trên, và là đối thủ của nhịp ĐÓNG arc 2 |
| Bộ "Trúc Cơ" (`foundation_*`, 6 món, Lv30) | Đồ tông môn cấp cho đệ tử sắp vào khảo hạch Trúc Cơ. Không bán ở Tàng Bảo Các: nó là ĐIỀU KIỆN CẦN của hai nhịp cuối, bán đi là cho phép mua đường tắt qua chính chuỗi quest dựng ra nó |
| Trắc Nội Môn Thạch Nhân (`inner_trial_construct`) | Cùng dòng máy móc Linh Triều với hai cỗ khảo hạch trước — cỗ cuối cùng tông môn sửa lại, dành riêng cho kỳ khảo hạch nội môn (mở arc 3) |
| Tế Đồ Trà Trộn (`blood_sand_infiltrator`) | Tế đồ Huyết Sa khoác áo đệ tử lẻn vào tông; trong người mang lịch khảo hạch mà chỉ hàng trưởng lão được xem — vật chứng của nội gián (twist arc 3, danh tính giữ tới arc 5) |
| Bạch Kính Chi (`bach_kinh_chi`) | Đối thủ ở đại bỉ võ toàn tông — trận PvE scripted, mốc "phàm nhân vượt thiên tài" (§5) |
| Đàn Chủ Huyết Sa — Tế Đàn (`blood_sand_altar_master`) | Đàn chủ dẫn toán tập kích lễ bỉ võ, bắt hạt giống Tử Phủ làm nhiên liệu cho tế đàn (cao trào arc 3) |
| Bộ "Nội Môn" (`inner_sect_*`, 6 món, Lv37) | Y phục tông môn trao cho người qua khảo hạch nội môn — điều kiện cần của các trận sau, không bán ở Tàng Bảo Các |
| Pháp bảo (ô ARTIFACT, ADR-127) | Pháp khí tự luyện ở Luyện Khí Đường, mang một thần thông nhỏ (khiên, hồi xuân, ngự phong) |
| Trận pháp nhóm (ADR-128) | Đệ tử Thanh Hư học được cả 5 hệ (§4.1) nên đứng gần nhau là tự kết trận ngũ hành — càng đủ hệ càng vững |
| Thợ rèn Luyện Khí Đường (`sect_blacksmith`, ADR-131) | Người của Luyện Khí Đường (§4.1) giữ lò rèn trong sân tông — vô danh như người trực trạm |
| Thương nhân du hành (`traveling_merchant`, ADR-118/131) | Người của thương hội liên tông đi buôn một vòng các trạm tiếp ứng; chỉ đứng ở trạm đang ghé |
| Động phủ (ADR-129) | Đệ tử được khai một động phủ nhỏ bên một tiểu linh mạch; linh khí tụ lại thành linh thạch theo thời gian |
| Tà Tu Hoang Vực (`wasteland_marauder`) | Tu sĩ không tông không phái chặn cửa ngõ Hoang Vực sâu — trận mở arc 4, nơi không có luật |
| Quật Mạch Đồ Huyết Sa (`blood_sand_digger`) | Đồ chúng Huyết Sa đào bới quanh linh mạch đã chết, không lấy linh thạch mà "tìm thứ gì đó" (arc 4 chỉ được nói tới đây — §8 quy tắc 2) |
| Cai Ngục Huyết Sa (`blood_sand_beast_warden`) | Kẻ giam yêu tộc khai trí làm chó dò mạch — twist arc 4: yêu tộc cũng là nạn nhân Đại Kiếp (§4.4) |
| Cuồng Mạch Yêu Thú (`frenzied_vein_beast`) | Yêu tộc hoá cuồng vì mạch chết lan tới đất của chúng — "yêu thú" với người, "anh em đã mất" với đồng loại |
| Cổ Thạch Quái Hộ Mạch (`ancient_stone_colossus`) | Máy hộ mạch Linh Triều (dòng `stone_ogre` nâng cấp) Huyết Sa đánh thức dưới mạch chết — cao trào arc 4, người chơi + Bạch Kính Chi + yêu tộc liên thủ |
| Bộ "Kim Đan" (`golden_core_*`, 6 món, Lv55) | Y phục tông môn trao cho đệ tử Kim Đan khi xuống núi lịch luyện — điều kiện cần của các trận sau, không bán ở Tàng Bảo Các |
| Chết → hồi sinh tại hub | Đệ tử mang **hộ mệnh ngọc** của tông: thân tan thì hồn về núi, tông môn tái tụ thân thể (tổn thất = rơi tu vi/đồ tuỳ cơ chế) |
| Nhiệm vụ ngày | Công việc tông môn: gánh nước, tuần sơn, hái linh thảo, trông lò luyện đan |
| Nhiệm vụ bang | Nhiệm vụ Đường/bang hội giao theo tuần |
| Cường hoá (tôi luyện) thất bại | Linh khí phản phệ khi tôi luyện pháp khí — có **Hộ Linh Phù** thì phù nát thay đồ |
| Cửa hàng (Tàng Bảo Các) | Kho trao đổi của tông — đệ tử dùng linh thạch đổi vật tư |
| Thư (mail) | Phi kiếm truyền thư — phí gửi kèm đồ là "phí ngự kiếm" (gold sink) |
| Trade mặt đối mặt | Trao đổi trực tiếp giữa đạo hữu |
| Đấu giá (post-MVP) | Thương hội liên tông tổ chức — thuế là phí bảo lãnh (gold sink) |
| Luận Kiếm Đài (Arena 1v1) / Chiến trường 5v5 | Minh ước giao lưu Thanh Hư ↔ Bách Luyện; đấu tới "điểm dừng", không tổn hại thật |
| Bang hội + chiếm Linh Mạch | Đệ tử tự lập hội theo chí hướng; tông cho phép các hội tranh quyền quản lý linh mạch nhỏ |
| Bảng xếp hạng | Bia Anh Danh trước quảng trường tông môn |
| Chat khu vực/thế giới | Truyền âm phù (gần) / trận truyền âm của tông (xa) |
| Cưỡi phi kiếm (mount, cosmetic) | Ngự khí phi hành — dáng bay là phong thái, không phải sức mạnh |
| Thời trang | Đạo bào, pháp bào — "y quan là thể diện tông môn" |

---

## §8. Quy tắc viết quest bám cốt truyện

1. **Mỗi chuỗi quest thuộc đúng 1 arc** (§6). File script quest ghi comment header:
   `// STORY: arc-<n> §<mục>` — reviewer đối chiếu được. Quest không chỉ ra được nó thuộc arc
   nào → không merge.
2. **Không nói trước bí mật**: nhịp hé lộ Đại Kiếp theo §6 (arc 1 gieo → 3 nghi → 5 lộ → 6 chọn).
   NPC không được thoại vượt nhịp, kể cả úp mở "sâu" hơn arc hiện tại.
3. **Thuật ngữ** tra [GLOSSARY.md](../GLOSSARY.md), không tự chế; tên riêng mới phải nguyên bản
   (Điều 5) và thêm vào §4/§5 tài liệu này **cùng PR**.
4. **Xưng hô đúng vai vế**: đạo hữu (ngang vai lạ), sư huynh/tỷ/đệ/muội (cùng tông), tiền bối/vãn
   bối (chênh cảnh giới), lão phu/bản toạ (trưởng lão/ma đầu tự xưng). Tone trang trọng vừa phải,
   được phép hài nhẹ ở quest ngày, KHÔNG meme.
5. **Nhiệm vụ ngày cũng phải có lý do tường thuật** (§7 — công việc tông môn), nhưng không cần
   cốt truyện riêng.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | STORY.md là SSOT tường thuật — quest/NPC/tên vùng phải map về đây | ✅ Accepted (2026-09-01, cùng ADR-038) |
| 2 | Bí mật Thiên Địa Đại Kiếp (Thiên Môn vỡ do Linh Triều ép mở) là trục xuyên suốt 6 arc | ✅ Accepted (2026-09-01, bố cục); chi tiết v0.2.0 chờ duyệt |
| 3 | 6 arc = 6 cảnh giới, khớp [ADR-038 D2](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) | ✅ Accepted (2026-09-01) |
| 4 | Rebirth có lời giải tường thuật: mọi độ kiếp đều thất bại vì Thiên Môn hỏng → Trùng Tu Giả | 🟡 v0.2.0 — chờ duyệt |
| 5 | Tô Vân Nhi phản bội (arc 5) là twist bị ép buộc, không rẽ nhánh cơ chế ở MVP | 🟡 v0.2.0 — chờ duyệt |
| 6 | Yêu tộc không thuần ác — phe thứ ba, mở quest phe phái Post-MVP | 🟡 v0.2.0 — chờ duyệt |

## ADR cần tạo

Không — tường thuật thuộc thẩm quyền GDD/STORY. [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) (Accepted) đã bao quyết định re-theme.

## Tài liệu liên quan

- [GAME-DESIGN-DOCUMENT.md](GAME-DESIGN-DOCUMENT.md) — cơ chế mà lore phải giải thích
- [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ bắt buộc
- [SCRIPTING.md](../06-plugin/SCRIPTING.md) — quest viết bằng script sandbox

## Việc cần làm tiếp theo

1. ~~Chủ dự án đọc + duyệt nội dung v0.2.0~~ ✅ **XONG 2026-09-05 — duyệt nguyên trạng**, không sửa mục nào. Tài liệu lên **v1.0.0**. Từ đây mọi thay đổi §1–§6 (tiền đề, bí mật trục chính, thế lực, nhân vật, arc) là **đổi SSOT tường thuật** — phải hỏi lại chủ dự án, không tự sửa; §7/§8 thì thêm dòng cùng PR như luật đã ghi.
2. ✅ **Arc 1 đã có mặt trong game** (Đợt 2 ADR-038, 2026-09-01): Chu Hoành nói đúng giọng §5; cây hội thoại `town_elder.js` phủ §6 arc 1 (tông môn → cảnh giới → thân thế trưởng lão); quest `kill_training_dummies` khai `// STORY: arc-1 §6` theo §8. Nhịp hé lộ §8 quy tắc 2 được **gác bằng test** (`TownElderDialogueTreeTest`).
3. ✅ **Ba nhịp của arc 1 đã có mặt trong game**: việc vặt mở màn (`kill_training_dummies`, V11), cao trào "đêm trực gặp tán tu cường đạo" (`night_watch_bandit`, V28, 2026-09-05), và nhịp gieo mầm "dọn góc Tàng Thư Đường bỏ hoang → nhặt mảnh ngọc cổ" (`clear_old_archive`, V30, 2026-09-06). Thứ tự được thi hành bằng máy qua `prerequisite_quest_id` ([ADR-044](../01-architecture/adr/ADR-044-Quest-Prerequisite-Enforcement.md)). 🔶 Còn thiếu của arc 1: khảo hạch thức tỉnh linh căn (Lv10) — mốc đóng arc. Arc 2 trở đi chưa có nội dung nào.
4. Khi thêm NPC/địa danh/thế lực mới trong quest → cập nhật §4/§5 cùng PR.
