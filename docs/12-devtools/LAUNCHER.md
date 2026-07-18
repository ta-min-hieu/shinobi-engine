# LAUNCHER.md

| | |
|---|---|
| Status | 🚧 **STUB — CÓ CHỦ ĐÍCH** |
| Blocked by | [ADR-024](../01-architecture/adr/README.md) (asset distribution) · chưa có bản phát hành |
| Sẽ viết khi | [Phase 10 — Release](../11-process/ROADMAP.md) |

---

## Vì sao chưa viết

Launcher (auto-update, patch, version check) chỉ cần khi **có bản phát hành cho người chơi tải**. Trước Release không có gì để patch.

---

## Phân biệt: Launcher ≠ Game Launcher

⚠️ Đừng nhầm hai thứ:

| | Nghĩa |
|---|---|
| `client-desktop` **launcher** | Class `main()` khởi động game ([CLIENT.md §2](../04-client/CLIENT.md)) — **đã có ở Phase 1** |
| **Auto-update Launcher** (tài liệu này) | App riêng: kiểm tra version, tải patch, rồi mới chạy game — **Phase 10** |

---

## Vấn đề cốt lõi chưa giải quyết

Phụ thuộc [ADR-024](../01-architecture/adr/README.md) (asset distribution) và [RESOURCE.md §10](../04-client/RESOURCE.md):

| Câu hỏi | Ảnh hưởng |
|---|---|
| Asset trong bản cài hay tải sau? | Có cần launcher tải asset không |
| Có CDN không? Ai trả tiền? | Chi phí |
| Patch từng file hay tải nguyên atlas? | Băng thông |
| **Desktop** cần launcher; **Android** dùng Google Play update? | Nền tảng khác nhau hoàn toàn |

⚠️ **Android/iOS thường KHÔNG cần launcher tự viết** — store lo update. Launcher chủ yếu cho **desktop** (nơi không có store update).

→ Nếu **mobile-first**, launcher tự viết có thể **không cần** ở MVP.

---

## Bố cục dự kiến (khi viết)

1. Vì sao cần (desktop, không qua store)
2. Version check (client version vs server yêu cầu — [NETWORK.md §6](../05-server/NETWORK.md))
3. Patch asset (dùng `asset-manifest.json` — [RESOURCE.md §9](../04-client/RESOURCE.md))
4. Delta patch vs full download
5. CDN
6. Rollback nếu patch lỗi
7. Self-update của chính launcher

## Câu hỏi trước khi viết

- [ ] ADR-024: chiến lược asset distribution?
- [ ] Desktop-first hay mobile-first? (mobile → có thể bỏ launcher)
- [ ] Có ngân sách CDN không?
- [ ] Phát hành qua store (Steam/itch.io) hay tự phân phối? (Steam lo update)

## Tài liệu liên quan

- [RESOURCE.md §9–10](../04-client/RESOURCE.md) — asset version, manifest, patch
- [NETWORK.md §6](../05-server/NETWORK.md) — version check
- [CLIENT.md §2](../04-client/CLIENT.md) — launcher ≠ launcher
- [ADR-024](../01-architecture/adr/README.md) — asset distribution

## Việc cần làm tiếp theo

1. **Không làm gì tới Phase 10.**
2. Chốt ADR-024 + desktop/mobile-first trước.
3. Nếu phát hành qua Steam/store → **có thể bỏ hẳn** launcher tự viết.
