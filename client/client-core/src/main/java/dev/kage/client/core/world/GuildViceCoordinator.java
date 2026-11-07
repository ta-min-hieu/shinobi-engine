package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildViceLeaderCommand;
import dev.kage.protocol.SetGuildViceLeaderOutcome;
import dev.kage.protocol.SetGuildViceLeaderResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Phó bang chủ (ADR-184) — bang chủ gõ {@code /phobang <tên>} để bổ nhiệm, {@code /bophobang <tên>} để bãi nhiệm (hoặc
 * {@code /vice}, {@code /unvice}). Server kiểm bang chủ / cùng bang / trần 2 phó và báo cả bang.
 */
final class GuildViceCoordinator implements WindowFlow {

    private static final String OUTCOME_PREFIX = "SET_GUILD_VICE_LEADER_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildViceCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        boolean appoint = "/phobang".equals(command) || "/vice".equals(command);
        boolean dismiss = "/bophobang".equals(command) || "/unvice".equals(command);
        if (!appoint && !dismiss) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_vice.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_COMMAND_VALUE,
                SetGuildViceLeaderCommand.newBuilder()
                        .setTargetName(words[1].strip())
                        .setAppoint(appoint)
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_GUILD_VICE_LEADER_RESPONSE_VALUE) {
            return false;
        }
        SetGuildViceLeaderResponse response = SetGuildViceLeaderResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getOutcome() == SetGuildViceLeaderOutcome.SET_GUILD_VICE_LEADER_OUTCOME_OK) {
            sender.requestGuildMembers(); // nhãn "Phó bang chủ" trong danh sách thành viên
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, SetGuildViceLeaderResponse response) {
        SetGuildViceLeaderOutcome outcome = response.getOutcome();
        String name = ChatController.sanitize(response.getTargetName());
        if (outcome == SetGuildViceLeaderOutcome.SET_GUILD_VICE_LEADER_OUTCOME_OK) {
            return KageI18n.formatOrKey(
                    i18n, response.getAppoint() ? "guild_vice.appointed" : "guild_vice.dismissed", name);
        }
        String suffix = outcome == SetGuildViceLeaderOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(i18n, "guild_vice.outcome." + suffix, name);
    }
}
