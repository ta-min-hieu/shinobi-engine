package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Màu riêng cho kênh chat (ADR-339) — {@code /maukenh <kênh> <màu>} (hoặc {@code /chatcolor}); kênh: {@code thegioi,
 * khuvuc, rieng, hethong, loa, nhacten}; màu: tên trong bảng ({@code do, cam, vang, xanhla, xanhduong, tim, hong,
 * trang}) hoặc mã 6 chữ số hex; {@code /maukenh <kênh> macdinh} trả màu gốc. Nhớ trên máy, thuần client.
 */
final class ChatColorCoordinator implements WindowFlow {

    /** Tên kênh trong lệnh ⇒ loại dòng. */
    static final Map<String, ChatPanel.LineKind> CHANNELS = Map.of(
            "thegioi", ChatPanel.LineKind.WORLD,
            "khuvuc", ChatPanel.LineKind.AREA,
            "rieng", ChatPanel.LineKind.PRIVATE,
            "hethong", ChatPanel.LineKind.SYSTEM,
            "loa", ChatPanel.LineKind.BROADCAST,
            "nhacten", ChatPanel.LineKind.MENTION);

    /** Bảng màu có tên. */
    static final Map<String, String> NAMED = Map.of(
            "do", "FF5A4A",
            "cam", "FF9F43",
            "vang", "FFD24A",
            "xanhla", "7CFF7C",
            "xanhduong", "5FA8FF",
            "tim", "C9A2FF",
            "hong", "F58AD0",
            "trang", "F2F2F2");

    private static final String PREF_PREFIX = "chat.color.";
    private static final int HEX_DIGITS = 6;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;

    ChatColorCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs -> CHANNELS.forEach((name, kind) -> parseColor(
                        java.util.Objects.requireNonNullElse(prefs.getString(PREF_PREFIX + name, ""), ""))
                .ifPresent(hex -> chat.panel().setLineColor(kind, Optional.of(Color.valueOf(hex))))));
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/maukenh".equals(command) && !"/chatcolor".equals(command)) {
            return false;
        }
        ChatPanel.LineKind kind = words.length == 3 ? CHANNELS.get(MentionWatch.fold(words[1])) : null;
        if (kind == null) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatcolor.usage"));
            return true;
        }
        String value = MentionWatch.fold(words[2]);
        if ("macdinh".equals(value) || "default".equals(value)) {
            remember(words[1], "");
            chat.panel().setLineColor(kind, Optional.empty());
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatcolor.reset"));
            return true;
        }
        Optional<String> hex = parseColor(value);
        if (hex.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatcolor.usage"));
            return true;
        }
        remember(words[1], hex.get());
        chat.panel().setLineColor(kind, Optional.of(Color.valueOf(hex.get())));
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "chatcolor.ok", hex.get()));
        return true;
    }

    private void remember(String channel, String hex) {
        preferences.ifPresent(prefs -> {
            prefs.putString(PREF_PREFIX + MentionWatch.fold(channel), hex);
            prefs.flush();
        });
    }

    /** Tên màu hoặc mã hex 6 chữ số (có/không {@code #}) ⇒ mã hex in hoa. Tĩnh để test. */
    static Optional<String> parseColor(String value) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        if (NAMED.containsKey(text)) {
            return Optional.of(NAMED.get(text));
        }
        String hex = text.startsWith("#") ? text.substring(1) : text;
        return hex.length() == HEX_DIGITS && hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)
                ? Optional.of(hex.toUpperCase(Locale.ROOT))
                : Optional.empty();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
