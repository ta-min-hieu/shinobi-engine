# EDITOR.md

| | |
|---|---|
| Status | 🚧 **STUB — CÓ CHỦ ĐÍCH** |
| Blocked by | Cần gameplay thật để biết editor cần chỉnh gì |
| Sẽ viết khi | [Phase 6+](../11-process/ROADMAP.md) — sau khi có quest/item/skill thật |

---

## Vì sao chưa viết

Content editor (Map/NPC/Quest/Skill/Item Editor) chỉ có ý nghĩa khi **đã biết content trông thế nào**. Thiết kế editor trước khi có `quest_template`, `skill_template` thật = đoán mò → làm lại.

Điều 9 (Simplicity) + Điều 10 (công cụ phục vụ gameplay, không ngược lại).

---

## Phân biệt: có sẵn vs tự viết

| Content | Công cụ | Tự viết? |
|---|---|---|
| Map / Scene | **HyperLap2D** ([RESOURCE.md](../04-client/RESOURCE.md)) | ❌ Đã có |
| Animation | HyperLap2D | ❌ Đã có |
| **Quest** | — | ⏳ Có thể cần |
| **Skill** | — | ⏳ Có thể cần |
| **Item** | — | ⏳ Có thể cần |
| NPC / Dialogue | — | ⏳ Có thể cần |

⚠️ **Quan trọng**: Map/Animation đã có HyperLap2D. Editor tự viết chỉ cho **data** (quest/skill/item) — và câu hỏi là: có cần **GUI editor**, hay **chỉnh trực tiếp file JSON/script + validate** là đủ?

---

## Cách rẻ nhất trước (đề xuất)

Trước khi xây GUI editor, thử:

| Cấp độ | Cách | Chi phí |
|---|---|---|
| 1 | Chỉnh trực tiếp JSON/Lua + validate lúc load | Gần như 0 |
| 2 | Google Sheets → export JSON | Thấp |
| 3 | GUI editor tự viết | Cao |

⚠️ **Nhiều game vận hành hoàn toàn ở cấp 1–2.** Chỉ lên cấp 3 khi game designer thật sự không thao tác nổi với file — **đo nhu cầu thật, không đoán**.

---

## Bố cục dự kiến (khi viết)

1. Vì sao cần GUI editor (nhu cầu thật từ designer)
2. Quest Editor · Skill Editor · Item Editor · NPC/Dialogue Editor
3. Validate + export → format runtime ([DATABASE.md](../05-server/DATABASE.md), [SCRIPTING.md](../06-plugin/SCRIPTING.md))
4. Editor dùng chung engine hay tách?

## Câu hỏi trước khi viết

- [ ] Game designer có thao tác được với JSON/Lua + validate không? (nếu có → **không cần** editor)
- [ ] Content nhiều tới mức nào? (10 quest ≠ 1000 quest)
- [ ] Có non-technical designer không?

## Tài liệu liên quan

- [RESOURCE.md](../04-client/RESOURCE.md) — HyperLap2D đã lo map/animation
- [SCRIPTING.md](../06-plugin/SCRIPTING.md) — quest/skill là script
- [DATABASE.md](../05-server/DATABASE.md) — template tables

## Việc cần làm tiếp theo

1. **Không làm gì tới Phase 6.**
2. Phase 6: thử cấp 1–2 trước. Chỉ xây editor nếu đo được nhu cầu thật.
