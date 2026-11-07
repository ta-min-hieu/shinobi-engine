package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TransferPartyLeaderCommand;
import dev.kage.protocol.TransferPartyLeaderOutcome;
import dev.kage.protocol.TransferPartyLeaderResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhường trưởng nhóm (ADR-191) — lệnh chat {@code /nhuongnhom <tên thành viên>} (hoặc {@code /partyleader}). Server
 * kiểm trưởng nhóm / cùng nhóm và báo cả nhóm bằng {@code PartyChangedEvent}; client chỉ gửi ý định và in kết quả.
 */
final class PartyLeaderCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/nhuongnhom";
    private static final String COMMAND_EN = "/partyleader";
    private static final String OUTCOME_PREFIX = "TRANSFER_PARTY_LEADER_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PartyLeaderCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "party_leader.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_TRANSFER_PARTY_LEADER_COMMAND_VALUE,
                TransferPartyLeaderCommand.newBuilder()
                        .setTargetName(words[1].strip())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_TRANSFER_PARTY_LEADER_RESPONSE_VALUE) {
            return false;
        }
        TransferPartyLeaderResponse response = TransferPartyLeaderResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response)); // cửa sổ nhóm tự hỏi lại khi nhận PartyChangedEvent
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, TransferPartyLeaderResponse response) {
        TransferPartyLeaderOutcome outcome = response.getOutcome();
        String suffix = outcome == TransferPartyLeaderOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n, "party_leader.outcome." + suffix, ChatController.sanitize(response.getTargetName()));
    }
}
