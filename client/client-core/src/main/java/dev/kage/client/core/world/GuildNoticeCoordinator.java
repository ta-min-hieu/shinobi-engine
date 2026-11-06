package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildNoticeCommand;
import dev.kage.protocol.SetGuildNoticeOutcome;
import dev.kage.protocol.SetGuildNoticeResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thông báo bang (ADR-179) — bang chủ gõ {@code /thongbaobang <nội dung>} (hoặc {@code /guildnotice}; trống = xoá).
 * Thành viên thấy thông báo một dòng trong chat lúc vào game và mỗi khi nó đổi (đọc từ {@code GetMyGuildResponse} —
 * mọi lần hỏi lại bang đều mang theo), và luôn thấy ở đầu khối tiến độ của cửa sổ bang.
 */
final class GuildNoticeCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/thongbaobang";
    private static final String COMMAND_EN = "/guildnotice";
    private static final String OUTCOME_PREFIX = "SET_GUILD_NOTICE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    /** Thông báo đã in ra chat lần gần nhất — chỉ in lại khi nó đổi. */
    private String lastShown = "";

    GuildNoticeCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Lúc vào game: hỏi bang của mình một lần để thấy thông báo ngay. */
    void requestOnEnter() {
        sender.requestMyGuild();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_GUILD_NOTICE_COMMAND_VALUE,
                SetGuildNoticeCommand.newBuilder()
                        .setNotice(words.length > 1 ? words[1] : "")
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE) {
            // Không "nuốt" gói: GuildCoordinator cũng đọc nó (PanelHub hỏi mọi luồng).
            onMyGuild(GetMyGuildResponse.parseFrom(frame.payload()));
            return false;
        }
        if (opcode != Opcode.OPCODE_SET_GUILD_NOTICE_RESPONSE_VALUE) {
            return false;
        }
        SetGuildNoticeResponse response = SetGuildNoticeResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getOutcome() == SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_OK) {
            lastShown = ChatController.sanitize(response.getNotice()); // chính mình vừa ghi — khỏi in lại
        }
        return true;
    }

    private void onMyGuild(GetMyGuildResponse response) {
        String notice = response.getInGuild() ? ChatController.sanitize(response.getNotice()) : "";
        if (!notice.isEmpty() && !notice.equals(lastShown)) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "guild.notice_line", notice));
        }
        lastShown = notice;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, SetGuildNoticeResponse response) {
        SetGuildNoticeOutcome outcome = response.getOutcome();
        if (outcome == SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_OK) {
            return response.getNotice().isEmpty()
                    ? KageI18n.textOrKey(i18n, "guild_notice.cleared")
                    : KageI18n.formatOrKey(i18n, "guild_notice.ok", ChatController.sanitize(response.getNotice()));
        }
        String suffix = outcome == SetGuildNoticeOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "guild_notice.outcome." + suffix);
    }
}
