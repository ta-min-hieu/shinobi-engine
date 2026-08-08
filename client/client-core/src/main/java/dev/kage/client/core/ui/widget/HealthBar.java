package dev.kage.client.core.ui.widget;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Thanh máu thuần hiển thị — nhận dữ liệu qua {@link #setValues(int, int)}, KHÔNG tự đọc ECS/state
 * gameplay (UI.md §4, §10: widget chỉ nhận dữ liệu qua setter). Vẽ 2 lớp phẳng (nền + phần còn lại)
 * qua {@link KageSkin} thay vì {@code ProgressBar} có sẵn của Scene2D — không cần 3 trạng thái
 * knob/background/foreground phức tạp cho một thanh máu đơn giản.
 */
public final class HealthBar extends Actor {

    private final KageSkin skin;
    private int current;
    private int max = 1;

    public HealthBar(KageSkin skin) {
        this.skin = skin;
    }

    /**
     * Cập nhật giá trị hiển thị.
     *
     * @param current HP hiện tại
     * @param max HP tối đa (kẹp tối thiểu 1, tránh chia cho 0)
     */
    public void setValues(int current, int max) {
        this.current = current;
        this.max = Math.max(1, max);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();

        skin.healthEmpty.draw(batch, x, y, width, height);
        float ratio = MathUtils.clamp(current / (float) max, 0f, 1f);
        if (ratio > 0f) {
            skin.healthFill.draw(batch, x, y, width * ratio, height);
        }
    }
}
