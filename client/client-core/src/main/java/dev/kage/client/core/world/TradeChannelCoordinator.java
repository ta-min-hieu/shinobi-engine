package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;

/**
 * Kênh chat giao dịch (ADR-222) — gửi bằng {@code /gd <nội dung>} (hoặc {@code /trade}, hoặc xoay kênh tới "Giao
 * dịch"); {@code /kenhgd tat|bat} (hoặc {@code /tradechat off|on}) ẩn / hiện tin kênh giao dịch trong khung chat của
 * mình.
 */
final class TradeChannelCoordinator implements WindowFlow {

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    TradeChannelCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/kenhgd".equals(command) && !"/tradechat".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "";
        if ("tat".equals(arg) || "off".equals(arg)) {
            chat.setChannelVisible(ChatChannel.CHAT_CHANNEL_TRADE, false);
        } else if ("bat".equals(arg) || "on".equals(arg)) {
            chat.setChannelVisible(ChatChannel.CHAT_CHANNEL_TRADE, true);
        }
        chat.showSystemNotice(KageI18n.textOrKey(
                i18n,
                chat.isChannelVisible(ChatChannel.CHAT_CHANNEL_TRADE) ? "trade_chat.shown" : "trade_chat.hidden"));
        return true;
    }

    @Override
    public boolean dispatch(dev.kage.protocol.wire.PacketCodec.Frame frame) {
        return false;
    }
}
