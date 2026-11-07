package dev.kage.client.core.world;

import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildCheckInCommand;
import dev.kage.protocol.GuildCheckInOutcome;
import dev.kage.protocol.GuildCheckInResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Điểm danh bang (ADR-186) — lệnh chat {@code /diemdanhbang} (hoặc {@code /guildcheckin}), mỗi ngày một lần. Server
 * cộng cống hiến + kinh nghiệm bang; client chỉ gửi ý định và in kết quả.
 */
final class GuildCheckInCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/diemdanhbang", "/guildcheckin");
    private static final String OUTCOME_PREFIX = "GUILD_CHECK_IN_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildCheckInCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_GUILD_CHECK_IN_COMMAND_VALUE, GuildCheckInCommand.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GUILD_CHECK_IN_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(i18n, GuildCheckInResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GuildCheckInResponse response) {
        GuildCheckInOutcome outcome = response.getOutcome();
        if (outcome == GuildCheckInOutcome.GUILD_CHECK_IN_OUTCOME_OK) {
            return KageI18n.formatOrKey(
                    i18n,
                    "guild_checkin.ok",
                    response.getContribution(),
                    response.getGuildExp(),
                    response.getTodayCount());
        }
        String suffix = outcome == GuildCheckInOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(i18n, "guild_checkin.outcome." + suffix, response.getTodayCount());
    }
}
