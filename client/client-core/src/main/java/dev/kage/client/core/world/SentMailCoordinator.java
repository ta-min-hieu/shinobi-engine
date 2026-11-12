package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListSentMailRequest;
import dev.kage.protocol.ListSentMailResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SentMailEntry;
import dev.kage.protocol.wire.PacketCodec;

/** Thư đã gửi (ADR-290) — {@code /thudagui} (hoặc {@code /sentmail}): 20 thư gần nhất mình đã gửi, đã lĩnh chưa. */
final class SentMailCoordinator implements WindowFlow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    SentMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/thudagui".equals(command) && !"/sentmail".equals(command)) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_LIST_SENT_MAIL_REQUEST_VALUE, ListSentMailRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LIST_SENT_MAIL_RESPONSE_VALUE) {
            return false;
        }
        ListSentMailResponse response = ListSentMailResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "sentmail.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "sentmail.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (SentMailEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    entry.getClaimed() ? "sentmail.line.claimed" : "sentmail.line",
                    TIME.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                    entry.getRecipientName(),
                    entry.getSubject(),
                    entry.getGoldAmount(),
                    entry.getAttachments()));
        }
        return true;
    }
}
