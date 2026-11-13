package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FriendRemovedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Báo khi bị xoá khỏi danh sách bạn bè (ADR-345) — một dòng hệ thống rồi hỏi lại danh sách bạn bè. */
final class FriendRemovedCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendRemovedCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_FRIEND_REMOVED_EVENT_VALUE) {
            return false;
        }
        FriendRemovedEvent event = FriendRemovedEvent.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "friendremoved.notice", event.getName()));
        sender.listFriends();
        return true;
    }
}
