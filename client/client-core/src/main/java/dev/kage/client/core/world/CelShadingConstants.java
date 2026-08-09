package dev.kage.client.core.world;

import com.badlogic.gdx.math.Vector3;

/**
 * Hằng số cho hợp đồng cel shader + viền inverted-hull (docs/03-engine/RENDERING.md §12.2) — dùng
 * chung giữa {@link CelOutlineShader} và {@link CelBodyShader} (CODING-STANDARDS §18 — không magic
 * number).
 */
final class CelShadingConstants {

    /**
     * Số xương của rig, đo THẬT từ {@code assets/exported/models/med/*.glb} (không phải số chung
     * chung của gdx-gltf) — mọi file kiểm tra (Hoả/Lôi/Thuỷ...) đều có đúng 11, khớp
     * docs/14-art/ART-PIPELINE.md §4.2 "rig 11 xương". Dùng làm kích thước mảng {@code u_bones} —
     * sai số này làm nhân vật vỡ hình khi bind pose đổi.
     */
    static final int RIG_BONE_COUNT = 11;

    /**
     * Nấc cel ramp cho mesh CÓ base-color texture (đáy 0.80) — {@code TEXTURED_SHADE_STEPS} trong
     * {@code tools/blender/kage_text3d_kit.py:345}. Texture đã có sẵn bóng vẽ tay nên ramp chỉ cần
     * khắc thêm hình khối, không phải gánh toàn bộ việc tạo bóng — dùng nhầm bảng đáy 0.55 là tối
     * hai lần (RENDERING.md §12.2, lỗi đã gặp thật ở khâu art).
     */
    static final float[] TEXTURED_SHADE_STEPS = {0.80f, 0.90f, 1.0f};

    /**
     * Nấc cel ramp cho mesh KHÔNG có texture, chỉ có {@code baseColorFactor} phẳng (đáy 0.55) —
     * {@code SHADE_STEPS[3]} trong {@code tools/blender/kage_sprite_render.py:74}. Ramp ở đây LÀ
     * nguồn tạo bóng duy nhất (không có texture vẽ sẵn để khắc thêm lên) nên đáy phải sâu hơn.
     *
     * <p>⚠️ Phát hiện lúc chạy thật (không có trong mô tả hợp đồng ban đầu): mọi {@code .glb} nhân
     * vật đã kiểm tra đều có 4 mesh primitive, nhưng chỉ MỘT cái (thân người) có {@code TEXCOORD_0}
     * + {@code baseColorTexture} — 3 cái còn lại (phụ kiện, VD "Gourd" của hệ Thuỷ) chỉ có
     * {@code baseColorFactor} phẳng, không UV. {@link CelBodyShader} phải chọn bảng ramp ĐÚNG theo
     * từng primitive, không phải một bảng chung cho cả file — nếu không phụ kiện sẽ dùng nhầm nấc
     * đáy 0.80 của mesh có texture (không "tối hai lần" vì không có texture để tối, nhưng bóng nông
     * hơn ý đồ khâu art).
     */
    static final float[] FLAT_SHADE_STEPS = {0.55f, 0.80f, 1.0f};

    /**
     * Hướng sáng key light, hằng số runtime cố định — CÙNG giá trị {@code DirectionalLightEx} cũ
     * dùng lúc nghiệm thu art (docs/14-art/ART-QUALITY-STANDARD.md §7.1), chỉ đổi chỗ đọc: trước
     * đọc từ {@code Environment} của gdx-gltf PBR, giờ nạp thẳng vào shader qua uniform vì cel
     * shader không dùng hệ lighting của gdx-gltf (RENDERING.md §13 — không có nguồn sáng đặt được
     * trong cảnh, không đổ bóng).
     */
    static final Vector3 LIGHT_DIRECTION = new Vector3(-1f, -3f, -1f).nor();

    /**
     * Độ dày viền, world unit — đẩy dọc pháp tuyến ở {@link CelOutlineShader}.
     *
     * <p>Khâu art quy đổi độ dày viền theo px@1x qua {@code ortho_scale} của camera fit-theo-nhân-vật
     * ({@code add_inverted_hull_outline()}, {@code tools/blender/kage_text3d_kit.py:600}). Runtime
     * KHÔNG có phép quy đổi tương đương: {@code WorldModelRenderer.CAMERA_DISTANCE} là hằng số,
     * và kích thước ống nhìn ortho lấy theo world unit cố định từ {@code FitViewport}
     * (RENDERING.md §4.2), không theo khung hình riêng từng nhân vật như lúc render art — nên không
     * có "zoom" nào để quy đổi. Hằng số world-unit cố định là lựa chọn ĐÚNG cho hiện trạng; nếu sau
     * này có zoom thật, độ dày phải chia cho hệ số zoom để giữ bề rộng không đổi trên màn hình, cùng
     * công thức khâu art dùng. Giá trị hiệu chỉnh bằng mắt qua ảnh chụp render thật (xem
     * RENDERING.md §12.2 câu hỏi runtime #1) — không phải suy ra từ con số px@1x=1.2 của khâu art vì
     * hai camera không cùng cách lấy khung hình.
     */
    static final float OUTLINE_THICKNESS_WORLD_UNITS = 0.012f;

    private CelShadingConstants() {}
}
