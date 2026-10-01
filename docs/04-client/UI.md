# UI.md

**Giao diện người dùng (Scene2D)**

| | |
|---|---|
| Version | 1.1.0 — song ngữ Việt–Anh (§7.1, §7.2), 2026-10-01 |
| Status | Draft |
| Liên quan | [CLIENT.md](CLIENT.md), [RENDERING.md](../03-engine/RENDERING.md) |

---

## 1. Phạm vi

| Scene2D dùng cho | Không dùng cho |
|---|---|
| HUD (thanh máu, Khí, skill bar) | Render thế giới game |
| Menu (login, chọn nhân vật, cài đặt) | Entity gameplay |
| Cửa sổ (túi đồ, kỹ năng, bang hội) | Hiệu ứng skill |
| Dialog NPC | |
| Chat box | |

**UI có camera riêng, atlas riêng, layer riêng** ([RENDERING.md §9](../03-engine/RENDERING.md)).

---

## 2. Vì sao Scene2D (dù nó cũ)

| Ủng hộ | Phản đối |
|---|---|
| Đi kèm libGDX, không thêm dependency | API cũ, layout khó chịu |
| Đủ cho HUD/menu | Không có data binding |
| Có sẵn Skin/Theme system | Table layout khó debug |

**Quyết định**: dùng Scene2D ở MVP (Điều 9 — không thêm dependency cho thứ đã có).

⚠️ **Rủi ro ghi nhận**: UI của MMORPG rất nhiều (túi đồ, bang hội, đấu giá, chợ...). Scene2D có thể trở thành điểm đau khi UI phình to. Nếu Phase 8 (khi UI phình theo guild/auction/PvP) thấy không kham nổi → cân nhắc thư viện UI khác, nhưng **đó là quyết định tốn kém**, cần ADR.

---

## 3. Cấu trúc UI

**Thực tế đã code** (2026-08-08, HUD combat + Login/CharacterSelect — lệch vài chỗ so với bản kế
hoạch ban đầu, ghi lại đúng thực tế thay vì sửa code cho khớp doc cũ):

```
client-core/
├── ui/
│   ├── theme/
│   │   ├── KageSkin.java        ← Skin thủ tục (mảng màu phẳng, chưa có atlas UI thật)
│   │   ├── KageI18n.java        ← nạp I18NBundle theo ngôn ngữ đang chọn (§7.1)
│   │   ├── ClientLanguage.java  ← VI/EN; LanguageSettings + LanguageStore (port) +
│   │   │                            GdxPreferencesLanguageStore lưu lựa chọn cục bộ
│   ├── widget/
│   │   └── HealthBar.java       ← ItemSlot/CooldownIcon CHƯA làm, chưa có consumer thật
│   └── hud/
│       └── CombatHud.java       ← gộp PlayerStatusHud+SkillBarHud (1 skill duy nhất, chưa
│                                    tách — tách khi HUD có thêm bề mặt). ChatHud CHƯA làm.
└── screen/                      ← GameScreen thật (KageEngine.setScreen), không nằm trong ui/
    ├── LoginScreen.java         ← không phải "LoginScreenUi"
    └── CharacterSelectScreen.java
```

`UiConstants.java` chưa cần — 2 màn hình + 1 HUD hiện tại chưa đủ lặp lại số đo để đáng tách hằng
số riêng (Điều 30, tránh abstraction thừa trước nhu cầu thật).

---

## 4. Widget

**Quy tắc**: widget **không biết gameplay**. Widget nhận dữ liệu, hiển thị dữ liệu.

```java
// ❌ Sai — widget tự đi tìm dữ liệu
class HealthBar extends Widget {
    void update() {
        var hp = world.getEntity(playerId).getComponent(HealthComponent.class);  // ❌
    }
}

// ✅ Đúng — widget nhận dữ liệu
class HealthBar extends Widget {
    void setValues(int current, int max) { ... }
}
// HUD controller đọc ECS rồi đẩy vào widget
```

**Vì sao**: widget không biết ECS → test được độc lập, tái sử dụng được (thanh máu của quái, của boss, của người chơi đều là nó).

---

## 5. Theme / Skin

| Quyết định | Chi tiết |
|---|---|
| **1 skin duy nhất** ở MVP | Điều 9 — không làm hệ thống theme khi chưa cần |
| Skin định nghĩa trong file `.json` + atlas | Chuẩn libGDX |
| Màu sắc, khoảng cách → `UiConstants` | Không hardcode rải rác |

**Không** làm dark mode / theme tuỳ chỉnh ở MVP. Chưa gameplay nào cần (Điều 10).

---

## 6. Responsive & UI Scale

Đây là phần khó nhất của UI đa nền tảng.

| Vấn đề | Xử lý |
|---|---|
| Màn hình 1920×1080 vs 720×1280 (dọc) | UI dùng `Table` với tỉ lệ, không toạ độ tuyệt đối |
| DPI khác nhau (điện thoại nhỏ, chữ không đọc được) | `PlatformAdapter.uiScale()` → nhân kích thước |
| Điện thoại có notch / tai thỏ | Safe area inset qua adapter |
| Ngón tay to hơn con trỏ chuột | **Nút tối thiểu 48dp trên mobile** |

⚠️ **Vùng chạm tối thiểu là bắt buộc**, không phải "nice to have". Nút nhỏ hơn 48dp trên mobile = không bấm trúng = game không chơi được.

**Chưa chốt**: UI có **layout riêng cho mobile** hay **cùng một layout co giãn**?

| | Ưu | Nhược |
|---|---|---|
| Cùng layout, co giãn | Ít việc, dễ bảo trì | Layout desktop hiếm khi hợp mobile |
| Layout riêng cho mobile | Trải nghiệm tốt hơn hẳn | Gấp đôi công UI |

**Đề xuất**: HUD gameplay layout riêng (rất khác nhau), menu/cửa sổ dùng chung co giãn.

---

## 7. Localization (i18n)

**Nguyên tắc bắt buộc từ đầu**: **không hardcode chuỗi hiển thị trong code.**

```java
// ❌ Sai
label.setText("Không đủ Khí");

// ✅ Đúng
label.setText(i18n.get("skill.error.not_enough_chi"));
```

**Vì sao bắt buộc ngay cả khi chỉ có tiếng Việt**: thêm ngôn ngữ sau khi có 500 chuỗi hardcode rải rác = phải rà toàn bộ codebase. Làm đúng từ đầu tốn ~0 công.

Điều này khớp với [NETWORK.md §107](../05-server/NETWORK.md): server trả `errorKey`, không trả message tiếng Việt — client tự dịch.

| Hạng mục | Quyết định |
|---|---|
| Công cụ | libGDX `I18NBundle` (có sẵn) |
| File | `strings_vi.properties`, `strings_en.properties` |
| MVP có tiếng Anh không? | ✅ **CÓ — song ngữ Việt–Anh từ MVP** (quyết định chủ dự án 2026-10-01). Mặc định tiếng Việt |

### 7.1 Đổi ngôn ngữ (2026-10-01)

| Hạng mục | Thực tế đã code |
|---|---|
| Chỗ đổi | **Chỉ ở `LoginScreen`** — hàng nút "Tiếng Việt" / "English" dưới nút Kết nối, kèm dòng chữ "Ngôn ngữ: …" (không dùng màu xám của nút đang chọn làm thông tin duy nhất, §8). Khoá khi đang kết nối |
| Ngôn ngữ có | `ClientLanguage` (`VI`, `EN`); `DEFAULT = VI` |
| Lưu ở đâu | Máy người chơi, libGDX `Preferences` bộ `kage-client`, khoá `language` (`GdxPreferencesLanguageStore`). Desktop: `~/.kage/kage-client` (launcher đặt `setPreferencesConfig`). Giá trị lạ/hỏng ⇒ tiếng Việt (`LanguageSettings`) |
| Server biết không? | **Không.** Thuần client: không opcode, không cột DB. Server chỉ gửi khoá (`error_key`, `name_key`, `text_key`) nên câu lỗi server **tự đổi theo** ngôn ngữ đã chọn (`KageI18n.errorText` dịch bằng bundle của màn hình) |
| Cơ chế | `KageI18n.useLanguage(...)` đổi ngôn ngữ của TIẾN TRÌNH; `KageI18n.load()` nạp theo nó. Mọi màn hình/HUD/coordinator nạp bundle **một lần lúc dựng** — vì thế đổi được an toàn chỉ ở màn đăng nhập: `LoginScreen` tự nạp lại bundle + viết lại mọi chữ của nó (dòng trạng thái nhớ dưới dạng KHOÁ, nên câu lỗi cũng đổi theo); màn chọn nhân vật, màn world và mọi HUD đều được dựng MỚI sau đó |
| ⚠️ Muốn đổi trong game | Phải dựng lại cả cây UI đang sống (WorldScreen + ~14 panel + coordinator đều giữ bundle cũ trong field `final`). Chưa làm — chưa có nhu cầu |
| Tên ngôn ngữ | `language.name.vi` = "Tiếng Việt", `language.name.en` = "English" **giống hệt ở cả hai bundle**: lỡ chọn nhầm sang thứ tiếng không đọc được vẫn thấy đường quay về |

### 7.2 Cổng chặn chữ tràn nút (2026-10-01)

Mỗi chuỗi giờ có HAI độ dài. Bẫy "nút bề rộng cố định" (nhãn vẽ tràn ra ngoài nền nút — nhật ký
nhiệm vụ 2026-09-05) trước đây chỉ rà được bằng ảnh chụp game thật. Nay là test JUnit chạy trong
`check`, không cần GL:

- `TextWidthMeter` (test, `ui/theme`) — đo bề rộng chữ bằng metric FreeType THẬT, đúng font/cỡ/tập
  ký tự của `KageSkin`; mẹo không cần GL: tự đưa `PixmapPacker` cho `generateData` nên không dựng
  `Texture`. Lề tối thiểu 4px mỗi bên.
- `HudButtonLabelFitTest` / `ScreenButtonLabelFitTest` — bảng VIẾT TAY các nút rộng cố định (hằng bề
  rộng lấy thẳng từ panel, package-private) × khoá nhãn, đo ở MỌI ngôn ngữ. **Thêm nút rộng cố định
  mới ⇒ thêm một dòng vào bảng.** Nút dùng `minWidth` (VD `QuestLogPanel`) tự nở, không cần có mặt.
- Có ca đối chứng: thước phải báo tràn "Nhận thưởng" trong nút 96px cũ của `QuestLogPanel`.

Lần chạy đầu bắt được (đã sửa): `TradePanel` "Đặt linh thạch"/"Set spirit stones" tràn ~40–60px
khỏi nút 104px — **tràn cả ở tiếng Việt**; dòng nhân vật ở `CharacterSelectScreen` tràn ở CẢ HAI
ngôn ngữ với tên 16 ký tự (cỡ chữ thật ~11,5px/ký tự, không phải 9,7 như ước cũ) ⇒ 440 → 640px;
"Add friend", "Create New Character", "Spirit Ward: On/Off", ô gợi ý phím "Target/Mouse",
"Attack/Z"; vài nút tiếng Việt chạm sát viền ("Lập bang", "Thách đấu", "Chấp nhận", "Làm mới").

`MessageBundleIntegrityTest` thêm ca: không câu (≥ 3 từ) nào ở bundle tiếng Việt được trùng
nguyên văn câu tiếng Anh — bắt được 8 khoá `error.*` của server mang câu tiếng Anh trong
`strings_vi.properties`.

⚠️ **Tiếng Việt có dấu cần font hỗ trợ** — dùng `gdx-freetype` ([TECHNOLOGY-STACK.md §4](../01-architecture/TECHNOLOGY-STACK.md)). **Test glyph tiếng Việt ở Phase 1**, không để tới cuối mới phát hiện font thiếu chữ.

---

## 8. Accessibility

Trung thực về phạm vi: MMORPG 2D không thể đạt accessibility đầy đủ. Nhưng **những thứ rẻ mà giá trị cao** thì nên làm:

| Làm ở MVP | Vì sao |
|---|---|
| **Không dùng màu làm thông tin duy nhất** | ~8% nam giới mù màu đỏ-lục. Hồi máu/mất máu chỉ khác màu = không phân biệt được → thêm icon/số |
| Cỡ chữ đủ lớn, tương phản đủ | Rẻ, ai cũng hưởng lợi |
| Cho phép chỉnh cỡ chữ | Rẻ |
| Rebind phím (desktop) | Rẻ, đã có §5 CLIENT.md |

| Không làm ở MVP | Vì sao |
|---|---|
| Screen reader | Không khả thi với game realtime 2D |
| Chế độ mù màu đầy đủ | Tốn công; ưu tiên "không dùng màu làm thông tin duy nhất" trước |

---

## 9. UI và Performance

| Quy tắc | Vì sao |
|---|---|
| **UI atlas tách riêng gameplay atlas** | UI luôn resident, gameplay atlas unload theo zone |
| Không `new` widget mỗi frame | Điều 31 |
| `setText()` chỉ khi giá trị **thay đổi** | `setText` với String mới = allocation |
| Cửa sổ đóng → `setVisible(false)`, không destroy/recreate | Tạo lại UI tốn |
| Danh sách dài (túi đồ 100 slot) → tái sử dụng cell | Không tạo 100 widget cùng lúc |

```java
// ❌ Tạo rác mỗi frame
hpLabel.setText("HP: " + current + "/" + max);

// ✅ Chỉ cập nhật khi đổi
if (current != lastHp) {
    hpLabel.setText(hpTextCache.format(current, max));
    lastHp = current;
}
```

---

## 10. UI không được làm gì

| ❌ Cấm | Vì sao |
|---|---|
| Chứa gameplay logic | UI chỉ hiển thị |
| Gọi thẳng network | Qua controller/net layer |
| Tự tính damage/giá tiền | Server tính (Điều 15) |
| Đọc ECS trực tiếp từ widget | §4 |
| Hardcode chuỗi hiển thị | §7 |
| Hardcode toạ độ tuyệt đối | §6 |
| **Khung HUD che vùng chơi vô thời hạn** | Xem ngay dưới |

### 10.1. Khung HUD không được đứng che vùng chơi mãi (2026-08-30)

Nhật ký chat rộng 420px neo trái-dưới, tức nó che hẳn **một phần ba trái** vùng chơi ở cửa sổ
1280px — và con quái đang đánh mình thường đứng đúng sau nó. Buổi playtest 2026-08-30 (ROADMAP mục
48) thấy chữ "−18" bay lên mà không thấy ai đánh, và suýt kết luận nhầm rằng art quái mới không
hiện.

Luật rút ra, áp cho MỌI khung HUD đứng đè lên vùng chơi: **có thông tin mới thì hiện, im lặng quá
lâu thì tự ẩn**. `ChatPanel.IDLE_HIDE_SECONDS` (12 giây) là bản đầu tiên thi hành luật này; dòng mới
hoặc phím mở ô nhập là hiện lại ngay.

⚠️ Đây là thứ **build gate không bắt được**: không test nào đỏ, chỉ có mắt người nhìn ảnh chụp mới
thấy. Cùng loại với bẫy "vạch máu 139/100" và "khung túi đồ tràn màn hình" đã trả giá trước đó.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Dùng Scene2D ở MVP | Có sẵn, Điều 9 | Accepted — ghi nhận rủi ro khi UI phình |
| 2 | **Widget không biết ECS** — nhận dữ liệu qua setter | Test được, tái sử dụng được | Accepted |
| 3 | **1 skin duy nhất**, không làm theme system | Điều 9, 10 | Accepted |
| 4 | **i18n bắt buộc từ đầu** kể cả khi chỉ có tiếng Việt | Retrofit i18n rất đắt; làm đúng từ đầu tốn ~0 | Accepted |
| 4b | **Song ngữ Việt–Anh từ MVP**, chọn ở màn đăng nhập, lưu cục bộ, thuần client (§7.1) | Quyết định chủ dự án 2026-10-01; server chỉ gửi khoá nên không cần protocol/DB | Accepted |
| 5 | Server trả `errorKey`, client dịch | Khớp NETWORK.md | Accepted |
| 6 | **Nút tối thiểu 48dp trên mobile** | Không bấm trúng = không chơi được | Accepted |
| 7 | **Không dùng màu làm thông tin duy nhất** | Mù màu ~8% nam giới; chi phí gần 0 | Accepted |
| 8 | Không screen reader / accessibility đầy đủ | Không khả thi với game realtime 2D | Accepted — trung thực về giới hạn |
| 9 | UI atlas tách riêng gameplay atlas | Vòng đời khác nhau | Accepted |
| 10 | HUD layout riêng cho mobile; menu dùng chung co giãn | Cân bằng công sức vs trải nghiệm | ⏳ Đề xuất — chờ chốt mobile-first |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| ~~**ADR-023**~~ | ✅ **Desktop-first — Accepted 2026-09-05** ([ADR-023](../01-architecture/adr/ADR-023-Desktop-First-Input.md)). HUD §6 thiết kế cho bàn phím + chuột; bố cục cảm ứng để dành cho ADR riêng khi làm mobile thật | Hết chặn |
| — | Nếu Phase 8 thấy Scene2D không kham nổi UI MMORPG → ADR đánh giá thư viện khác | 🟢 Chờ dữ liệu |

## Tài liệu liên quan

- [CLIENT.md](CLIENT.md) — §5 input, §11 PlatformAdapter (nguồn `uiScale()`)
- [RENDERING.md §9](../03-engine/RENDERING.md) — UI là layer riêng
- [RESOURCE.md](RESOURCE.md) — UI atlas, font
- [NETWORK.md](../05-server/NETWORK.md) — `errorKey` cho i18n

## Việc cần làm tiếp theo

1. ✅ **Test font tiếng Việt có dấu** (§7) — `FontFactoryVietnameseGlyphTest`, xong từ Phase 1 (xác
   nhận độc lập lại 2026-08-02). `LoginScreen`/`CharacterSelectScreen`/`CombatHud` (2026-08-08) là
   consumer thật đầu tiên, render đúng ("Hỏa Cầu", "Hình nộm luyện tập"...).
2. Chốt mobile-first hay desktop-first → mở khoá §6 và ADR-023. Vẫn CHƯA chốt — 2 màn hình +
   HUD hiện tại (§3) mới build cho desktop, chưa test `uiScale()`/vùng chạm 48dp trên mobile thật.
3. ✅ **Hạ tầng i18n** (§7) — `KageI18n`/`I18NBundle` + `strings_vi.properties`/`strings_en.properties`,
   dùng thật trong cả 3 màn hình từ 2026-08-08. ✅ **Song ngữ Việt–Anh** (chủ dự án chốt
   2026-10-01): chọn ngôn ngữ ở màn đăng nhập, lưu cục bộ, mặc định tiếng Việt — §7.1; cổng chặn
   chữ tràn nút ở cả hai ngôn ngữ — §7.2.
4. ⏳ **Chưa kiểm bằng mắt trên client thật**: hàng nút ngôn ngữ ở màn đăng nhập, và các nút vừa
   nới bề rộng (`TradePanel`, `DuelPanel`, `GuildPanel`, `InventoryPanel`, `MailPanel`, ô gợi ý phím
   `CombatHud`, dòng nhân vật 640px). Cổng §7.2 đo CHỮ trong NÚT; nó không đo được một hàng nút rộng
   hơn khung chứa — chạy `runHudLayoutCheck` + `runHudPreview` (cần GL) ở cả hai ngôn ngữ
   (`-PkageLanguage=en` cho tiếng Anh).
5. ⏳ Đổi ngôn ngữ TRONG GAME (không chỉ ở màn đăng nhập) — cần dựng lại cả cây UI, xem §7.1.
