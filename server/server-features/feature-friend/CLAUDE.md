# CLAUDE.md — feature-friend

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-friend/**`.

## Cấu trúc (Clean Arch)

```
api/            FriendFacade (+ SendRequestResult/AcceptResult/RejectResult/RemoveResult/
                 PendingRequestView — enum/record RIÊNG, không lộ type UseCase ra ngoài)
application/    SendFriendRequestUseCase · AcceptFriendRequestUseCase · RejectFriendRequestUseCase
                RemoveFriendUseCase · ListFriendsUseCase · ListPendingFriendRequestsUseCase
                FriendUseCases (gộp 6 UseCase, tránh FriendFacade vượt 4 tham số — Checkstyle
                ParameterNumber, cùng tinh thần ShopPorts/EquipmentUseCases)
domain/         Friendship (aggregate) · FriendshipStatus (enum) · FriendPort
                DuplicateFriendRequestException
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresFriendAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-23, Phase 6 lát cắt 4)

- **Tên → ID**: `FriendFacade` chỉ nhận `characterId`, KHÔNG nhận tên nhân vật. Đổi tên→ID (kết bạn
  theo tên) là việc của **server-gateway** — gọi `CharacterFacade.findByName` TRƯỚC, rồi mới gọi
  `FriendFacade.sendRequest(myId, resolvedId)`. `feature-friend` KHÔNG phụ thuộc Gradle vào
  `feature-character` (không module nào phụ thuộc module feature khác — chỉ gateway compose).
- **Trạng thái online KHÔNG lưu DB.** GAMEPLAY.md §11 gợi ý "qua Redis" nhưng dự án hiện là
  single-instance (ADR-010) và server-gateway đã có `ConnectedCharacterRegistry` in-memory
  (`Map<characterId, ChannelHandlerContext>`) dùng cho mọi push per-player khác (item grant, quest
  status...). Thêm 1 registry Redis riêng chỉ để lặp lại đúng câu hỏi "ai đang online" là trùng lặp
  hạ tầng không cần thiết ở quy mô 1 instance — KHÔNG PHẢI thiếu sót, là quyết định tránh
  over-engineering (Điều 9). Khi có multi-instance thật (ngoài scope hiện tại), đổi điểm này.
- **Từ chối = xoá dòng**, không có trạng thái `REJECTED` — người bị từ chối có thể gửi lại lời mời
  mới bất cứ lúc nào, không cần lịch sử từ chối.
- **Race 2 lời mời ngược hướng** (A mời B đúng lúc B mời A): UNIQUE `(requester_id, addressee_id)`
  của V12 chỉ khoá đúng 1 hướng, nên chặn ở `PostgresFriendAdapter.sendRequest` — khoá tư vấn
  (`pg_advisory_xact_lock`) theo CẶP rồi kiểm cả 2 chiều trong CÙNG giao dịch với INSERT. Bản MVP
  cũ ghi race này "vô hại" — sai: nếu cả hai cùng chấp nhận, mỗi người thấy người kia HAI LẦN trong
  danh sách bạn (2 dòng ACCEPTED). Không đổi schema (một UNIQUE theo `LEAST/GREATEST` là cách triệt
  để hơn nhưng là migration, phải dọn trước các cặp trùng đã có trong DB thật).

## Cứng

✅ Validate tự-mời/đã-bạn/đã-có-lời-mời ở `application/`, trước khi gọi Port
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`Friendship`)
✅ Module khác gọi qua `FriendFacade`, không đọc bảng `friendship` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ `feature-friend` phụ thuộc Gradle vào `feature-character` (xem mục "Tên → ID" ở trên)

## Test

`domain/` test **không mock** (`FriendshipTest`). `application/` test với **Fake Port** (không
Mockito) — mỗi UseCase 1 file test riêng.
