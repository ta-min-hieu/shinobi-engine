package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;

/**
 * Cấp {@link CelOutlineShader} cho lượt vẽ viền — một shader dùng chung cho mọi renderable nhân
 * vật, không có biến thể theo material (RENDERING.md §12.2 — viền luôn đen phẳng, không phân biệt
 * texture hay flat-color).
 *
 * <p>Đây là điểm chèn CHÍNH mà {@link CharacterModelRenderer} dùng để thay thế lượt vẽ mặc định của
 * {@code SceneManager}: một {@code ModelBatch} riêng dựng bằng provider này, xem javadoc
 * {@link CharacterModelRenderer} cho lý do bỏ {@code SceneManager.render()}.
 */
final class CelOutlineShaderProvider extends BaseShaderProvider {

    @Override
    protected Shader createShader(Renderable renderable) {
        // KHÔNG tự gọi init() ở đây — BaseShaderProvider.getShader() gọi init() đúng MỘT lần sau khi
        // createShader() trả về (tự gọi thêm ở đây là double-init, rò ShaderProgram thứ nhất).
        return new CelOutlineShader();
    }
}
