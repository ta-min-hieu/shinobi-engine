package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bong bóng chat (ADR-315) — tin kênh khu vực hiện thành chữ trên đầu người nói vài giây (cùng lớp vẽ biểu cảm
 * ADR-092); {@code /bongbong} bật/tắt.
 */
final class ChatBubbleCoordinator implements WindowFlow {

    /** Bong bóng dài nhất (ký tự). */
    static final int MAX_LENGTH = 40;

    private static final String PREF_KEY = "chat.bubbles";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final EmoteCoordinator.EmoteDisplay display;
    private boolean enabled;
    private java.util.function.Predicate<dev.kage.protocol.ChatMessageEvent> audience = event -> true; // ADR-421

    ChatBubbleCoordinator(ChatCoordinator chat, EmoteCoordinator.EmoteDisplay display) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        this.display = display;
        chat.onIncoming(this::onIncoming);
        chat.addSlashCommands(this::onSlash);
    }

    /** ADR-421 — ai được hiện bong bóng (mặc định: mọi người). */
    void useAudience(java.util.function.Predicate<dev.kage.protocol.ChatMessageEvent> shown) {
        this.audience = shown;
    }

    /** Công tắc đang bật không. */
    boolean enabled() {
        return enabled;
    }

    private void onIncoming(dev.kage.protocol.ChatMessageEvent event) {
        if (enabled
                && event.getSenderId() != 0L
                && event.getChannel() == dev.kage.protocol.ChatChannel.CHAT_CHANNEL_AREA
                && audience.test(event)) {
            display.show(event.getSenderId(), bubbleText(event.getBody()));
        }
    }

    /** Cắt gọn nội dung bong bóng. Tĩnh để test. */
    static String bubbleText(String body) {
        String clean = ChatController.sanitize(body).strip();
        return clean.length() <= MAX_LENGTH ? clean : clean.substring(0, MAX_LENGTH - 3) + "...";
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/bongbong".equals(command) && !"/bubbles".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "bubbles.on" : "bubbles.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
