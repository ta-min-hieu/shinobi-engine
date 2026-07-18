# ADMIN.md

| | |
|---|---|
| Status | 🚧 **STUB — CÓ CHỦ ĐÍCH** |
| Blocked by | Cần server chạy có người chơi để quản trị |
| Sẽ viết khi | [Phase 8–9](../11-process/ROADMAP.md) — khi vận hành playtest nội bộ quy mô thật |

---

## Vì sao chưa viết

Admin tool (GM command, dashboard, quản lý người chơi) chỉ cần khi **có người chơi để quản lý**. Trước Phase 3 không có gì để admin.

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

## Câu hỏi trước khi viết

- [ ] GM tool là in-game command, web app, hay cả hai?
- [ ] Cần bao nhiêu cấp quyền GM?
- [ ] Tách service riêng hay nhúng vào server?

## Tài liệu liên quan

- [SECURITY.md §9](../08-security/SECURITY.md) — audit, GM bị audit
- [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — dashboard health
- [ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md) — không Spring MVC

## Việc cần làm tiếp theo

1. Phase 4–6: GM command tối thiểu (teleport/give/setlevel/ban) + audit — **bắt buộc để playtest nội bộ**.
2. Viết đầy đủ tài liệu này khi có nhu cầu vận hành thật.
