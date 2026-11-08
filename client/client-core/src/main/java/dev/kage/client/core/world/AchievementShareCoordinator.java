package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildAchievementSharedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetAchievementShareCommand;
import dev.kage.protocol.SetAchievementShareResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Khoe thành tựu vào bang (ADR-230) — hiện dòng "X vừa mở thành tựu Y" khi người trong bang mở thành tựu;
 * {@code /khoett bat|tat} (hoặc {@code /shareach on|off}) bật / tắt khoe thành tựu của chính mình.
 */
final class AchievementShareCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    AchievementShareCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/khoett".equals(command) && !"/shareach".equals(command)) {
            return false;
        }
        SetAchievementShareCommand.Builder request = SetAchievementShareCommand.newBuilder();
        String arg = words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "";
        if ("bat".equals(arg) || "on".equals(arg)) {
            request.setEnabled(true);
        } else if ("tat".equals(arg) || "off".equals(arg)) {
            request.setEnabled(false);
        } else {
            request.setQueryOnly(true);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_ACHIEVEMENT_SHARE_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GUILD_ACHIEVEMENT_SHARED_EVENT_VALUE) {
            GuildAchievementSharedEvent event = GuildAchievementSharedEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "achievement_share.shared",
                    ChatController.sanitize(event.getCharacterName()),
                    KageI18n.textOrKey(i18n, event.getAchievementNameKey())));
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_ACHIEVEMENT_SHARE_RESPONSE_VALUE) {
            boolean enabled =
                    SetAchievementShareResponse.parseFrom(frame.payload()).getEnabled();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "achievement_share.on" : "achievement_share.off"));
            return true;
        }
        return false;
    }
}
