package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderOutcome;
import dev.kage.protocol.TransferGuildLeaderResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhường chức bang chủ (ADR-183) — lệnh chat {@code /nhuongbang <tên thành viên>} (hoặc {@code /guildleader}). Server
 * kiểm bang chủ / cùng bang và báo cả bang; client chỉ gửi ý định, in kết quả và hỏi lại bang.
 */
final class GuildLeadershipCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/nhuongbang";
    private static final String COMMAND_EN = "/guildleader";
    private static final String OUTCOME_PREFIX = "TRANSFER_GUILD_LEADER_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildLeadershipCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_leader.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_COMMAND_VALUE,
                TransferGuildLeaderCommand.newBuilder()
                        .setTargetName(words[1].strip())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_TRANSFER_GUILD_LEADER_RESPONSE_VALUE) {
            return false;
        }
        TransferGuildLeaderResponse response = TransferGuildLeaderResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getOutcome() == TransferGuildLeaderOutcome.TRANSFER_GUILD_LEADER_OUTCOME_OK) {
            sender.requestMyGuild(); // mình vừa thành thành viên thường — cửa sổ bang đổi nút
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, TransferGuildLeaderResponse response) {
        TransferGuildLeaderOutcome outcome = response.getOutcome();
        String suffix = outcome == TransferGuildLeaderOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n, "guild_leader.outcome." + suffix, ChatController.sanitize(response.getTargetName()));
    }
}
