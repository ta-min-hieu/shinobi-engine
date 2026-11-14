package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * Vẽ một {@link Drawable} khác với độ đậm nhân thêm {@link #setOpacity} (ADR-381 — độ mờ nền khung chat). Chỉ nền mờ
 * đi, chữ bên trên giữ nguyên. Không cấp phát khi vẽ (lưu bốn số màu cũ vào trường).
 */
final class FadedDrawable extends BaseDrawable {

    private final Drawable inner;
    private float opacity = 1f;

    FadedDrawable(Drawable inner) {
        super(inner);
        this.inner = inner;
    }

    /** Độ đậm 0..1 (ngoài khoảng thì kẹp lại). */
    void setOpacity(float value) {
        this.opacity = Math.clamp(value, 0f, 1f);
    }

    float opacity() {
        return opacity;
    }

    @Override
    public void draw(Batch batch, float x, float y, float width, float height) {
        if (opacity <= 0f) {
            return;
        }
        Color color = batch.getColor();
        float r = color.r;
        float g = color.g;
        float b = color.b;
        float a = color.a;
        batch.setColor(r, g, b, a * opacity);
        inner.draw(batch, x, y, width, height);
        batch.setColor(r, g, b, a);
    }
}
