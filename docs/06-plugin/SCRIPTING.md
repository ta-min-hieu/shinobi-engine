# SCRIPTING.md

**Scripting cho nội dung game**

| | |
|---|---|
| Version | 1.0.0 |
| Status | ✅ Engine đã chốt: **GraalJS** ([ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md)) |
| Luật gốc | [Hiến pháp Điều 27](../00-governance/KAGE-CONSTITUTION.md) |

> Điều 27: Quest · NPC · Dialogue · Cutscene · Event · Festival **không hardcode**. Phải hỗ trợ scripting, và **script phải có sandbox**.

---

## 1. Vì sao cần Script

| Không có Script | Có Script |
|---|---|
| Thêm 1 quest = sửa Java + build + deploy | Thêm 1 quest = thêm file script |
| Game designer phải nhờ lập trình viên | Game designer tự làm |
| Sự kiện lễ hội = release mới | Bật/tắt bằng file + Feature Flag |
| 500 quest = 500 class Java | 500 file script |

**Script chạy ở server** (Điều 15). Client không chạy script gameplay.

---

## 2. Script dùng cho gì

| ✅ Script | ❌ Không script |
|---|---|
| Quest: điều kiện, tiến độ, thưởng | Vòng lặp ECS |
| Dialogue NPC | Movement, physics |
| Boss: các pha, chuỗi skill | Rendering |
| Hiệu ứng skill cụ thể | Network |
| Sự kiện lễ hội | Database access trực tiếp |
| Cutscene | Bất cứ thứ gì chạy mỗi tick trên nhiều entity |

**Nguyên tắc**: script chạy theo **sự kiện** (giết quái, nói chuyện NPC), không chạy **mỗi tick trên hàng nghìn entity**. Script trong hot path = giết hiệu năng.

---

## 3. Lua vs GraalJS — phân tích

> ✅ **Đã chốt: GraalJS** ([ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md)). Phần dưới đây giữ lại nguyên phân tích ban đầu (giá trị lịch sử — vì sao từng cân nhắc cả hai), nhưng **kết luận "GraalJS cần GraalVM" ở cuối mục này đã lỗi thời** — xem ADR-016 mục "Kiểm chứng 2 vấn đề kỹ thuật" để biết vì sao (GraalJS chạy được trên Temurin JDK 25 tiêu chuẩn qua `org.graalvm.polyglot:js`, không cần đổi JDK distribution, ADR-001 không bị động tới).

File recommend nói đúng: **chọn MỘT, không hỗ trợ cả hai ngay từ đầu.** Hai script engine = hai sandbox, hai bộ binding API, hai chỗ để bug.

| Tiêu chí | **LuaJ / Lua** | **GraalJS** |
|---|---|---|
| **Hiệu năng** | Trung bình (LuaJ là interpreter Java, không phải LuaJIT) | Tốt hơn khi warm-up (JIT của GraalVM) |
| **Khởi động** | Nhanh | ⚠️ Chậm hơn; cần warm-up |
| **Bộ nhớ** | Nhẹ | ⚠️ Nặng hơn đáng kể |
| **Sandbox** | LuaJ có thể giới hạn được, nhưng cần cẩn thận | ✅ **Polyglot API có sandbox tốt hơn**: giới hạn host access, CPU, memory |
| **Cú pháp cho designer** | ✅ Đơn giản, dễ học nhất | Phức tạp hơn (JS có nhiều bẫy: `this`, async, prototype) |
| **Hệ sinh thái game** | ✅ **Chuẩn ngành game** (WoW, Roblox, Garry's Mod) | Ít dùng trong game |
| **Trên JVM tiêu chuẩn** | ✅ LuaJ chạy mọi JVM | ⚠️ **Cần GraalVM để có hiệu năng tốt**; trên JVM thường thì chậm |
| **Bảo trì** | ⚠️ LuaJ bảo trì chậm | ✅ Oracle hậu thuẫn |
| **Debug** | Khó | Tốt hơn (Chrome DevTools protocol) |

### Vấn đề then chốt: GraalJS cần GraalVM

⚠️ Đây là điểm quyết định thường bị bỏ qua. GraalJS chạy trên JVM tiêu chuẩn (HotSpot) ở **chế độ interpreter** → **chậm**. Muốn hiệu năng tốt phải chạy trên **GraalVM**.

Nhưng [ADR-001](../01-architecture/adr/ADR-001-Why-Java25.md) đã chốt **Temurin JDK 25**. Chọn GraalJS = phải đổi runtime sang GraalVM = **mở lại ADR-001**.

### Quyết định cuối: **GraalJS** ([ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md))

Khuyến nghị ban đầu của tài liệu này là Lua (LuaJ) — lý do lịch sử giữ nguyên ở bảng trên. Khi thực sự kiểm chứng (không chỉ so sánh lý thuyết), 2 phát hiện đã đảo ngược kết luận — xem đầy đủ ở ADR-016:

| Phát hiện | Ảnh hưởng |
|---|---|
| **LuaJ thực chất không còn bảo trì** — bản MIT gần nhất 3.0.2 là tháng 3/2019, không có sandbox policy dựng sẵn | Ưu điểm "designer học được / nhẹ" không bù được rủi ro phải tự vá lỗ hổng sandbox trên một engine ngừng phát triển |
| **"GraalJS cần GraalVM" không còn đúng** — chạy được trên Temurin JDK 25 tiêu chuẩn qua `org.graalvm.polyglot:js`, không cần đổi JDK distribution | Rào cản chính khiến từng nghiêng về Lua không tồn tại — ADR-001 không bị động tới |

**Rủi ro phải chấp nhận**: cú pháp JS phức tạp hơn Lua cho designer không rành lập trình; không chạy Graal Compiler nên hiệu năng ở mức interpreter (chấp nhận được vì script không ở hot path, §2/§7).

---

## 4. Sandbox — bắt buộc

Điều 27: **Không cho phép script truy cập JVM trực tiếp.**

Script là nơi **nguy hiểm nhất** của hệ thống: nếu script thoát sandbox, nó có toàn quyền trên server.

| ❌ Script KHÔNG được | Vì sao |
|---|---|
| `require`/import module tuỳ ý ngoài whitelist | Nạp code ngoài |
| Truy cập class Java qua `Java.type`/reflection | Thoát sandbox hoàn toàn |
| Mở socket, đọc/ghi file | Chạy lệnh hệ thống, kênh liên lạc ngoài |
| Vòng lặp vô hạn | Treo tick thread |
| Cấp phát bộ nhớ không giới hạn | OOM server |

**Cấu hình sandbox bắt buộc** (GraalJS `SandboxPolicy.ISOLATED`/`UNTRUSTED` — [ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md)):

```
1. SandboxPolicy.UNTRUSTED: tắt hoàn toàn host access mặc định
   → không cho phép Java.type, Java interop, class loading từ script
2. Giới hạn CPU/statement count qua ResourceLimits
   → chống vòng lặp vô hạn
3. Giới hạn bộ nhớ qua ResourceLimits
4. Timeout mỗi lần gọi script (Context.close(true) khi vượt)
   → ✅ làm 2026-10-06 (`ScriptSandbox.DEFAULT_TIMEOUT` 500ms, kèm `sandbox.MaxStackFrames`);
     mục 3 (bộ nhớ) CHƯA có ở mức CONSTRAINED — xem ADR-016 "Kiểm chứng bằng script thù địch"
5. API host: WHITELIST tường minh qua Value/ProxyObject, không expose object Java thô
```

⚠️ **Điểm chết người**: expose một object Java cho script mà object đó có method trả về object khác → script đi từ đó tới `Class` → tới `ClassLoader` → **thoát sandbox**. 

→ **Chỉ expose API dạng dữ liệu thuần (number/string/table), không expose object Java.**

```js
// ✅ An toàn — API dạng dữ liệu
quest.give_item(character_id, "sword_01", 1);

// ❌ Nguy hiểm — expose object Java thô cho script
const inv = quest.get_inventory(character_id);   // trả về object Java?
inv.getClass().getClassLoader();                  // → thoát sandbox
```

---

## 5. Script API (thiết kế dự kiến)

**Nguyên tắc**: API dạng hàm, tham số/kết quả là dữ liệu thuần.

```js
// quest/kill_10_wolves.js
const quest = {
    id: "kill_10_wolves",
    name_key: "quest.kill_10_wolves.name",

    on_accept(ctx) {
        ctx.set_progress("killed", 0);
    },

    on_event(ctx, event) {
        if (event.type === "monster_killed" && event.monster_code === "wolf") {
            const n = ctx.get_progress("killed") + 1;
            ctx.set_progress("killed", n);
            if (n >= 10) ctx.complete();
        }
    },

    on_complete(ctx) {
        ctx.give_exp(500);
        ctx.give_item("herb_01", 3);
    }
};
```

`ctx` là **facade an toàn** — chỉ expose hàm được whitelist qua `ProxyObject`, không expose object Java.

---

## 6. Hot Reload

| Môi trường | Hot reload |
|---|---|
| Development | ✅ Đổi file → nạp lại ngay (vòng lặp làm content nhanh) |
| Production | ⚠️ **Cẩn thận** |

**Vấn đề ở Production**: script đang chạy dở (người chơi đang làm quest) mà reload → state cũ vs code mới không khớp.

**Đề xuất**:
- Script **mới** (quest chưa ai làm) → reload an toàn.
- Script **đang chạy** → chỉ áp dụng cho instance mới, instance đang chạy giữ code cũ tới khi xong.

⏳ Cơ chế cụ thể cần thiết kế ở Phase 6.

---

## 7. Hiệu năng

| Quy tắc | Vì sao |
|---|---|
| Script chạy **theo sự kiện**, không mỗi tick | §2 |
| **Timeout mỗi lần gọi** (VD: 5ms) | Script chậm không được giết tick |
| Script vượt timeout → **huỷ + log** | Fail fast |
| Cache compiled script | Không parse lại mỗi lần |
| Script nặng (snapshot ranking) → **Virtual Thread**, không trong tick | [SERVER.md §7](../05-server/SERVER.md) |

⚠️ **Script chạy trong game tick thread** → một script tồi làm giật cả server. Timeout không phải tuỳ chọn.

---

## 8. Testing Script

Script là code → phải test được:

| Cách | Chi tiết |
|---|---|
| Unit test script | Chạy script với `ctx` giả (Fake), assert kết quả |
| Validate lúc khởi động | Parse mọi script → lỗi cú pháp = fail fast, không đợi người chơi nhận quest |
| CI | Chạy toàn bộ script test |

**Fail fast lúc khởi động là bắt buộc** — không để lỗi cú pháp quest phát hiện lúc người chơi bấm nhận nhiệm vụ.

---

## 9. Script vs Config — phân định

Không phải thứ gì "không hardcode" cũng cần script.

| Dùng **Config (DB/JSON)** | Dùng **Script** |
|---|---|
| Tham số: cooldown, damage, giá tiền | **Logic**: điều kiện phức tạp, chuỗi hành động |
| Tỉ lệ rơi đồ | Boss các pha |
| Chỉ số quái | Quest có nhánh |
| Bảng exp | Dialogue có lựa chọn |

⚠️ **Đừng script hoá thứ chỉ cần config.** Script phức tạp hơn, chậm hơn, dễ lỗi hơn. Config là lựa chọn đầu tiên; script chỉ khi cần **logic**.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **Chọn MỘT script engine**, không hỗ trợ cả hai | Hai sandbox = hai chỗ có lỗ hổng | Accepted |
| 2 | **GraalJS** | Sandbox dựng sẵn (`SandboxPolicy`); bảo trì tích cực; chạy trên Temurin JDK 25 hiện có, không phá ADR-001 | ✅ Accepted — [ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md) |
| 3 | Script chạy **server-side** | Điều 15 | Accepted |
| 4 | Script chạy **theo sự kiện**, không mỗi tick | Hiệu năng | Accepted |
| 5 | **Sandbox bắt buộc**: whitelist thư viện, giới hạn instruction/memory/timeout | Điều 27 | Accepted |
| 6 | **API dạng dữ liệu thuần, KHÔNG expose object Java** | Object Java → reflection → thoát sandbox | Accepted — quan trọng nhất |
| 7 | **Timeout mỗi lần gọi script** | Script tồi không được giật server | Accepted |
| 8 | **Validate toàn bộ script lúc khởi động** | Fail fast, không đợi người chơi phát hiện | Accepted |
| 9 | Config trước, Script sau — không script hoá thứ chỉ cần config | Điều 9 | Accepted |
| 10 | Hot reload: dev ✅, production cẩn thận | State cũ vs code mới | ⏳ Thiết kế Phase 6 |

## Tài liệu liên quan

- [PLUGIN.md](PLUGIN.md) — Plugin ≠ Script
- [GAMEPLAY.md §4, §9](../05-server/GAMEPLAY.md) — skill/quest dùng script
- [AI.md §4](../03-engine/AI.md) — Boss AI dùng script
- [SECURITY.md](../08-security/SECURITY.md) — sandbox là vấn đề bảo mật
- [DATABASE.md](../05-server/DATABASE.md) — `script_ref`, `effect_script_ref`

## Việc cần làm tiếp theo

1. ✅ Thêm `org.graalvm.polyglot:js` vào `libs.versions.toml` (Phase 4).
2. ✅ Sandbox thiết kế trước, API sau (§4) — `ScriptSandbox` (Phase 4).
3. ✅ §8 "Validate lúc khởi động" đã làm (2026-07-23, Phase 6) — `ScriptSandbox.validateSyntax`
   (parse-only, `Context.parse`) + `ScriptValidator` (`engine-scripting`), gọi từ
   `GatewayBootstrap` TRƯỚC khi world tick/server nhận kết nối. Danh sách script gom từ 2 nguồn:
   DB (`QuestTemplatePort.listAllScriptRefs`/`NpcTemplatePort.listAllDialogueScriptRefs` — tự
   động khớp thực tế, không cần khai tay) + hằng số Java (`WorldFacade.listSkillScriptResources`,
   vì skill chưa DB-driven). Xem `ScriptValidatorTest`/`ScriptSandboxTest` (engine-scripting).
