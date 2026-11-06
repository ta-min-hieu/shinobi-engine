package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetAwayMessageCommand;
import dev.kage.protocol.SetAwayMessageOutcome;
import dev.kage.protocol.SetAwayMessageResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tự trả lời khi vắng (ADR-178) — lệnh chat {@code /vang <lời nhắn>} (hoặc {@code /away}) treo lời nhắn; {@code /vang}
 * không lời nhắn thì tắt. Server giữ trạng thái và tự trả lời người nhắn riêng; client chỉ gửi ý định và in kết quả.
 */
final class AwayCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/vang";
    private static final String COMMAND_EN = "/away";
    private static final String OUTCOME_PREFIX = "SET_AWAY_MESSAGE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    AwayCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
        sender.sendReadRequest(
                Opcode.OPCODE_SET_AWAY_MESSAGE_COMMAND_VALUE,
                SetAwayMessageCommand.newBuilder()
                        .setMessage(words.length > 1 ? words[1] : "")
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_AWAY_MESSAGE_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(i18n, SetAwayMessageResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, SetAwayMessageResponse response) {
        SetAwayMessageOutcome outcome = response.getOutcome();
        if (outcome == SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_OK) {
            return response.getAway()
                    ? KageI18n.formatOrKey(i18n, "away.on", ChatController.sanitize(response.getMessage()))
                    : KageI18n.textOrKey(i18n, "away.off");
        }
        String suffix = outcome == SetAwayMessageOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "away.outcome." + suffix);
    }
}
