package dev.kage.client.core.world;

import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.ui.hud.HudWindow;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;

/**
 * Thu phóng camera (ADR-323) — lăn chuột phóng to / thu nhỏ khung nhìn thế giới 3D trong [{@value #MIN_ZOOM},
 * {@value #MAX_ZOOM}], nhớ trong Preferences {@code camera.zoom}. Là một "cửa sổ" rỗng trong {@link PanelHub} chỉ để
 * nhận sự kiện lăn chuột (đăng ký SAU mọi cửa sổ khác ⇒ lăn trên một bảng có thanh cuộn thì bảng đó nhận trước).
 */
final class CameraZoomCoordinator implements HudWindow {

    /** Gần nhất / xa nhất (hệ số {@code OrthographicCamera.zoom}: nhỏ = gần). */
    static final float MIN_ZOOM = 0.7f;

    static final float MAX_ZOOM = 1.5f;

    private static final float STEP = 0.1f;
    private static final String PREF_KEY = "camera.zoom";

    private final Stage stage;
    private final Optional<Preferences> preferences;
    private java.util.function.Consumer<Float> camera = zoom -> {};
    private float zoom;

    CameraZoomCoordinator() {
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.zoom = clamp(preferences.map(prefs -> prefs.getFloat(PREF_KEY, 1f)).orElse(1f));
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch()) {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (super.scrolled(amountX, amountY)) {
                    return true;
                }
                applyStep(amountY);
                return true;
            }
        };
    }

    /** Nối camera thế giới (áp ngay mức đã nhớ). */
    void useCamera(java.util.function.Consumer<Float> setZoom) {
        this.camera = setZoom;
        setZoom.accept(zoom);
    }

    private void applyStep(float amountY) {
        zoom = next(zoom, amountY);
        camera.accept(zoom);
        preferences.ifPresent(prefs -> {
            prefs.putFloat(PREF_KEY, zoom);
            prefs.flush();
        });
    }

    /** Mức kế tiếp sau một nấc lăn (dương = lăn xuống = ra xa). Tĩnh để test. */
    static float next(float current, float amountY) {
        return clamp(current + Math.signum(amountY) * STEP);
    }

    private static float clamp(float value) {
        return Math.clamp(value, MIN_ZOOM, MAX_ZOOM);
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render(float deltaTime) {
        // Không vẽ gì — chỉ nhận lăn chuột.
    }

    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
