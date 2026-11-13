package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendFriendsMailCommand;
import dev.kage.protocol.SendFriendsMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thư gửi mọi bạn bè (ADR-312) — {@code /thubanbe <tiêu đề> | <nội dung>} (hoặc {@code /friendmail}): một thư chữ tới
 * toàn bộ bạn bè, một đợt mỗi ngày (server quyết).
 */
final class FriendsMailCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendsMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/thubanbe".equals(command) && !"/friendmail".equals(command)) {
            return false;
        }
        String args = words.length > 1 ? words[1] : "";
        int bar = args.indexOf('|');
        String subject = (bar < 0 ? args : args.substring(0, bar)).strip();
        if (subject.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "friendmail.usage"));
            return true;
        }
        String body = bar < 0 ? "" : args.substring(bar + 1).strip();
        sender.sendReadRequest(
                Opcode.OPCODE_SEND_FRIENDS_MAIL_COMMAND_VALUE,
                SendFriendsMailCommand.newBuilder()
                        .setSubject(subject)
                        .setBody(body)
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SEND_FRIENDS_MAIL_RESPONSE_VALUE) {
            return false;
        }
        SendFriendsMailResponse response = SendFriendsMailResponse.parseFrom(frame.payload());
        String key = "friendmail."
                + response.getOutcome()
                        .name()
                        .replace("SEND_FRIENDS_MAIL_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getRecipients()));
        return true;
    }
}
