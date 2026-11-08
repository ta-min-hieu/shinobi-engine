package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetBagValueRequest;
import dev.kage.protocol.GetBagValueResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tổng giá trị túi đồ (ADR-274) — {@code /giatri} (hoặc {@code /bagvalue}). SERVER cộng giá bán cho cửa hàng của mọi
 * món trong túi (570/571) — client không tự tính giá (luật vùng client); lớp này chỉ hỏi và in.
 */
final class BagValueCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BagValueCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/giatri".equals(command) && !"/bagvalue".equals(command)) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_GET_BAG_VALUE_REQUEST_VALUE, GetBagValueRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_BAG_VALUE_RESPONSE_VALUE) {
            return false;
        }
        GetBagValueResponse response = GetBagValueResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "bagvalue.line",
                response.getTotalGold(),
                response.getPricedStacks(),
                response.getUnpricedStacks()));
        return true;
    }
}
