package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.protocol.GetTreasureHuntResponse;
import dev.kage.protocol.TreasureHuntResponse;

/**
 * Dấu chỗ chôn kho (ADR-147 D8) — một chữ X vàng kim ĐỨNG trên mặt đất ở toạ độ X của cuộc tìm đang mở. Camera nhìn
 * gần như ngang nên dấu nằm phẳng dưới đất gần như vô hình (cùng lý do với {@code BossTelegraphPresenter}): X dựng
 * đứng trên một cọc mảnh, ghép từ các khối vuông nhỏ dọc hai đường chéo — không xoay hình, không cấp phát mỗi khung.
 *
 * <p>Chỉ hiển thị: vị trí lấy từ phản hồi của server, server mới quyết đào được hay không.
 */
final class TreasureMarker {

    private static final int STEPS = 9;
    private static final float CROSS_SIZE = 0.9f;
    private static final float CROSS_BOTTOM = 0.55f;
    private static final float BLOCK = 0.13f;
    private static final float STAKE_WIDTH = 0.06f;
    private static final float DEPTH = 0.12f;
    private static final float MIN_ALPHA = 0.55f;
    private static final float PULSE_RANGE = 0.35f;
    private static final float PULSE_SPEED = 3f;

    private static final Color GOLD = Color.valueOf("F2C14EFF");

    private final Color tint = new Color();
    private boolean active;
    private float x;
    private float clock;

    /** Cập nhật theo phản hồi lệnh 342: còn cuộc tìm thì hiện ở {@code spot_x}, hết thì ẩn. */
    void apply(TreasureHuntResponse response) {
        apply(response.getHunting(), response.getSpotX());
    }

    /** Như trên, cho phản hồi hỏi chỉ đọc 344 (D9). */
    void apply(GetTreasureHuntResponse response) {
        apply(response.getHunting(), response.getSpotX());
    }

    private void apply(boolean hunting, float spotX) {
        active = hunting && Float.isFinite(spotX);
        if (active) {
            x = spotX;
        }
    }

    boolean isActive() {
        return active;
    }

    float x() {
        return x;
    }

    void hide() {
        active = false;
    }

    /** Vẽ — {@code projection} là ma trận của nhánh render đang hoạt động (cùng không gian với model). */
    void render(ShapeRenderer shapeRenderer, Matrix4 projection, float deltaTime) {
        if (!active) {
            return;
        }
        clock = (clock + deltaTime * PULSE_SPEED) % (2f * (float) Math.PI);
        float alpha = MIN_ALPHA + PULSE_RANGE * (0.5f + 0.5f * (float) Math.sin(clock));
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(tint.set(GOLD).mul(1f, 1f, 1f, alpha));
        float halfDepth = DEPTH / 2f;
        // Cọc: từ mặt đất lên tâm chữ X. box(góc x, góc y, đỉnh z, rộng x, sâu y, cao z) — quy ước MonsterPresenter.
        float center = CROSS_BOTTOM + CROSS_SIZE / 2f;
        shapeRenderer.box(x - STAKE_WIDTH / 2f, -halfDepth, center, STAKE_WIDTH, DEPTH, center);
        // Hai đường chéo, mỗi đường STEPS khối vuông.
        float left = x - CROSS_SIZE / 2f;
        float step = (CROSS_SIZE - BLOCK) / (STEPS - 1);
        for (int i = 0; i < STEPS; i++) {
            float bx = left + i * step;
            float rising = CROSS_BOTTOM + i * step + BLOCK;
            float falling = CROSS_BOTTOM + CROSS_SIZE - i * step;
            shapeRenderer.box(bx, -halfDepth, rising, BLOCK, DEPTH, BLOCK);
            shapeRenderer.box(bx, -halfDepth, falling, BLOCK, DEPTH, BLOCK);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
}
