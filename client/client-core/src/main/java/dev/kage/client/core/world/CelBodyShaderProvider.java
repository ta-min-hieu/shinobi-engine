package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;

/**
 * Cấp {@link CelBodyShader} cho lượt vẽ thân — một shader dùng chung cho mọi renderable nhân vật;
 * khác biệt texture/flat-color được xử lý BÊN TRONG {@link CelBodyShader#render} theo material của
 * từng renderable, không cần nhiều biến thể provider (xem javadoc {@link CelBodyShader}).
 */
final class CelBodyShaderProvider extends BaseShaderProvider {

    @Override
    protected Shader createShader(Renderable renderable) {
        // KHÔNG tự gọi init() — BaseShaderProvider.getShader() làm việc đó đúng một lần (xem
        // CelOutlineShaderProvider cho lý do đầy đủ).
        return new CelBodyShader();
    }
}
