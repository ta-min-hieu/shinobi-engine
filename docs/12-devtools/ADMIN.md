# ADMIN.md

| | |
|---|---|
| Status | ✅ **Lệnh GM trong game đã có** (ADR-052, Accept + thi hành 2026-10-18). Phần "chưa viết" bên dưới giữ làm lịch sử. |

---

## Lệnh GM trong game — cách dùng (ADR-052)

**Bật** (mặc định TẮT): `KAGE_GM_ENABLED=true` và (tuỳ chọn) `KAGE_GM_ALLOWED_CIDRS=127.0.0.1/32,::1/128,<IP nhà>/32`.
Giá trị lạ của `KAGE_GM_ENABLED` ⇒ server từ chối khởi động. Chạy trong Docker có NAT thì địa chỉ nguồn
có thể là IP gateway của Docker — kiểm trước khi mở rộng CIDR.

**Cấp quyền** — CHỈ bằng SQL tay, cùng transaction với một dòng audit (không lệnh nào trong game đổi quyền):

```sql
BEGIN;
INSERT INTO gm_role (account_id, role, granted_at, note) VALUES (<account_id>, 'GM', now(), '<ai cấp, vì sao>');
INSERT INTO gm_audit_log (actor_account_id, actor_role, command, command_line, outcome, created_at, completed_at)
     VALUES (<account_id người cấp>, 'ADMIN', 'role_grant', 'SQL tay: cấp GM cho <account_id>', 'OK', now(), now());
COMMIT;
-- Thu quyền: DELETE FROM gm_role WHERE account_id = <id>; + một dòng 'role_revoke' như trên.
```

Quyền đọc lại MỖI lệnh — thu quyền có hiệu lực ngay lệnh kế tiếp.

**Lệnh** (gõ trong ô chat, mọi kênh; dòng `/gm` không bao giờ được phát đi). Người không có quyền gõ `/gm`
không nhận gì (server không xác nhận lệnh tồn tại). Trần 5 lệnh / 10 giây mỗi kết nối.

| Lệnh | Cấp | Làm gì |
|---|---|---|
| `/gm help` | GM | Danh sách lệnh |
| `/gm who <tên>` | GM | id, tài khoản, cấp, vàng, online? |
| `/gm kick <tên>` | GM | Đá phiên — client nhận `error.kicked_by_gm`, KHÔNG tự đăng nhập lại |
| `/gm mute <tên> <phút 1-10080>` | GM | Khoá chat theo TÀI KHOẢN (`chat_mute`); người bị khoá nhận outcome `MUTED` |
| `/gm unmute <tên>` | GM | Mở khoá |
| `/gm announce <nội dung ≤200>` | GM | Dòng hệ thống tới mọi người online (qua lọc từ cấm) |
| `/gm heal <tên>` | GM | Hồi đầy máu (đang ở trong một World) |
| `/gm inspect <tên>` | GM | Một dòng: mọi món trong túi (`mã`, `xSố`, `+cường hoá`), món đang mặc có `*` — chỉ đọc |
| `/gm goto <tên>` | GM | Dời CHÍNH mình tới chỗ người đó đang đứng (có thể đổi khu vực) |
| `/gm summon <tên>` | GM | Kéo người đó tới chỗ mình đang đứng — gỡ người kẹt địa hình; họ thấy dòng báo "ban quản lý dời" |
| `/gm give <tên> <mã_vật_phẩm> <1-99>` | **ADMIN** | Thư hệ thống kèm vật phẩm |
| `/gm gold <tên> <1-1000000>` | **ADMIN** | Thư hệ thống kèm vàng |

Không tác động lên người có cấp ≥ mình (trừ `who`/`heal` chính mình). `goto`/`summon` chỉ chạy khi CẢ HAI
người đang ở world chính (không ở bí cảnh/đấu trường); người bị dời vào chỗ mới với máu đầy (mọi lần đổi
World đều vậy — ADR-051) và có thể vượt trần người của khu vực đích đúng một người.

**Audit**: mọi lệnh của người có quyền ghi `gm_audit_log` TRƯỚC khi có hiệu lực (`PENDING`), chốt một lần
(`OK`/`REJECTED`/`DENIED`/`FAILED`). Không bao giờ xoá bảng này. Truy vấn mẫu:
`SELECT created_at, command_line, outcome, outcome_detail FROM gm_audit_log ORDER BY id DESC LIMIT 50;`

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
[ART-PIPELINE §8.3 · §8.6 · §8.7 · §8.8](../14-art/ART-PIPELINE.md) và dựa trên bảng `art_asset` đã
thi hành thật (`V19` cho nhánh sprite, `V20` cho nhánh model glTF — cả hai nhánh đã có dữ liệu thật
trong bảng). Khi viết file này, **trỏ sang đó chứ đừng chép lại** — hai bản mô tả cùng một màn hình
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

## Câu hỏi trước khi viết — ~~🔴 **đây mới là thứ đang chặn**~~

> ✅ 2026-10-02: cả ba câu đã có HƯỚNG — chủ dự án chọn 2026-10-01 (`30db3f8`, [docs/README.md](../README.md) dòng 6):
> **lệnh in-game** `/gm`, **hai cấp quyền** GM/ADMIN, **nhúng trong server**. Chi tiết nằm ở
> [ADR-052](../01-architecture/adr/ADR-052-In-Game-GM-Commands.md) — vẫn **Proposed**, nên tài liệu này vẫn chờ ADR đó Accepted.

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
