# GAME-DESIGN-DOCUMENT.md

**Thiết kế game — game này LÀ GÌ**

| | |
|---|---|
| Version | 0.2.0 |
| Status | Draft — cần duyệt trước khi khoá scope MVP |
| Liên quan | [GAMEPLAY.md](../05-server/GAMEPLAY.md) — server implement thế nào |

> Các tài liệu 00–12 nói **xây dựng thế nào**. Tài liệu này nói **xây dựng cái gì**.
> Mọi hệ thống gameplay phải map ngược về một mục ở đây trước khi có Component/System ([GAMEPLAY.md §1](../05-server/GAMEPLAY.md)).

---

## 1. Thể loại & Cảm hứng

2D anime ninja MMORPG side-view — cùng thể loại Ninja School Online, Ngôi Sao Trắng.

⚠️ **Cảm hứng ≠ sao chép** (Điều 5). Không sao chép IP, tên riêng, cốt truyện, asset. Thế giới/nhân vật là **nguyên bản**. Chỉ vay mượn **cơ chế gameplay** đặc trưng thể loại:

- Ninja side-view, nhảy nhiều tầng địa hình
- Combat real-time (auto-target + skill chủ động)
- Hệ phái nguyên tố thay class cố định
- Chuyển sinh mở rộng vòng đời
- Bang hội, PvP, thời trang, pet, sự kiện mùa

---

## 2. Game Pillars

Mọi tính năng phải phục vụ ≥1 pillar. Không → loại khỏi scope.

1. **Tiến trình đọc được** — người chơi luôn biết bước tiếp theo để mạnh hơn.
2. **Chơi cùng nhau có ý nghĩa** — bang/PvP/event tạo lý do chơi cùng người khác.
3. **Chiều sâu không phụ thuộc grind thuần** — build đa dạng > số giờ cày.
4. **Server Authority tuyệt đối** — Điều 15.

---

## 3. Thế giới

**Đại lục Kagesekai.** Sức mạnh gọi là **Khí**. Sau "Đại Chiến Ngũ Hệ", các làng ẩn lập **Học Viện Ninja** trung lập đào tạo thế hệ mới — điểm khởi đầu người chơi.

### Bản đồ

| Khu vực | Vai trò |
|---|---|
| **Học Viện Ninja** | Hub trung tâm, safe zone tuyệt đối |
| **5 Lãnh Địa Hệ Phái** | Luyện cấp theo hệ, khó dần theo level |
| **Vùng Tranh Chấp** | PvP mở, tài nguyên hiếm |
| **Bí Cảnh** | Instance theo party + Hard Mode + World Boss |

---

## 4. Năm hệ phái (thay "class")

| Hệ | Nguyên tố | Thiên hướng | Khắc | Bị khắc |
|---|---|---|---|---|
| Hỏa | Lửa | Sát thương liên tục, bùng nổ | Phong | Thủy |
| Thủy | Nước | Khống chế, hồi phục | Hỏa | Thổ |
| Phong | Gió | Tốc độ, bạo kích | Thổ | Hỏa |
| Lôi | Sấm | Đơn mục tiêu, xuyên giáp | — | — |
| Thổ | Đất | Phòng thủ, khiên | Thủy | Phong |

⚠️ Hệ số khắc chế ±15% là **số tham khảo, phải cân bằng qua playtest** (Phase 4+). Lôi trung lập để cân bằng meta.

Chọn hệ ở **Lv10**. Đổi hệ 1 lần miễn phí trước Lv30, sau đó tốn **Đá Chuyển Hệ**.

---

## 5. Nhân vật & Tiến trình

### Chỉ số

Cơ bản: `STR · AGI · INT · VIT · Khí`
Dẫn xuất (⚠️ **server tính** — [GAMEPLAY.md §2](../05-server/GAMEPLAY.md)): `ATK, DEF, HIT, DODGE, CRIT, CRIT_DMG, ATK_SPEED, MOVE_SPEED`

Công thức derive ở `CombatConstants` — không hardcode.

### Level & Chuyển sinh

- **Lv1–100**: mở khoá hệ (10), skill (15), pet (20), guild (25), PvP (30)
- **Chuyển Sinh** (Lv100): reset về 1, giữ trang bị/danh hiệu, nhận **Điểm Nguồn** vĩnh viễn. Tối đa 5 lần ở MVP (Feature Flag mở rộng)
- Mục đích: kéo dài vòng đời không cần nâng trần level vô hạn (tránh power creep)

### Skill

- Mỗi hệ: 3 active + 2 passive + 1 **Tuyệt Kỹ** (Lv50, CD 60–120s, tốn toàn bộ Khí)
- Skill point từ level + quest — **không mua bằng tiền thật**

### Trang bị

Slot: Vũ khí · Giáp · Mũ · Găng · Giày · Phụ kiện ×2

- **Cường hoá** +0→+15, tỉ lệ giảm dần, có **Bảo Vệ** chống rớt cấp từ +10 (chống frustration)
- **Ngọc** (Post-MVP): bonus phụ, có **pity system**
- **Set** 4/6/8 món: khuyến khích build đa dạng, không 1 món OP tuyệt đối

### Pet & Mount

- Pet: buff/damage phụ, level riêng, tiến hoá
- Mount: **chỉ tốc độ + thẩm mỹ, KHÔNG cộng chỉ số** (pay-for-cosmetic sạch)

---

## 6. Combat

Real-time side-view. Auto-target đánh thường; skill chủ động bằng phím 1–6 / chạm.

| Loại | Nội dung |
|---|---|
| PvE | Quái thường; Bí Cảnh party (2–5); World Boss theo lịch |
| PvP | Arena 1v1 (MMR/season); Battlefield 5v5 (objective); Chiếm Lãnh Địa (guild war) |

⚠️ Toàn bộ combat **server tính** (Điều 15). Vi phạm = bug nghiêm trọng.

⚠️ **Công thức damage cụ thể CHƯA CÓ** — là quyết định game design, **chặn Phase 4** ([GAMEPLAY.md §3](../05-server/GAMEPLAY.md)).

---

## 7. Xã hội

| Hệ thống | Mô tả |
|---|---|
| Guild | Từ Lv25; cấp bang, kho, nhiệm vụ tuần, chiến tranh lãnh địa |
| Friend | Danh sách, trạng thái online, mời party/trade |
| Mail | Gửi item/tiền, phí chống spam gold-selling |
| Chat | Thế giới/Bang/Party/Riêng/Khu vực; rate-limit + filter |
| Auction | Giao dịch liên instance, thuế (gold sink) |
| Ranking | Level/PvP/boss damage/guild; cập nhật định kỳ |

---

## 8. Quest & Nội dung

Điều 27 — **không hardcode**, qua Script Sandbox ([SCRIPTING.md](../06-plugin/SCRIPTING.md)):

- **Cốt truyện chính** Lv1–100, giới thiệu lore 5 hệ
- **Nhiệm vụ ngày** — nguồn resource ổn định
- **Nhiệm vụ bang** — hợp tác
- **Sự kiện mùa** — Feature Flag bật/tắt không deploy lại

---

## 9. Core Loop

```
Nhận mục tiêu → Đánh quái/Bí Cảnh/PvP → EXP+Item+Tiền
→ Nâng cấp trang bị/skill/chuyển sinh → Mạnh hơn → Nội dung khó hơn
→ (quay lại, đồng thời tương tác bang/bạn/đấu giá)
```

Mục tiêu phiên chơi: **15–25 phút** cho một chuỗi hoàn chỉnh (hợp cả desktop lẫn mobile).

---

## 10. Kinh tế & Monetization

⚠️ **Mô hình kinh doanh CHƯA CHỐT** — cần ADR nếu quyết định thu phí. Nguyên tắc nếu có:

- ❌ **Không Pay-to-Win**: không bán ATK/DEF thô bằng tiền thật
- ✅ **Pay-for-Cosmetic/Convenience**: thời trang, mount, mở túi, rút ngắn chờ
- ✅ **Gold Sink bắt buộc**: cường hoá, khảm, phí đấu giá/thư — chống lạm phát
- Mọi thay đổi economy qua Data-Driven Config, không sửa code (Điều 27)

⚠️ Theo dõi `kage.gold.total` để phát hiện lạm phát/duplicate ([OBSERVABILITY.md §3.5](../07-infrastructure/OBSERVABILITY.md)).

---

## 11. MVP Scope

Chống scope creep (Điều 9). MVP = **hết [Phase 6](../11-process/ROADMAP.md)**.

**Có trong MVP:**
- 1 hệ phái đầy đủ (đề xuất: **Hỏa**) — verify toàn pipeline trước khi nhân ra 4 hệ
- Lv1–30, không Chuyển Sinh
- PvE cơ bản (quái + 1 Bí Cảnh mẫu)
- Trang bị + cường hoá (không Ngọc)
- Chat + Friend + Mail cơ bản
- 1 hub + 1 map luyện cấp

**Post-MVP:** PvP xếp hạng, Battlefield, Chiếm lãnh địa · Guild đầy đủ · 4 hệ còn lại · Auction, Ranking toàn cục · Mount, Pet evolve, Festival · Trade.

---

## 12. ⚠️ Open Questions — chốt trước khi code

Các câu này **chưa có quyết định**, chặn các Phase tương ứng:

| # | Câu hỏi | Chặn | ADR |
|---|---|---|---|
| 1 | **Công thức damage/derived stats** cụ thể | Phase 4 | — (việc game designer) |
| 2 | Desktop-first hay mobile-first | Input, UI, launcher | ADR-023 |
| 3 | ~~Single-realm hay multi-realm~~ → single-realm + `realm_id` | Schema, scaling | ✅ [ADR-010](../01-architecture/adr/ADR-010-Realm-Model.md) |
| 4 | i18n: chỉ tiếng Việt hay có tiếng Anh ở MVP | UI | — |
| 5 | ~~Tên thương hiệu~~ → package root `dev.kage` (branding đổi sau được) | Package root | ✅ [ADR-012](../01-architecture/adr/ADR-012-Package-Root.md) |
| 6 | Mô hình kinh doanh | §10 | — |
| 7 | Viewport (ảnh hưởng công bằng PvP) | Phase 1 | ADR-020 |
| 8 | Kích thước AoI (thấy được ai) | Phase 3 | — (đo Phase 3) |

Mỗi câu trả lời → cập nhật tài liệu này hoặc tạo ADR.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 hệ phái nguyên tố thay class cố định | Accepted |
| 2 | Chuyển sinh thay nâng trần level vô hạn | Accepted |
| 3 | Mount/cosmetic **không** cộng chỉ số chiến đấu | Accepted |
| 4 | MVP: **1 hệ (Hỏa)** trước, nhân ra sau | Accepted |
| 5 | Không Pay-to-Win | Accepted (nếu có monetization) |
| 6 | **Công thức damage chưa có** | ⏳ **Chặn Phase 4** |
| 7 | Hệ số cân bằng là số tham khảo, chờ playtest | ⏳ Phase 4+ |
| 8 | 8 open questions §12 | ⏳ |

## ADR cần tạo

Xem §12 — ✅ ADR-010, ADR-012 đã chốt; còn ADR-020, ADR-023.

## Tài liệu liên quan

- [GAMEPLAY.md](../05-server/GAMEPLAY.md) — server implement
- [GLOSSARY.md](../GLOSSARY.md) — thuật ngữ
- [ROADMAP.md](../11-process/ROADMAP.md) — nội dung theo Phase
- [DATABASE.md](../05-server/DATABASE.md) — bảng dữ liệu
- [ADR Index](../01-architecture/adr/README.md)

## Việc cần làm tiếp theo

1. ✅ §12 câu 3 (realm) và câu 5 (package) đã chốt.
2. 🔴 **Bổ sung công thức damage — chặn Phase 4** (việc game designer).
3. Playtest cân bằng hệ số §4 từ Phase 4.
