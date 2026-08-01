# ASSET-LOG.md — Sổ asset đồ họa

Sổ tay **token-rẻ** cho phiên AI: phiên sau đọc bảng này thay vì mở lại `.blend` để "xem lại"
([ART-PIPELINE.md §6.1 quy tắc 6](../../docs/14-art/ART-PIPELINE.md)).

Nguồn chuẩn cho trang quản trị là bảng `art_asset` trong DB; `tools/asset-pipeline` đồng bộ
sổ này → DB nên hai bên không lệch ([ART-PIPELINE.md §8.3](../../docs/14-art/ART-PIPELINE.md)).

Xong asset nào **ghi ngay 1 dòng**. License: `ORIGINAL` (tự làm) · `CC0` · `CC_BY` (bắt buộc điền
cột Ghi chú phần attribution).

| asset_key | Danh mục | Hệ | .blend nguồn | Action đã render | Ngày | License | Ghi chú |
|---|---|---|---|---|---|---|---|
| — | — | — | `kage_character_base.blend` | — | 2026-08-01 | ORIGINAL | **File base dùng chung**, không phải asset. Camera/light/Freestyle/render đã chuẩn + 5 material `toon_<hệ>` + scale guide chibi (cao 1.0 unit, chân z=0, đầu 0.4 unit). Mọi nhân vật mới **copy từ file này**, không dựng lại |

## Quy ước bắt buộc

- **Cao đúng 1.0 unit**, chân đặt tại `z=0`, mặt quay **+X** (phải). Sai 1 trong 3 → cả bộ sheet vô dụng.
- Tên action đúng bộ chuẩn: `idle` `run` `attack` `skill_cast` `hit` `jump` `die`.
- Mọi action phải bật `use_fake_user` trước khi save, nếu không Blender xoá action 0-user.
- VFX đặt tên `vfx_<skill_code>` khớp `SkillRegistry.java`.
