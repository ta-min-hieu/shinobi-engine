package dev.kage.client.core.ui.widget;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Thanh máu thuần hiển thị — nhận dữ liệu qua {@link #setValues(int, int)}, KHÔNG tự đọc ECS/state
 * gameplay (UI.md §4, §10: widget chỉ nhận dữ liệu qua setter). Vẽ 2 lớp phẳng (nền + phần còn lại)
 * qua {@link KageSkin} thay vì {@code ProgressBar} có sẵn của Scene2D — không cần 3 trạng thái
 * knob/background/foreground phức tạp cho một thanh máu đơn giản.
 */
public final class HealthBar extends Actor {

    /** Loại thanh — quyết định màu phần còn lại, tra từ {@link KageSkin} lúc vẽ. */
    public enum Kind {
        HEALTH,
        CHI,
        /** Vạch kinh nghiệm (2026-08-09) — cùng hình dạng, chỉ khác màu và ý nghĩa số. */
        EXPERIENCE
    }

    private final KageSkin skin;
    private final Kind kind;
    private long current;
    private long max = 1L;

    public HealthBar(KageSkin skin) {
        this(skin, Kind.HEALTH);
    }

    /**
     * @param kind cho phép tái dùng widget này cho thanh Khí thay vì viết một lớp gần như y hệt.
     *     Truyền {@link Kind} chứ KHÔNG truyền thẳng {@code Drawable}: giữ {@code Drawable} ngoài
     *     vào field là lưu tham chiếu tới đối tượng có thể bị sửa từ bên ngoài (SpotBugs {@code EI2}).
     */
    public HealthBar(KageSkin skin, Kind kind) {
        this.skin = skin;
        this.kind = kind;
    }

    /**
     * Cập nhật giá trị hiển thị.
     *
     * @param current giá trị hiện tại (HP/Khí/EXP trong cấp)
     * @param max giá trị đầy (kẹp tối thiểu 1, tránh chia cho 0)
     */
    public void setValues(long current, long max) {
        this.current = current;
        this.max = Math.max(1L, max);
    }

    private Drawable fillFor(Kind barKind) {
        return switch (barKind) {
            case CHI -> skin.chiFill;
            case EXPERIENCE -> skin.expFill;
            case HEALTH -> skin.healthFill;
        };
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();

        skin.healthEmpty.draw(batch, x, y, width, height);
        float ratio = MathUtils.clamp((float) current / (float) max, 0f, 1f);
        if (ratio > 0f) {
            Drawable fill = fillFor(kind);
            fill.draw(batch, x, y, width * ratio, height);
        }
    }
}
