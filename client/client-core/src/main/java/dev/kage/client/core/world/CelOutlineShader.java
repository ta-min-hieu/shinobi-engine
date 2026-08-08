package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Lượt vẽ 1 của hợp đồng cel shader (RENDERING.md §12.2) — vỏ lật normal: đẩy đỉnh ra ngoài dọc
 * pháp tuyến rồi cull MẶT TRƯỚC, chỉ mặt sau (giờ hướng ra ngoài do vỏ lớn hơn thân) mới hiện, tạo
 * đường viền quanh silhouette. Tương đương {@code add_inverted_hull_outline()} ở khâu art
 * ({@code tools/blender/kage_text3d_kit.py:581}) nhưng chạy trong GLSL thay vì Solidify modifier
 * của Blender — cùng kỹ thuật, khác chỗ thi hành.
 *
 * <p>Tô đen phẳng, không tính ánh sáng — viền chỉ cần đường bao, không cần khối
 * (RENDERING.md §12.2, bảng "Hai lượt vẽ").
 *
 * <p>GLSL viết theo phong cách ES 1.00 (không {@code #version}, {@code attribute}/{@code varying},
 * {@code gl_FragColor}) — chạy được cả desktop GL lẫn OpenGL ES (Android), giống mọi shader mặc
 * định của libGDX (RENDERING.md §12.2 "Shader phải viết cho cả OpenGL ES"). ⚠️ Chưa test trên
 * Android thật trong lát cắt này — môi trường triển khai không có thiết bị/emulator Android
 * (RENDERING.md §12.2 "Việc cần làm tiếp theo").
 *
 * <p>Skin thủ công thay vì dùng lại {@code DefaultShader}/PBR của gdx-gltf vì cả hai đều là shader
 * PBR nguyên khối không có chỗ chèn ramp lượng tử hoá — xem {@link CelBodyShader} cho lý do đầy đủ.
 * Công thức skin (nhân {@code u_bones[joint]} theo trọng số rồi cộng dồn) là idiom chuẩn của
 * libGDX/glTF, không phải phát minh riêng.
 *
 * <p>⚠️ <b>PHẢI nhân {@code u_worldTrans}</b> — xem javadoc {@link CelBodyShader} cho lý do đầy đủ
 * (đọc thẳng {@code ModelInstance.getRenderable()} thật trong {@code gdx-1.14.2-sources.jar}): với
 * {@code NodePart} có skin, {@code Renderable.worldTransform} = {@code ModelInstance.transform}
 * (Y_UP_TO_Z_UP + vị trí), KHÔNG bao giờ nằm trong {@code bones[i]}. Bản trước (2026-08-08) bỏ phép
 * nhân này dựa trên tiền đề sai ("bones đã ở world space") — đúng nguyên nhân gốc của bug "nằm ngang"
 * ghi ở RENDERING.md §11.1.
 */
final class CelOutlineShader extends BaseShader {

    private static final String VERTEX_SHADER = """
            attribute vec3 a_position;
            attribute vec3 a_normal;
            attribute vec2 a_boneWeight0;
            attribute vec2 a_boneWeight1;
            attribute vec2 a_boneWeight2;
            attribute vec2 a_boneWeight3;

            uniform mat4 u_projViewTrans;
            uniform mat4 u_worldTrans;
            uniform mat4 u_bones[11]; // PHẢI khớp CelShadingConstants.RIG_BONE_COUNT — GLSL không đọc hằng số Java
            uniform float u_outlineThickness;

            void main() {
                // +0.5 trước khi truncate: chỉ số xương đi qua vertex buffer dạng float, sai số làm
                // tròn có thể hụt xuống chỉ số liền trước (VD 3.0 lưu thành 2.999999) — cùng cạm bẫy
                // DefaultShader gốc của libGDX tự phòng.
                mat4 skin = mat4(0.0);
                skin += a_boneWeight0.y * u_bones[int(a_boneWeight0.x + 0.5)];
                skin += a_boneWeight1.y * u_bones[int(a_boneWeight1.x + 0.5)];
                skin += a_boneWeight2.y * u_bones[int(a_boneWeight2.x + 0.5)];
                skin += a_boneWeight3.y * u_bones[int(a_boneWeight3.x + 0.5)];

                // u_worldTrans PHẢI nhân — xem javadoc class. Đẩy viền dọc pháp tuyến SAU khi đã ở
                // world space (đúng đơn vị world unit của u_outlineThickness).
                vec4 skinnedPosition = u_worldTrans * (skin * vec4(a_position, 1.0));
                vec3 skinnedNormal = normalize((u_worldTrans * (skin * vec4(a_normal, 0.0))).xyz);
                vec3 expanded = skinnedPosition.xyz + skinnedNormal * u_outlineThickness;
                gl_Position = u_projViewTrans * vec4(expanded, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif

            void main() {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            }
            """;

    private final float[] boneMatrices = new float[CelShadingConstants.RIG_BONE_COUNT * 16];

    @Override
    public void init() {
        ShaderProgram program = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!program.isCompiled()) {
            throw new IllegalStateException("CelOutlineShader không compile được: " + program.getLog());
        }
        init(program, null);
    }

    @Override
    public int compareTo(Shader other) {
        return 0; // Một shader duy nhất cho mọi renderable — không có thứ tự nào có ý nghĩa để so.
    }

    @Override
    public boolean canRender(Renderable renderable) {
        // Mọi mesh nhân vật đều skin đủ 4 khe trọng số (đo thật — xem CelShadingConstants), kể cả
        // phần phụ kiện không texture. Không có nhánh "không skin" ở nhánh render nhân vật.
        return renderable.bones != null && renderable.bones.length > 0;
    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        super.begin(camera, context);
        // Cull MẶT TRƯỚC (không phải mặt sau như bình thường) — đúng kỹ thuật vỏ lật normal ở
        // javadoc class: vỏ đã đẩy ra ngoài, chỉ mặt xa camera của nó mới cần hiện.
        context.setCullFace(GL20.GL_FRONT);
        context.setDepthTest(GL20.GL_LEQUAL);
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformf("u_outlineThickness", CelShadingConstants.OUTLINE_THICKNESS_WORLD_UNITS);
    }

    @Override
    public void render(Renderable renderable) {
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform);
        int boneCount = Math.min(renderable.bones.length, CelShadingConstants.RIG_BONE_COUNT);
        for (int i = 0; i < boneCount; i++) {
            System.arraycopy(renderable.bones[i].val, 0, boneMatrices, i * 16, 16);
        }
        // Tham số cuối là TỔNG SỐ FLOAT (11 ma trận × 16), không phải số ma trận — xác nhận bằng
        // chạy thật, xem bindBones() của CelBodyShader cho lý do đầy đủ (cùng lỗi từng gặp ở đây).
        program.setUniformMatrix4fv("u_bones", boneMatrices, 0, boneMatrices.length);
        renderable.meshPart.render(program);
    }
}
