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
 * Sổ ghi chép cá nhân (ADR-261) — {@code /so them <nội dung>}, {@code /so} (hoặc {@code /so xem}), {@code /so xoa <số>}
 * (hoặc {@code /note add|list|delete}). Tối đa {@value #MAX_NOTES} dòng × {@value #MAX_LENGTH} ký tự, lưu Preferences
 * trên máy — không gửi server.
 */
final class NotebookCoordinator implements WindowFlow {

    static final int MAX_NOTES = 20;
    static final int MAX_LENGTH = 120;
    private static final String PREF_KEY = "notebook";
    private static final String SEPARATOR = "\n";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final List<String> notes = new ArrayList<>();

    NotebookCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(
                prefs -> notes.addAll(parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))));
        chat.addSlashCommands(this::onSlash);
    }

    /** Dòng ghi chép từ chuỗi lưu (bỏ dòng rỗng, cắt ở trần). Tĩnh để test. */
    static List<String> parse(String saved) {
        List<String> parsed = new ArrayList<>();
        for (String line : saved.split(SEPARATOR, -1)) {
            if (!line.isBlank() && parsed.size() < MAX_NOTES) {
                parsed.add(line.strip());
            }
        }
        return parsed;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/so".equals(command) && !"/note".equals(command)) {
            return false;
        }
        String action = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "";
        String rest = words.length > 2 ? words[2].strip() : "";
        switch (action) {
            case "them", "add" -> add(rest);
            case "xoa", "delete" -> delete(rest);
            default -> list();
        }
        return true;
    }

    private void add(String text) {
        if (text.isEmpty() || text.length() > MAX_LENGTH || text.contains(SEPARATOR)) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "notebook.usage"));
        } else if (notes.size() >= MAX_NOTES) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "notebook.full"));
        } else {
            notes.add(text);
            save();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "notebook.added", notes.size()));
        }
    }

    private void delete(String number) {
        int index = number.matches("[0-9]{1,2}") ? Integer.parseInt(number) - 1 : -1;
        if (index < 0 || index >= notes.size()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "notebook.usage"));
            return;
        }
        notes.remove(index);
        save();
        list();
    }

    private void list() {
        if (notes.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "notebook.empty"));
            return;
        }
        for (int i = 0; i < notes.size(); i++) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "notebook.line", i + 1, notes.get(i)));
        }
    }

    private void save() {
        preferences.ifPresent(prefs -> {
            prefs.putString(PREF_KEY, String.join(SEPARATOR, notes));
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
