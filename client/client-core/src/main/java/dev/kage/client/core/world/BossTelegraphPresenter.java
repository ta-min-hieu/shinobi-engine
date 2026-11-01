package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.BossTelegraphEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Vòng báo trước dậm đất của boss (ADR-145) — nhận gói 413, vẽ trong KHÔNG GIAN THẾ GIỚI cho tới lúc nổ: hai cột đỏ
 * đứng ở hai mép vùng nổ (camera nhìn gần như ngang nên hình nằm phẳng dưới đất gần như vô hình — xem
 * {@code MonsterPresenter}) và một dải đỏ mỏng trên mặt đất giữa hai cột; càng gần lúc nổ càng đậm.
 *
 * <p>Chỉ hiển thị — server quyết ai trúng. Tối đa {@value #MAX_RINGS} vòng cùng lúc trong mảng cố định, không cấp phát
 * mỗi khung.
 */
final class BossTelegraphPresenter implements WindowFlow {

    private static final int MAX_RINGS = 8;
    private static final float PILLAR_WIDTH = 0.14f;
    private static final float PILLAR_HEIGHT = 1.6f;
    private static final float GROUND_DEPTH = 1.4f;
    private static final float GROUND_THICKNESS = 0.06f;
    private static final float MIN_ALPHA = 0.25f;
    private static final float MAX_ALPHA = 0.75f;
    /** Trần thời lượng tin từ gói — gói hỏng không được treo vòng đỏ cả phút. */
    private static final float MAX_SECONDS = 10f;

    private static final float MAX_RADIUS = 20f;

    private static final Color RING = Color.valueOf("E8442EFF");

    private final float[] x = new float[MAX_RINGS];
    private final float[] y = new float[MAX_RINGS];
    private final float[] radius = new float[MAX_RINGS];
    private final float[] total = new float[MAX_RINGS];
    private final float[] left = new float[MAX_RINGS];
    private final Color tint = new Color();

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_BOSS_TELEGRAPH_EVENT_VALUE) {
            return false;
        }
        BossTelegraphEvent event = BossTelegraphEvent.parseFrom(frame.payload());
        add(event.getX(), event.getY(), event.getRadius(), event.getSeconds());
        return true;
    }

    /** Thêm một vòng vào ô trống (hoặc ô sắp hết nhất). Số không hữu hạn/âm bị bỏ. */
    void add(float centerX, float centerY, float ringRadius, float seconds) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !(ringRadius > 0f) || !(seconds > 0f)) {
            return;
        }
        int slot = 0;
        for (int i = 1; i < MAX_RINGS; i++) {
            if (left[i] < left[slot]) {
                slot = i;
            }
        }
        x[slot] = centerX;
        y[slot] = centerY;
        radius[slot] = Math.min(ringRadius, MAX_RADIUS);
        total[slot] = Math.min(seconds, MAX_SECONDS);
        left[slot] = total[slot];
    }

    /** Số vòng còn đang hiện. */
    int activeRings() {
        int n = 0;
        for (int i = 0; i < MAX_RINGS; i++) {
            if (left[i] > 0f) {
                n++;
            }
        }
        return n;
    }

    /** Đếm ngược mọi vòng. */
    void update(float deltaTime) {
        for (int i = 0; i < MAX_RINGS; i++) {
            left[i] = Math.max(0f, left[i] - deltaTime);
        }
    }

    /** Đếm ngược rồi vẽ — {@code projection} là ma trận của nhánh render đang hoạt động (cùng không gian với model). */
    void render(ShapeRenderer shapeRenderer, Matrix4 projection, float deltaTime) {
        update(deltaTime);
        if (activeRings() == 0) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < MAX_RINGS; i++) {
            if (left[i] > 0f) {
                drawRing(shapeRenderer, i);
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void drawRing(ShapeRenderer shapeRenderer, int i) {
        float progress = 1f - (left[i] / total[i]);
        shapeRenderer.setColor(tint.set(RING).mul(1f, 1f, 1f, MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * progress));
        float halfDepth = GROUND_DEPTH / 2f;
        // box(góc x, góc y, đỉnh z, rộng x, sâu y, cao z) — cùng quy ước MonsterPresenter/DemoPlatforms.
        shapeRenderer.box(
                x[i] - radius[i], y[i] - halfDepth, GROUND_THICKNESS, 2f * radius[i], GROUND_DEPTH, GROUND_THICKNESS);
        shapeRenderer.box(x[i] - radius[i], y[i] - halfDepth, PILLAR_HEIGHT, PILLAR_WIDTH, GROUND_DEPTH, PILLAR_HEIGHT);
        shapeRenderer.box(
                x[i] + radius[i] - PILLAR_WIDTH,
                y[i] - halfDepth,
                PILLAR_HEIGHT,
                PILLAR_WIDTH,
                GROUND_DEPTH,
                PILLAR_HEIGHT);
    }

    @Override
    public void reset() {
        for (int i = 0; i < MAX_RINGS; i++) {
            left[i] = 0f;
        }
    }
}
