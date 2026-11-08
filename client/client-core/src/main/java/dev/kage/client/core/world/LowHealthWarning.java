package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;

/**
 * Cảnh báo máu thấp (ADR-243) — máu dưới {@value #THRESHOLD_PERCENT}% thì viền màn hình nháy đỏ và một dòng chat nhắc
 * uống đan (một lần mỗi lần tụt; hồi lên ≥ {@value #REARM_PERCENT}% mới nhắc lại). Thuần client, chỉ đọc máu HUD đang
 * có. Không cấp phát mỗi khung: màu và ma trận dựng sẵn.
 */
final class LowHealthWarning {

    static final int THRESHOLD_PERCENT = 30;
    static final int REARM_PERCENT = 40;
    private static final int HUNDRED = 100;
    private static final float PULSE_SPEED = 6f;
    private static final float MIN_ALPHA = 0.15f;
    private static final float ALPHA_RANGE = 0.35f;
    private static final float BORDER_FRACTION = 0.04f;
    private static final float HALF = 0.5f;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Color tint = new Color(1f, 0f, 0f, 0f);
    private final Matrix4 screenProjection = new Matrix4();
    private java.util.Optional<CombatHudController> health = java.util.Optional.empty();
    private boolean low;
    private boolean warned;
    private float clock;

    LowHealthWarning(ChatCoordinator chat) {
        this.chat = chat;
    }

    /** Nối nguồn máu của HUD. */
    void useHealth(CombatHudController combat) {
        this.health = java.util.Optional.of(combat);
    }

    /** Máu đang dưới ngưỡng (và còn sống). Tĩnh để test. */
    static boolean isLow(int hp, int maxHp) {
        return hp > 0 && maxHp > 0 && hp * HUNDRED < maxHp * THRESHOLD_PERCENT;
    }

    /** Mỗi khung — cập nhật trạng thái; vừa tụt dưới ngưỡng lần đầu thì nhắc một dòng. */
    void update(int hp, int maxHp, float delta) {
        low = isLow(hp, maxHp);
        clock += delta;
        if (low && !warned) {
            warned = true;
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "lowhealth.notice"));
        } else if (maxHp > 0 && hp * HUNDRED >= maxHp * REARM_PERCENT) {
            warned = false;
        }
    }

    /** Mỗi khung — cập nhật theo máu HUD rồi vẽ viền đỏ nháy nếu máu thấp. */
    void render(ShapeRenderer shapeRenderer, int width, int height) {
        if (health.isPresent()) { // không lambda: khung hình không cấp phát
            CombatHudController combat = health.get();
            update(combat.playerHp(), combat.playerMaxHp(), Gdx.graphics.getDeltaTime());
        }
        if (!low) {
            return;
        }
        float pulse = (float) Math.sin(clock * PULSE_SPEED) * HALF + HALF;
        tint.a = MIN_ALPHA + ALPHA_RANGE * pulse;
        float border = Math.min(width, height) * BORDER_FRACTION;
        screenProjection.setToOrtho2D(0f, 0f, width, height);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(screenProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(tint);
        shapeRenderer.rect(0f, 0f, width, border);
        shapeRenderer.rect(0f, height - border, width, border);
        shapeRenderer.rect(0f, border, border, height - 2 * border);
        shapeRenderer.rect(width - border, border, border, height - 2 * border);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
