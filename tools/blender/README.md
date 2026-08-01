# tools/blender — Script chuẩn cho pipeline Blender MCP

Tài liệu gốc: [docs/14-art/ART-PIPELINE.md](../../docs/14-art/ART-PIPELINE.md) (thiết kế) · [BLENDER-SESSION-GUIDE.md](../../docs/14-art/BLENDER-SESSION-GUIDE.md) (thao tác từng bước). Phiên làm art nạp 2 file đó + file này, không đọc thêm.

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
| `setup_scene(px=256, outline_px_at_1x=2.5, samples=32)` | EEVEE, `film_transparent`, resolution px×px, Freestyle, sun key ~35° trên-trước-phải + fill yếu, camera Ortho trên trục −Y nhìn +Y |
| `make_toon_material(name, base_hex, shades=3)` | Diffuse → Shader-to-RGB → ColorRamp CONSTANT → Emission. Trả về material |
| `apply_toon(obj, base_hex, shades=3)` | Thay toàn bộ material của mesh bằng 1 toon material |
| `fit_camera(objects, margin=0.1, armature=None, actions=None, baseline=0.08)` | Quét **mọi frame của mọi action** để set `ortho_scale`; đặt camera sao cho chân (world z=0) luôn rơi đúng 1 hàng pixel cố định |
| `render_preview(path, action=None, frame=None, armature=None)` | Render đúng 1 frame PNG để duyệt style |
| `render_action(armature, action_name, n_frames, out_dir, loop=None)` | Sample action đều thành n frame → `<action>_00.png`... |
| `render_all_actions(armature, mapping=None, out_dir=".", objects=None)` | Fit camera rồi render cả bộ; `mapping=None` = tự lấy action chuẩn có trong file. Ghi kèm `render-meta.json` |
| `find_armature()` · `hex_to_linear(hex)` | Tiện ích |
| CLI `-- --all-actions --out <dir> [--px N]` | Chạy headless, xuất master @2x |

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
- Outline: art direction là **2.5px ở @1x** ([ART-PIPELINE §2.1](../../docs/14-art/ART-PIPELINE.md)), master @2x nên script set `line_thickness = 5.0`. Nếu thấy log in `outline=5.0px` là **đúng**, không phải lỗi.
- Engine EEVEE: **không hardcode tên**. Blender ≤4.1 và ≥5.0 dùng `BLENDER_EEVEE`, 4.2–4.5 dùng `BLENDER_EEVEE_NEXT` — script tự dò từ enum thật.
