package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Khung chat lô 54 — số dòng hiện (ADR-363, {@code /dongchat 4-12}, hoặc {@code /chatlines}, nhớ trên máy) và tô màu
 * tin của bạn bè (ADR-364, theo danh sách bạn bè đang giữ).
 */
final class ChatLinesCoordinator implements WindowFlow {

    private static final String PREF_KEY = "chat.visible_lines";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;

    ChatLinesCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences
                .map(prefs -> prefs.getInteger(PREF_KEY, ChatPanel.DEFAULT_VISIBLE_LINES))
                .ifPresent(lines -> chat.panel().setVisibleLines(lines));
        chat.addSlashCommands(this::onSlash);
    }

    /** ADR-364 — nối nguồn "tên này là bạn bè". */
    void useFriends(Predicate<String> friendNamed) {
        chat.useFriendNames(friendNamed);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/dongchat".equals(command) && !"/chatlines".equals(command)) {
            return false;
        }
        int lines = parse(words.length > 1 ? words[1].strip() : "");
        if (lines < ChatPanel.MIN_VISIBLE_LINES || lines > ChatPanel.MAX_VISIBLE_LINES) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "chatlines.usage", ChatPanel.MIN_VISIBLE_LINES, ChatPanel.MAX_VISIBLE_LINES));
            return true;
        }
        chat.panel().setVisibleLines(lines);
        preferences.ifPresent(prefs -> {
            prefs.putInteger(PREF_KEY, lines);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "chatlines.set", lines));
        return true;
    }

    private static int parse(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1; // không phải số ⇒ sai cú pháp
        }
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
