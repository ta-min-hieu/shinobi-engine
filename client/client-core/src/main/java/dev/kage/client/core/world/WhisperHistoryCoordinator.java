package dev.kage.client.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử tin riêng (ADR-284) — {@code /tinrieng <tên>} (hoặc {@code /whispers}): các tin riêng đã trao đổi với người
 * đó trong phiên (tối đa {@value #MAX_PER_PERSON} dòng mỗi người, mới nhất cuối). Tin nhận lấy từ gói chat; tin gửi lấy
 * từ đường gửi chat của ô nhập ({@code /w}). Chỉ RAM — thoát game là mất.
 */
final class WhisperHistoryCoordinator implements WindowFlow {

    static final int MAX_PER_PERSON = 30;

    /** Một dòng: chiều + nội dung. */
    record Line(boolean incoming, String body) {}

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Map<String, Deque<Line>> byPerson = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();

    WhisperHistoryCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
        chat.onOutgoing(send -> {
            if (send.channel() == ChatChannel.CHAT_CHANNEL_PRIVATE) {
                remember(send.targetName(), new Line(false, send.body()));
            }
        });
    }

    /** Ghi một dòng cho {@code name}. */
    void remember(String name, Line line) {
        String key = MentionWatch.fold(name.strip());
        displayNames.putIfAbsent(key, name.strip());
        Deque<Line> lines = byPerson.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (lines.size() >= MAX_PER_PERSON) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    /** Các dòng với {@code name} (không dấu, không phân biệt hoa thường). */
    Deque<Line> linesWith(String name) {
        return byPerson.getOrDefault(MentionWatch.fold(name.strip()), new ArrayDeque<>());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/tinrieng".equals(command) && !"/whispers".equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(
                    byPerson.isEmpty()
                            ? KageI18n.textOrKey(i18n, "whispers.usage")
                            : KageI18n.formatOrKey(i18n, "whispers.people", String.join(", ", displayNames.values())));
            return true;
        }
        Deque<Line> lines = linesWith(words[1]);
        if (lines.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "whispers.none", words[1].strip()));
            return true;
        }
        String name = displayNames.getOrDefault(MentionWatch.fold(words[1].strip()), words[1].strip());
        for (Line entry : lines) {
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, entry.incoming() ? "whispers.in" : "whispers.out", name, entry.body()));
        }
        return true;
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
            remember(event.getSenderName(), new Line(true, event.getBody()));
        }
        return false;
    }
}
