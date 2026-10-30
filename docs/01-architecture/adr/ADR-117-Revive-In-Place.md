# ADR-117: Hồi sinh tại chỗ (Hồi Hồn Đan)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Hồi sinh tại chỗ" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: chết thì chọn về điểm hồi sinh như cũ hoặc dùng Hồi Hồn Đan hồi sinh tại chỗ 30% máu, cấm trong Arena/bang chiến; opcode + vật phẩm mới). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-30 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-046 (hồi máu) · ADR-051 (đổi World) · ADR-100 (Truyền Tống Phù — cùng đường tiêu/hoàn vật phẩm) · ADR-102 (bang chiến) |

---

## Decision

**D1 — Nằm chờ thay vì hồi sinh ngay**: gục ở **world chính** thì nhân vật nằm tại chỗ (0 máu, không đi/đánh được) tối đa
**30 giây** chờ chọn. Hết giờ chưa chọn ⇒ tự về điểm hồi sinh đầy máu y như trước ADR-117. Hầm ngục/đấu trường giữ
nguyên luồng riêng (không đổi). Rời World / ngắt kết nối giữa lúc nằm ⇒ huỷ chờ.

**D2 — Hai lựa chọn**: về điểm hồi sinh (đầy máu, miễn phí) hoặc tiêu **một Hồi Hồn Đan** (`revive_pill`, V97, bán ở cửa
hàng tông môn 300/75) để đứng dậy **tại chỗ với 30% máu tối đa** (Chi giữ nguyên, sạch hiệu ứng như hồi sinh thường).
Server tiêu đan trước rồi mới hỏi World; World từ chối (đã tự về, ...) ⇒ hoàn đan.

**D3 — Cấm tại chỗ khi bị NGƯỜI CHƠI hạ** (luận võ ngoài đấu trường, bang chiến, truy nã — mọi đòn kết liễu từ nhân vật
khác): chỉ còn lựa chọn về điểm hồi sinh. Arena đã có luồng kết thúc riêng nên tự nhiên không áp dụng.

**D4 — Giao thức, chỉ thêm**: 321 `AwaitingRevivalEvent{seconds, can_revive_in_place}` cho chính người gục; 322/323
`Revive{in_place}`. Hồi sinh (cả hai cách) vẫn báo bằng `CharacterRespawnedEvent` (305) + `HealthChangedEvent` cho người
đang nhìn — hai đường ống sẵn có. Client: cửa sổ "Trọng thương" tự mở với hai nút (không có nút đan khi bị PvP hạ), đóng
khi nhận 305.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nằm chờ 30 giây ở world chính, Hồi Hồn Đan đứng dậy tại chỗ 30% máu, cấm khi bị người chơi hạ | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-30 (lô 23 phần 1): server-world `PendingRevivals` + `ReviveCommand` + `RevivalListener` (nối riêng
   cho world chính, không thêm phương thức vào sink gateway); feature-world `ConsumableFacade`/`ConsumablePort` (tiêu/hoàn
   vật phẩm theo mã — dùng lại cho lô 23); gateway `RevivalCommands`; client `RevivalCoordinator`. ⚠️ 5 test cũ ghim
   "chết là hồi sinh ngay" đã sửa theo luồng mới (gửi lựa chọn), bất biến fuzz World đổi thành "xác nằm không quá 30
   giây" + "hồi sinh đầy ở điểm hồi sinh HOẶC đúng 30% tại chỗ"; bộ fuzz gửi thêm lệnh hồi sinh trên luồng ngẫu nhiên
   riêng (không xê dịch seed cũ).
2. ✅ **Kiểm sống bằng gameProbe 2026-10-30** (tanthua44 Lv15 đứng giữa hoang mạc x=185): gục ⇒ 321 `tạiChỗ=true`
   chờ 30s; dùng Hồi Hồn Đan ⇒ 305 TẠI CHỖ x=185, máu 61/206 (đúng 30%), đan bị tiêu. Gục lần nữa, không chọn ⇒ đúng
   30,0 giây sau (21638 → 51637 ms) tự về x=0 đầy máu. Gọi hồi sinh khi không nằm chờ ⇒ NOT_DEAD và đan ĐƯỢC HOÀN (túi
   vẫn 1 viên). Nhánh "bị người chơi hạ ⇒ FORBIDDEN" mới kiểm bằng test World (chưa dựng PvP thật trên server dev).
3. ⚠️ Lệch cần chủ dự án xem: trong 30 giây nằm chờ, client vẫn có thể dự đoán di chuyển của chính mình (server bỏ qua
   lệnh đi của xác); vị trí được sửa lại khi hồi sinh.
