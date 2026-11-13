package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CastVoteKickCommand;
import dev.kage.protocol.CastVoteKickResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StartVoteKickCommand;
import dev.kage.protocol.StartVoteKickResponse;
import dev.kage.protocol.VoteKickEvent;
import dev.kage.protocol.VoteKickOutcome;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Biểu quyết đuổi khỏi nhóm (ADR-338) — {@code /duoi <tên>} (hoặc {@code /votekick}) khởi xướng; thành viên khác thấy
 * một dòng mời bỏ phiếu {@code /bophieu co|khong} (hoặc {@code /vote yes|no}). Quá nửa đồng ý trong 60 giây thì người
 * đó rời nhóm. Server đếm phiếu.
 */
final class VoteKickCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    VoteKickCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        if ("/duoi".equals(command) || "/votekick".equals(command)) {
            start(arg);
            return true;
        }
        if ("/bophieu".equals(command) || "/vote".equals(command)) {
            cast(MentionWatch.fold(arg));
            return true;
        }
        return false;
    }

    private void start(String targetName) {
        if (targetName.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "votekick.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_START_VOTE_KICK_COMMAND_VALUE,
                StartVoteKickCommand.newBuilder().setTargetName(targetName).build());
    }

    private void cast(String folded) {
        boolean yes = "co".equals(folded) || "yes".equals(folded);
        if (!yes && !"khong".equals(folded) && !"no".equals(folded)) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "votekick.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CAST_VOTE_KICK_COMMAND_VALUE,
                CastVoteKickCommand.newBuilder().setYes(yes).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_START_VOTE_KICK_RESPONSE_VALUE) {
            StartVoteKickResponse response = StartVoteKickResponse.parseFrom(frame.payload());
            outcome(response.getOutcome(), response.getYes(), response.getNeeded());
            return true;
        }
        if (opcode == Opcode.OPCODE_CAST_VOTE_KICK_RESPONSE_VALUE) {
            CastVoteKickResponse response = CastVoteKickResponse.parseFrom(frame.payload());
            outcome(response.getOutcome(), response.getYes(), response.getNeeded());
            return true;
        }
        if (opcode != Opcode.OPCODE_VOTE_KICK_EVENT_VALUE) {
            return false;
        }
        VoteKickEvent event = VoteKickEvent.parseFrom(frame.payload());
        chat.showSystemNotice(
                event.getPassed()
                        ? KageI18n.formatOrKey(i18n, "votekick.passed_event", event.getTargetName())
                        : KageI18n.formatOrKey(
                                i18n,
                                "votekick.event",
                                event.getInitiatorName(),
                                event.getTargetName(),
                                event.getYes(),
                                event.getNeeded(),
                                event.getSecondsLeft()));
        return true;
    }

    private void outcome(VoteKickOutcome outcome, int yes, int needed) {
        String key =
                "votekick." + outcome.name().replace("VOTE_KICK_OUTCOME_", "").toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, yes, needed));
    }
}
