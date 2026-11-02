package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BroadcastMessageCommand;
import dev.kage.protocol.BroadcastMessageOutcome;
import dev.kage.protocol.BroadcastMessageResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Truyền Âm Phù (ADR-152) — lệnh chat {@code /loa <nội dung>} (hoặc {@code /shout}). Server kiểm hồi chiêu, khoá chat,
 * phù trong túi và nội dung; dòng loa tới mọi người qua {@code ChatMessageEvent} kênh BROADCAST (ChatCoordinator vẽ).
 * Ở đây chỉ gửi ý định và báo lý do khi bị từ chối.
 */
final class BroadcastCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/loa";
    private static final String COMMAND_EN = "/shout";
    private static final String OUTCOME_PREFIX = "BROADCAST_MESSAGE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BroadcastCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        String body = words.length > 1 ? words[1].strip() : "";
        if (body.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "broadcast.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BROADCAST_MESSAGE_COMMAND_VALUE,
                BroadcastMessageCommand.newBuilder().setBody(body).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_BROADCAST_MESSAGE_RESPONSE_VALUE) {
            return false;
        }
        BroadcastMessageResponse response = BroadcastMessageResponse.parseFrom(frame.payload());
        BroadcastMessageOutcome outcome = response.getOutcome();
        if (outcome == BroadcastMessageOutcome.BROADCAST_MESSAGE_OUTCOME_OK) {
            sender.listInventory(); // vừa tiêu một Truyền Âm Phù; dòng loa tự tới qua kênh chat
            return true;
        }
        String suffix = outcome == BroadcastMessageOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "broadcast.outcome." + suffix, response.getSecondsLeft()));
        return true;
    }
}
