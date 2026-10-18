# ADR-052: Lệnh GM trong game — đi qua đường chat, hai cấp quyền, nhúng trong server, mọi lệnh đều audit

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn Accept trong lượt hỏi lô 15 (AskUserQuestion, câu hỏi "ADR đang Proposed từ lâu — Accept để tôi thi hành?"). Các câu hỏi mở được trả lời theo đúng KHUYẾN NGHỊ ghi trong ADR — xem mục "Việc cần làm tiếp theo". _(Proposed 2026-10-01.)_ |
| Date | 2026-10-01 |
| Deciders | Chủ dự án quyết hướng (2026-10-01: **lệnh trong game + HAI cấp quyền GM/ADMIN + nhúng trong server, không tách service + mọi lệnh đều audit**); AI soạn phần thi hành |
| Related | [ADMIN.md](../../12-devtools/ADMIN.md) (stub đang chờ đúng quyết định này) · [SECURITY.md §3, §4, §7, §9](../../08-security/SECURITY.md) · [DATABASE.md §2.7, §5, §9, §10](../../05-server/DATABASE.md) · [ADR-009](ADR-009-Why-No-Spring-Boot.md) (không Spring MVC) · [ADR-017](ADR-017-Auth-Token.md) (đăng nhập username-only) · [ADR-051](ADR-051-World-Changed-Event.md) (`WorldChangedEvent` — Revisit Trigger đã nhắc trước "GM kéo") · [ADR-046](ADR-046-Health-Recovery.md) (`enqueueHealCharacter`) · [ADR-013](ADR-013-Flyway-vs-Liquibase.md) (migration) |

---

## Context

### Câu hỏi đã có lời đáp

Dòng #6 của "🔴 ĐANG CHỜ CHỦ DỰ ÁN" ([docs/README.md](../../README.md)) hỏi ba câu, và
[ADMIN.md](../../12-devtools/ADMIN.md) bị chặn bởi đúng ba câu đó. Ngày 2026-10-01 chủ dự án trả lời:

| Câu hỏi | Trả lời |
|---|---|
| In-game command, web app, hay cả hai? | **Lệnh trong game** |
| Mấy cấp quyền? | **Hai: GM và ADMIN** |
| Tách service hay nhúng? | **Nhúng vào server**, không service riêng |
| (thêm) | **Mọi lệnh đều được audit** |

ADR này biến bốn câu trả lời đó thành thiết kế bám vào code đang chạy. Nó cần ADR vì chạm ba vùng
§4 của [/CLAUDE.md](../../../CLAUDE.md): **schema DB** (bảng quyền + bảng audit), **module mới**, và
**hai giá trị enum mới trên dây** (chỉ THÊM — xem D7).

### Thực tế trong code (đọc 2026-10-01, HEAD `07fec23`)

**1. Đường chat đủ chở một dòng lệnh, và client không cần biết gì.**
`chat.proto`: `SendChatMessageCommand { channel, target_name, body }` (opcode 800) → `SendChatMessageResponse
{ outcome }` (801) → `ChatMessageEvent { channel, sender_id, sender_name, body }` (802). Phía client,
`ChatController.compose` chỉ đặc biệt hoá tiền tố `/w `; mọi dòng khác — kể cả `/gm kick X` — đi
nguyên văn lên server trên kênh đang chọn, trần 200 ký tự (`ChatController.MAX_BODY_LENGTH` = 200 =
`ChatMessage.MAX_BODY_LENGTH` phía server). Chiều về cũng có sẵn: `ChatMessageEvent` với
`channel = CHAT_CHANNEL_UNSPECIFIED` và `sender_name` rỗng được `ChatCoordinator.render` vẽ thành
**dòng hệ thống** (chỉ nội dung, `LineKind.SYSTEM`) — đúng hình dạng một dòng phản hồi lệnh GM hoặc
một thông báo toàn server.

**2. Nhưng hôm nay mọi tin chat đều bị PHÁT ĐI và GHI AUDIT như chat.**
`GatewayChannelHandler.handleSendChatMessageCommand` (EventLoop) áp rate limiter theo kênh rồi
`executeBlocking` → `handleSendChatMessageBlocking` → `ChatFacade.sendMessage` (ghi `audit_log`
`CHAT_MESSAGE`) → `routeChatMessage` phát cho cả server. Tức là nếu chỉ "thêm GM" phía sau, dòng
`/gm give ...` của GM sẽ hiện trước mặt toàn server trước khi kịp làm gì. **Lệnh phải bị chặn lại
TRƯỚC `ChatFacade`.**

**3. Đăng nhập KHÔNG có mật khẩu — đây là rủi ro lớn nhất của cả ADR.**
`system.proto`: `ConnectRequest { token, username }`; `LoginOrCreateAccountUseCase.execute(username)`
tìm-hoặc-tạo account theo username, không kiểm gì thêm (ghi rõ "chưa có mật khẩu ở bước này").
Hệ quả thẳng: **ai biết username của tài khoản GM là đăng nhập được thành GM.** Gắn quyền vào account
mà không có lớp chặn nào khác là phát quyền ADMIN cho người đoán đúng một chuỗi. Xem D3.

**4. Bảng `account` có `status` nhưng không ai đọc.** `V1__create_account_table.sql` tạo
`status VARCHAR(16) DEFAULT 'ACTIVE'`; record `Account(id, username, createdAt)` không mang nó,
đăng nhập không kiểm. Tức **"ban" hôm nay không tồn tại**, và với đăng nhập username-only thì ban một
account chỉ khiến người đó gõ username khác. Lý do D4 KHÔNG đưa `ban` vào bộ lệnh đầu.

**5. `audit_log` (V7) không hợp để chứa hành động GM.** Cột: `character_id NOT NULL REFERENCES
character`, `action VARCHAR(32)`, `detail_json TEXT`, chỉ một index theo `character_id`. Hành động GM
có HAI bên (người ra lệnh, người bị tác động), cần tra theo NGƯỜI RA LỆNH ("GM X đã làm gì tuần
này") và theo account chứ không chỉ theo nhân vật. Nhồi vào `detail_json` thì truy vấn đó thành quét
toàn bảng `LIKE`. Xem D5.

**6. "Give" đã có sẵn một đường đúng chuẩn: thư hệ thống.** `MailFacade.sendMail` với
`senderId == null` là **thư hệ thống** — javadoc `SendMailUseCase` gọi thẳng tên nó là "thư đền bù
GM/quest reward tồn đọng", không ký quỹ của ai, và "không với tới được từ client" vì gateway luôn
truyền `characterId` từ session. Người nhận lĩnh qua `ClaimMailUseCase` — **transaction + khoá túi
đồ + `audit_log` `MAIL_CLAIM`** (DATABASE.md §5), và túi đầy thì trả `INVENTORY_FULL` chứ không mất
đồ. Gateway đã biết đẩy `NEW_MAIL_EVENT` (908) cho người nhận đang online sau khi gửi thư. Ngược lại,
`InventoryFacade.addItem` KHÔNG ghi audit — gọi nó từ lệnh GM là lặp lại đúng lớp bug "đi tắt use
case" đã từng có (thưởng quest ghi thẳng SQL ⇒ HUD đứng im, sổ bug `kage_quest_exp_bypass_bug`).

**7. Các mảnh thế giới đã có, chỉ cần nối.**

| Cần | Đã có | Ở đâu |
|---|---|---|
| Đá một phiên | `CharacterSessionRegistry.Session#evict` → `GatewayChannelHandler.kick` chạy trên EventLoop của kết nối bị đá: dọn nhân vật, gửi `ErrorResponse` có `error_key`, đóng SAU khi gói đã ra dây | quyết định chủ dự án 2026-09-25 "đá phiên cũ" |
| Hồi máu | `WorldFacade.enqueueHealCharacter(characterId, amount)` → hộp thư tick → `HealthChangedEvent` (408) | ADR-046 D5 |
| Dời nhân vật | `WorldTransfer.move(detach → WorldChangedEvent → attach)` + `CharacterLoadout` (chỉ số nền + trang bị) | ADR-051 D2; Revisit Trigger của nó đã ghi sẵn "GM kéo chỉ cần phát đúng event này" |
| Phát toàn server | `ConnectedCharacterRegistry.broadcastToAll` (đang chở chat Thế giới) | — |
| Tra người chơi | `CharacterFacade.findByName/findById/loadGold/loadPosition`, `registry.isOnline` | — |
| Rate limit | `RateLimiter` theo kết nối + `dbCommandRateLimiter` trong `executeBlocking` | SECURITY.md §7 |

**8. Client tự đăng nhập lại sau khi mất kết nối** (`AutoRelog`/`SessionRecovery`, quyết định
2026-09-29, hạn 60 giây 2026-10-01). Nó chỉ KHÔNG chạy khi `ConnectionCoordinator.endsSession(errorKey)`
đúng — hôm nay chỉ nhận `error.logged_in_elsewhere`. ⇒ Một lệnh `kick` chỉ đóng socket sẽ bị client
tự vào lại trong 1–2 giây: **kick vô hiệu nếu client không được dạy một khoá mới.** Đây là thay đổi
client DUY NHẤT bắt buộc (D7).

---

## Decision

**Đề xuất:** lệnh GM là một dòng chat bắt đầu bằng `/gm`, bị gateway chặn lại trước đường chat,
được phân quyền theo bảng `gm_role` (GM ⊂ ADMIN) đọc lại từ DB **mỗi lệnh**, ghi
`gm_audit_log` **trước** khi có hiệu lực, và thi hành bằng đúng các facade/use case/hộp thư World đang
có — không SQL đi tắt, không chạm tick thread ngoài hộp thư.

### D1. Đường đi: chat path, tiền tố `/gm`, không opcode mới

```
Client gõ "/gm kick TieuLy"   ──800 SendChatMessageCommand{channel=<đang chọn>, body="/gm kick TieuLy"}──▶
EventLoop  handleSendChatMessageCommand
   ├─ body.strip() bắt đầu bằng "/gm" (khớp cả "/gm" trơn, không phân biệt hoa thường)?
   │     KHÔNG ⇒ đường chat cũ y nguyên
   │     CÓ    ⇒ rẽ sang GmCommandRouter (KHÔNG qua rate limiter kênh chat, KHÔNG qua ChatFacade)
   │              ├─ gmRateLimiter.tryAcquire()   (theo kết nối — D8)
   │              └─ executeBlocking(...)         (Virtual Thread — D6)
Virtual Thread  GmCommandRouter
   ├─ parse (thuần, whitelist) → kiểm quyền (đọc gm_role) → ghi gm_audit_log PENDING → thi hành
   ├─ trả 801 SendChatMessageResponse{OUTCOME_OK}   (client: OK = im lặng, như hôm nay)
   └─ trả 802 ChatMessageEvent{channel=UNSPECIFIED, sender_name="", body="<phản hồi>"} CHỈ cho GM
```

- Áp cho **mọi kênh**, kể cả `PRIVATE` (`/w Ai /gm ...` cũng bị chặn) — tin có tiền tố `/gm` **không
  bao giờ** được phát đi và **không** ghi `CHAT_MESSAGE`.
- Người **không có quyền** gõ `/gm ...`: trả `OUTCOME_OK`, không phát, không phản hồi gì (không xác
  nhận lệnh GM có tồn tại), ghi log ứng dụng mức `WARN` (đã qua rate limit nên không thành kênh spam
  log). KHÔNG ghi `gm_audit_log` (tránh để người chơi thường dùng nó làm đường bơm dòng vào DB).
- Phản hồi cho GM là **chữ do server soạn, tiếng Anh, thuần ASCII** (xem câu hỏi mở Q4). Đây là một
  **ngoại lệ có chủ ý và có biên** của NETWORK.md §107 ("server không hardcode message"): người đọc là
  người vận hành chứ không phải người chơi, và nội dung có số liệu động (id, vị trí, vàng) mà khoá
  i18n không chở được. Ngoại lệ chỉ áp cho dòng gửi tới **đúng người có quyền** — `announce` thì
  nội dung là chữ GM tự gõ (cùng bản chất với chat). Mỗi phản hồi ≤ 2 dòng vì khung chat chỉ giữ
  `ChatController.MAX_LINES = 6` dòng.
- **Client không cần sửa gì cho chiều đi/về của lệnh** (đã kiểm `ChatController.compose`,
  `ChatCoordinator.render`/`kindOf`).

### D2. Quyền nằm ở bảng riêng `gm_role`, đọc lại MỖI lệnh

- Bảng mới `gm_role(account_id PK → account, role ∈ {GM, ADMIN})`. **Không có dòng = người chơi.**
  Chọn bảng riêng thay vì thêm cột `account.role` vì: (a) bảng `account` thuộc `feature-account`, còn
  phân quyền GM thuộc module mới (D9) — một feature không đọc bảng của feature khác (server/CLAUDE.md);
  (b) không đụng record `Account`, `LoginOrCreateAccountUseCase`, đường đăng nhập nóng; (c) "ai đang là
  GM" là một `SELECT * FROM gm_role` ngắn chứ không phải quét `account`.
- Thứ bậc: `ADMIN` làm được mọi lệnh của `GM`. Hai cấp, đúng quyết định chủ dự án — không dựng hệ
  role/permission tổng quát (Điều 9).
- **Quyền đọc từ DB mỗi lệnh** (trên Virtual Thread), KHÔNG cache vào kết nối lúc đăng nhập. Lệnh GM
  hiếm, một `SELECT` theo khoá chính là không đáng kể; đổi lại, **thu quyền có hiệu lực ngay lệnh kế
  tiếp** thay vì "tới lần đăng nhập sau" — với một tài khoản GM bị chiếm, khoảng trễ đó là toàn bộ
  thiệt hại.
- Người ra lệnh = `boundAccountId` + `boundCharacterId` của **kết nối** (SECURITY.md §4). Gói chỉ
  mang TÊN mục tiêu (`/gm kick TieuLy`); đó không phải IDOR vì quyền tác động lên người khác đến từ
  role của session, không từ việc "tự khai là ai". Phải có nhân vật đang gắn (`boundCharacterId != null`)
  — lệnh GM chỉ phát được từ trong world.
- **Cấp/thu quyền chỉ bằng SQL tay** (quyết định chủ dự án), theo mẫu có sẵn trong ADMIN.md, và câu
  SQL đó đi **cùng transaction** với một dòng `gm_audit_log` `command='role_grant'/'role_revoke'`
  (xem Q3 về việc ép bằng trigger).

**Phác thảo migration — `V55__create_gm_role_and_audit.sql`** (số kế tiếp: hiện cao nhất là
`V54__unique_names_ignoring_case.sql` — ⚠️ bản đầu của ADR này ghi V50, rồi V51, V52, V53, V54; cả năm đã thuộc về
migration khác (V50 cân bằng 2026-10-01, V51 bảng rơi rỗng 2026-10-03, V52 hàng rào CHECK 2026-10-11, V53 dữ liệu bảng rơi boss 2026-10-14, V54 tên không trùng hoa-thường 2026-10-14); **chưa tạo file** — ADR còn Proposed; đặt số lúc thi hành, xem
bẫy "đặt chỗ số hiệu migration"):

```sql
-- Chỉ THÊM bảng, không đổi bảng đang chạy ⇒ không phá client/code cũ (DATABASE.md §10).
CREATE TABLE gm_role (
    account_id  BIGINT PRIMARY KEY REFERENCES account (id),
    role        VARCHAR(8)   NOT NULL CHECK (role IN ('GM', 'ADMIN')),
    granted_at  TIMESTAMPTZ  NOT NULL,
    note        VARCHAR(200)                       -- ai cấp, vì sao (SQL tay ghi)
);

CREATE TABLE gm_audit_log (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_account_id     BIGINT       NOT NULL REFERENCES account (id),
    actor_character_id   BIGINT       REFERENCES character (id),  -- NULL chỉ cho dòng role_grant/role_revoke ghi bằng SQL tay
    actor_role           VARCHAR(8)   NOT NULL,                   -- role LÚC ra lệnh (role có thể bị thu sau)
    command              VARCHAR(16)  NOT NULL,                   -- 'kick', 'give', ... (đã parse)
    command_line         VARCHAR(256) NOT NULL,                   -- nguyên văn dòng gõ (≤ 200 ký tự chat)
    target_character_id  BIGINT       REFERENCES character (id),
    outcome              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                         CHECK (outcome IN ('PENDING', 'OK', 'REJECTED', 'DENIED', 'FAILED')),
    outcome_detail       TEXT,                                    -- vd. {"mailId":123}; server tự build, không input người dùng
    created_at           TIMESTAMPTZ  NOT NULL,
    completed_at         TIMESTAMPTZ
);
CREATE INDEX idx_gm_audit_log_actor  ON gm_audit_log (actor_account_id, created_at);
CREATE INDEX idx_gm_audit_log_target ON gm_audit_log (target_character_id, created_at);

-- Thuộc feature-chat (D4 mute) — tách bảng vì feature-chat không được đọc gm_role/account.
CREATE TABLE chat_mute (
    account_id        BIGINT PRIMARY KEY REFERENCES account (id),
    muted_until       TIMESTAMPTZ NOT NULL,
    gm_audit_log_id   BIGINT      NOT NULL REFERENCES gm_audit_log (id)   -- truy ngược ai khoá, vì sao
);
```

Rollback plan (DATABASE.md §10): ba bảng mới, không cột nào của bảng cũ bị đổi. Lùi bản = tắt
tính năng (D3 `KAGE_GM_ENABLED=false`) và để nguyên bảng — **không `DROP` `gm_audit_log`** (SECURITY.md
§9: audit không được xoá).

### D3. Khi CHƯA có đăng nhập bằng mật khẩu: role chỉ có hiệu lực sau HAI chốt cấu hình

Vì Context mục 3, role trong DB là **điều kiện cần, chưa đủ**. Một lệnh GM chỉ được thi hành khi CẢ BA:

1. `gm_role` có dòng cho account của session, đủ cấp cho lệnh;
2. biến môi trường `KAGE_GM_ENABLED=true` — **mặc định `false`** (cùng khuôn `KAGE_TLS_ENABLED` của
   `TlsConfig`: cờ tường minh, đọc một lần lúc khởi động; giá trị lạ ⇒ server từ chối khởi động);
3. địa chỉ nguồn của kết nối nằm trong `KAGE_GM_ALLOWED_CIDRS` — **mặc định chỉ loopback**
   (`127.0.0.1/32,::1/128`). Chủ dự án thêm IP nhà mình khi chạy Closed Beta.

Chốt 3 là một "yếu tố thứ hai" rẻ: kẻ đoán được username GM nhưng không ngồi ở máy/mạng của chủ dự
án vẫn không ra lệnh được. ⚠️ Biết trước: chạy server trong Docker có NAT thì địa chỉ nguồn có thể là
IP gateway của Docker — phải kiểm lúc thi hành, nếu không chốt 3 hoặc chặn hết, hoặc mở hết.
Khi có đăng nhập bằng mật khẩu thật (SECURITY.md §3 — cần ADR riêng, xem "ADR cần tạo") thì xem lại
chốt 3 (Revisit Trigger). Đây là phần **AI thêm vào** ngoài bốn câu trả lời của chủ dự án ⇒ Q1.

### D4. Bộ lệnh đầu cho Closed Beta — 10 lệnh, cố ý nhỏ

| Lệnh | Cấp | Làm gì / đi đường nào | Vì sao cấp này |
|---|---|---|---|
| `/gm help` | GM | Liệt kê lệnh **trong cấp của mình** (≤ 2 dòng) | Không tác động ai; vẫn audit cho đủ "mọi lệnh" |
| `/gm who <tên>` | GM | Tra: id nhân vật, id account, cấp, vàng, online?, đang ở World nào (chính/bí cảnh/đấu trường), vị trí đã lưu/đang theo dõi. **Không** đọc máu sống (phải hỏi tick thread — để sau) | Chỉ đọc; cần cho mọi khiếu nại "tôi mất đồ/kẹt" |
| `/gm kick <tên>` | GM | Đá phiên qua đúng đường `Session#evict`/`kick` có sẵn, với `error_key = error.kicked_by_gm` (D7) | Xử lý người phá — hành động tạm, không mất gì của ai |
| `/gm mute <tên> <phút>` | GM | Ghi `chat_mute` theo **account** của mục tiêu, `1 ≤ phút ≤ 10080` (7 ngày); `SendChatMessageUseCase` từ chối với kết quả mới `MUTED` (D7) | Như kick — tạm thời, hoàn tác được |
| `/gm unmute <tên>` | GM | Xoá dòng `chat_mute` | Đối xứng với mute |
| `/gm announce <nội dung>` | GM | `broadcastToAll` một `ChatMessageEvent{channel=UNSPECIFIED, sender_name=""}` — client vẽ là dòng hệ thống. Nội dung qua cùng `ProfanityFilter` + trần 200 ký tự | Thông báo bảo trì/sự kiện |
| `/gm goto <tên>` | GM | Dời CHÍNH GM tới vị trí mục tiêu: `WorldTransfer.move` (detach → `WorldChangedEvent` → attach, `CharacterLoadout`), **chỉ trong world chính** | Đi xem tận nơi một báo cáo kẹt/bug |
| `/gm summon <tên>` | GM | Dời MỤC TIÊU tới vị trí GM, cùng đường, chỉ world chính | Gỡ người chơi kẹt địa hình — đúng việc GM cần nhất |
| `/gm heal <tên>` | GM | `enqueueHealCharacter(id, maxHp)` với `maxHp = WorldFacade.characterMaxHp(bonus)` | Hồi phục sau bug combat; không tạo tài sản |
| `/gm give <tên> <mã_vật_phẩm> <số>` | **ADMIN** | Gửi **thư hệ thống** (`MailFacade.sendMail`, `senderId = null`) kèm vật phẩm, `1 ≤ số ≤ 99`; mã tra qua `InventoryFacade.itemCatalog()`; đẩy `NEW_MAIL_EVENT` nếu online. Người nhận lĩnh qua `ClaimMailUseCase` (transaction + `MAIL_CLAIM`) | **Tạo tài sản từ không khí** ⇒ cấp cao nhất. Đi thư nên túi đầy không mất đồ, offline vẫn nhận |
| `/gm gold <tên> <số>` | **ADMIN** | Như `give`, thư hệ thống chỉ kèm vàng, `1 ≤ số ≤ 1.000.000` | Tạo vàng = lạm phát ⇒ ADMIN |

Luật chung cho mọi lệnh có mục tiêu:
- Mục tiêu tra bằng **tên nhân vật** (`CharacterFacade.findByName`); không thấy ⇒ `REJECTED`.
- **Không tác động lên người có cấp ≥ mình** (GM không kick/mute/summon được GM khác hay ADMIN; ADMIN
  không tác động được ADMIN khác) — trừ chính mình ở `heal`/`who`. Chặn một GM bị chiếm quay sang
  khoá miệng người có quyền thu hồi nó.
- `goto`/`summon` từ chối nếu một trong hai bên đang ở bí cảnh/đấu trường (trận đang chạy có luật
  riêng — ADR-026/051), hoặc mục tiêu offline.
- Con số (`phút`, `số`) parse chặt (`Integer.parseInt` trong biên), sai ⇒ `REJECTED` kèm cú pháp đúng.

**Cố ý KHÔNG có ở bộ đầu** (mỗi cái kèm lý do để không ai "tiện tay" thêm):

| Không làm | Vì sao |
|---|---|
| `ban` | Context mục 4: `account.status` không được thi hành, và đăng nhập username-only khiến ban vô nghĩa (đổi username là xong). Làm SAU đăng nhập thật |
| `tp <x> <y>` theo toạ độ | Trục chơi có bệ/địa hình (ADR-033, bẫy "spawn trong bệ nhảy"); toạ độ tay dễ đặt người vào trong khối. `goto`/`summon` luôn rơi vào vị trí hợp lệ của một nhân vật có thật |
| `setlevel`, `exp` | Đổi tiến trình đi tắt `GrantExperienceUseCase`/HUD (đúng lớp bug quest EXP); chưa ai cần ở Closed Beta |
| `revive` | Không có trạng thái "nằm chết" cần cứu: world chính hồi sinh ngay (`respawnCharacter`), gục trong bí cảnh tự về world chính (ADR-051 D5) |
| Bất kỳ lệnh nào đổi `gm_role` | D8 — quyền không bao giờ tự cấp được từ trong game |
| Xoá/thu hồi vật phẩm | Phá hoại không hoàn tác được; hoàn đồ thì dùng `give` |

### D5. Audit: `gm_audit_log` riêng, ghi TRƯỚC khi có hiệu lực, không bao giờ xoá

- **Ghi trước (write-ahead)**: sau khi parse + kiểm quyền, `INSERT` một dòng `outcome='PENDING'`. Nếu
  `INSERT` lỗi ⇒ lệnh **không chạy**. Sau khi thi hành, `UPDATE ... SET outcome=?, outcome_detail=?,
  completed_at=? WHERE id=? AND outcome='PENDING'` — chuyển trạng thái **đúng một lần**; ngoài ra bảng
  chỉ có `INSERT`. Lý do không gộp chung transaction với hiệu lực: hiệu lực đi qua facade của feature
  khác (thư, chat mute) hoặc hộp thư World — không chia sẻ được `Transaction` (cùng giới hạn đã ghi ở
  `GoldPort`). Write-ahead cho bảo đảm mạnh nhất có thể ở đây: **không có hiệu lực nào mà không có dòng
  audit**; điều ngược lại (dòng `PENDING` mãi vì server chết giữa chừng) là thứ điều tra được.
- Ghi gì: người ra lệnh (account + nhân vật + role lúc đó), lệnh đã parse, **nguyên văn dòng gõ**,
  mục tiêu, kết quả, chi tiết kết quả do server tự build (`mailId` cho give/gold — nối thẳng sang dòng
  `MAIL_CLAIM` trong `audit_log` khi người nhận lĩnh; `muted_until` cho mute).
- `DENIED` (có role nhưng thiếu cấp, hoặc tác động người cấp cao hơn) **có** ghi; người không có role
  thì không (D1).
- Song song, mỗi lệnh một dòng log ứng dụng `INFO` (id, lệnh, mục tiêu, kết quả) — không bao giờ log
  mỗi tick.
- **Thời hạn lưu**: không xoá (SECURITY.md §9). Closed Beta: giữ vĩnh viễn trong bảng sống (khối lượng
  vài trăm dòng/tháng). Lưu trữ sang bảng/phân vùng archive khi chạm Revisit Trigger (DATABASE.md §9 đã
  liệt `audit_log` là ứng viên partition đầu tiên — bảng này đi cùng).

### D6. Luồng: EventLoop không chạm DB; trạng thái World chỉ qua hộp thư

| Bước | Luồng | Ghi chú |
|---|---|---|
| Nhận diện tiền tố, `gmRateLimiter` | Netty EventLoop | So chuỗi, không DB, không khoá |
| Parse, đọc `gm_role`, ghi audit, gọi facade (mail, chat mute, tra nhân vật, `CharacterLoadout`) | Virtual Thread (`executeBlocking` → `authExecutor`) | Đã qua `dbCommandRateLimiter` sẵn có |
| Hồi máu | Tick thread, qua `enqueueHealCharacter` | Không đọc lại entity ngay sau khi xếp hàng (bẫy ADR-043 D5) |
| `goto`/`summon` | Virtual Thread chờ `inboxBarrier()` của world chính rồi `WorldTransfer.move` | Cùng cách hầm ngục/đấu trường làm sau 2026-10-01 — không để World cũ phát nốt gói sau event |
| `kick` | EventLoop của **kết nối bị đá** (`runOnEventLoop` trong `evictCharacter`) | Đường có sẵn, đảm bảo không lệnh nào của phiên bị đá lọt vào world sau detach |
| `announce` | `broadcastToAll` (an toàn đa luồng — đang chở chat Thế giới) | — |

⚠️ Bẫy đã thấy trước cho `heal`: `handleHealCharacter` tính `Math.min(max, current + amount)` — truyền
`Integer.MAX_VALUE` cho "hồi đầy" sẽ **tràn số** thành âm. Phải truyền đúng `maxHp`.

⚠️ Bẫy cho `goto`/`summon`: vị trí lưu lúc thoát lấy từ vị trí registry đang theo dõi
(`trackPositionIfConnected`, cập nhật theo `MoveAccepted`); attach ở chỗ mới phải cập nhật luôn chỗ
đó, nếu không thoát game ngay sau khi bị kéo sẽ lưu vị trí CŨ (họ hàng của bẫy "pos_x chỉ ghi lúc
ngắt kết nối"). Có test riêng (xem Testing).

### D7. Giao thức: KHÔNG opcode mới; 2 giá trị enum chỉ-thêm; 1 `error_key` mới — `protocolVersion` KHÔNG tăng

| Thay đổi | Loại | Client cũ gặp nó thì sao |
|---|---|---|
| `SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MUTED = 10` | Thêm giá trị enum (NETWORK.md §6, tiền lệ ADR-044) | `outcomeMessageKey` rơi vào `chat.error.unknown` — vẫn có một dòng báo |
| `WorldChangeReason.WORLD_CHANGE_REASON_GM_MOVED = 5` | Thêm giá trị enum (ADR-051 đã dự liệu "GM kéo") | Client chỉ so `DEFEATED`; giá trị lạ ⇒ dọn màn hình + đặt vị trí bình thường |
| `error_key = "error.kicked_by_gm"` trong `ErrorResponse` | Giá trị chuỗi mới, không đổi schema | ⚠️ Client cũ KHÔNG coi là kết thúc phiên ⇒ **tự đăng nhập lại** ⇒ kick vô hiệu tới khi cập nhật client |

**Việc client phải làm** (nhỏ, nhưng kick không chạy nếu thiếu):
1. `ConnectionCoordinator.endsSession` nhận thêm `error.kicked_by_gm` ⇒ `AutoRelog` không chạy, màn
   hình về đăng nhập với lý do.
2. Hai chuỗi i18n vi/en: `error.kicked_by_gm` và `chat.error.muted` (cổng build `verifyServerErrorKeys`
   sẽ bắt nếu thiếu cái đầu).
3. `ChatController.OUTCOME_MESSAGE_KEYS` thêm `MUTED → chat.error.muted`.

Không có gì khác: chiều đi/về của lệnh và `announce` dùng nguyên đường chat (D1).

### D8. Bảo mật

- **Rate limit riêng** `gmRateLimiter` theo kết nối: 5 lệnh / 10 giây, vượt liên tục ⇒ ngắt kết nối
  (cùng khuôn `RateLimiter` của chat); cộng thêm `dbCommandRateLimiter` vốn có. Lệnh GM không ăn vào
  hạn mức chat Thế giới (5/10 giây) và ngược lại.
- **Không lệnh nào tự nâng quyền**: không có lệnh trong game đọc-ghi `gm_role`; adapter
  `PostgresGmRoleAdapter` chỉ có `SELECT`. Một test quét chuỗi SQL trong `server-database` bảo đảm
  không đâu có `INSERT|UPDATE|DELETE` vào `gm_role` (ArchUnit không soi được chuỗi SQL — xem Testing).
- **Không tác động người cấp ≥ mình** (D4).
- Parse theo **whitelist cố định** (sealed record mỗi lệnh — /CLAUDE.md §7); lệnh lạ ⇒ `REJECTED` +
  gợi ý `/gm help`. Không reflection, không tra lệnh theo tên lớp.
- Tên mục tiêu qua cùng luật hợp lệ của tên nhân vật; số qua biên ở D4; nội dung `announce` qua
  `ProfanityFilter` + 200 ký tự + sanitize như chat (SECURITY.md §12).
- Ba chốt D3 khi chưa có mật khẩu.
- Log: không ghi session token; chỉ id + lệnh + kết quả.

### D9. Module: `server-features/feature-gm` mới + một lớp mới ở gateway

- **`feature-gm`** (Clean Architecture, copy `docs/templates/feature-module/`):
  `api/GmFacade` (kiểm quyền, mở/chốt dòng audit), `domain/` (`GmRole`, `GmCommand` sealed + parser
  thuần, `GmRolePort`, `GmAuditPort`), `application/` (use case kiểm quyền + audit). **Không** gọi
  facade của feature khác — điều phối liên feature là việc của gateway, như mọi luồng khác hôm nay.
- **`server-gateway`**: lớp mới `GmCommandRouter` (parse → `GmFacade` → gọi `MailFacade`/`ChatFacade`/
  `CharacterFacade`/`WorldTransfer`/`Session#evict`/`WorldFacade`). Tách lớp riêng vì
  `GatewayChannelHandler` đã 3.861 dòng và sát trần độ phức tạp PMD tính cho cả lớp; handler chỉ thêm
  đúng một nhánh rẽ ở `handleSendChatMessageCommand`.
- **`feature-chat`**: `ChatMutePort` + kết quả `MUTED` trong `SendChatMessageUseCase` (kiểm trước
  `ProfanityFilter`), `ChatFacade.mute/unmute`. `feature-chat` không biết GM là gì — nó chỉ biết "account
  này đang bị khoá chat tới lúc nào".
- **`server-database`**: `PostgresGmRoleAdapter`, `PostgresGmAuditAdapter`, `PostgresChatMuteAdapter`,
  migration V55.
- **Không đụng**: `feature-account`, `server-world` (dùng API có sẵn), `shared-protocol` ngoài 2 giá trị
  enum.

### D10. Bảo đảm "mọi lệnh đều audit" bằng cấu trúc, không bằng trí nhớ

`GmCommandRouter` chỉ có MỘT lối thi hành: `GmFacade.begin(...)` trả về một `AuditTicket` (đã ghi dòng
`PENDING`); hàm thi hành từng lệnh nhận `AuditTicket` làm tham số bắt buộc và phải gọi
`ticket.finish(outcome, detail)`. Không có ticket thì không gọi được hàm thi hành — thêm lệnh thứ 11
mà quên audit là lỗi biên dịch, không phải lỗi review.

---

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Lệnh trong game qua đường chat `/gm`** — ADR này | 0 opcode mới; client không sửa gì cho chiều đi/về; tái dùng rate limit, TLS, session, `executeBlocking`; GM thấy phản hồi ngay trong khung chat | Lệnh là chuỗi tự do ⇒ parse phía server (chấp nhận được: parse thuần, test được); phản hồi là chữ server soạn (ngoại lệ §107 có biên — D1); khung chat 6 dòng giới hạn độ dài phản hồi | **Khuyến nghị** — đúng quyết định chủ dự án, rẻ nhất |
| **B. Opcode riêng `GmCommand` (dải mới, vd. 1700–1799 "Admin")** với message có cấu trúc | Kiểu dữ liệu chặt trên dây; không lẫn với chat; phản hồi có thể là khoá i18n + tham số | Đổi protocol thật (opcode + message), client phải có đường gửi/nhận mới; vẫn cần chỗ gõ lệnh ⇒ lại là ô chat; lợi ích chỉ hiện ra khi có GUI quản trị | Không — trả giá protocol cho lợi ích chưa ai cần. Revisit nếu làm GUI |
| **C. Web admin tách service** | Hiển thị được bảng/kho đồ hoạ (ART-PIPELINE §8.7), dễ thêm dashboard | Chủ dự án đã chọn nhúng, in-game. Thêm tầng HTTP + xác thực riêng (ADR-009 cấm Spring MVC ⇒ tự dựng); service ngoài **không chạm được trạng thái World đang sống** (kick, heal, kéo người) nếu không dựng thêm RPC vào tiến trình game; bề mặt tấn công mới lộ qua mạng | Không — bị bác bởi quyết định chủ dự án và bởi việc nửa bộ lệnh cần trạng thái trong tiến trình |
| **D. Hoãn, vận hành bằng SQL tay** (ADMIN.md cũ: "Closed Beta vận hành được bằng SQL tay") | 0 công | SQL tay **đi tắt use case** (đúng lớp bug quest EXP/HUD đứng im), không ghi audit, không với tới trạng thái World (không kick/heal/kéo người được), thư/vật phẩm sửa tay dễ vỡ khoá túi đồ (DATABASE.md §5.1) | Không — chủ dự án đã quyết làm; và SQL tay vẫn giữ cho đúng một việc: cấp/thu `gm_role` (D2) |
| **E. Cột `account.role` thay bảng `gm_role`** | Một bảng ít hơn; "role" nằm cạnh "status" | Đụng record/adapter/đường đăng nhập của `feature-account`; module GM phải đọc bảng của feature khác hoặc đi vòng qua `AccountFacade` | Không — biến thể hợp lệ nếu chủ dự án muốn ít bảng (Q2) |
| **F. `mute` kiểu "shadow" (tin vẫn "gửi được" nhưng chỉ người gửi thấy)** | 0 thay đổi protocol cho mute | Lừa người chơi; khó giải thích khi họ khiếu nại; lệch với tinh thần server báo đúng lý do (ADR-044) | Không khuyến nghị; hợp lệ nếu chủ dự án muốn tránh mọi thay đổi enum |
| **G. `summon`/`goto` dùng lại `REASON_REQUESTED` thay vì thêm `GM_MOVED`** | 0 thay đổi enum | `summon` KHÔNG phải do người bị kéo yêu cầu — một message mang nghĩa sai, đúng loại lỗi ADR-047/051 đã bác | Không khuyến nghị |

---

## Consequences

**Tích cực:**
- Gỡ chặn ADMIN.md (dòng #6 docs/README.md) và Closed Beta có công cụ vận hành không phải sửa DB tay.
- Mọi hành động GM truy được: ai, lúc nào, với ai, kết quả gì; give/gold nối thẳng sang `MAIL_CLAIM`.
- Không có đường nào tạo tài sản ngoài use case có transaction + audit.
- Thu quyền GM có hiệu lực ở lệnh kế tiếp (D2), không cần đá phiên.

**Đánh đổi phải chấp nhận:**
- Một ngoại lệ có biên của NETWORK.md §107 (phản hồi GM là chữ server soạn — D1).
- Kick vô hiệu với client chưa cập nhật (D7) — Closed Beta tự phát bản client nên chấp nhận được.
- Ba bảng mới; `gm_audit_log` lớn dần mãi (rất chậm).
- Tới khi có mật khẩu, an toàn của quyền GM dựa vào chốt cấu hình D3 — chặn được người đoán username,
  không chặn được người ngồi cùng mạng được phép.
- Write-ahead nghĩa là có thể có dòng `PENDING` không bao giờ chốt (server chết giữa lệnh) — điều tra
  bằng tay; không có hiệu lực nào thiếu dòng audit.

**Module bị ảnh hưởng:** *(tra [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md))*
- **MỚI** `server-features/feature-gm` (cần cập nhật REPOSITORY-STRUCTURE §1 khi thi hành).
- `server-gateway` — `GmCommandRouter` mới, một nhánh rẽ trong `handleSendChatMessageCommand`, một biến
  thể `evict` mang `error_key`, đọc `KAGE_GM_ENABLED`/`KAGE_GM_ALLOWED_CIDRS`.
- `feature-chat` — mute.
- `server-database` — 3 adapter + V55.
- `shared-protocol` — 2 giá trị enum.
- `client-core` — `ConnectionCoordinator.endsSession`, `ChatController.OUTCOME_MESSAGE_KEYS`, 2 chuỗi i18n × 2 ngôn ngữ.
- **Không đụng** `server-world`, `feature-account`, `feature-inventory`, `feature-mail` (dùng API có sẵn).

**Chi phí ước tính:** vừa — một module nhỏ, một router, ba adapter, một migration; phần đắt nhất là
test tích hợp hai socket. Không đổi luồng nóng nào (đường chat thường chỉ thêm một phép so tiền tố).

---

## Testing

| Lớp | Test |
|---|---|
| Đơn vị, `feature-gm` | Parser: từng lệnh, thiếu/thừa tham số, số ngoài biên, hoa thường, khoảng trắng, `/gm` trơn. Ma trận quyền: 10 lệnh × {không role, GM, ADMIN}. Luật "không tác động người cấp ≥ mình". Write-ahead: `GmAuditPort` giả ném lỗi ở `INSERT` ⇒ không hàm thi hành nào được gọi. |
| Đơn vị, `feature-chat` | `MUTED` khi còn hạn; hết hạn thì gửi được; kiểm mute đứng TRƯỚC lọc từ cấm. |
| Postgres thật (Testcontainers, cùng bộ 197 test hiện có) | V55 áp lên DB đã có dữ liệu; CHECK của `role`/`outcome`; FK; chuyển `PENDING → OK` chỉ được một lần. |
| Tích hợp socket, `server-gateway` (khuôn `SecurityIntegrationTest`/`WorldChangedEventOrderTest`) | (1) Người không role gõ `/gm announce x` ⇒ socket thứ hai KHÔNG nhận gì, không dòng `gm_audit_log`. (2) GM `kick` ⇒ mục tiêu nhận `ErrorResponse error.kicked_by_gm` rồi socket đóng. (3) GM gõ `give` ⇒ `DENIED` + dòng audit, không thư. (4) ADMIN `give` ⇒ thư hệ thống + `NEW_MAIL_EVENT`; lĩnh ⇒ `MAIL_CLAIM`; túi đầy ⇒ đồ còn trong thư. (5) `mute` ⇒ `OUTCOME_MUTED`; `unmute` ⇒ gửi lại được. (6) `summon` ⇒ `WorldChangedEvent{GM_MOVED}` tới TRƯỚC spawn đầu tiên ở chỗ mới; thoát ngay sau đó ⇒ vị trí lưu là chỗ MỚI. (7) `heal` ⇒ `HealthChangedEvent` với `current = max`. (8) `KAGE_GM_ENABLED=false` hoặc địa chỉ ngoài CIDR ⇒ mọi lệnh bị từ chối dù có role. (9) Tin `/gm` không bao giờ sinh dòng `CHAT_MESSAGE`. |
| Kiến trúc | ArchUnit (`quality/architecture-tests`): `feature-gm` không import Netty/JDBC (luật sẵn có phủ nếu mẫu package khớp — kiểm lại khi thêm module). Test quét chuỗi SQL trong `server-database`: không `INSERT|UPDATE|DELETE` nào vào `gm_role`; chỉ `PostgresGmAuditAdapter` được `UPDATE gm_audit_log` và câu đó phải có `outcome = 'PENDING'` trong `WHERE`. |
| Client | `ConnectionCoordinator.endsSession("error.kicked_by_gm")` = true; `outcomeMessageKey(MUTED)`; cổng `verifyServerErrorKeys` + `verifyContentKeys` xanh. |
| Kiểm sống | Hai client thật: GM kick ⇒ client kia về màn đăng nhập và KHÔNG tự vào lại; summon người đứng ở làng; announce hiện ở cả hai. Bằng chứng phải có ngày SAU lần thi hành (sổ `kage_evidence_must_postdate_change`). |

---

## Revisit Trigger

- **Có đăng nhập bằng mật khẩu thật** (SECURITY.md §3) ⇒ xem lại chốt CIDR của D3 (giữ làm lớp phòng
  thủ thứ hai hay bỏ), và đưa `ban` vào bộ lệnh.
- **Có người vận hành thứ hai ngoài chủ dự án** ⇒ cân lại 2FA cho GM (ADMIN.md cũ đã ghi) và việc cấp
  quyền bằng SQL tay.
- **Cần nhìn bảng/kho đồ hoạ/dashboard** (ART-PIPELINE §8.7) ⇒ ADR riêng cho giao diện web (phương án C).
- `gm_audit_log` vượt **1 triệu dòng** hoặc truy vấn theo người ra lệnh vượt **100 ms** ⇒ archive/partition
  theo `created_at` (DATABASE.md §9).
- Bộ lệnh vượt **20 lệnh** hoặc cần phản hồi dài hơn 2 dòng thường xuyên ⇒ cân phương án B (opcode riêng).

---

## Technical Decisions

- Đường chat (800/801/802) đủ chở lệnh và phản hồi; client không cần sửa cho chiều đi/về (đã đọc
  `ChatController`/`ChatCoordinator`).
- Lệnh `/gm` bị chặn TRƯỚC `ChatFacade` — nếu không, nó bị phát toàn server và ghi như chat.
- Đăng nhập username-only (`LoginOrCreateAccountUseCase`) ⇒ role trong DB không đủ; thêm chốt cấu hình (D3).
- `give`/`gold` đi **thư hệ thống** có sẵn (`senderId = null`) — tài sản chỉ vào túi qua
  `ClaimMailUseCase` (transaction + audit), không qua `InventoryFacade.addItem` (không audit).
- `audit_log` (V7) không hợp cho hành động hai bên ⇒ bảng `gm_audit_log` riêng, ghi trước khi có hiệu lực.
- Kick phải dạy client một khoá kết thúc phiên mới, nếu không `AutoRelog` vô hiệu hoá nó.
- Không opcode mới; hai giá trị enum chỉ-thêm; `protocolVersion` không tăng.

## ADR cần tạo

- **Đăng nhập bằng mật khẩu** (SECURITY.md §3 đã chốt Argon2id/bcrypt nhưng chưa thi hành — thư viện
  băm mật khẩu là **dependency mới** ⇒ cần ADR). Là tiền đề để `ban` có nghĩa và để nới chốt D3.
- (Khi tới Revisit Trigger) giao diện quản trị web — phương án C.

## Tài liệu liên quan

- [ADMIN.md](../../12-devtools/ADMIN.md) — viết đầy đủ khi ADR này Accepted (bộ lệnh, mẫu SQL cấp quyền, cách đọc `gm_audit_log`).
- [SECURITY.md §3, §4, §7, §9, §12](../../08-security/SECURITY.md) — xác thực, IDOR, rate limit, audit, chat.
- [DATABASE.md §2.7, §5, §5.1, §9, §10](../../05-server/DATABASE.md) — audit, transaction, thứ tự khoá túi đồ, partition, migration.
- [NETWORK.md §6, §107](../../05-server/NETWORK.md) — thêm giá trị enum không tăng version; error_key.
- [ADR-051](ADR-051-World-Changed-Event.md) — `WorldTransfer`, thứ tự detach → event → attach.
- [ADR-046](ADR-046-Health-Recovery.md) — `enqueueHealCharacter`, `HealthChangedEvent`.
- [ADR-009](ADR-009-Why-No-Spring-Boot.md) — vì sao web admin không rẻ.
- Code: `GatewayChannelHandler#handleSendChatMessageCommand`, `#evictCharacter`/`#kick`, `CharacterSessionRegistry`,
  `WorldTransfer`, `SendMailUseCase`/`ClaimMailUseCase`, `SendChatMessageUseCase`, `client-core/.../world/ConnectionCoordinator`,
  `AutoRelog`, `ChatController`, `V1__create_account_table.sql`, `V7__add_gold_and_audit_log.sql`.

## Việc cần làm tiếp theo

1. **Chủ dự án duyệt D1–D10** và trả lời:
   - **Q1** — Chốt cấu hình D3 (`KAGE_GM_ENABLED` + CIDR) có được không, hay chấp nhận rủi ro
     username-only, hay chờ đăng nhập mật khẩu trước rồi mới mở lệnh GM?
   - **Q2** — Bảng `gm_role` riêng (khuyến nghị) hay cột `account.role` (phương án E)?
   - **Q3** — Cấp/thu quyền bằng SQL tay: chỉ quy trình (mẫu SQL trong ADMIN.md ghi kèm dòng
     `gm_audit_log`), hay ép bằng trigger DB tự ghi audit khi `gm_role` đổi?
   - **Q4** — Phản hồi lệnh GM bằng tiếng Anh (khuyến nghị: ngoại lệ hẹp, không nạp i18n phía server)
     hay tiếng Việt?
   - **Q5** — Biên số: `give` ≤ 99 món/lệnh, `gold` ≤ 1.000.000/lệnh, `mute` ≤ 7 ngày — giữ hay đổi?
   - **Q6** — `mute` thật với `OUTCOME_MUTED` (khuyến nghị) hay shadow-mute (phương án F, 0 enum)?
2. ✅ **ĐÃ THI HÀNH 2026-10-18 (lô 15)** — Q1–Q6 theo khuyến nghị (D3 chốt cấu hình; bảng `gm_role` riêng; cấp/thu bằng SQL tay theo mẫu ADMIN.md, không trigger; phản hồi tiếng Anh ASCII; biên 99/1.000.000/7 ngày; mute thật với `SEND_CHAT_MESSAGE_OUTCOME_MUTED = 10`). Migration là **V61** (không phải V55 như phác thảo). `feature-gm` (parser + quyền + audit `AuditTicket`), `GmCommandRouter`/`GmConfig` ở gateway, `ChatMutePort`, kick qua `CharacterSessionRegistry.Session#evict(id, errorKey)`; client `ConnectionCoordinator.endsSession` nhận `error.kicked_by_gm`. Test: `GmCommandParserTest`, `GmConfigTest`, `GmCommandRouterDatabaseTest` (Postgres thật: im lặng khi không quyền/chốt đóng, GM không tạo vàng, ADMIN gửi vàng qua thư hệ thống, mute/unmute, không đụng người cùng cấp, không dòng audit nào PENDING). ✅ **`goto`/`summon` thi hành cùng ngày (lô 16)** — `MainWorldRelocation` + `MainWorldChannelRegistry.beginRelocation/finishRelocation`: gỡ khỏi khu vực cũ nhưng GIỮ định tuyến (gói tới giữa chừng không làm đóng kết nối) → chờ `inboxBarrier` → `WorldChangedEvent` lý do `WORLD_CHANGE_REASON_GM_MOVED = 5` → gắn vào ĐÚNG khu vực của người neo (khu đích đã đầy thì vượt trần một người, có chủ ý; khu đích đã đóng trong lúc chờ ⇒ gắn lại khu cũ, vẫn đúng toạ độ). Đích = vị trí registry đang theo dõi của người neo; sau khi gắn cập nhật luôn vị trí theo dõi (bẫy D6). Người dời rớt mạng/bị kéo vào hầm ngục trong lúc chờ ⇒ bỏ nửa sau, không 308. Client: dòng báo `world.notice.gm_moved`. Test: `MainWorldChannelRegistryTest` (dời qua khu vực, bỏ khi đã rời), `GmCommandRouterDatabaseTest.gotoAndSummonShouldMoveInOppositeDirections`. ⏳ Chưa kiểm sống bằng hai client.
   (Kế hoạch gốc:) Sau khi Accepted: thi hành theo thứ tự migration V55 → `feature-gm` → `feature-chat` mute → `GmCommandRouter`
   → client (D7) → test (mục Testing) → viết ADMIN.md → cập nhật REPOSITORY-STRUCTURE §1, docs/README.md dòng #6.
3. Kiểm sống bằng hai client thật (mục Testing, dòng cuối).
