package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;

/**
 * Danh hiệu cảnh giới trên đầu (ADR-140, phần D3 treo của ADR-136) — tên cảnh giới ("Kim Đan", "Tử Phủ"…) vẽ phía
 * trên đầu chính mình và mọi người đang thấy. Cấp người khác tới từ {@code CharacterSpawnedEvent.level} /
 * {@code CharacterAppearanceChangedEvent.level}; cảnh giới suy bằng {@link CultivationRealm} — hiển thị thuần.
 *
 * <p>Vẽ ở KHÔNG GIAN MÀN HÌNH như {@link FloatingCombatText} (chữ sắc ở mọi zoom), chiếu điểm đầu nhân vật qua
 * {@link WorldToScreen}. Không cấp phát mỗi khung: batch, ma trận, vector, layout dựng sẵn; chuỗi lấy từ bundle i18n
 * (đã cache). KHÔNG sở hữu font — font thuộc HUD.
 *
 * <p>Thứ tự gọi mỗi khung: {@link #begin} → {@link #draw} (mỗi nhân vật) → {@link #end}.
 */
final class RealmTitles implements Disposable {

    /** Cao hơn đỉnh đầu một chút — dưới mốc neo chữ damage ({@code FloatingCombatText}, 1,9) để hai thứ không đè. */
    private static final float HEAD_HEIGHT_UNITS = 1.55f;

    /** Bóng chữ lệch bao nhiêu pixel — cùng cách {@link FloatingCombatText} làm chữ đọc được trên mọi nền. */
    private static final float SHADOW_OFFSET_PX = 1.5f;

    private static final float SCALE = 0.75f;

    /** Vàng nhạt — màu "tu vi" chung, không trùng màu phe nào (ART-PIPELINE §2.2). */
    private static final Color TITLE = Color.valueOf("F2DFA0FF");

    private static final Color SHADOW = new Color(0f, 0f, 0f, 0.8f);

    /** ADR-142 — nhãn Ma Đầu: đỏ son, một dòng phía trên danh hiệu. */
    private static final Color OUTLAW = Color.valueOf("FF5A4AFF");

    /** Khoảng cách dòng Ma Đầu phía trên danh hiệu, theo pixel màn hình. */
    private static final float OUTLAW_LINE_OFFSET_PX = 16f;

    private final BitmapFont font;
    private final I18NBundle i18n = KageI18n.load();
    private final SpriteBatch batch = new SpriteBatch();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 screenProjection = new Matrix4();
    private final Vector3 scratch = new Vector3();
    private WorldToScreen projector;
    private float previousScaleX;
    private float previousScaleY;

    RealmTitles(BitmapFont font) {
        this.font = font;
    }

    void begin(WorldToScreen activeProjector) {
        this.projector = activeProjector;
        screenProjection.setToOrtho2D(0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        batch.setProjectionMatrix(screenProjection);
        previousScaleX = font.getData().scaleX;
        previousScaleY = font.getData().scaleY;
        font.getData().setScale(previousScaleX * SCALE, previousScaleY * SCALE);
        batch.begin();
    }

    /** Một danh hiệu tại chân nhân vật ({@code x}, {@code y}); cấp ≤ 0 (chưa biết) thì bỏ qua. */
    void draw(float x, float y, int level) {
        draw(x, y, level, false);
    }

    /** Như {@link #draw(float, float, int)}, thêm dòng "Ma Đầu" đỏ phía trên nếu {@code outlaw} (ADR-142). */
    void draw(float x, float y, int level, boolean outlaw) {
        if (level <= 0) {
            return;
        }
        scratch.set(x, y, HEAD_HEIGHT_UNITS);
        projector.project(scratch);
        line(i18n.get(CultivationRealm.of(level).nameKey()), scratch.y, TITLE);
        if (outlaw) {
            line(i18n.get("realm.outlaw"), scratch.y + OUTLAW_LINE_OFFSET_PX, OUTLAW);
        }
    }

    /** Một dòng chữ có bóng, căn giữa quanh {@code scratch.x}. */
    private void line(String text, float baselineY, Color color) {
        layout.setText(font, text);
        float left = scratch.x - (layout.width / 2f);
        font.setColor(SHADOW);
        font.draw(batch, text, left + SHADOW_OFFSET_PX, baselineY - SHADOW_OFFSET_PX);
        font.setColor(color);
        font.draw(batch, text, left, baselineY);
    }

    void end() {
        batch.end();
        font.getData().setScale(previousScaleX, previousScaleY);
        font.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        batch.dispose(); // KHÔNG dispose font — thuộc về HUD.
    }
}
