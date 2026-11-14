package dev.kage.client.core.world;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tiện ích lô 55 thuần client, nhớ trên máy: nhớ kênh chat cuối (ADR-371), giờ máy trên HUD (ADR-372, {@code /giomay}
 * hoặc {@code /localtime}), đếm quái đã hạ trên HUD (ADR-374, {@code /demquai} hoặc {@code /killcount}).
 */
final class ChatHudExtrasCoordinator implements WindowFlow {

    private static final String CHANNEL_KEY = "chat.last_channel";
    private static final String CLOCK_KEY = "hud.local_clock";
    private static final String KILLS_KEY = "hud.kill_count";
    private static final int MINUTES_PER_HOUR = 60;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<Boolean> clockHud = visible -> {};
    private Consumer<Boolean> killsHud = visible -> {};
    private boolean clockOn;
    private boolean killsOn;

    ChatHudExtrasCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.clockOn =
                preferences.map(prefs -> prefs.getBoolean(CLOCK_KEY, false)).orElse(false);
        this.killsOn =
                preferences.map(prefs -> prefs.getBoolean(KILLS_KEY, false)).orElse(false);
        preferences
                .map(prefs -> prefs.getInteger(CHANNEL_KEY, ChatChannel.CHAT_CHANNEL_WORLD_VALUE))
                .map(ChatChannel::forNumber)
                .ifPresent(chat::restoreChannel);
        chat.onChannelChanged(channel -> remember(prefs -> prefs.putInteger(CHANNEL_KEY, channel.getNumber())));
        chat.addSlashCommands(this::onSlash);
    }

    /** Số phút trong ngày theo giờ máy (đọc mỗi khung; HUD chỉ đổi chữ khi sang phút). */
    int localMinuteOfDay() {
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        return now.getHour() * MINUTES_PER_HOUR + now.getMinute();
    }

    /** Nối nhãn giờ máy và nhãn đếm quái (áp ngay trạng thái đã nhớ). */
    void useHud(Consumer<Boolean> setClockVisible, Consumer<Boolean> setKillsVisible) {
        this.clockHud = setClockVisible;
        this.killsHud = setKillsVisible;
        setClockVisible.accept(clockOn);
        setKillsVisible.accept(killsOn);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if ("/giomay".equals(command) || "/localtime".equals(command)) {
            clockOn = !clockOn;
            remember(prefs -> prefs.putBoolean(CLOCK_KEY, clockOn));
            clockHud.accept(clockOn);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, clockOn ? "localclock.on" : "localclock.off"));
            return true;
        }
        if ("/demquai".equals(command) || "/killcount".equals(command)) {
            killsOn = !killsOn;
            remember(prefs -> prefs.putBoolean(KILLS_KEY, killsOn));
            killsHud.accept(killsOn);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, killsOn ? "killcount.on" : "killcount.off"));
            return true;
        }
        return false;
    }

    private void remember(Consumer<Preferences> write) {
        preferences.ifPresent(prefs -> {
            write.accept(prefs);
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
