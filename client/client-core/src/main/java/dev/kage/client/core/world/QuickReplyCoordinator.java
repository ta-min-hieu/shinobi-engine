package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Trả lời nhanh tin riêng (ADR-247) — nhớ người khác gần nhất đã nhắn riêng cho mình; {@code /r <nội dung>} (hoặc
 * {@code /reply}) gửi một tin riêng thường tới người đó. Server kiểm như mọi tin riêng.
 */
final class QuickReplyCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/r";
    private static final String COMMAND_EN = "/reply";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Optional<String> lastFrom = Optional.empty();

    QuickReplyCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Người được trả lời nếu gõ {@code /r} lúc này. */
    Optional<String> lastFrom() {
        return lastFrom;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (lastFrom.isEmpty() || words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(
                    lastFrom.isEmpty()
                            ? KageI18n.textOrKey(i18n, "reply.nobody")
                            : KageI18n.formatOrKey(i18n, "reply.usage", lastFrom.get()));
            return true;
        }
        sender.sendChatMessage(ChatChannel.CHAT_CHANNEL_PRIVATE, lastFrom.get(), words[1].strip());
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
            lastFrom = Optional.of(event.getSenderName());
        }
        return false;
    }
}
