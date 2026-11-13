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
 * Đếm người xung quanh (ADR-325) — số người chơi khác đang trong tầm nhìn hiện ở góc HUD; {@code /xungquanh} (hoặc
 * {@code /nearby}) bật/tắt, nhớ trong Preferences. Số đọc thẳng từ bản đồ người chơi đang thấy (không cấp phát).
 */
final class NearbyCountCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.nearby";

    private final ChatCoordinator chat;
    private final EmoteCoordinator.EmoteDisplay others;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private java.util.function.Consumer<Boolean> hud = visible -> {};
    private boolean enabled;

    NearbyCountCoordinator(ChatCoordinator chat, EmoteCoordinator.EmoteDisplay others) {
        this.chat = chat;
        this.others = others;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, false)).orElse(false);
        chat.addSlashCommands(this::onSlash);
    }

    /** Số người chơi khác đang thấy. */
    int count() {
        return others.visibleOthers();
    }

    /** Nối nhãn của HUD (áp ngay trạng thái đã nhớ). */
    void useHud(java.util.function.Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(enabled);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/xungquanh".equals(command) && !"/nearby".equals(command)) {
            return false;
        }
        enabled = !enabled;
        hud.accept(enabled);
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "nearby.on" : "nearby.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
