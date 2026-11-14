package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.OfficerChatCommand;
import dev.kage.protocol.OfficerChatOutcome;
import dev.kage.protocol.OfficerChatResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kênh chat lãnh đạo bang (ADR-367) — {@code /lanhdao <tin>} (hoặc {@code /officer}): tin chỉ tới bang chủ và phó bang
 * chủ đang online. Tin nhận được (kênh bang, cờ {@code officer_only}) mang tiền tố "[Lãnh đạo]".
 */
final class OfficerChatCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    OfficerChatCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.useEventRewriter(this::markOfficer);
        chat.addSlashCommands(this::onSlash);
    }

    private ChatMessageEvent markOfficer(ChatMessageEvent event) {
        if (!event.getOfficerOnly()) {
            return event;
        }
        return event.toBuilder()
                .setBody(KageI18n.textOrKey(i18n, "officer.prefix") + " " + event.getBody())
                .build();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/lanhdao".equals(command) && !"/officer".equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "officer.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_OFFICER_CHAT_COMMAND_VALUE,
                OfficerChatCommand.newBuilder().setBody(words[1].strip()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_OFFICER_CHAT_RESPONSE_VALUE) {
            return false;
        }
        OfficerChatOutcome outcome =
                OfficerChatResponse.parseFrom(frame.payload()).getOutcome();
        if (outcome != OfficerChatOutcome.OFFICER_CHAT_OUTCOME_OK) { // OK: tin tự hiện qua ChatMessageEvent
            chat.showSystemNotice(KageI18n.textOrKey(
                    i18n,
                    "officer."
                            + outcome.name()
                                    .replace("OFFICER_CHAT_OUTCOME_", "")
                                    .toLowerCase(Locale.ROOT)));
        }
        return true;
    }
}
