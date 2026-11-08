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
 * Giờ trước tin chat (ADR-268) — {@code /giochat} (hoặc {@code /timestamps}) bật/tắt; {@code /giochat bat|tat} (hoặc
 * {@code on|off}) đặt hẳn. Hiện {@code [HH:mm]} theo giờ máy lúc tin tới; lựa chọn lưu Preferences trên máy.
 */
final class ChatTimestampCoordinator implements WindowFlow {

    private static final String PREF_KEY = "chat.timestamps";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;

    ChatTimestampCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, false)).orElse(false);
        chat.setTimestamps(enabled);
        chat.addSlashCommands(this::onSlash);
    }

    /** Trạng thái mới sau lệnh với đối số {@code argument} (rỗng = đảo). Rỗng = đối số lạ. Tĩnh để test. */
    static Optional<Boolean> next(boolean current, String argument) {
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "" -> Optional.of(!current);
            case "bat", "bật", "on" -> Optional.of(true);
            case "tat", "tắt", "off" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/giochat".equals(command) && !"/timestamps".equals(command)) {
            return false;
        }
        Optional<Boolean> wanted = next(enabled, parts.length > 1 ? parts[1].strip() : "");
        if (wanted.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chattime.usage"));
            return true;
        }
        enabled = wanted.get();
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.setTimestamps(enabled);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "chattime.on" : "chattime.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
