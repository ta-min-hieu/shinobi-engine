# tools/blender — Script chuẩn cho pipeline Blender

Tài liệu gốc: [docs/14-art/ART-PIPELINE.md](../../docs/14-art/ART-PIPELINE.md) (thiết kế) · [ART-QUALITY-STANDARD.md](../../docs/14-art/ART-QUALITY-STANDARD.md) (chuẩn ĐẠT/KHÔNG ĐẠT + vòng nghiệm thu) · [BLENDER-SESSION-GUIDE.md](../../docs/14-art/BLENDER-SESSION-GUIDE.md) (thao tác từng bước). Phiên làm art nạp 3 file đó + file này, không đọc thêm.

> ⚠️ **Từ [ADR-031](../../docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (2026-08-03) pipeline chia 2 nhánh.**
> **Nhân vật · quái · NPC** đi nhánh 3D: dựng bằng `kage_text3d_kit.py`, sản phẩm giao là **model glTF**,
> ảnh render chỉ là bằng chứng nghiệm thu. **VFX · icon · UI** vẫn là nhánh 2D pre-render ra sprite.
> Đọc đúng nhánh của thứ mình đang làm — phần lớn script dưới đây thuộc nhánh 2D.

## Script nào cho việc gì

| Script | Dùng khi | Cần mở Blender? |
|---|---|---|
| `kage_text3d_kit.py` | **Nhân vật / NPC / quái hình người — đường chính hiện nay** (nhánh 3D) | ✅ cho bước sinh+import mesh (tool MCP), ❌ từ bước chuẩn hoá trở đi |
| `export_character_lods.py` | **Bước E nhánh 3D** — decimate + xuất 3 tier `.glb` + sidecar `model-meta.json` | ❌ headless |
| `measure_triangle_budget.py` | Đo lại ngân sách tam giác khi cần (đã chạy 2026-08-05, kết quả ở ART-PIPELINE §4.4) | ❌ headless |
| `kage_sprite_render.py` | Render preview 1 frame hoặc cả sheet. Nhánh 2D: **là sản phẩm**. Nhánh 3D: **chỉ để nghiệm thu** | ❌ headless |
| `make_contact_sheet.py` | Ghép nhiều frame thành 1 ảnh để nghiệm thu | ❌ headless |
| `build_character_base.py` | Dựng lại `kage_character_base.blend` | ❌ headless |
| `build_skill_vfx.py` · `build_item_icon.py` · `build_monster_dummy.py` · `build_npc_elder.py` | VFX / icon / placeholder thế hệ cũ | ❌ headless |
| `kage_mesh_kit.py` | **Chỉ còn vũ khí · phụ kiện · rig · action** — phần dựng cơ thể đã bị thay | ❌ headless |
| `build_kage_character.py` | ⚠️ **Thế hệ mesh kit đã bị bác** (~1/5 NSO, 2026-08-03) — chỉ để dựng lại file cũ; bảng `CHARACTERS` trong đó vẫn là **spec trang phục** tham chiếu cho prompt Text→3D | ❌ headless |
| `build_box_character.py` | ⚠️ **Thế hệ box đã bị thay thế** — chỉ để dựng lại file cũ, không dùng cho asset mới | ❌ headless |
| MCP (`execute_blender_code`) | Model tay: prop, tile, boss dị hình | ✅ |

---

## `kage_text3d_kit.py` — nhân vật từ Text→3D (đường chính)

Nhận mesh do Hyper3D Rodin sinh → chuẩn hoá → rig → action → toon → viền. Trình tự lệnh đầy đủ kèm
4 cái bẫy của REST API: [BLENDER-SESSION-GUIDE §1.5](../../docs/14-art/BLENDER-SESSION-GUIDE.md).
Vòng nghiệm thu và ngân sách vòng: [ART-QUALITY-STANDARD §7](../../docs/14-art/ART-QUALITY-STANDARD.md).

| Hàm | Nhiệm vụ |
|---|---|
| `normalise(source_name, out_name)` | Cao 1.0, chân z=0, mặt +X — biến đổi ở cấp mesh data |
| `clean(obj)` | Hàn đỉnh; glTF tách đỉnh theo seam UV, không hàn thì rig gãy |
| `measure(obj)` | In số đo khớp thật của **mesh này** — không có hằng số chung |
| `rig(obj, name, arm_y, leg_y, hip, chest, shoulder, elbow, wrist, knee)` | Rig 11 xương, tự chuyển `weight_by_proximity()` khi bone-heat gãy |
| `author_scaled_actions(rig, gains=None)` | 7 action chuẩn, hiệu chỉnh biên độ theo `CHIBI_GAINS`, giữ nguyên nhịp bảng gốc |
| `toon_textured(obj, shades=3)` | Toon cho mesh **có texture sẵn** — dùng nấc ramp riêng, không dùng bảng của mesh thủ tục |
| `add_far_shade(obj)` · `add_inverted_hull_outline(obj, width_px_at_1x)` | Chiều sâu + viền. Viền gọi **sau** `fit_camera` |
| `attach_weapon(rig, name, kind, offset, outline_px_at_1x)` | Vũ khí là object rời nên phải tự thêm viền riêng. **Vũ khí không bao giờ nằm trong prompt Rodin** — xem ART-PIPELINE §2.3 |
| `decimate(obj, target_triangles)` | Hạ mesh về ngân sách tam giác. Modifier được đẩy lên **đầu** stack trước khi apply, nếu không nó nướng luôn rest pose + vỏ viền vào mesh |
| `export_gltf(path, obj, armature, extras, texture_px)` | Xuất 1 LOD ra `.glb`; tự bỏ viền/far-shade/vật liệu toon và trả lại nguyên trạng sau khi xuất |

⚠️ **`.blend` sinh ra không tái tạo được** — chạy lại đúng prompt cũ ra model khác. Save kèm
`bpy.ops.file.pack_all()` (texture đến từ job cloud) và **commit** bản đã PASS.

---

## `export_character_lods.py` — bước E

```bash
blender -b <char>.blend -P tools/blender/export_character_lods.py -- \
  --object Hoa_D --rig hoa_d_Rig --extras hoa_KunaiBlade,hoa_KunaiGrip,hoa_KunaiRing \
  --asset-key char_hoa-b1 --out <abs>/assets/exported/models
```

Ngân sách trong `TIERS` **đo thật**, không đoán: low 5k/512 · med 10k/1024 · high 20k/2048
(ART-PIPELINE §4.4). Xuất `.glb` một file tự chứa chứ không phải bộ `.gltf`+`.bin`+`.png`.

⚠️ **Luôn ghi rõ `--object` và `--rig`.** File nhân vật thật giữ nhiều thế hệ mesh, và
`find_armature()` lấy armature **đầu tiên trong scene** — ở file pilot Hỏa đó là rig của town-elder.

---

## `kage_mesh_kit.py` — vũ khí, phụ kiện, rig, action

Phần **dựng cơ thể** của kit (`build_chibi_body` · `add_face` · `add_hair` · `assemble_character`)
đã bị thay bởi Text→3D và **không dùng cho nhân vật mới**. Còn dùng nguyên vẹn: `build_rig` ·
`build_weapon` · `add_accessory` · `add_motion_detail` · `author_actions` · `shade_hex`.
API đầy đủ: [ART-QUALITY-STANDARD §5](../../docs/14-art/ART-QUALITY-STANDARD.md).

Ràng buộc dễ sai, đã có trong code kèm lý do — đọc comment trước khi sửa số:
`ARM_Y` phải lớn hơn nửa bề dày thân · dấu xoay xương tính từ `axis × bone_direction` · góc pose
cộng dồn theo chuỗi xương · không để hình học dưới `z=0` · độ dày outline **không phải hằng số**,
nguồn duy nhất là [ART-QUALITY-STANDARD §4](../../docs/14-art/ART-QUALITY-STANDARD.md) mục 3.

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
| `setup_scene(px=256, outline_px_at_1x=2.5, samples=32)` | EEVEE, `film_transparent`, resolution px×px, Freestyle, sun key ~35° trên-trước-phải + fill yếu, camera Ortho trên trục −Y nhìn +Y. ⚠️ Mặc định 2.5 là số cũ chưa kiểm chứng — **luôn** truyền giá trị riêng, xem §Ràng buộc. Mesh Text→3D **tắt Freestyle** và dùng `add_inverted_hull_outline()` thay thế |
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
- Nền trong suốt · **KHÔNG** downscale trong Blender (@1x/@0.5x là việc của `tools/asset-pipeline`).
- Model quay mặt **+X** trong không gian gốc. Sau ADR-031 đây **không còn là hướng nhìn trên màn** (góc camera là hằng số runtime, không nướng vào tài sản) — nhưng vẫn bắt buộc vì rig, bảng pose và `add_far_shade()` đều giả định trục này.
- Outline: **không có một con số cho mọi asset**, và cũng không phải một kỹ thuật — mesh thủ tục dùng Freestyle, mesh Text→3D dùng inverted hull. Nguồn duy nhất cho số đo là [ART-QUALITY-STANDARD §4](../../docs/14-art/ART-QUALITY-STANDARD.md) mục 3; **đừng chép số vào đây**. Lưu ý khi đọc log: giá trị in ra là px ở master (@2x), tức gấp đôi số `@1x` trong tài liệu.
- Engine EEVEE: **không hardcode tên**. Blender ≤4.1 và ≥5.0 dùng `BLENDER_EEVEE`, 4.2–4.5 dùng `BLENDER_EEVEE_NEXT` — script tự dò từ enum thật.
