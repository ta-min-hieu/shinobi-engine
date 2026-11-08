package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ActivateGuildBuffCommand;
import dev.kage.protocol.ActivateGuildBuffOutcome;
import dev.kage.protocol.ActivateGuildBuffResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Buff bang (ADR-239) — bang chủ gõ {@code /buffbang} (hoặc {@code /guildbuff}) để tiêu kinh nghiệm bang bật +EXP cho
 * cả bang 1 giờ; {@code /buffbang ?} cho mọi thành viên xem còn bao lâu.
 */
final class GuildBuffCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/buffbang";
    private static final String COMMAND_EN = "/guildbuff";
    private static final String OUTCOME_PREFIX = "ACTIVATE_GUILD_BUFF_OUTCOME_";
    private static final long SECONDS_PER_MINUTE = 60L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildBuffCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        boolean query = words.length > 1 && "?".equals(words[1].strip());
        sender.sendReadRequest(
                Opcode.OPCODE_ACTIVATE_GUILD_BUFF_COMMAND_VALUE,
                ActivateGuildBuffCommand.newBuilder().setQueryOnly(query).build());
        return true;
    }

    /** Phút còn lại, làm tròn lên (0 giây ⇒ 0). Tĩnh để test. */
    static long minutesLeft(long seconds) {
        return (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_ACTIVATE_GUILD_BUFF_RESPONSE_VALUE) {
            return false;
        }
        ActivateGuildBuffResponse response = ActivateGuildBuffResponse.parseFrom(frame.payload());
        ActivateGuildBuffOutcome outcome = response.getOutcome();
        String key = outcome == ActivateGuildBuffOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        if (outcome == ActivateGuildBuffOutcome.ACTIVATE_GUILD_BUFF_OUTCOME_STATUS && response.getSecondsLeft() == 0) {
            key = "none";
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "guildbuff." + key,
                response.getPercent(),
                minutesLeft(response.getSecondsLeft()),
                response.getCost(),
                response.getGuildExpLeft()));
        return true;
    }
}
