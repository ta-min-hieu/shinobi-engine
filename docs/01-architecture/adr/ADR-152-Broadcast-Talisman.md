# ADR-152: Truyền Âm Phù — loa thế giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Truyền Âm Phù (loa thế giới)" trong lượt hỏi lô 28 (AskUserQuestion; mô tả kèm: vật phẩm bán ở cửa hàng: /loa <nội dung> gửi một dòng nổi bật tới mọi người online — qua lọc từ cấm, giới hạn tần suất, audit). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) (`/gm announce`, khoá chat) · dòng 71(e) (audit nội dung chat) |

---

## Decision

**D1 — Vật phẩm (V123)**: `broadcast_talisman` (Truyền Âm Phù, xếp chồng 20), bán ở cửa hàng tông môn **2.000** linh
thạch (bán lại 200). PLACEHOLDER (Điều 27).

**D2 — Lệnh riêng, kiểm như chat**: 810/811 `BroadcastMessageCommand{body}` / `BroadcastMessageResponse{outcome,
seconds_left}`. Thứ tự: hồi chiêu **30 giây** mỗi nhân vật (RAM) → khoá chat (`ChatFacade.isMuted`, theo tài khoản) →
tiêu một phù → kiểm nội dung + ghi audit qua ĐÚNG `ChatFacade.sendMessage` với kênh miền mới `BROADCAST` (trống, quá
dài, từ cấm — cùng luật chat thường) → bị từ chối thì **trả lại phù** (`ConsumableFacade.refund`) → phát
`ChatMessageEvent` kênh mới `CHAT_CHANNEL_BROADCAST = 7` cho MỌI người online (kèm danh hiệu đang đeo). Nội dung bị
ép về một dòng (`ChatMessage.singleLine`) như `/gm announce`.

**D3 — Không đi lệnh chat thường**: `SendChatMessageCommand` với kênh BROADCAST rơi vào nhánh "kênh lạ" sẵn có ⇒
`error.invalid_chat_channel` — không thể phát loa miễn phí. Không đụng `ChatRateLimits` (phù + hồi chiêu đã là trần).

**D4 — Client**: lệnh chat `/loa <nội dung>` (hoặc `/shout`); dòng loa vẽ màu cam riêng (`LineKind.BROADCAST`) kèm
tiền tố "[Loa]" — chữ mang nghĩa, màu chỉ phụ (luật UI). Bị từ chối ⇒ một dòng hệ thống nói lý do.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Kênh BROADCAST đi chung `SendChatMessageCommand` | Phải thêm nhánh tiêu phù vào handler chat vốn đã chạm trần độ phức tạp; lệnh riêng tách sạch |
| Không hồi chiêu, chỉ tốn phù | Người nhiều tiền xả loa liên tục làm trôi kênh chat của cả server |
| Banner chạy ngang màn hình | Cần widget mới; dòng chat màu riêng đủ cho bản đầu |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phù tiêu hao 2.000, lệnh 810/811, hồi chiêu 30 s, kiểm + audit qua ChatFacade kênh BROADCAST, từ chối thì trả phù | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): V123, `ChatChannel.BROADCAST`, `BroadcastCommands`, `BroadcastCoordinator` (`/loa`),
   `ChatPanel.LineKind.BROADCAST`, bước probe `shout:<nội dung>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, TLS, DB chạy V123), hai người online: không phù ⇒ `NO_TALISMAN`;
   có 2 phù ⇒ `OK`, người kia nhận `ChatMessageEvent` kênh `CHAT_CHANNEL_BROADCAST` đúng tên + nội dung tiếng Việt, phù
   2→1, audit `CHAT_MESSAGE` `{"channel":"BROADCAST",…}`; gửi lại ngay ⇒ `COOLDOWN` còn 29 s; người khác gửi "đồ ngu" ⇒
   `BANNED_WORD`, phù vẫn còn (đã trả).
