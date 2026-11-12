package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/** Bộ đếm FPS (ADR-283) — {@code /fps} bật/tắt số khung hình/giây ở góc HUD; nhớ lựa chọn trong Preferences. */
final class FpsCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.fps";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<Boolean> hud = visible -> {};
    private boolean visible;

    FpsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.visible =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, false)).orElse(false);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nhãn FPS của HUD (áp ngay trạng thái đã nhớ). */
    void useHud(Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(visible);
    }

    private boolean onSlash(String line) {
        if (!"/fps".equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        visible = !visible;
        hud.accept(visible);
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, visible);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, visible ? "fps.on" : "fps.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
