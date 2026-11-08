package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendGuildMailCommand;
import dev.kage.protocol.SendGuildMailOutcome;
import dev.kage.protocol.SendGuildMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thư toàn bang (ADR-235) — bang chủ / phó gõ {@code /thubang <tiêu đề> | <nội dung>} (hoặc {@code /guildmail}): mỗi
 * thành viên nhận một thư.
 */
final class GuildMailCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/thubang";
    private static final String COMMAND_EN = "/guildmail";
    private static final String OUTCOME_PREFIX = "SEND_GUILD_MAIL_OUTCOME_";

    /** Tiêu đề + nội dung đã tách. */
    record Draft(String subject, String body) {}

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Tách {@code tiêu đề | nội dung}; thiếu một trong hai ⇒ rỗng. Tĩnh để test. */
    static Optional<Draft> parse(String argument) {
        int bar = argument.indexOf('|');
        if (bar < 0) {
            return Optional.empty();
        }
        String subject = argument.substring(0, bar).strip();
        String body = argument.substring(bar + 1).strip();
        return subject.isEmpty() || body.isEmpty() ? Optional.empty() : Optional.of(new Draft(subject, body));
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Draft> draft = parse(words.length > 1 ? words[1] : "");
        if (draft.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildmail.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SEND_GUILD_MAIL_COMMAND_VALUE,
                SendGuildMailCommand.newBuilder()
                        .setSubject(draft.get().subject())
                        .setBody(draft.get().body())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SEND_GUILD_MAIL_RESPONSE_VALUE) {
            return false;
        }
        SendGuildMailResponse response = SendGuildMailResponse.parseFrom(frame.payload());
        SendGuildMailOutcome outcome = response.getOutcome();
        String key = outcome == SendGuildMailOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(
                KageI18n.formatOrKey(i18n, "guildmail." + key, response.getRecipients(), response.getRemainingToday()));
        return true;
    }
}
