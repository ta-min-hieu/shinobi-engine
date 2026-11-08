package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FriendRankEntry;
import dev.kage.protocol.GetFriendRankingRequest;
import dev.kage.protocol.GetFriendRankingResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Xếp hạng bạn bè (ADR-258) — {@code /xephangban} (hoặc {@code /friendrank}): mình + bạn bè theo Lực Chiến. */
final class FriendRankingCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/xephangban";
    private static final String COMMAND_EN = "/friendrank";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendRankingCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_FRIEND_RANKING_REQUEST_VALUE, GetFriendRankingRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_FRIEND_RANKING_RESPONSE_VALUE) {
            return false;
        }
        GetFriendRankingResponse response = GetFriendRankingResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "friendrank.header"));
        int rank = 0;
        for (FriendRankEntry entry : response.getEntriesList()) {
            rank++;
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    entry.getIsSelf() ? "friendrank.line.self" : "friendrank.line",
                    rank,
                    entry.getName(),
                    entry.getLevel(),
                    entry.getCombatPower()));
        }
        return true;
    }
}
