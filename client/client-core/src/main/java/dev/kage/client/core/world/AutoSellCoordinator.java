package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetAutoSellJunkCommand;
import dev.kage.protocol.SetAutoSellJunkResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lọc nhặt đồ (ADR-204) — {@code /tubanrac bat|tat} (hoặc {@code /autosell on|off}); không kèm gì = hỏi trạng thái. Bật
 * thì đồ rác rơi ra được server bán ngay, một dòng chat báo số linh thạch nhận.
 */
final class AutoSellCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/tubanrac";
    private static final String COMMAND_EN = "/autosell";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    AutoSellCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
        SetAutoSellJunkCommand.Builder request = SetAutoSellJunkCommand.newBuilder();
        if (words.length == 1) {
            request.setQueryOnly(true);
        } else {
            String arg = words[1].strip().toLowerCase(Locale.ROOT);
            if ("bat".equals(arg) || "on".equals(arg)) {
                request.setEnabled(true);
            } else if ("tat".equals(arg) || "off".equals(arg)) {
                request.setEnabled(false);
            } else {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "auto_sell.usage"));
                return true;
            }
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_AUTO_SELL_JUNK_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_AUTO_SELL_JUNK_RESPONSE_VALUE) {
            return false;
        }
        boolean enabled = SetAutoSellJunkResponse.parseFrom(frame.payload()).getEnabled();
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "auto_sell.on" : "auto_sell.off"));
        return true;
    }
}
