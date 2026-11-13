package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chống chữ hoa la hét (ADR-348) — tin của người chơi khác viết HOA toàn bộ (ít nhất {@value #MIN_LETTERS} chữ cái)
 * được hạ về chữ thường khi hiện. {@code /chuhoa} (hoặc {@code /caps}) bật/tắt, mặc định bật, nhớ trên máy.
 */
final class CapsCalmCoordinator implements WindowFlow {

    /** Tin ngắn hơn số chữ cái này thì để nguyên (viết tắt, "OK"...). */
    static final int MIN_LETTERS = 5;

    private static final String PREF_KEY = "chat.calm_caps";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;

    CapsCalmCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.useEventRewriter(this::calm);
        chat.addSlashCommands(this::onSlash);
    }

    private ChatMessageEvent calm(ChatMessageEvent event) {
        if (!enabled || event.getSenderId() == 0L || !isShouting(event.getBody())) {
            return event;
        }
        return event.toBuilder()
                .setBody(event.getBody().toLowerCase(Locale.ROOT))
                .build();
    }

    /** Toàn bộ chữ cái là chữ hoa và có ít nhất {@value #MIN_LETTERS} chữ cái. Tĩnh để test. */
    static boolean isShouting(String body) {
        long letters = body.codePoints().filter(Character::isLetter).count();
        return letters >= MIN_LETTERS
                && body.codePoints().filter(Character::isLetter).noneMatch(Character::isLowerCase);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/chuhoa".equals(command) && !"/caps".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "caps.on" : "caps.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
