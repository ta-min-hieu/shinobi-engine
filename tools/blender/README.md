# tools/blender — Script chuẩn cho pipeline Blender

Tài liệu gốc: [docs/14-art/ART-PIPELINE.md](../../docs/14-art/ART-PIPELINE.md) (thiết kế) · [ART-QUALITY-STANDARD.md](../../docs/14-art/ART-QUALITY-STANDARD.md) (chuẩn ĐẠT/KHÔNG ĐẠT + mesh kit) · [BLENDER-SESSION-GUIDE.md](../../docs/14-art/BLENDER-SESSION-GUIDE.md) (thao tác từng bước). Phiên làm art nạp 3 file đó + file này, không đọc thêm.

## Script nào cho việc gì

| Script | Dùng khi | Cần mở Blender? |
|---|---|---|
| `kage_mesh_kit.py` + `build_kage_character.py` | **Nhân vật / NPC / quái hình người** — cách làm chuẩn hiện nay | ❌ headless |
| `kage_sprite_render.py` | Render preview 1 frame hoặc cả sheet, cho mọi loại asset | ❌ headless |
| `make_contact_sheet.py` | Ghép nhiều frame thành 1 ảnh để nghiệm thu | ❌ headless |
| `build_character_base.py` | Dựng lại `kage_character_base.blend` | ❌ headless |
| `build_skill_vfx.py` · `build_item_icon.py` · `build_monster_dummy.py` · `build_npc_elder.py` | VFX / icon / placeholder thế hệ cũ | ❌ headless |
| `build_box_character.py` | ⚠️ **Thế hệ box đã bị thay thế** — chỉ để dựng lại file cũ, không dùng cho asset mới | ❌ headless |
| MCP (`execute_blender_code`) | Model tay: prop, tile, boss dị hình | ✅ |

---

## `kage_mesh_kit.py` — thư viện dựng nhân vật chibi

Mặt tròn xoay + mặt + tóc + phụ kiện + vũ khí, rig **11 xương** (thêm `forearm_l/r`, `shin_l/r` so với rig cũ nên khuỷu/gối gập được), 7 action chuẩn. API đầy đủ: [ART-QUALITY-STANDARD §5](../../docs/14-art/ART-QUALITY-STANDARD.md).

```bash
# dựng .blend từ spec (bảng CHARACTERS trong build_kage_character.py)
blender -b assets/raw/blender/kage_character_base.blend -P tools/blender/build_kage_character.py \
  -- --element hoa --tier b1 --out assets/raw/blender/characters/hoa-b1.blend
```

Ràng buộc dễ sai, đã có trong code kèm lý do — đọc comment trước khi sửa số:
`ARM_Y` phải lớn hơn nửa bề dày thân · outline nhân vật 1.1px@1x (không phải 2.5) · dấu xoay xương tính từ `axis × bone_direction` · góc pose cộng dồn theo chuỗi xương · không để hình học dưới `z=0`.

---

## `make_contact_sheet.py`

```bash
blender -b -P tools/blender/make_contact_sheet.py -- \
  --dir assets/raw/render-out/char_hoa-b1 \
  --frames idle_00 run_01 attack_00 attack_02 die_05 \
  --out assets/raw/render-out/_review/hoa-b1.png --scale 2
```

Ghép các frame trên nền **đục** (không phải nền trong suốt/caro) — nghiệm thu viền sprite trên nền caro là cách bỏ sót viền trắng quanh mép.

---

## `kage_sprite_render.py`

Trạng thái: **đã implement và chạy thật trên Blender 5.2.0 LTS** (render ra PNG + `render-meta.json`).

Cách dùng qua MCP (không paste script vào chat — quy tắc token ART-PIPELINE §6.1):

```python
exec(open(r'E:/study/libgdx/shinobi-engine/tools/blender/kage_sprite_render.py').read())
setup_scene(px=256)
```

Headless (render dài, chạy background bằng Bash tool):

```
blender -b assets/raw/blender/characters/ninja-fire.blend -P tools/blender/kage_sprite_render.py -- --all-actions --out assets/raw/render-out/char_ninja-fire
```

---

## API

| Hàm | Nhiệm vụ |
|---|---|
| `setup_scene(px=256, outline_px_at_1x=2.5, samples=32)` | EEVEE, `film_transparent`, resolution px×px, Freestyle, sun key ~35° trên-trước-phải + fill yếu, camera Ortho trên trục −Y nhìn +Y. ⚠️ Tham số mặc định 2.5 là số cũ chưa kiểm chứng — nhân vật **luôn** truyền giá trị riêng, xem §Ràng buộc |
| `make_toon_material(name, base_hex, shades=3)` | Diffuse → Shader-to-RGB → ColorRamp CONSTANT → Emission. Trả về material |
| `apply_toon(obj, base_hex, shades=3)` | Thay toàn bộ material của mesh bằng 1 toon material |
| `fit_camera(objects, margin=0.1, armature=None, actions=None, baseline=0.08)` | Quét **mọi frame của mọi action** để set `ortho_scale`; đặt camera sao cho chân (world z=0) luôn rơi đúng 1 hàng pixel cố định |
| `render_preview(path, action=None, frame=None, armature=None)` | Render đúng 1 frame PNG để duyệt style |
| `render_action(armature, action_name, n_frames, out_dir, loop=None)` | Sample action đều thành n frame → `<action>_00.png`... |
| `render_all_actions(armature, mapping=None, out_dir=".", objects=None)` | Fit camera rồi render cả bộ; `mapping=None` = tự lấy action chuẩn có trong file. Ghi kèm `render-meta.json` |
| `find_armature()` · `hex_to_linear(hex)` | Tiện ích |
| CLI `-- --all-actions --out <dir> [--px N]` | Chạy headless, xuất master @2x |
| CLI `-- --preview <action> --out <dir> [--preview-frame N]` | Render đúng 1 frame để duyệt style, **fit camera y như bản sheet** nên duyệt cái gì là ra cái đó |

`--outline-px` mặc định **kế thừa giá trị đã lưu trong .blend**, không ép về 2.5. Độ dày đúng phụ thuộc nhân vật to bao nhiêu trong khung, và script dựng đã chọn sẵn.

`ACTION_SPEC` trong script là nguồn sự thật cho bộ action chuẩn (ART-PIPELINE §4.2) — action **loop** (`idle`, `run`) bỏ frame cuối trùng frame đầu, action không loop lấy trọn cả 2 đầu.

## Sidecar `render-meta.json`

Sinh cùng lúc với sheet, là thứ `tools/asset-pipeline` đọc để upsert bảng `art_asset` (ART-PIPELINE §8.3):

```json
{ "master_px": 256, "master_scale": 2, "playback_fps": 12,
  "actions": {"idle": 4, "attack": 5},
  "pivot_x_px": 128.0, "pivot_y_px_from_bottom": 20.48,
  "ortho_scale": 1.463, "blend_file": "..." }
```

## Ràng buộc

- Pivot chân-giữa: `baseline=0.08` → chân luôn ở `0.08 × px` tính từ đáy khung, **mọi asset** dùng chung mốc này.
- Nền trong suốt · chỉ render hướng phải (+X) · **KHÔNG** downscale trong Blender (@1x/@0.5x là việc của `tools/asset-pipeline`).
- Outline: **không có một con số cho mọi asset** — độ dày đúng phụ thuộc nhân vật chiếm bao nhiêu phần khung. Nhân vật chibi (master 256px, cao ~166px trong khung) đo được **1.1px@1x**; xem [ART-QUALITY-STANDARD §4](../../docs/14-art/ART-QUALITY-STANDARD.md) là nguồn duy nhất. Log in `outline=2.2px` cho nhân vật là **đúng** (1.1 × master scale 2).
- Engine EEVEE: **không hardcode tên**. Blender ≤4.1 và ≥5.0 dùng `BLENDER_EEVEE`, 4.2–4.5 dùng `BLENDER_EEVEE_NEXT` — script tự dò từ enum thật.
