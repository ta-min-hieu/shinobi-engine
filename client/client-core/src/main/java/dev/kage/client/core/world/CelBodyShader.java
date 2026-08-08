package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

/**
 * Lượt vẽ 2 của hợp đồng cel shader (RENDERING.md §12.2) — {@code baseColor} × {@code N·L} lượng tử
 * hoá 3 nấc (bậc thang, không nội suy mượt). Tương đương {@code toon_textured()} ở khâu art
 * ({@code tools/blender/kage_text3d_kit.py:348}) nhưng thay {@code ShaderNodeValToRGB} kiểu
 * CONSTANT bằng {@code if/else if} trong GLSL — cùng phép lượng tử, khác chỗ thi hành.
 *
 * <p><b>Không dùng lại {@code DefaultShader}/{@code PBRShaderProvider} của gdx-gltf</b> — cả hai là
 * shader nguyên khối build sẵn cho ánh sáng MƯỢT (Blinn-Phong/PBR thật), không có điểm chèn nào để
 * thay bằng ramp bậc thang mà không viết lại gần hết GLSL bên trong. Skin (trọng số xương) là phần
 * DUY NHẤT mượn nguyên quy ước glTF/libGDX (không phải phát minh riêng) — phần ánh sáng viết mới
 * hoàn toàn theo hợp đồng §12.2.
 *
 * <p>⚠️ <b>Không port {@code add_far_shade()}</b> — nó giả chiều sâu cho MỘT góc nhìn cố định, sai
 * khi nhân vật xoay được ở runtime (RENDERING.md §12.2). {@code N·L} lượng tử hoá ở đây đã lo phần
 * đó, không cần thêm lớp giả chiều sâu riêng.
 *
 * <p><b>Phát hiện lúc chạy thật</b> (không có trong mô tả hợp đồng ban đầu): không phải mọi mesh
 * primitive trong một file {@code .glb} nhân vật đều có {@code baseColorTexture} — xem
 * {@link CelShadingConstants#FLAT_SHADE_STEPS}. Shader này chọn bảng ramp VÀ nguồn màu (texture hay
 * {@code baseColorFactor} phẳng) theo material của TỪNG {@link Renderable} lúc {@link #render}, chứ
 * không giả định cả file dùng chung một kiểu vật liệu.
 *
 * <p>⚠️ <b>PHẢI nhân {@code u_worldTrans}</b> — bản trước (2026-08-08) bỏ phép nhân này vì tưởng
 * {@code Renderable.bones[i]} đã ở world space; SAI, xác nhận bằng cách đọc thẳng
 * {@code ModelInstance.getRenderable()} thật trong {@code gdx-1.14.2-sources.jar}:
 *
 * <pre>
 * if (nodePart.bones == null &amp;&amp; transform != null)
 *     out.worldTransform.set(transform).mul(node.globalTransform);
 * else if (transform != null)
 *     out.worldTransform.set(transform);       // &lt;-- nhánh mesh có skin (trường hợp của ta)
 * </pre>
 *
 * Với mesh có skin, {@code Renderable.worldTransform} = ĐÚNG {@code ModelInstance.transform} (không
 * nhân {@code node.globalTransform}) — vì {@code Node.calculateBoneTransforms()} tính
 * {@code bones[i] = jointGlobalTransform * invBindMatrix} và {@code Node.calculateWorldTransform()}
 * KHÔNG BAO GIỜ đọc {@code ModelInstance.transform} (trường đó tách hẳn khỏi cây Node, chỉ được gdx
 * đưa vào ở đúng chỗ {@code getRenderable()} này). Bỏ qua {@code u_worldTrans} nghĩa là bỏ luôn phép
 * xoay {@code CharacterModelRenderer.Y_UP_TO_Z_UP} VÀ vị trí thế giới của nhân vật — mesh vẽ ra bằng
 * toạ độ cục bộ Y-up thô, trục "cao" của mesh (Y cục bộ) nằm ngang trên mặt đất Z-up của thế giới —
 * đúng triệu chứng "nằm ngang" đã ghi ở RENDERING.md §11.1. Xác nhận lại bằng ảnh chụp
 * {@code CharacterModelDebugTool} thật, không phải suy đoán tĩnh — xem RENDERING.md §11.1 bản ghi
 * 2026-08-08 (sửa).
 */
final class CelBodyShader extends BaseShader {

    private static final String VERTEX_SHADER = """
            attribute vec3 a_position;
            attribute vec3 a_normal;
            attribute vec2 a_texCoord0;
            attribute vec2 a_boneWeight0;
            attribute vec2 a_boneWeight1;
            attribute vec2 a_boneWeight2;
            attribute vec2 a_boneWeight3;

            uniform mat4 u_projViewTrans;
            uniform mat4 u_worldTrans;
            uniform mat4 u_bones[11]; // PHẢI khớp CelShadingConstants.RIG_BONE_COUNT

            varying vec3 v_normal;
            varying vec2 v_texCoord;

            void main() {
                mat4 skin = mat4(0.0);
                skin += a_boneWeight0.y * u_bones[int(a_boneWeight0.x + 0.5)];
                skin += a_boneWeight1.y * u_bones[int(a_boneWeight1.x + 0.5)];
                skin += a_boneWeight2.y * u_bones[int(a_boneWeight2.x + 0.5)];
                skin += a_boneWeight3.y * u_bones[int(a_boneWeight3.x + 0.5)];

                // u_worldTrans là ModelInstance.transform (Y_UP_TO_Z_UP + vị trí thế giới) — PHẢI
                // nhân, xem javadoc class. w=0 cho normal để bỏ phần tịnh tiến, đúng idiom chuẩn.
                vec4 skinnedPosition = u_worldTrans * (skin * vec4(a_position, 1.0));
                vec3 skinnedNormal = (u_worldTrans * (skin * vec4(a_normal, 0.0))).xyz;

                gl_Position = u_projViewTrans * skinnedPosition;
                v_normal = normalize(skinnedNormal);
                v_texCoord = a_texCoord0;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif

            varying vec3 v_normal;
            varying vec2 v_texCoord;

            uniform sampler2D u_texture;
            uniform float u_hasTexture;
            uniform vec4 u_baseColorFactor;
            uniform vec3 u_lightDir;
            uniform vec3 u_shadeSteps;

            void main() {
                vec3 normal = normalize(v_normal);
                float ndotl = clamp(dot(normal, u_lightDir), 0.0, 1.0);
                float shade;
                if (ndotl < 0.33333) {
                    shade = u_shadeSteps.x;
                } else if (ndotl < 0.66667) {
                    shade = u_shadeSteps.y;
                } else {
                    shade = u_shadeSteps.z;
                }

                vec3 albedo;
                if (u_hasTexture > 0.5) {
                    albedo = texture2D(u_texture, v_texCoord).rgb;
                } else {
                    albedo = u_baseColorFactor.rgb;
                }
                gl_FragColor = vec4(albedo * shade, 1.0);
            }
            """;

    private final float[] boneMatrices = new float[CelShadingConstants.RIG_BONE_COUNT * 16];

    @Override
    public void init() {
        ShaderProgram program = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!program.isCompiled()) {
            throw new IllegalStateException("CelBodyShader không compile được: " + program.getLog());
        }
        init(program, null);
    }

    @Override
    public int compareTo(Shader other) {
        return 0; // Một shader duy nhất cho mọi renderable — không có thứ tự nào có ý nghĩa để so.
    }

    @Override
    public boolean canRender(Renderable renderable) {
        return renderable.bones != null && renderable.bones.length > 0;
    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        super.begin(camera, context);
        // KHÔNG cull — mọi material xuất từ pipeline art đánh dấu doubleSided=true (đo thật từ
        // .glb: "doubleSided": true trên cả 2 material mỗi file). Tôn trọng đúng cờ đó thay vì áp
        // GL_BACK mặc định — mesh phụ kiện/tóc mỏng lộ mặt trong khi xoay, cull sẽ khoét lỗ xuyên
        // qua ở đúng góc camera xoay tới.
        context.setCullFace(GL20.GL_NONE);
        context.setDepthTest(GL20.GL_LEQUAL);
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        // Hướng TỪ mặt tới đèn (ngược dấu hằng số, vốn lưu hướng đèn TỚI mặt — quy ước
        // DirectionalLight chuẩn của libGDX) để N·L dương khi bề mặt hướng về phía sáng.
        program.setUniformf(
                "u_lightDir",
                -CelShadingConstants.LIGHT_DIRECTION.x,
                -CelShadingConstants.LIGHT_DIRECTION.y,
                -CelShadingConstants.LIGHT_DIRECTION.z);
    }

    @Override
    public void render(Renderable renderable) {
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform);
        bindBones(renderable);
        bindMaterial(renderable);
        renderable.meshPart.render(program);
    }

    private void bindBones(Renderable renderable) {
        int boneCount = Math.min(renderable.bones.length, CelShadingConstants.RIG_BONE_COUNT);
        for (int i = 0; i < boneCount; i++) {
            System.arraycopy(renderable.bones[i].val, 0, boneMatrices, i * 16, 16);
        }
        // Tham số cuối là TỔNG SỐ FLOAT (11 ma trận × 16), không phải số ma trận — xác nhận bằng
        // chạy thật: truyền số ma trận (11) làm ShaderProgram chỉ nạp 11/176 float đầu tiên, phần
        // còn lại giữ nguyên 0 từ mảng CPU, sụp ma trận skin của hầu hết xương về ma trận không và
        // nhân vật biến mất hoàn toàn không lỗi (glUniformMatrix4fv không báo lỗi khi count sai).
        program.setUniformMatrix4fv("u_bones", boneMatrices, 0, boneMatrices.length);
    }

    /**
     * Chọn nguồn màu (texture hay {@code baseColorFactor} phẳng) VÀ bảng ramp đúng floor theo
     * material của renderable NÀY — xem javadoc class, phát hiện lúc chạy thật rằng một file
     * {@code .glb} trộn cả hai kiểu mesh.
     */
    private void bindMaterial(Renderable renderable) {
        Attribute rawTextureAttribute = renderable.material.get(PBRTextureAttribute.BaseColorTexture);
        boolean hasTexture = rawTextureAttribute instanceof TextureAttribute;
        float[] steps = hasTexture ? CelShadingConstants.TEXTURED_SHADE_STEPS : CelShadingConstants.FLAT_SHADE_STEPS;
        program.setUniformf("u_shadeSteps", steps[0], steps[1], steps[2]);
        program.setUniformf("u_hasTexture", hasTexture ? 1f : 0f);
        if (rawTextureAttribute instanceof TextureAttribute textureAttribute) {
            int unit = context.textureBinder.bind(textureAttribute.textureDescription);
            program.setUniformi("u_texture", unit);
        }

        Attribute rawColorAttribute = renderable.material.get(PBRColorAttribute.BaseColorFactor);
        Color baseColor =
                rawColorAttribute instanceof ColorAttribute colorAttribute ? colorAttribute.color : Color.WHITE;
        program.setUniformf("u_baseColorFactor", baseColor.r, baseColor.g, baseColor.b, baseColor.a);
    }
}
