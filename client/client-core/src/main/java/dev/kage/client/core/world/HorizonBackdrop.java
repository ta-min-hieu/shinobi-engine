package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Hậu cảnh parallax (lô 25, [ART-PIPELINE §2.8](docs/14-art/ART-PIPELINE.md)) — trời, hai lớp núi đá dựng đứng và biển
 * mây dưới chân sân tông, vẽ THỦ TỤC bằng {@link ShapeRenderer} trong không gian màn hình, không asset nào.
 *
 * <p>Chỉ là HÌNH: không chạm gameplay, không đọc ECS. Vẽ ngay sau {@code glClear} và TRƯỚC model, depth test tắt nên
 * không
 * ghi depth — model 3D vẽ sau luôn đè lên. Lớp phủ ngày đêm ({@link DayNightShade}) vẽ sau cùng nên tự tối luôn cả hậu
 * cảnh.
 *
 * <p>Đường chân trời không phải hằng số: chiếu hai mép dải nền đất ({@link DemoPlatforms}) qua ma trận camera đang dùng
 * nên núi luôn mọc đúng sau mép đất và biển mây luôn nằm dưới mặt trước của dải, dù camera theo nhân vật nhảy lên bệ.
 * Độ trôi mỗi lớp tính theo số pixel của MỘT mét trên màn hình, nên đổi zoom không làm lệch tỉ lệ parallax.
 *
 * <p>Không cấp phát mỗi khung (CLAUDE.md §6): biên dạng núi/mây tính một lần lúc dựng, ma trận/vector/màu dựng sẵn.
 */
final class HorizonBackdrop {

    /** Số mẫu trên một chu kỳ biên dạng — đủ mịn cho đỉnh nhọn ở 1080p, ít tam giác (~2 × 64 mỗi chu kỳ). */
    private static final int SAMPLES = 64;

    /** Nửa bề sâu dải nền đất và độ dày của nó — khớp {@code DemoPlatforms.PLATFORMS[0]} và {@code THICKNESS}. */
    private static final float GROUND_HALF_DEPTH = 0.75f;

    private static final float GROUND_THICKNESS = 0.3f;

    /** Hệ số trôi so với nền đất (1 = đi cùng đất). Lớp xa trôi chậm nhất — đó là toàn bộ cảm giác chiều sâu. */
    private static final float FAR_DRIFT = 0.04f;

    private static final float NEAR_DRIFT = 0.12f;
    private static final float CLOUD_DRIFT = 0.3f;

    /** Chu kỳ biên dạng tính bằng bề rộng màn hình — mỗi lớp một chu kỳ khác nhau để hai lớp không lặp cùng nhịp. */
    private static final float FAR_PERIOD = 1.7f;

    private static final float NEAR_PERIOD = 1.3f;
    private static final float CLOUD_PERIOD = 0.9f;

    /** Chiều cao tối đa của đỉnh, tính bằng phần chiều cao màn hình. */
    private static final float FAR_PEAK_HEIGHT = 0.30f;

    private static final float NEAR_PEAK_HEIGHT = 0.17f;
    private static final float CLOUD_BUMP_HEIGHT = 0.045f;

    /** Biển mây: dải gờ mây sáng bắt đầu dưới mép trước của đất bao nhiêu (phần chiều cao màn hình). */
    private static final float CLOUD_BAND_DEPTH = 0.11f;

    /** Sương ở chân núi — dày bao nhiêu phía trên đường chân trời, và đục cỡ nào. */
    private static final float MIST_HEIGHT = 0.09f;

    private static final float MIST_ALPHA = 0.45f;

    private static final int FAR_PEAKS = 7;
    private static final int NEAR_PEAKS = 9;
    private static final int CLOUD_BUMPS = 11;

    /** Mũ của sườn đỉnh: > 1 cho sườn lõm — dáng núi đá dựng đứng kiểu tranh thuỷ mặc, không phải đồi tròn. */
    private static final float SPIRE_SHARPNESS = 1.7f;

    /** Hằng số tựa ngẫu nhiên (tỉ lệ vàng và họ hàng) — rải đỉnh đều mà không lặp, không cần {@code Random}. */
    private static final float GOLDEN = 0.618_034f;

    private static final float PLASTIC = 0.754_877_7f;
    private static final float WIDTH_SPREAD = 0.43f;

    private static final Color SKY_TOP = Color.valueOf("2E4A6BFF");
    private static final Color SKY_HORIZON = Color.valueOf("E6D7B5FF");
    private static final Color FAR_PEAK = Color.valueOf("8FA2B6FF");
    private static final Color NEAR_PEAK = Color.valueOf("4E6A72FF");
    private static final Color MIST = new Color(0.93f, 0.92f, 0.88f, MIST_ALPHA);
    private static final Color MIST_CLEAR = new Color(0.93f, 0.92f, 0.88f, 0f);
    private static final Color CLOUD_SEA_BOTTOM = Color.valueOf("5F6F8AFF");
    private static final Color CLOUD_CREST = Color.valueOf("EEF2F6FF");
    /** Khe sâu giữa mép đất và ngọn mây: sáng ở ngọn mây, tối dần lên tới bóng đổ dưới mép đất. */
    private static final Color GAP_LOW = Color.valueOf("A3B3C6FF");

    private static final Color GAP_HIGH = Color.valueOf("46566EFF");

    private final Layer far =
            new Layer(spireProfile(FAR_PEAKS, 0f), FAR_PEAK, new Motion(FAR_PEAK_HEIGHT, FAR_PERIOD, FAR_DRIFT));
    private final Layer near =
            new Layer(spireProfile(NEAR_PEAKS, 0.5f), NEAR_PEAK, new Motion(NEAR_PEAK_HEIGHT, NEAR_PERIOD, NEAR_DRIFT));
    private final Layer clouds =
            new Layer(cloudProfile(), CLOUD_CREST, new Motion(CLOUD_BUMP_HEIGHT, CLOUD_PERIOD, CLOUD_DRIFT));
    private final Matrix4 screenProjection = new Matrix4();
    private final Vector3 probe = new Vector3();
    private int width;
    private int height;

    /**
     * Một lớp parallax: biên dạng tuần hoàn (độ cao chuẩn hoá [0, 1] của {@value #SAMPLES} mẫu trên một chu kỳ) + màu +
     * cao tối đa và chu kỳ (theo phần màn hình) + hệ số trôi. Class chứ không record: mảng biên dạng là của riêng lớp
     * này,
     * không lộ ra qua accessor.
     */
    private static final class Layer {
        private final float[] profile;
        private final Color color;
        private final Motion motion;

        Layer(float[] profile, Color color, Motion motion) {
            this.profile = profile;
            this.color = color;
            this.motion = motion;
        }
    }

    /** Cao tối đa và chu kỳ (phần màn hình) + hệ số trôi so với nền đất. */
    private record Motion(float amplitude, float period, float drift) {}

    /**
     * Vẽ cả hậu cảnh. Gọi ngay sau {@code glClear}, trước model.
     *
     * @param worldProjection ma trận camera của nhánh render đang hoạt động (khung trước cũng được — lệch một khung
     *     không
     *     thấy được)
     * @param cameraX toạ độ X thế giới mà camera đang bám theo
     */
    void render(ShapeRenderer shapeRenderer, Matrix4 worldProjection, float cameraX) {
        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();
        float horizon = screenY(worldProjection, cameraX, GROUND_HALF_DEPTH, 0f);
        float groundFront = screenY(worldProjection, cameraX, -GROUND_HALF_DEPTH, -GROUND_THICKNESS);
        float top = Math.max(horizon, groundFront);
        float bottom = Math.min(horizon, groundFront);
        float metre = Math.abs(screenX(worldProjection, cameraX + 1f) - screenX(worldProjection, cameraX));
        float scroll = cameraX * metre;

        screenProjection.setToOrtho2D(0f, 0f, width, height);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(screenProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.rect(0f, top, width, height - top, SKY_HORIZON, SKY_HORIZON, SKY_TOP, SKY_TOP);
        drawLayer(shapeRenderer, far, top, scroll);
        drawLayer(shapeRenderer, near, top, scroll);
        float mist = MIST_HEIGHT * height;
        shapeRenderer.rect(0f, top, width, mist, MIST, MIST, MIST_CLEAR, MIST_CLEAR);

        // Biển mây: thân mây (tối dần xuống đáy) liền màu với ngọn mây ở trên, khe sâu xen giữa ngọn mây và mép đất.
        float crestBase = bottom - CLOUD_BAND_DEPTH * height;
        shapeRenderer.rect(0f, crestBase, width, bottom - crestBase, GAP_LOW, GAP_LOW, GAP_HIGH, GAP_HIGH);
        shapeRenderer.rect(0f, 0f, width, crestBase, CLOUD_SEA_BOTTOM, CLOUD_SEA_BOTTOM, CLOUD_CREST, CLOUD_CREST);
        drawLayer(shapeRenderer, clouds, crestBase, scroll);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Tô một biên dạng tuần hoàn: mỗi đoạn giữa hai mẫu là một tứ giác từ {@code base} lên tới đường biên. Bước vẽ khớp
     * đúng lưới mẫu nên không cần nội suy.
     */
    private void drawLayer(ShapeRenderer shapeRenderer, Layer layer, float base, float scroll) {
        float[] profile = layer.profile;
        float amplitude = layer.motion.amplitude() * height;
        float offset = scroll * layer.motion.drift();
        float step = layer.motion.period() * width / SAMPLES;
        int first = (int) Math.floor(offset / step);
        float x = first * step - offset;
        float y = base + amplitude * profile[Math.floorMod(first, SAMPLES)];
        shapeRenderer.setColor(layer.color);
        for (int k = first + 1; x < width; k++) {
            float nextX = x + step;
            float nextY = base + amplitude * profile[Math.floorMod(k, SAMPLES)];
            shapeRenderer.triangle(x, base, nextX, base, nextX, nextY);
            shapeRenderer.triangle(x, base, nextX, nextY, x, y);
            x = nextX;
            y = nextY;
        }
    }

    private float screenY(Matrix4 projection, float x, float y, float z) {
        probe.set(x, y, z).prj(projection);
        return (probe.y + 1f) * 0.5f * height;
    }

    private float screenX(Matrix4 projection, float x) {
        probe.set(x, 0f, 0f).prj(projection);
        return (probe.x + 1f) * 0.5f * width;
    }

    /** Núi đá dựng đứng: mỗi mẫu là đỉnh cao nhất phủ tới nó (max, không cộng — đỉnh không gộp thành cao nguyên). */
    static float[] spireProfile(int peaks, float phase) {
        float[] profile = new float[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            float u = (float) i / SAMPLES;
            float highest = 0f;
            for (int p = 0; p < peaks; p++) {
                float centre = fraction(phase + p * GOLDEN);
                float halfWidth = (0.5f + WIDTH_SPREAD * fraction(p * PLASTIC + phase)) / peaks;
                float peak = 0.45f + 0.55f * fraction((p + 1) * PLASTIC * GOLDEN + phase);
                float reach = 1f - wrappedDistance(u, centre) / halfWidth;
                if (reach > 0f) {
                    highest = Math.max(highest, peak * (float) Math.pow(reach, SPIRE_SHARPNESS));
                }
            }
            profile[i] = highest;
        }
        return profile;
    }

    /** Gờ biển mây: các cung tròn nhô lên (max của nửa vòng tròn) — đường cong tròn, đối lập hẳn với núi nhọn. */
    static float[] cloudProfile() {
        float[] profile = new float[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            float u = (float) i / SAMPLES;
            float highest = 0.15f;
            for (int b = 0; b < CLOUD_BUMPS; b++) {
                float centre = fraction(b * GOLDEN);
                float radius = (0.6f + 0.4f * fraction(b * PLASTIC)) / CLOUD_BUMPS;
                float d = wrappedDistance(u, centre) / radius;
                if (d < 1f) {
                    highest = Math.max(highest, (float) Math.sqrt(1f - d * d));
                }
            }
            profile[i] = highest;
        }
        return profile;
    }

    private static float fraction(float value) {
        return value - (float) Math.floor(value);
    }

    /** Khoảng cách trên vòng tròn [0, 1) — biên dạng tuần hoàn nên đỉnh sát mép phải phủ sang mép trái. */
    private static float wrappedDistance(float a, float b) {
        float d = Math.abs(a - b);
        return Math.min(d, 1f - d);
    }
}
