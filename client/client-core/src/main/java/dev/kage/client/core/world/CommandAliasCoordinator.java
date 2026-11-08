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
 * Biệt danh lệnh (ADR-273) — {@code /alias <tắt> <lệnh>} (hoặc {@code /bietdanh}) đặt, ví dụ {@code /alias a /khobang};
 * {@code /alias} liệt kê; {@code /alias xoa <tắt>} xoá. Tối đa {@value #MAX_ALIASES}, lưu Preferences. Khi gõ
 * {@code /<tắt> phần còn lại}, dòng được viết lại thành {@code <lệnh> phần còn lại} TRƯỚC mọi xử lý (một lần —
 * biệt danh không trỏ được tới biệt danh khác; trùng tên lệnh sẵn có thì che lệnh đó — xoá biệt danh là lấy lại).
 */
final class CommandAliasCoordinator implements WindowFlow {

    static final int MAX_ALIASES = 20;
    static final int MAX_TARGET = 60;
    private static final String PREF_KEY = "chat.aliases";
    private static final String NAME = "[a-z0-9]{1,12}";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final Map<String, String> aliases = new LinkedHashMap<>();

    CommandAliasCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs ->
                aliases.putAll(parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))));
        chat.addSlashCommands(this::onSlash);
        chat.useLineRewriter(this::expand);
    }

    /** {@code tắt=lệnh} mỗi dòng ⇒ bảng; dòng hỏng bị bỏ. Tĩnh để test. */
    static Map<String, String> parse(String saved) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : saved.split("\n", -1)) {
            int eq = line.indexOf('=');
            if (eq > 0
                    && line.substring(0, eq).matches(NAME)
                    && validTarget(line.substring(eq + 1))
                    && parsed.size() < MAX_ALIASES) {
                parsed.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return parsed;
    }

    /** Lệnh đích hợp lệ: bắt đầu bằng {@code /}, không phải chính {@code /alias}, không xuống dòng. Tĩnh để test. */
    static boolean validTarget(String target) {
        String command = target.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return target.startsWith("/")
                && target.length() <= MAX_TARGET
                && !target.contains("\n")
                && !"/alias".equals(command)
                && !"/bietdanh".equals(command);
    }

    /** Viết lại {@code line} theo {@code table}; không khớp thì giữ nguyên. Tĩnh để test. */
    static String expand(String line, Map<String, String> table) {
        String stripped = line.strip();
        if (!stripped.startsWith("/")) {
            return line;
        }
        String[] parts = stripped.split("\\s+", 2);
        String target = table.get(parts[0].substring(1).toLowerCase(Locale.ROOT));
        if (target == null) {
            return line;
        }
        return parts.length > 1 ? target + " " + parts[1] : target;
    }

    private String expand(String line) {
        return expand(line, aliases);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/alias".equals(command) && !"/bietdanh".equals(command)) {
            return false;
        }
        if (words.length == 1) {
            list();
        } else if (("xoa".equals(words[1]) || "delete".equals(words[1])) && words.length == 3) {
            delete(words[2].toLowerCase(Locale.ROOT));
        } else if (words.length == 3) {
            set(words[1].toLowerCase(Locale.ROOT).replaceFirst("^/", ""), words[2].strip());
        } else {
            notice("alias.usage");
        }
        return true;
    }

    private void set(String name, String target) {
        if (!name.matches(NAME) || !validTarget(target)) {
            notice("alias.usage");
        } else if (!aliases.containsKey(name) && aliases.size() >= MAX_ALIASES) {
            notice("alias.full");
        } else {
            aliases.put(name, target);
            save();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "alias.set", name, target));
        }
    }

    private void delete(String name) {
        if (aliases.remove(name.replaceFirst("^/", "")) == null) {
            notice("alias.usage");
            return;
        }
        save();
        list();
    }

    private void list() {
        if (aliases.isEmpty()) {
            notice("alias.empty");
            return;
        }
        aliases.forEach(
                (name, target) -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "alias.line", name, target)));
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }

    private void save() {
        StringBuilder saved = new StringBuilder();
        aliases.forEach(
                (name, target) -> saved.append(name).append('=').append(target).append('\n'));
        preferences.ifPresent(prefs -> {
            prefs.putString(PREF_KEY, saved.toString());
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
