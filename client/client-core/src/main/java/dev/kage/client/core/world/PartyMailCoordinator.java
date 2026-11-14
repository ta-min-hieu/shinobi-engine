package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetPeakOnlineRequest;
import dev.kage.protocol.GetPeakOnlineResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendPartyMailCommand;
import dev.kage.protocol.SendPartyMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hai lệnh hỏi server lô 56: thư cả nhóm (ADR-375, {@code /thunhom <tiêu đề> | <nội dung>} hoặc {@code /partymail})
 * và đỉnh online hôm nay (ADR-377, {@code /onlinecao} hoặc {@code /peakonline}).
 */
final class PartyMailCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PartyMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if ("/onlinecao".equals(command) || "/peakonline".equals(command)) {
            sender.sendReadRequest(
                    Opcode.OPCODE_GET_PEAK_ONLINE_REQUEST_VALUE, GetPeakOnlineRequest.getDefaultInstance());
            return true;
        }
        if (!"/thunhom".equals(command) && !"/partymail".equals(command)) {
            return false;
        }
        String args = words.length > 1 ? words[1] : "";
        int bar = args.indexOf('|');
        String subject = (bar < 0 ? args : args.substring(0, bar)).strip();
        if (subject.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "partymail.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SEND_PARTY_MAIL_COMMAND_VALUE,
                SendPartyMailCommand.newBuilder()
                        .setSubject(subject)
                        .setBody(bar < 0 ? "" : args.substring(bar + 1).strip())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_PEAK_ONLINE_RESPONSE_VALUE) {
            GetPeakOnlineResponse response = GetPeakOnlineResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "peak_online.line", response.getPeak(), response.getCurrent()));
            return true;
        }
        if (opcode != Opcode.OPCODE_SEND_PARTY_MAIL_RESPONSE_VALUE) {
            return false;
        }
        SendPartyMailResponse response = SendPartyMailResponse.parseFrom(frame.payload());
        String key = "partymail."
                + response.getOutcome()
                        .name()
                        .replace("SEND_PARTY_MAIL_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getRecipients()));
        return true;
    }
}
