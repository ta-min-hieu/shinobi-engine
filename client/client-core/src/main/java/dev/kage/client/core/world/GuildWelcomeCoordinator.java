package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildWelcomeCommand;
import dev.kage.protocol.SetGuildWelcomeOutcome;
import dev.kage.protocol.SetGuildWelcomeResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lời chào thành viên mới (ADR-313) — {@code /loichao} xem; {@code /loichao <nội dung>} đặt (bang chủ / phó);
 * {@code /loichao xoa} xoá. Người mới vào bang nhận lời chào qua chat + thư (server làm).
 */
final class GuildWelcomeCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildWelcomeCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/loichao".equals(command) && !"/guildwelcome".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip() : "";
        SetGuildWelcomeCommand.Builder request = SetGuildWelcomeCommand.newBuilder();
        if (arg.isEmpty()) {
            request.setQueryOnly(true);
        } else {
            request.setMessage(ChatLayoutCoordinator.isOff(arg) || "xoa".equals(MentionWatch.fold(arg)) ? "" : arg);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_GUILD_WELCOME_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_GUILD_WELCOME_RESPONSE_VALUE) {
            return false;
        }
        SetGuildWelcomeResponse response = SetGuildWelcomeResponse.parseFrom(frame.payload());
        if (response.getOutcome() == SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_OK) {
            chat.showSystemNotice(
                    response.getMessage().isEmpty()
                            ? KageI18n.textOrKey(i18n, "guildwelcome.none")
                            : KageI18n.formatOrKey(i18n, "guildwelcome.current", response.getMessage()));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(
                i18n,
                "guildwelcome."
                        + response.getOutcome()
                                .name()
                                .replace("SET_GUILD_WELCOME_OUTCOME_", "")
                                .toLowerCase(Locale.ROOT)));
        return true;
    }
}
