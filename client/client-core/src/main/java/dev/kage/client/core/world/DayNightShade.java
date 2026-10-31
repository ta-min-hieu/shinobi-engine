package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

/**
 * Ngày đêm (ADR-123) — chỉ là HÌNH: một lớp phủ tối trong suốt trên cảnh (dưới HUD) và màu nền tối theo, mờ dần trong
 * vài giây khi đổi. Server nói đêm hay ngày (gói 327); client không tự tính gameplay gì từ đây.
 */
final class DayNightShade {

    /** Độ tối lớp phủ lúc đêm hẳn (0 = không phủ). */
    static final float NIGHT_DARKNESS = 0.38f;

    /** Tốc độ đổi độ tối mỗi giây — từ ngày sang đêm hẳn mất chừng 4 giây. */
    private static final float FADE_PER_SECOND = 0.1f;

    private static final float BASE_RED = 0.1f;
    private static final float BASE_GREEN = 0.1f;
    private static final float BASE_BLUE = 0.15f;
    private static final float NIGHT_TINT_RED = 0.02f;
    private static final float NIGHT_TINT_GREEN = 0.03f;
    private static final float NIGHT_TINT_BLUE = 0.1f;

    private final Matrix4 screenProjection = new Matrix4();
    private final Color overlay = new Color();
    private float target;
    private float current;

    void setNight(boolean night) {
        target = night ? NIGHT_DARKNESS : 0f;
    }

    /** Về ban ngày ngay, không mờ dần (rời màn hình). */
    void reset() {
        target = 0f;
        current = 0f;
    }

    float darkness() {
        return current;
    }

    void update(float deltaTime) {
        float step = FADE_PER_SECOND * deltaTime;
        if (current < target) {
            current = Math.min(target, current + step);
        } else if (current > target) {
            current = Math.max(target, current - step);
        }
    }

    /** Màu nền (glClearColor) theo độ tối hiện tại. */
    void applyClearColor() {
        float day = 1f - current;
        Gdx.gl.glClearColor(BASE_RED * day, BASE_GREEN * day, BASE_BLUE * day, 1f);
    }

    /** Phủ tối toàn màn hình — gọi SAU cảnh, TRƯỚC HUD. Không cấp phát (ma trận và màu dựng sẵn). */
    void render(ShapeRenderer shapeRenderer, int width, int height) {
        if (current <= 0f) {
            return;
        }
        screenProjection.setToOrtho2D(0f, 0f, width, height);
        overlay.set(NIGHT_TINT_RED, NIGHT_TINT_GREEN, NIGHT_TINT_BLUE, current);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(screenProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(overlay);
        shapeRenderer.rect(0f, 0f, width, height);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
