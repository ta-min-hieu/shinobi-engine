# ADMIN.md

| | |
|---|---|
| Status | 🚧 **STUB — CÓ CHỦ ĐÍCH** |
| Blocked by | **3 câu hỏi thiết kế chưa có lời đáp** (xem "Câu hỏi trước khi viết" bên dưới) — chủ dự án quyết, không phải AI |
| Sẽ viết khi | Có câu trả lời cho 3 câu hỏi đó |

---

## Vì sao chưa viết

> ⚠️ **Lý do chặn đã đổi (cập nhật 2026-08-05).** File này từng ghi "chờ tới Phase 8–9, cần server có
> người chơi thật". Điều kiện đó **đã qua từ lâu** — Phase 8 và 9 đóng xong, dự án đang ở
> [Phase 10 Release](../11-process/ROADMAP.md). Thứ thật sự còn chặn là **3 câu hỏi thiết kế ở cuối
> file**, không phải tiến độ. Chúng là quyết định của chủ dự án ([/CLAUDE.md §4](../../CLAUDE.md)),
> nên viết bừa một thiết kế lúc này là bịa ([AI-WORKING-RULES §13–14](../00-governance/AI-WORKING-RULES.md)).

Admin tool (GM command, dashboard, quản lý người chơi) chỉ cần khi **có người chơi để quản lý** — điều kiện đó nay đã thoả.

⚠️ **Một phần trang quản trị ĐÃ được thiết kế đầy đủ ở nơi khác**: mục quản lý kho đồ hoạ (duyệt asset
theo cây danh mục, xem sprite/model, so tier, kiểm license) nằm ở
[ART-PIPELINE §8.3 · §8.7 · §8.8](../14-art/ART-PIPELINE.md) và dựa trên bảng `art_asset` đã thi hành
thật (`V19`). Khi viết file này, **trỏ sang đó chứ đừng chép lại** — hai bản mô tả cùng một màn hình
sẽ lệch nhau ngay lần sửa đầu tiên.

---

## Thứ cần sớm nhất (Phase 4–6 — playtest nội bộ)

Không phải cả bộ admin — chỉ thứ **bắt buộc để playtest nội bộ** khi combat/inventory chơi được:

| Cần sớm | Vì sao |
|---|---|
| **GM command cơ bản** | Teleport, give item, set level — để test |
| **Kick/ban** | Xử lý người phá |
| ⚠️ **Xem audit log** | Điều tra "tôi mất đồ" ([SECURITY.md §9](../08-security/SECURITY.md)) |

Thứ để **sau**: dashboard đẹp, analytics, quản lý hàng loạt.

---

## ⚠️ GM tool là bề mặt tấn công nghiêm trọng

| Rủi ro | Chặn |
|---|---|
| GM command bị lạm dụng | ⚠️ **Mọi GM action bị audit** — kể cả GM ([SECURITY.md §9](../08-security/SECURITY.md)) |
| Tài khoản GM bị chiếm | 2FA bắt buộc cho GM; quyền tối thiểu |
| "give item" tạo duplicate | GM give cũng qua transaction + audit |
| GM endpoint lộ ra ngoài | Tách mạng, không expose internet |

⚠️ **GM tool KHÔNG dùng Spring MVC** ([ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md)). HTTP endpoint nhẹ hoặc trên Netty sẵn có.

---

## Bố cục dự kiến (khi viết)

1. GM command (in-game + web)
2. Dashboard: người chơi online, server health ([OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md))
3. Player management: xem, kick, ban, hoàn item
4. Audit log viewer
5. Phân quyền GM (role-based)
6. Bảo mật GM tool

## Câu hỏi trước khi viết — 🔴 **đây mới là thứ đang chặn**

- [ ] **GM tool là in-game command, web app, hay cả hai?** Ảnh hưởng lớn nhất: web app kéo theo một
      tầng HTTP + xác thực riêng ([ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md) cấm
      Spring MVC nên phải tự dựng trên Netty sẵn có), còn in-game command thì gần như miễn phí nhưng
      không hiển thị được kho đồ hoạ (§ART-PIPELINE 8.7 giả định một trang web).
- [ ] **Cần bao nhiêu cấp quyền GM?** 1 cấp (chỉ chủ dự án) là đủ cho Closed Beta người quen; nhiều
      cấp chỉ cần khi có người ngoài hỗ trợ vận hành.
- [ ] **Tách service riêng hay nhúng vào server?** Nhúng thì đơn giản nhưng làm bề mặt tấn công của
      server game rộng ra; tách thì phải giải bài toán truy cập DB và triển khai riêng.

> Ba câu này nằm ở mục "🔴 ĐANG CHỜ CHỦ DỰ ÁN" trong [docs/README.md](../README.md) khi được ưu tiên.
> Hiện chúng **không chặn Phase 10** — Closed Beta người quen vận hành được bằng truy vấn SQL tay.

## Tài liệu liên quan

- [SECURITY.md §9](../08-security/SECURITY.md) — audit, GM bị audit
- [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — dashboard health
- [ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md) — không Spring MVC

## Việc cần làm tiếp theo

1. Phase 4–6: GM command tối thiểu (teleport/give/setlevel/ban) + audit — **bắt buộc để playtest nội bộ**.
2. Viết đầy đủ tài liệu này khi có nhu cầu vận hành thật.
