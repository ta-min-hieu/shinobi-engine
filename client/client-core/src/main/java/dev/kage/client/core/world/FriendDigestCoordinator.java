package dev.kage.client.core.world;

import java.util.Locale;
import java.util.stream.Collectors;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetAbsentFriendsRequest;
import dev.kage.protocol.GetAbsentFriendsResponse;
import dev.kage.protocol.GetNewPlayersRequest;
import dev.kage.protocol.GetNewPlayersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hai lệnh hỏi server lô 61: bạn bè lâu vắng (ADR-416, {@code /banlauvang} hoặc {@code /absentfriends}), tân thủ hôm
 * nay (ADR-417, {@code /tanthu} hoặc {@code /newplayers}). Tóm tắt online (ADR-415) và nhắc hộp thư (ADR-418) là dòng
 * hệ thống server tự gửi lúc vào world.
 */
final class FriendDigestCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendDigestCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        switch (command) {
            case "/banlauvang", "/absentfriends" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_ABSENT_FRIENDS_REQUEST_VALUE, GetAbsentFriendsRequest.getDefaultInstance());
            case "/tanthu", "/newplayers" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_NEW_PLAYERS_REQUEST_VALUE, GetNewPlayersRequest.getDefaultInstance());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_ABSENT_FRIENDS_RESPONSE_VALUE) {
            chat.showSystemNotice(describeAbsent(i18n, GetAbsentFriendsResponse.parseFrom(frame.payload())));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_NEW_PLAYERS_RESPONSE_VALUE) {
            GetNewPlayersResponse response = GetNewPlayersResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    response.getCount() == 0
                            ? KageI18n.textOrKey(i18n, "newplayers.none")
                            : KageI18n.formatOrKey(
                                    i18n,
                                    "newplayers.line",
                                    response.getCount(),
                                    String.join(", ", response.getNewestList())));
            return true;
        }
        return false;
    }

    /** Một dòng bạn lâu vắng. Tĩnh để test. */
    static String describeAbsent(I18NBundle i18n, GetAbsentFriendsResponse response) {
        if (response.getEntriesCount() == 0) {
            return KageI18n.textOrKey(i18n, "absentfriends.none");
        }
        String list = response.getEntriesList().stream()
                .map(entry -> KageI18n.formatOrKey(i18n, "absentfriends.entry", entry.getName(), entry.getDays()))
                .collect(Collectors.joining(", "));
        return KageI18n.formatOrKey(i18n, "absentfriends.line", response.getEntriesCount(), list);
    }
}
