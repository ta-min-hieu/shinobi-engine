package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ping trên HUD (ADR-316) — {@code /pinghud} ẩn/hiện nhãn độ trễ có sẵn (ADR cũ: heartbeat); nhãn tô màu theo chất
 * lượng.
 */
final class PingHudCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.ping";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private java.util.function.Consumer<Boolean> hud = visible -> {};
    private boolean enabled;

    PingHudCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    /** Công tắc đang bật không. */
    boolean enabled() {
        return enabled;
    }

    /** Nối nhãn độ trễ của HUD (áp ngay trạng thái đã nhớ). */
    void useHud(java.util.function.Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(enabled);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/pinghud".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        hud.accept(enabled);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "pinghud.on" : "pinghud.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
