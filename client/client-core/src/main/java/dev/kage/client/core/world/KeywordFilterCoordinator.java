package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lọc chat theo từ khoá (ADR-249) — {@code /locchu them <từ>}, {@code /locchu bo <từ>}, {@code /locchu} (hoặc {@code
 * /mute add|remove}). Tin người chơi chứa từ đó (không dấu, không phân biệt hoa thường) bị ẩn; dòng hệ thống không bao
 * giờ bị ẩn. Lưu Preferences cùng tệp với lọc kênh (ADR-229).
 */
final class KeywordFilterCoordinator implements WindowFlow {

    static final int MAX_WORDS = 20;
    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 24;
    private static final String PREF_KEY = "chat.keywords";
    private static final String SEPARATOR = "\n";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final List<String> words = new ArrayList<>();

    KeywordFilterCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(
                prefs -> words.addAll(parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))));
        chat.hideBodiesMatching(this::matches);
        chat.addSlashCommands(this::onSlash);
    }

    /** Danh sách từ đã chuẩn hoá từ chuỗi lưu. Tĩnh để test. */
    static List<String> parse(String saved) {
        List<String> parsed = new ArrayList<>();
        for (String part : saved.split(SEPARATOR, -1)) {
            String word = MentionWatch.fold(part.strip());
            if (word.length() >= MIN_LENGTH && !parsed.contains(word) && parsed.size() < MAX_WORDS) {
                parsed.add(word);
            }
        }
        return parsed;
    }

    /** Tin có chứa một trong các từ không. */
    boolean matches(String body) {
        if (words.isEmpty()) {
            return false;
        }
        String folded = MentionWatch.fold(body);
        for (String word : words) {
            if (folded.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 3);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/locchu".equals(command) && !"/mute".equals(command)) {
            return false;
        }
        String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        String word = parts.length > 2 ? MentionWatch.fold(parts[2].strip()) : "";
        if (change(action, word)) {
            chat.showSystemNotice(
                    words.isEmpty()
                            ? KageI18n.textOrKey(i18n, "keyword.none")
                            : KageI18n.formatOrKey(i18n, "keyword.list", String.join(", ", words)));
        }
        return true;
    }

    /** Thêm / bỏ một từ; {@code false} nếu đã báo lỗi (không cần in danh sách). */
    private boolean change(String action, String word) {
        boolean add = "them".equals(action) || "add".equals(action);
        boolean remove = "bo".equals(action) || "remove".equals(action);
        if (!add && !remove) {
            return true;
        }
        Optional<String> rejected = rejection(add, word);
        if (rejected.isPresent()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, rejected.get()));
            return false;
        }
        if (remove) {
            words.remove(word);
        } else if (!words.contains(word)) {
            words.add(word);
        }
        save();
        return true;
    }

    /** Khoá lỗi nếu từ không hợp lệ hoặc danh sách đã đầy. */
    private Optional<String> rejection(boolean add, String word) {
        if (word.length() < MIN_LENGTH || word.length() > MAX_LENGTH) {
            return Optional.of("keyword.usage");
        }
        return add && !words.contains(word) && words.size() >= MAX_WORDS
                ? Optional.of("keyword.full")
                : Optional.empty();
    }

    private void save() {
        preferences.ifPresent(prefs -> {
            prefs.putString(PREF_KEY, String.join(SEPARATOR, words));
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
