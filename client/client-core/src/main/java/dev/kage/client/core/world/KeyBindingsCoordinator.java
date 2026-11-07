package dev.kage.client.core.world;

import java.util.Locale;
import java.util.StringJoiner;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.input.KeyBindings;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đổi phím tắt (ADR-190) — lệnh chat {@code /phimtat} (hoặc {@code /keys}): không đối số ⇒ in mọi phím đang dùng;
 * {@code /phimtat <tên> <phím>} đổi (vd {@code /phimtat tuido K}); {@code /phimtat macdinh} trả hết về mặc định. Thuần
 * client, lưu trên máy — không gói nào lên server.
 */
final class KeyBindingsCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/phimtat";
    private static final String COMMAND_EN = "/keys";
    private static final String RESET_PREFIX = "macdinh";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    KeyBindingsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        chat.showSystemNotice(handle(KeyBindings.shared(), words, i18n));
        return true;
    }

    /** Một lệnh {@code /phimtat} đã tách từ — tĩnh để test không cần màn hình. Trả dòng hệ thống cần in. */
    static String handle(KeyBindings bindings, String[] words, I18NBundle i18n) {
        if (words.length == 1) {
            return KageI18n.formatOrKey(i18n, "keybind.list", list(bindings));
        }
        String arg = words[1].toLowerCase(Locale.ROOT);
        if (words.length == 2 && (RESET_PREFIX.equals(arg) || "reset".equals(arg))) {
            bindings.resetAll();
            return KageI18n.textOrKey(i18n, "keybind.reset");
        }
        java.util.Optional<KeyBindings.Binding> binding = KeyBindings.Binding.byId(arg);
        if (binding.isEmpty() || words.length < 3) {
            return KageI18n.textOrKey(i18n, "keybind.usage");
        }
        int key = Keys.valueOf(
                words[2].toUpperCase(Locale.ROOT).length() == 1
                        ? words[2].toUpperCase(Locale.ROOT)
                        : capitalize(words[2]));
        KeyBindings.Rebind result = bindings.rebind(binding.get(), key);
        return result == KeyBindings.Rebind.OK
                ? KageI18n.formatOrKey(i18n, "keybind.ok", binding.get().id(), Keys.toString(key))
                : KageI18n.formatOrKey(i18n, "keybind.outcome." + result.name().toLowerCase(Locale.ROOT), words[2]);
    }

    /** {@code tuido=I, nhiemvu=J, ...} */
    static String list(KeyBindings bindings) {
        StringJoiner out = new StringJoiner(", ");
        for (KeyBindings.Binding binding : KeyBindings.Binding.values()) {
            out.add(binding.id() + "=" + Keys.toString(bindings.keyOf(binding)));
        }
        return out.toString();
    }

    /** Tên phím libGDX nhiều chữ viết hoa chữ đầu ({@code Enter}, {@code Home}); người gõ thường không biết. */
    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return false; // thuần client — không có gói nào
    }
}
