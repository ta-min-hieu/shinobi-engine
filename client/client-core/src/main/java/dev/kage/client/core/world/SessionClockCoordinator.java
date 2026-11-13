package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đồng hồ phiên chơi trên HUD (ADR-332) — {@code /dongho} (hoặc {@code /clock}) bật/tắt nhãn đếm thời gian đã chơi kể
 * từ lúc vào world; nhớ trên máy. Giờ máy người chơi, thuần client.
 */
final class SessionClockCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.session_clock";
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final long startedAtMillis;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<Boolean> hud = visible -> {};
    private boolean enabled;

    SessionClockCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.startedAtMillis = clockMillis.getAsLong();
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, false)).orElse(false);
        chat.addSlashCommands(this::onSlash);
    }

    /** Số giây đã chơi kể từ lúc dựng màn world. */
    long elapsedSeconds() {
        return Math.max(0L, clockMillis.getAsLong() - startedAtMillis) / MILLIS_PER_SECOND;
    }

    boolean enabled() {
        return enabled;
    }

    /** Nối nhãn HUD (áp ngay trạng thái đã nhớ). */
    void useHud(Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(enabled);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/dongho".equals(command) && !"/clock".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        hud.accept(enabled);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "sessionclock.on" : "sessionclock.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
