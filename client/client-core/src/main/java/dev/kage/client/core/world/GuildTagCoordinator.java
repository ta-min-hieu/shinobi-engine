package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildTagCommand;
import dev.kage.protocol.SetGuildTagResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ký hiệu bang (ADR-344) — {@code /kyhieubang} xem, {@code /kyhieubang <2–4 ký tự>} đặt, {@code /kyhieubang xoa} xoá
 * (hoặc {@code /guildtag}); chỉ bang chủ đặt. Ký hiệu hiện trước tên thành viên ở chat thế giới và khi bị soi.
 */
final class GuildTagCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildTagCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/kyhieubang".equals(command) && !"/guildtag".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip() : "";
        SetGuildTagCommand.Builder request = SetGuildTagCommand.newBuilder();
        String folded = MentionWatch.fold(arg);
        if (arg.isEmpty()) {
            request.setQueryOnly(true);
        } else if (!"xoa".equals(folded) && !"clear".equals(folded)) {
            request.setTag(arg);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_GUILD_TAG_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_GUILD_TAG_RESPONSE_VALUE) {
            return false;
        }
        SetGuildTagResponse response = SetGuildTagResponse.parseFrom(frame.payload());
        String key = "guildtag."
                + response.getOutcome()
                        .name()
                        .replace("SET_GUILD_TAG_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        if ("guildtag.ok".equals(key) && response.getTag().isEmpty()) {
            key = "guildtag.none";
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getTag()));
        return true;
    }
}
