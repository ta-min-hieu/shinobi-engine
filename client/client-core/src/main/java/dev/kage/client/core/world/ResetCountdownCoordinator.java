package dev.kage.client.core.world;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Đếm ngược reset ngày trên HUD (ADR-356) — {@code /demreset} (hoặc {@code /resettimer}) bật/tắt nhãn đếm tới 00:00
 * giờ Việt Nam (mốc reset ngày của server, {@code DailyReset.ZONE}); nhớ trên máy. Bảng đủ các mốc vẫn là {@code
 * /lammoi} (ADR-200, số do server tính).
 */
final class ResetCountdownCoordinator implements WindowFlow {

    /** Múi giờ mốc reset ngày — trùng {@code DailyReset.ZONE} phía server. */
    static final ZoneId RESET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String PREF_KEY = "hud.reset_countdown";
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<Boolean> hud = visible -> {};
    private boolean enabled;
    private long nextResetMillis;

    ResetCountdownCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, false)).orElse(false);
        chat.addSlashCommands(this::onSlash);
    }

    /** Giây tới reset kế tiếp (tính lại mốc khi đã qua). Không cấp phát trừ lúc qua mốc. */
    long secondsToReset() {
        long now = clockMillis.getAsLong();
        if (now >= nextResetMillis) {
            nextResetMillis = nextResetMillis(now);
        }
        return (nextResetMillis - now) / MILLIS_PER_SECOND;
    }

    /** 00:00 giờ Việt Nam kế tiếp sau {@code nowMillis}. Tĩnh để test. */
    static long nextResetMillis(long nowMillis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), RESET_ZONE)
                .plusDays(1)
                .atStartOfDay(RESET_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    /** Nối nhãn HUD (áp ngay trạng thái đã nhớ). */
    void useHud(Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(enabled);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/demreset".equals(command) && !"/resettimer".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        hud.accept(enabled);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "resettimer.on" : "resettimer.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
