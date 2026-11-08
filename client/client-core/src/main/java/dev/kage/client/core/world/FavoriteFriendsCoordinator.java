package dev.kage.client.core.world;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bạn bè yêu thích (ADR-276) — {@code /yeuthich <tên>} (hoặc {@code /favorite}) ghim/bỏ ghim một người; {@code
 * /yeuthich} liệt kê. Người được ghim đứng đầu danh sách bạn bè, có dấu {@code [*]} trước tên ({@link
 * FriendCoordinator#useFavorites}). Lưu Preferences trên máy (tối đa {@value #MAX_FAVORITES}), khoá theo tên đã bỏ dấu
 * — chỉ là sắp xếp hiển thị.
 */
final class FavoriteFriendsCoordinator implements WindowFlow {

    static final int MAX_FAVORITES = 20;
    private static final String PREF_KEY = "friend.favorites";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final Set<String> favorites = new LinkedHashSet<>();
    private Runnable onChanged = () -> {};

    FavoriteFriendsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs ->
                favorites.addAll(parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))));
        chat.addSlashCommands(this::onSlash);
    }

    /** Tên đã lưu (mỗi dòng một tên, đã bỏ dấu). Tĩnh để test. */
    static Set<String> parse(String saved) {
        Set<String> parsed = new LinkedHashSet<>();
        for (String line : saved.split("\n", -1)) {
            if (!line.isBlank() && parsed.size() < MAX_FAVORITES) {
                parsed.add(MentionWatch.fold(line.strip()));
            }
        }
        return parsed;
    }

    /** Gọi lại khi danh sách đổi (khung bạn bè vẽ lại). */
    void onChanged(Runnable listener) {
        this.onChanged = listener;
    }

    /** {@code name} có được ghim không. */
    boolean isFavorite(String name) {
        return favorites.contains(MentionWatch.fold(name.strip()));
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/yeuthich".equals(command) && !"/favorite".equals(command)) {
            return false;
        }
        String name = parts.length > 1 ? parts[1].strip() : "";
        if (name.isEmpty()) {
            chat.showSystemNotice(
                    favorites.isEmpty()
                            ? KageI18n.textOrKey(i18n, "favorite.empty")
                            : KageI18n.formatOrKey(i18n, "favorite.list", String.join(", ", favorites)));
            return true;
        }
        String key = MentionWatch.fold(name);
        if (favorites.remove(key)) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "favorite.removed", name));
        } else if (favorites.size() >= MAX_FAVORITES) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "favorite.full"));
            return true;
        } else {
            favorites.add(key);
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "favorite.added", name));
        }
        preferences.ifPresent(prefs -> {
            prefs.putString(PREF_KEY, String.join("\n", favorites));
            prefs.flush();
        });
        onChanged.run();
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
