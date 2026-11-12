package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetPartyAutoAcceptCommand;
import dev.kage.protocol.SetPartyAutoAcceptResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tự nhận lời mời nhóm từ bạn bè (ADR-286) — {@code /tunhannhom bat|tat} (hoặc {@code /autoparty on|off}); không kèm gì
 * = hỏi trạng thái. Công tắc lưu ở server: bật thì một người BẠN mời là server cho vào nhóm luôn.
 */
final class PartyAutoAcceptCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PartyAutoAcceptCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/tunhannhom".equals(command) && !"/autoparty".equals(command)) {
            return false;
        }
        SetPartyAutoAcceptCommand.Builder request = SetPartyAutoAcceptCommand.newBuilder();
        if (words.length == 1) {
            request.setQueryOnly(true);
        } else {
            java.util.Optional<Boolean> enabled = ContactPrivacyCoordinator.parseSwitch(words[1]);
            if (enabled.isEmpty()) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "autoparty.usage"));
                return true;
            }
            request.setEnabled(enabled.get());
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_PARTY_AUTO_ACCEPT_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_PARTY_AUTO_ACCEPT_RESPONSE_VALUE) {
            return false;
        }
        boolean enabled = SetPartyAutoAcceptResponse.parseFrom(frame.payload()).getEnabled();
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "autoparty.on" : "autoparty.off"));
        return true;
    }
}
