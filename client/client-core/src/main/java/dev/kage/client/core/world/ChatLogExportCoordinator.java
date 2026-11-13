package dev.kage.client.core.world;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lưu chat ra file (ADR-347) — giữ {@value #MAX_LINES} tin gần nhất của phiên (kể cả tin hệ thống từ server); {@code
 * /luuchat} (hoặc {@code /savechat}) ghi ra {@code chatlogs/chat-<ngày-giờ>.txt} cạnh game. Thuần client.
 */
final class ChatLogExportCoordinator implements WindowFlow {

    /** Số tin giữ trong RAM. */
    static final int MAX_LINES = 500;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Deque<String> lines = new ArrayDeque<>();

    ChatLogExportCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.onIncoming(this::remember);
        chat.addSlashCommands(this::onSlash);
    }

    private void remember(ChatMessageEvent event) {
        if (lines.size() == MAX_LINES) {
            lines.removeFirst();
        }
        lines.addLast(format(LocalDateTime.now(java.time.ZoneId.systemDefault()), event));
    }

    /** Một dòng file: giờ, kênh, người gửi, nội dung. Tĩnh để test. */
    static String format(LocalDateTime at, ChatMessageEvent event) {
        String channel = event.getChannel().name().replace("CHAT_CHANNEL_", "").toLowerCase(Locale.ROOT);
        String sender = event.getSenderName().isEmpty() ? "*" : event.getSenderName();
        return "[" + TIME.format(at) + "] (" + channel + ") " + sender + ": " + event.getBody();
    }

    int size() {
        return lines.size();
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/luuchat".equals(command) && !"/savechat".equals(command)) {
            return false;
        }
        if (Gdx.files == null) {
            return true; // không có hệ file (test) — không làm gì
        }
        String name = "chatlogs/chat-" + FILE.format(LocalDateTime.now(java.time.ZoneId.systemDefault())) + ".txt";
        try {
            FileHandle file = Gdx.files.local(name);
            file.writeString(String.join(System.lineSeparator(), lines) + System.lineSeparator(), false, "UTF-8");
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "savechat.ok", lines.size(), file.path()));
        } catch (GdxRuntimeException e) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "savechat.failed", e.getMessage()));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
