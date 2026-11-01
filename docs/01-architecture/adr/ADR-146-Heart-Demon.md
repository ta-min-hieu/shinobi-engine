# ADR-146: Tâm ma khi độ kiếp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "tâm ma khi đột phá" trong lượt hỏi lô 26 (AskUserQuestion, nhóm Gameplay). Chi tiết và mọi con số do AI chọn — **đề xuất cân bằng**. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn và chọn chi tiết |
| Related | [ADR-126](ADR-126-Heavenly-Tribulation.md) (Thiên Kiếp — đài độ kiếp là World riêng một người) · [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) · [STORY.md](../../13-game-design/STORY.md) §2 ("tâm ma — chấp niệm của chính mình phản phệ trong lúc đột phá") |

---

## Context

STORY §2 nêu hai cái giá của đột phá: thiên kiếp và **tâm ma**. ADR-126 mới làm nửa đầu — độ kiếp chỉ là "chịu N đợt sét
và uống đan". Không có gì để người chơi *làm* trong đài.

## Decision

**D1 — Khi nào**: từ **kiếp thứ hai** (đã qua ≥ 1 kiếp — đột phá Tử Phủ trở lên). Kiếp đầu (Luyện Khí → Trúc Cơ) giữ
nguyên, để người mới chỉ phải học một thứ. Tâm ma hiện **ngay sau đợt lôi kích thứ 2** (`HEART_DEMON_AFTER_WAVE`), cách
**điểm vào đài** 2 m (người độ kiếp vào ở gốc toạ độ như mọi hầm ngục) và **săn khắp đài** (aggro = leash = 40 m).
Nó phải săn: sét ngừng khi nó còn sống, nên nếu nó chỉ thủ chỗ thì người chơi đứng xa sẽ hồi máu ngoài chiến đấu tới
đầy rồi mới đánh — kiểm sống thấy đúng chuyện đó (máu 168 → 278 trong lúc chờ).

**D2 — Lôi kiếp ngừng chờ**: khi tâm ma còn sống không có đợt sét nào. Hạ được nó ⇒ sét tiếp tục sau đúng một nhịp
(6 giây). Quá **60 giây** (`HEART_DEMON_SECONDS`) mà tâm ma còn sống ⇒ thua kiếp, cùng hình phạt ADR-126 D3. Gục dưới
tay tâm ma ⇒ đường gục có sẵn của Bí Cảnh ⇒ thua như cũ. Tổng số đợt sét KHÔNG đổi.

**D3 — Bốn bậc** `heart_demon_1..4` (V118, bậc = số kiếp đã qua), không EXP, không rơi đồ (bảng rơi RỖNG cùng mã — quy ước V33),
model riêng mỗi bậc `mob_heart-demon-1..4` (`tools/blender/build_monster_heart_demon.py`: tu sĩ hoá bóng tối, tóc/
mắt/khăn/đai/dải lụa ánh màu cảnh giới — tím/vàng/xanh băng/trắng):

| Bậc | Cấp | Máu | Công | Thủ | Kháng | Biên thật |
|---|---|---|---|---|---|---|
| 1 | 36 | 240 | 34 | 38 | 28 | +3 |
| 2 | 54 | 300 | 46 | 50 | 38 | +2 |
| 3 | 72 | 380 | 64 | 64 | 48 | +3 |
| 4 | 90 | 420 | 70 | 72 | 54 | +2 |

Chọn từ luật biên thật (`HeartDemonBalanceTest`): máu lúc chạm mặt = máu tối đa trừ hai đợt sét (60%), không đan dược,
mặc bộ cao nhất mặc được ở trần cấp đó. Tâm ma vì vậy YẾU hơn quái thường cùng cấp — nó đánh vào lúc người chơi đã mất
4/10 máu.

**D4 — Giao thức, chỉ thêm**: `TribulationEventKind` thêm `HEART_DEMON_APPEARED` (4) và `HEART_DEMON_SLAIN` (5);
`TribulationEvent` thêm `seconds_left` (field 4). Không opcode mới, không tăng `protocolVersion`. Client cũ hiện khoá
i18n thiếu cho hai loại mới — chấp nhận được, cùng gói 332.

**D5 — Đường phát hiện hạ tâm ma**: quái chết trong hầm ngục đi qua `DungeonInstanceRegistry.onMonsterKilled`; mã
`heart_demon_*` ⇒ `TribulationHooks.onHeartDemonSlain` (Virtual Thread, compare-and-set trên trạng thái kiếp trong
RAM). Trạng thái vẫn chỉ ở RAM như ADR-126 D5.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tâm ma từ kiếp 2, sau đợt 2; sét ngừng tới khi hạ; 60 giây; bốn bậc theo luật biên thật | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)
- [ADR-126](ADR-126-Heavenly-Tribulation.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: `TribulationRules` (`hasHeartDemon`/`heartDemonCode`/`isHeartDemon`, có test),
   `TribulationHooks` (trạng thái `demonAlive`, `onHeartDemonSlain`), `DungeonInstanceRegistry.onMonsterKilled`, V118,
   `HeartDemonBalanceTest`, chuỗi i18n vi/en, client truyền `seconds_left` vào dòng hệ thống.
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01** (bước probe mới `dokiep:<giây>` tự đánh tâm ma, `dokiepIdle:<giây>` đứng
   yên; DB dev V118):
   - **Thắng trọn**: tanthua44 (Cấp 36, đã qua 1 kiếp) ⇒ `STRIKE 1/4`, `STRIKE 2/4` ⇒ `HEART_DEMON_APPEARED 2/4 60s` +
     tâm ma `heart_demon_1` máu 240 ⇒ hạ ⇒ `HEART_DEMON_SLAIN` ⇒ `STRIKE 3/4`, `STRIKE 4/4` ⇒ 308 MAIN ⇒ Cấp 37 ⇒
     `PASSED 4/4`. ⚠️ Lượt này chạy nhầm trên server CŨ (bản mới chết ở cổng 9404 — bẫy "server cũ trả lời probe"),
     catalog lúc đó đang hạ công tâm ma bậc 1 xuống 1 để thử nhánh hết giờ; đường sự kiện là code mới cả, chỉ số tâm ma
     thì không.
   - **Hạ tâm ma bậc 3** (catalog đúng số V118): Kagemaru (Cấp 72, bộ Kim Đan) ⇒ tâm ma đánh **30/nhát** (luật biên dự đoán 31),
     Kagemaru mất 240 máu trong 7 giây rồi hạ nó ⇒ `SLAIN` ⇒ sét tiếp. Sau đó gục ở đợt 4/6 vì probe không uống đan
     (kiếp này 6 × 20% = 120% máu — ADR-126 vốn đã bắt buộc dùng đan). EXP đã trả lại.
   - **Hết 60 giây**: `dokiepIdle` ⇒ `HEART_DEMON_APPEARED` rồi đúng 60 giây sau `FAILED 2/4`, trừ EXP.
   - **Gục dưới tay tâm ma**: Benkei (Cấp 36 nhưng mặc đồ Cấp 19 — yếu hơn mức luật biên giả định) ⇒ tâm ma đánh 27,
     Benkei gục ⇒ 308 MAIN ⇒ `FAILED 2/4`, EXP −360 (= 10% của 3.600).
   - 🐛 **Kiểm sống tìm ra lỗi, đã sửa**: bản đầu sinh tâm ma ở `lastKnownPosition` của registry — đó là vị trí theo nhịp
     của WORLD CHÍNH, nên nhân vật đứng x=165 ở world chính làm tâm ma sinh ở x=167 trong đài, ngoài tầm nhìn (lượt
     `dokiepIdle` không thấy nó). Nay sinh ở điểm vào đài + săn 40 m; thử lại với Benkei đặt x=165: tâm ma hiện và ra
     tay ngay ở giây 12.
3. ⚠️ Chủ dự án xem: mọi con số D1–D3 do AI chọn. Tâm ma rút thêm ~⅓ máu ở kiếp sau ⇒ cần thêm đan dược ngoài số
   ADR-126 đã đòi. Model chưa chấm bằng ảnh render đối chứng ở camera client.
