package dev.kage.client.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử thông báo hệ thống (ADR-317) — giữ {@value #CAPACITY} dòng hệ thống gần nhất server gửi (tin không có người
 * gửi: boss, sự kiện, thư chưa đọc…), {@code /lichsuthongbao} (hoặc {@code /systemlog}) in lại. {@code /thongbao} đã là
 * thông báo bang (ADR-179). Chỉ sống trong phiên.
 */
final class SystemNoticeHistoryCoordinator implements WindowFlow {

    /** Số dòng giữ. */
    static final int CAPACITY = 30;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Deque<String> lines = new ArrayDeque<>();

    SystemNoticeHistoryCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.onIncoming(this::onIncoming);
        chat.addSlashCommands(this::onSlash);
    }

    private void onIncoming(ChatMessageEvent event) {
        if (event.getSenderId() == 0L && event.getSenderName().isEmpty()) {
            remember(lines, ChatController.sanitize(event.getBody()));
        }
    }

    /** Thêm một dòng, quá {@value #CAPACITY} thì bỏ dòng cũ nhất. Tĩnh để test. */
    static void remember(Deque<String> history, String line) {
        history.addLast(line);
        while (history.size() > CAPACITY) {
            history.removeFirst();
        }
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/lichsuthongbao".equals(command) && !"/systemlog".equals(command)) {
            return false;
        }
        if (lines.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "systemlog.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "systemlog.header", lines.size()));
        for (String notice : lines) {
            chat.showSystemNotice("· " + notice);
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
