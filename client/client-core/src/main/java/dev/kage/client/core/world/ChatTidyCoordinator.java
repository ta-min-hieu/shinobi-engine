package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn tiện ích khung chat lô 61, nhớ trên máy: ẩn dòng hệ thống từ server (ADR-419, {@code /antinhethong} hoặc {@code
 * /hidesystem}), gộp tin trùng liên tiếp (ADR-420, {@code /gopchat} hoặc {@code /collapsechat}, mặc định bật), bong
 * bóng chat chỉ của bạn bè / người cùng nhóm (ADR-421, {@code /bongbongban} hoặc {@code /friendbubbles}), câu nói nhanh
 * (ADR-422, {@code /caunhanh <1-5> <câu>} lưu, {@code /cn <1-5>} gửi vào kênh đang chọn).
 */
final class ChatTidyCoordinator implements WindowFlow {

    /** ADR-422 — số ô câu nói nhanh. */
    static final int PHRASE_SLOTS = 5;

    private static final String HIDE_SYSTEM_KEY = "chat.hide_system";
    private static final String COLLAPSE_KEY = "chat.collapse_repeats";
    private static final String FRIEND_BUBBLES_KEY = "chat.friend_bubbles";
    private static final String PHRASE_KEY = "chat.phrase.";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Predicate<String> isFriend = name -> false;
    private LongPredicate inParty = id -> false;
    private boolean hideSystem;
    private boolean collapse;
    private boolean friendBubbles;

    ChatTidyCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.hideSystem = flag(HIDE_SYSTEM_KEY, false);
        this.collapse = flag(COLLAPSE_KEY, true);
        this.friendBubbles = flag(FRIEND_BUBBLES_KEY, false);
        chat.useHideSystem(() -> hideSystem);
        chat.setCollapseRepeats(collapse);
        chat.useLineRewriter(this::expandPhrase);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nguồn "là bạn bè" (theo tên) và "cùng nhóm" (theo id). */
    void useRelations(Predicate<String> friendNamed, LongPredicate partyMember) {
        this.isFriend = friendNamed;
        this.inParty = partyMember;
    }

    /** ADR-421 — điều kiện hiện bong bóng cho {@code ChatBubbleCoordinator}. */
    boolean bubbleAllowed(ChatMessageEvent event) {
        return !friendBubbles
                || chat.isOwnName(event.getSenderName())
                || isFriend.test(event.getSenderName())
                || inParty.test(event.getSenderId());
    }

    /** ADR-422 — {@code /cn <n>} ⇒ câu đã lưu ở ô n (chưa lưu ⇒ báo và bỏ dòng). */
    String expandPhrase(String typed) {
        Optional<Integer> slot = phraseSlot(typed);
        if (slot.isEmpty()) {
            return typed;
        }
        String phrase = preferences
                .map(prefs -> prefs.getString(PHRASE_KEY + slot.get(), ""))
                .orElse("");
        if (phrase == null || phrase.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "phrase.empty", slot.get()));
            return "";
        }
        return phrase;
    }

    /** Ô câu nói nhanh của dòng {@code /cn <n>}, nếu đúng cú pháp. Tĩnh để test. */
    static Optional<Integer> phraseSlot(String typed) {
        String[] words = typed.strip().split("\\s+", 3);
        if (words.length != 2 || !"/cn".equalsIgnoreCase(words[0]) || !words[1].matches("[1-5]")) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(words[1]));
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "/antinhethong", "/hidesystem" -> toggleHideSystem();
            case "/gopchat", "/collapsechat" -> toggleCollapse();
            case "/bongbongban", "/friendbubbles" -> toggleFriendBubbles();
            case "/caunhanh", "/phrase" -> savePhrase(words);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void toggleHideSystem() {
        hideSystem = toggle(HIDE_SYSTEM_KEY, hideSystem);
        notice(hideSystem ? "hidesystem.on" : "hidesystem.off");
    }

    private void toggleCollapse() {
        collapse = toggle(COLLAPSE_KEY, collapse);
        chat.setCollapseRepeats(collapse);
        notice(collapse ? "collapsechat.on" : "collapsechat.off");
    }

    private void toggleFriendBubbles() {
        friendBubbles = toggle(FRIEND_BUBBLES_KEY, friendBubbles);
        notice(friendBubbles ? "friendbubbles.on" : "friendbubbles.off");
    }

    private void savePhrase(String[] words) {
        if (words.length < 3 || !words[1].matches("[1-5]") || words[2].isBlank()) {
            notice("phrase.usage");
            return;
        }
        String phrase = words[2].strip();
        preferences.ifPresent(prefs -> {
            prefs.putString(PHRASE_KEY + words[1], phrase);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "phrase.saved", words[1], phrase));
    }

    private boolean flag(String key, boolean fallback) {
        return preferences.map(prefs -> prefs.getBoolean(key, fallback)).orElse(fallback);
    }

    private boolean toggle(String key, boolean current) {
        boolean next = !current;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(key, next);
            prefs.flush();
        });
        return next;
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
