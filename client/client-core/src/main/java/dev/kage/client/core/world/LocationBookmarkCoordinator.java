package dev.kage.client.core.world;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đánh dấu vị trí yêu thích (ADR-250) — {@code /luuvitri <tên>} lưu toạ độ X đang đứng; {@code /vitri} liệt kê; {@code
 * /vitri <tên>} tự đi tới (đường tự đi ADR-206); {@code /vitri xoa <tên>}. Tối đa {@value #MAX_BOOKMARKS}, lưu
 * Preferences.
 */
final class LocationBookmarkCoordinator implements WindowFlow {

    static final int MAX_BOOKMARKS = 10;
    static final int MAX_NAME = 20;
    private static final String PREF_KEY = "world.bookmarks";
    private static final String NUMBER = "-?[0-9]{1,6}(\\.[0-9]{1,6})?";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final Map<String, Float> bookmarks = new LinkedHashMap<>();
    private Optional<AutoWalkTarget> walker = Optional.empty();

    LocationBookmarkCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs ->
                bookmarks.putAll(parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))));
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối đích tự đi (ADR-206) — cũng là nơi biết toạ độ hiện tại. */
    void useWalker(AutoWalkTarget target) {
        this.walker = Optional.of(target);
    }

    /** {@code tên=x} mỗi dòng ⇒ bảng; dòng hỏng bị bỏ. Tĩnh để test. */
    static Map<String, Float> parse(String saved) {
        Map<String, Float> parsed = new LinkedHashMap<>();
        for (String line : saved.split("\n", -1)) {
            int eq = line.lastIndexOf('=');
            String value = eq > 0 ? line.substring(eq + 1) : "";
            if (value.matches(NUMBER) && parsed.size() < MAX_BOOKMARKS) {
                parsed.put(line.substring(0, eq), Float.parseFloat(value));
            }
        }
        return parsed;
    }

    /** Tên đã lưu khớp không dấu với {@code query}. */
    Optional<String> find(String query) {
        String folded = MentionWatch.fold(query.strip());
        return bookmarks.keySet().stream()
                .filter(name -> MentionWatch.fold(name).equals(folded))
                .findFirst();
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].strip() : "";
        if ("/luuvitri".equals(command) || "/savespot".equals(command)) {
            save(argument);
            return true;
        }
        if (!"/vitri".equals(command) && !"/spot".equals(command)) {
            return false;
        }
        spot(argument);
        return true;
    }

    /** {@code /vitri} (liệt kê), {@code /vitri xoa <tên>}, {@code /vitri <tên>} (đi tới). */
    private void spot(String argument) {
        String[] sub = argument.split("\\s+", 2);
        boolean delete = "xoa".equalsIgnoreCase(sub[0]) || "delete".equalsIgnoreCase(sub[0]);
        if (argument.isEmpty()) {
            list();
        } else if (delete && sub.length > 1) {
            find(sub[1]).ifPresent(bookmarks::remove);
            persist();
            list();
        } else {
            walkTo(argument);
        }
    }

    private void save(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME || name.indexOf('=') >= 0 || walker.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bookmark.usage"));
            return;
        }
        Optional<String> existing = find(name);
        if (existing.isEmpty() && bookmarks.size() >= MAX_BOOKMARKS) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bookmark.full"));
            return;
        }
        existing.ifPresent(bookmarks::remove);
        float x = walker.get().lastKnownX();
        bookmarks.put(name, x);
        persist();
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "bookmark.saved", name, Math.round(x)));
    }

    private void list() {
        chat.showSystemNotice(
                bookmarks.isEmpty()
                        ? KageI18n.textOrKey(i18n, "bookmark.none")
                        : KageI18n.formatOrKey(i18n, "bookmark.list", String.join(", ", bookmarks.keySet())));
    }

    private void walkTo(String query) {
        Optional<String> name = find(query);
        if (name.isEmpty() || walker.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "bookmark.not_found", query));
            return;
        }
        AutoWalkTarget target = walker.get();
        float x = bookmarks.get(name.get());
        String label = name.get();
        target.stop();
        target.start(
                x,
                () -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.arrived", label)),
                () -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.stopped", label)));
        chat.showSystemNotice(
                KageI18n.formatOrKey(i18n, "autowalk.walking", label, Math.round(Math.abs(x - target.lastKnownX()))));
    }

    private void persist() {
        preferences.ifPresent(prefs -> {
            StringBuilder out = new StringBuilder();
            bookmarks.forEach(
                    (name, x) -> out.append(name).append('=').append(x).append('\n'));
            prefs.putString(PREF_KEY, out.toString());
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
