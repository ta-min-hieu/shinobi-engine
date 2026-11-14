package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hai tiện ích khung chat lô 56: độ mờ nền (ADR-381, {@code /nenchat 0-100} hoặc {@code /chatbg}, nhớ trên máy) và
 * sao chép tên vào clipboard (ADR-382, {@code /saochep [tên]} hoặc {@code /copyname}; không ghi tên ⇒ người nhắn
 * riêng gần nhất).
 */
final class ChatToolsCoordinator implements WindowFlow {

    /** Độ đậm nền mặc định (%) — như trước khi có lệnh. */
    static final int DEFAULT_OPACITY_PERCENT = 100;

    private static final String PREF_KEY = "chat.background_opacity";
    private static final float PERCENT = 100f;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<String> clipboard = text -> {
        if (Gdx.app != null) {
            Gdx.app.getClipboard().setContents(text);
        }
    };
    private Optional<String> lastWhisperer = Optional.empty();

    ChatToolsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        int saved = preferences
                .map(prefs -> prefs.getInteger(PREF_KEY, DEFAULT_OPACITY_PERCENT))
                .orElse(DEFAULT_OPACITY_PERCENT);
        chat.panel().setBackgroundOpacity(Math.clamp(saved, 0, 100) / PERCENT);
        chat.addSlashCommands(this::onSlash);
    }

    /** Thay nơi ghi clipboard (test). */
    void useClipboard(Consumer<String> sink) {
        this.clipboard = Objects.requireNonNull(sink);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        if ("/nenchat".equals(command) || "/chatbg".equals(command)) {
            setOpacity(arg);
            return true;
        }
        if ("/saochep".equals(command) || "/copyname".equals(command)) {
            copy(arg);
            return true;
        }
        return false;
    }

    private void setOpacity(String arg) {
        int percent = parse(arg);
        if (percent < 0 || percent > 100) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "chatbg.usage"));
            return;
        }
        chat.panel().setBackgroundOpacity(percent / PERCENT);
        preferences.ifPresent(prefs -> {
            prefs.putInteger(PREF_KEY, percent);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "chatbg.set", percent));
    }

    private void copy(String arg) {
        Optional<String> name = arg.isEmpty() ? lastWhisperer : Optional.of(arg);
        if (name.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "copyname.nobody"));
            return;
        }
        clipboard.accept(name.get());
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "copyname.done", name.get()));
    }

    private static int parse(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1; // không phải số ⇒ sai cú pháp
        }
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
            return false;
        }
        ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
        if (event.getChannel() == ChatChannel.CHAT_CHANNEL_PRIVATE
                && !event.getSenderName().isEmpty()
                && !chat.isOwnName(event.getSenderName())) {
            lastWhisperer = Optional.of(event.getSenderName());
        }
        return false;
    }
}
