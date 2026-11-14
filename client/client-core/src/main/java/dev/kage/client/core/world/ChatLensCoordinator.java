package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn tiện ích khung chat lô 62, nhớ trên máy: đếm tin mới khi ẩn chat (ADR-427 — dòng HUD "Chat: N tin mới"), tên kênh
 * rút gọn (ADR-428, {@code /kenhngan} hoặc {@code /shortchannels}), từ khoá theo dõi được tô màu (ADR-429, {@code
 * /theodoitu <từ>} thêm/bỏ, {@code /theodoitu} liệt kê; tối đa {@value #MAX_WATCH_WORDS} từ), chế độ streamer (ADR-430,
 * {@code /streamer} — tên người không phải bạn bè hiện thành "Tu sĩ #n").
 */
final class ChatLensCoordinator implements WindowFlow {

    /** ADR-429 — số từ theo dõi tối đa. */
    static final int MAX_WATCH_WORDS = 10;

    private static final String SHORT_KEY = "chat.short_channels";
    private static final String WATCH_KEY = "chat.watch_words";
    private static final String STREAMER_KEY = "chat.streamer";
    private static final String SEPARATOR = "\n";
    private static final long NOTHING = -1L;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final Set<String> watchWords = new LinkedHashSet<>();
    private final Map<String, Integer> aliases = new HashMap<>();
    private Predicate<String> isFriend = name -> false;
    private boolean shortChannels;
    private boolean streamer;
    private long unseen;

    ChatLensCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.shortChannels =
                preferences.map(p -> p.getBoolean(SHORT_KEY, false)).orElse(false);
        this.streamer = preferences.map(p -> p.getBoolean(STREAMER_KEY, false)).orElse(false);
        String saved = preferences.map(p -> p.getString(WATCH_KEY, "")).orElse("");
        for (String word : (saved == null ? "" : saved).split(SEPARATOR, -1)) {
            if (!word.isBlank()) {
                watchWords.add(word);
            }
        }
        chat.setShortChannels(shortChannels);
        chat.useHighlight(this::watched);
        chat.useEventRewriter(this::anonymize);
        chat.onIncoming(this::countUnseen);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nguồn "là bạn bè" (chế độ streamer giữ tên bạn bè). */
    void useFriends(Predicate<String> friendNamed) {
        this.isFriend = friendNamed;
    }

    /** ADR-427 — nối dòng HUD đếm tin mới (luôn bật; trống khi chat đang hiện). */
    void useHud(Consumer<Boolean> setVisible) {
        setVisible.accept(true);
    }

    /** ADR-427 — số tin mới từ lúc ẩn chat; chat đang hiện ⇒ -1 (trống). */
    long unseenCount() {
        if (!chat.panel().isHidden()) {
            unseen = 0L;
            return NOTHING;
        }
        return unseen;
    }

    private void countUnseen(ChatMessageEvent event) {
        if (chat.panel().isHidden() && event.getSenderId() != 0L) {
            unseen++;
        }
    }

    /** ADR-429 — nội dung có chứa một từ theo dõi (không phân biệt hoa thường). */
    boolean watched(String body) {
        return contains(watchWords, body);
    }

    /** Có từ nào trong {@code words} xuất hiện trong {@code body} không. Tĩnh để test. */
    static boolean contains(Set<String> words, String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** ADR-430 — đổi tên người lạ thành bí danh ổn định trong phiên khi bật chế độ streamer. */
    ChatMessageEvent anonymize(ChatMessageEvent event) {
        String name = event.getSenderName();
        if (!streamer || name.isEmpty() || chat.isOwnName(name) || isFriend.test(ChatCoordinator.bareName(name))) {
            return event;
        }
        int number = aliases.computeIfAbsent(name, key -> aliases.size() + 1);
        return event.toBuilder()
                .setSenderName(KageI18n.formatOrKey(i18n, "streamer.alias", number))
                .build();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "";
        switch (command) {
            case "/kenhngan", "/shortchannels" -> {
                shortChannels = remember(SHORT_KEY, !shortChannels);
                chat.setShortChannels(shortChannels);
                notice(shortChannels ? "shortchannels.on" : "shortchannels.off");
            }
            case "/streamer" -> {
                streamer = remember(STREAMER_KEY, !streamer);
                aliases.clear();
                notice(streamer ? "streamer.on" : "streamer.off");
            }
            case "/theodoitu", "/watchword" -> toggleWord(arg);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void toggleWord(String word) {
        if (word.isEmpty()) {
            chat.showSystemNotice(
                    watchWords.isEmpty()
                            ? KageI18n.textOrKey(i18n, "watchword.none")
                            : KageI18n.formatOrKey(i18n, "watchword.list", String.join(", ", watchWords)));
            return;
        }
        if (watchWords.remove(word)) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "watchword.removed", word));
        } else if (watchWords.size() >= MAX_WATCH_WORDS) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "watchword.full", MAX_WATCH_WORDS));
            return;
        } else {
            watchWords.add(word);
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "watchword.added", word));
        }
        preferences.ifPresent(prefs -> {
            prefs.putString(WATCH_KEY, String.join(SEPARATOR, watchWords));
            prefs.flush();
        });
        chat.useHighlight(this::watched);
    }

    private boolean remember(String key, boolean value) {
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(key, value);
            prefs.flush();
        });
        return value;
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
