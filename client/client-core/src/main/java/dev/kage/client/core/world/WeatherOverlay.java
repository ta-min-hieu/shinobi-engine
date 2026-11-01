package dev.kage.client.core.world;

import java.util.function.BooleanSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.protocol.WeatherKind;

/**
 * Thời tiết (ADR-144) — chỉ là HÌNH, vẽ ở không gian màn hình sau cảnh, trước lớp phủ đêm và HUD:
 * <ul>
 *   <li><b>Mưa linh khí</b>: vệt mưa xiên màu ngọc, rơi chéo;
 *   <li><b>Bão cát đỏ</b>: màn bụi đỏ phủ mỏng + vệt cát bay ngang nhanh.
 * </ul>
 * Đậm/nhạt dần trong {@value #FADE_SECONDS} giây khi bước vào/ra vùng. Hạt nằm trong mảng cố định (toạ độ chuẩn hoá
 * [0, 1] của màn hình) — không cấp phát mỗi khung.
 */
final class WeatherOverlay {

    private static final int PARTICLES = 140;
    private static final float FADE_SECONDS = 2f;

    /** Mưa: tốc độ rơi (phần màn hình/giây), độ xiên, dài vệt (phần chiều cao). */
    private static final float RAIN_FALL = 1.1f;

    private static final float RAIN_SLANT = 0.18f;
    private static final float RAIN_LENGTH = 0.035f;

    /** Cát: tốc độ bay ngang, độ chúc xuống, dài vệt (phần chiều rộng). */
    private static final float SAND_DRIFT = 1.6f;

    private static final float SAND_SINK = 0.08f;
    private static final float SAND_LENGTH = 0.05f;
    private static final float SAND_HAZE_ALPHA = 0.22f;

    private static final Color RAIN = Color.valueOf("8FD9C0FF");
    private static final Color SAND = Color.valueOf("D8734EFF");
    private static final Color HAZE = Color.valueOf("9E3B26FF");

    private final float[] px = new float[PARTICLES];
    private final float[] py = new float[PARTICLES];
    private final float[] speed = new float[PARTICLES];
    private final Matrix4 screenProjection = new Matrix4();
    private final Color tint = new Color();
    private BooleanSupplier enabled = () -> true;
    private WeatherKind shown = WeatherKind.WEATHER_KIND_UNSPECIFIED;
    private WeatherKind target = WeatherKind.WEATHER_KIND_UNSPECIFIED;
    private float strength;

    WeatherOverlay() {
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = MathUtils.random();
            py[i] = MathUtils.random();
            speed[i] = MathUtils.random(0.7f, 1.3f);
        }
    }

    /** ADR-143 — công tắc "thời tiết" trong cài đặt hiển thị. */
    void useSwitch(BooleanSupplier weatherOn) {
        this.enabled = weatherOn;
    }

    /** Thời tiết nơi mình đang đứng ({@code UNSPECIFIED} = trời quang). */
    void setTarget(WeatherKind kind) {
        this.target = kind;
    }

    WeatherKind shown() {
        return shown;
    }

    float strength() {
        return strength;
    }

    /** Đậm/nhạt dần; trời đổi loại thì nhạt hết loại cũ rồi mới đậm loại mới. */
    void update(float deltaTime) {
        float step = deltaTime / FADE_SECONDS;
        if (target == shown && target != WeatherKind.WEATHER_KIND_UNSPECIFIED) {
            strength = Math.min(1f, strength + step);
        } else {
            strength = Math.max(0f, strength - step);
            if (strength == 0f) {
                shown = target;
            }
        }
        advance(deltaTime);
    }

    private void advance(float deltaTime) {
        boolean sand = shown == WeatherKind.WEATHER_KIND_SANDSTORM;
        float dx = sand ? SAND_DRIFT : RAIN_SLANT * RAIN_FALL;
        float dy = sand ? SAND_SINK : RAIN_FALL;
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = wrap(px[i] + dx * speed[i] * deltaTime);
            py[i] = wrap(py[i] - dy * speed[i] * deltaTime);
        }
    }

    private static float wrap(float value) {
        return value - MathUtils.floor(value);
    }

    /** Vẽ — gọi SAU cảnh, TRƯỚC lớp phủ đêm. */
    void render(ShapeRenderer shapeRenderer, int width, int height) {
        if (strength <= 0f || shown == WeatherKind.WEATHER_KIND_UNSPECIFIED || !enabled.getAsBoolean()) {
            return;
        }
        screenProjection.setToOrtho2D(0f, 0f, width, height);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(screenProjection);
        boolean sand = shown == WeatherKind.WEATHER_KIND_SANDSTORM;
        if (sand) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(tint.set(HAZE).mul(1f, 1f, 1f, SAND_HAZE_ALPHA * strength));
            shapeRenderer.rect(0f, 0f, width, height);
            shapeRenderer.end();
        }
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(tint.set(sand ? SAND : RAIN).mul(1f, 1f, 1f, 0.55f * strength));
        for (int i = 0; i < PARTICLES; i++) {
            float x = px[i] * width;
            float y = py[i] * height;
            if (sand) {
                shapeRenderer.line(x, y, x - SAND_LENGTH * width * speed[i], y + SAND_SINK * SAND_LENGTH * height);
            } else {
                float length = RAIN_LENGTH * height * speed[i];
                shapeRenderer.line(x, y, x - RAIN_SLANT * length, y + length);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
