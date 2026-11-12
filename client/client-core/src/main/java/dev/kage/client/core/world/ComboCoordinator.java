package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/** Đếm combo (ADR-293) — {@code /combo} bật/tắt chữ "Liên kích ×N" trên đầu nhân vật; nhớ trong Preferences. */
final class ComboCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.combo";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;

    ComboCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    /** Công tắc cho lớp vẽ chữ bay ({@link CombatFeedbackPresenter#useCombo}). */
    boolean enabled() {
        return enabled;
    }

    private boolean onSlash(String line) {
        if (!"/combo".equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "combo.on" : "combo.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
