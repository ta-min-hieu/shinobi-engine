package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bố cục khung chat (lô 45): ghim tin {@code /ghimchat [bo]} (ADR-291), thu gọn khi rảnh {@code /chatan <giây>|tat}
 * (ADR-292), cỡ chữ {@code /cochu nho|vua|lon} (ADR-294). Hai cài đặt sau nhớ trong Preferences; dòng ghim chỉ sống
 * trong phiên.
 */
final class ChatLayoutCoordinator implements WindowFlow {

    /** Giây rảnh ngắn / dài nhất cho {@code /chatan}. */
    static final int MIN_IDLE_SECONDS = 3;

    static final int MAX_IDLE_SECONDS = 300;

    /** Mặc định cũ của khung chat (ChatPanel.IDLE_HIDE_SECONDS). */
    static final int DEFAULT_IDLE_SECONDS = 12;

    private static final String PREF_IDLE = "chat.idle_seconds";
    private static final String PREF_FONT = "chat.font_size";

    /** Ba cỡ chữ. */
    enum FontSize {
        SMALL(0.85f, "nho", "small"),
        MEDIUM(1f, "vua", "medium"),
        LARGE(1.2f, "lon", "large");

        private final float scale;
        private final String vietnamese;
        private final String english;

        FontSize(float scale, String vietnamese, String english) {
            this.scale = scale;
            this.vietnamese = vietnamese;
            this.english = english;
        }

        float scale() {
            return scale;
        }

        /** Từ người chơi gõ (không dấu) ⇒ cỡ chữ. */
        static Optional<FontSize> parse(String word) {
            String folded = MentionWatch.fold(word);
            for (FontSize size : values()) {
                if (size.vietnamese.equals(folded) || size.english.equals(folded)) {
                    return Optional.of(size);
                }
            }
            return Optional.empty();
        }
    }

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;

    ChatLayoutCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs -> {
            chat.panel().setIdleHideSeconds(prefs.getInteger(PREF_IDLE, DEFAULT_IDLE_SECONDS));
            FontSize.parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_FONT, "vua"), "vua"))
                    .ifPresent(size -> chat.panel().setFontScale(size.scale()));
        });
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String arg = words.length > 1 ? words[1].strip() : "";
        return switch (words[0].toLowerCase(Locale.ROOT)) {
            case "/ghimchat", "/pinchat" -> pin(arg);
            case "/chatan", "/chatfade" -> idle(arg);
            case "/cochu", "/fontsize" -> font(arg);
            default -> false;
        };
    }

    private boolean pin(String arg) {
        if (isOff(arg)) {
            chat.panel().setPinnedLine("");
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatpin.off"));
            return true;
        }
        Optional<String> latest = chat.latestPlayerLine();
        latest.ifPresent(chat.panel()::setPinnedLine);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, latest.isPresent() ? "chatpin.on" : "chatpin.none"));
        return true;
    }

    private boolean idle(String arg) {
        OptionalInt seconds = isOff(arg) ? OptionalInt.of(0) : parseIdleSeconds(arg);
        if (seconds.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "chatfade.usage", MIN_IDLE_SECONDS, MAX_IDLE_SECONDS));
            return true;
        }
        chat.panel().setIdleHideSeconds(seconds.getAsInt());
        save(prefs -> prefs.putInteger(PREF_IDLE, seconds.getAsInt()));
        chat.showSystemNotice(
                seconds.getAsInt() == 0
                        ? KageI18n.textOrKey(i18n, "chatfade.off")
                        : KageI18n.formatOrKey(i18n, "chatfade.on", seconds.getAsInt()));
        return true;
    }

    private boolean font(String arg) {
        Optional<FontSize> size = FontSize.parse(arg);
        if (size.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatfont.usage"));
            return true;
        }
        chat.panel().setFontScale(size.get().scale());
        save(prefs -> prefs.putString(PREF_FONT, size.get().vietnamese));
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatfont." + size.get().english));
        return true;
    }

    private void save(java.util.function.Consumer<Preferences> change) {
        preferences.ifPresent(prefs -> {
            change.accept(prefs);
            prefs.flush();
        });
    }

    /** "tat"/"bo"/"off"/"0". Tĩnh để test. */
    static boolean isOff(String arg) {
        String folded = MentionWatch.fold(arg);
        return "tat".equals(folded) || "bo".equals(folded) || "off".equals(folded) || "0".equals(folded);
    }

    /** Số giây hợp lệ cho {@code /chatan} (trong khoảng cho phép). Tĩnh để test. */
    static OptionalInt parseIdleSeconds(String arg) {
        if (arg.isEmpty() || arg.length() > 4 || !arg.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        int seconds = Integer.parseInt(arg);
        return seconds < MIN_IDLE_SECONDS || seconds > MAX_IDLE_SECONDS ? OptionalInt.empty() : OptionalInt.of(seconds);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
