package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetGuildNameHistoryRequest;
import dev.kage.protocol.GetGuildNameHistoryResponse;
import dev.kage.protocol.GuildRename;
import dev.kage.protocol.InviteOnlineFriendsCommand;
import dev.kage.protocol.InviteOnlineFriendsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetEnemyAlertCommand;
import dev.kage.protocol.SetEnemyAlertResponse;
import dev.kage.protocol.SetLoginGreetingCommand;
import dev.kage.protocol.SetLoginGreetingResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn lệnh hỏi server lô 62: lịch sử tên bang (ADR-423, {@code /lichsutenbang [tên]} hoặc {@code /guildnames}), mời
 * bạn online vào nhóm (ADR-424, {@code /moibanbe} hoặc {@code /invitefriends}), câu chào khi vào game (ADR-425, {@code
 * /cauchao <câu>|xoa} hoặc {@code /greeting}; bỏ trống ⇒ hỏi), báo cừu nhân online (ADR-426, {@code /baocuunhan
 * [bat|tat]} hoặc {@code /enemyalert}).
 */
final class SocialExtrasCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    SocialExtrasCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        switch (command) {
            case "/lichsutenbang", "/guildnames" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_GUILD_NAME_HISTORY_REQUEST_VALUE,
                        GetGuildNameHistoryRequest.newBuilder()
                                .setGuildName(arg)
                                .build());
            case "/moibanbe", "/invitefriends" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_INVITE_ONLINE_FRIENDS_COMMAND_VALUE,
                        InviteOnlineFriendsCommand.getDefaultInstance());
            case "/cauchao", "/greeting" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_SET_LOGIN_GREETING_COMMAND_VALUE,
                        SetLoginGreetingCommand.newBuilder()
                                .setQueryOnly(arg.isEmpty())
                                .setMessage(isClear(arg) ? "" : arg)
                                .build());
            case "/baocuunhan", "/enemyalert" -> {
                String mode = arg.toLowerCase(Locale.ROOT);
                sender.sendReadRequest(
                        Opcode.OPCODE_SET_ENEMY_ALERT_COMMAND_VALUE,
                        SetEnemyAlertCommand.newBuilder()
                                .setEnabled("bat".equals(mode) || "on".equals(mode))
                                .setQueryOnly(mode.isEmpty())
                                .build());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** {@code xoa}/{@code clear} ⇒ xoá câu chào. Tĩnh để test. */
    static boolean isClear(String arg) {
        return "xoa".equalsIgnoreCase(arg) || "clear".equalsIgnoreCase(arg);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_GUILD_NAME_HISTORY_RESPONSE_VALUE) {
            showNames(GetGuildNameHistoryResponse.parseFrom(frame.payload()));
        } else if (opcode == Opcode.OPCODE_INVITE_ONLINE_FRIENDS_RESPONSE_VALUE) {
            InviteOnlineFriendsResponse response = InviteOnlineFriendsResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    response.getNotLeader()
                            ? KageI18n.textOrKey(i18n, "invitefriends.not_leader")
                            : KageI18n.formatOrKey(i18n, "invitefriends.done", response.getInvited()));
        } else if (opcode == Opcode.OPCODE_SET_LOGIN_GREETING_RESPONSE_VALUE) {
            SetLoginGreetingResponse response = SetLoginGreetingResponse.parseFrom(frame.payload());
            chat.showSystemNotice(greetingText(response));
        } else if (opcode == Opcode.OPCODE_SET_ENEMY_ALERT_RESPONSE_VALUE) {
            boolean on = SetEnemyAlertResponse.parseFrom(frame.payload()).getEnabled();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, on ? "enemyalert.on" : "enemyalert.off"));
        } else {
            return false;
        }
        return true;
    }

    private String greetingText(SetLoginGreetingResponse response) {
        if (!response.getAccepted()) {
            return KageI18n.textOrKey(i18n, "greeting.invalid");
        }
        return response.getMessage().isEmpty()
                ? KageI18n.textOrKey(i18n, "greeting.none")
                : KageI18n.formatOrKey(i18n, "greeting.current", response.getMessage());
    }

    private void showNames(GetGuildNameHistoryResponse response) {
        if (!response.getFound()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildnames.not_found"));
            return;
        }
        if (response.getRenamesCount() == 0) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "guildnames.none", response.getCurrentName()));
            return;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "guildnames.header", response.getCurrentName()));
        for (GuildRename rename : response.getRenamesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "guildnames.line",
                    rename.getOldName(),
                    rename.getNewName(),
                    DATE.format(
                            Instant.ofEpochSecond(rename.getAtEpochSeconds()).atZone(ZoneId.systemDefault()))));
        }
    }
}
