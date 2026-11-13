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
 * Báo bạn bè vào/rời game (ADR-311) — {@code /baobanbe} bật/tắt; bật thì ngoài chữ nổi có sẵn còn thêm một dòng chat.
 */
final class FriendPresenceCoordinator implements WindowFlow {

    private static final String PREF_KEY = "social.friend_presence";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;

    FriendPresenceCoordinator(ChatCoordinator chat) {
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

    /** Nối vào khung bạn bè: tắt thì không chữ nổi, không dòng chat; bật thì cả hai. */
    void useIn(FriendCoordinator friends) {
        friends.usePresenceNotice(this::enabled, chat::showSystemNotice);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/baobanbe".equals(command) && !"/friendnotice".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "friendnotice.on" : "friendnotice.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
