package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RegisterTournamentCommand;
import dev.kage.protocol.RegisterTournamentResponse;
import dev.kage.protocol.TournamentNoticeEvent;
import dev.kage.protocol.TournamentNoticeKind;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Luận võ đại hội (ADR-125) — lệnh chat {@code /luanvo} đăng ký giải hôm nay; ghép cặp và tân vô địch hiện thành dòng
 * hệ thống. Trận đấu diễn ra ở World đấu trường như thách đấu thường (ADR-051) — server tự mở trận.
 */
final class TournamentCoordinator implements WindowFlow {

    private static final String COMMAND = "/luanvo";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    TournamentCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_REGISTER_TOURNAMENT_COMMAND_VALUE, RegisterTournamentCommand.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_REGISTER_TOURNAMENT_RESPONSE_VALUE) {
            String suffix = RegisterTournamentResponse.parseFrom(frame.payload())
                    .getOutcome()
                    .name()
                    .substring("TOURNAMENT_REGISTER_OUTCOME_".length())
                    .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "tournament.register." + suffix));
            return true;
        }
        if (opcode == Opcode.OPCODE_TOURNAMENT_NOTICE_EVENT_VALUE) {
            TournamentNoticeEvent event = TournamentNoticeEvent.parseFrom(frame.payload());
            if (event.getKind() == TournamentNoticeKind.TOURNAMENT_NOTICE_KIND_CHAMPION) {
                chat.showSystemNotice(KageI18n.formatOrKey(i18n, "tournament.notice.champion", event.getName()));
            } else if (event.getName().isEmpty()) {
                chat.showSystemNotice(KageI18n.formatOrKey(i18n, "tournament.notice.bye", event.getRound()));
            } else {
                chat.showSystemNotice(
                        KageI18n.formatOrKey(i18n, "tournament.notice.match", event.getRound(), event.getName()));
            }
            return true;
        }
        return false;
    }
}
