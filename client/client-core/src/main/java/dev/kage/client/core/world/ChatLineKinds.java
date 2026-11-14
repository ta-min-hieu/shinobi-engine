package dev.kage.client.core.world;

import dev.kage.client.core.ui.hud.ChatPanel;

/** Thứ tự màu dòng chat: tin của mình (ADR-436) > nhắc tên / từ khoá (ADR-245/429) > bạn bè (ADR-364) > kênh. */
final class ChatLineKinds {

    private ChatLineKinds() {}

    static ChatPanel.LineKind classify(
            boolean own, boolean mentioned, boolean friendInPublic, ChatPanel.LineKind channelKind) {
        if (own) {
            return ChatPanel.LineKind.SELF;
        }
        if (mentioned) {
            return ChatPanel.LineKind.MENTION;
        }
        return friendInPublic ? ChatPanel.LineKind.FRIEND : channelKind;
    }
}
