package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetSecurityPinStatusRequest;
import dev.kage.protocol.GetSecurityPinStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SecurityPinOutcome;
import dev.kage.protocol.SetSecurityPinCommand;
import dev.kage.protocol.SetSecurityPinResponse;
import dev.kage.protocol.VerifySecurityPinCommand;
import dev.kage.protocol.VerifySecurityPinResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Khoá an toàn phía client (ADR-115) — lệnh chat, không lên kênh chat nào: {@code /pin} (trạng thái), {@code /pin
 * <mã>} (mở khoá phiên), {@code /pin set <mã mới> [mã cũ]} (đặt/đổi). Kết quả về thành dòng hệ thống; server quyết hết
 * (đúng dạng, đếm sai, khoá 30 phút).
 */
final class SecurityPinCoordinator implements WindowFlow {

    private static final String COMMAND = "/pin";
    private static final String SET = "set";
    /** {@code /pin set <mới> <cũ>} — phần thừa dồn vào từ cuối, server sẽ báo sai dạng. */
    private static final int MAX_WORDS = 4;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    SecurityPinCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", MAX_WORDS);
        if (!COMMAND.equals(words[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (words.length == 1) {
            sender.sendReadRequest(
                    Opcode.OPCODE_GET_SECURITY_PIN_STATUS_REQUEST_VALUE,
                    GetSecurityPinStatusRequest.getDefaultInstance());
        } else if (SET.equalsIgnoreCase(words[1]) && words.length >= 3) {
            sender.sendReadRequest(
                    Opcode.OPCODE_SET_SECURITY_PIN_COMMAND_VALUE,
                    SetSecurityPinCommand.newBuilder()
                            .setNewPin(words[2])
                            .setCurrentPin(words.length > 3 ? words[3] : "")
                            .build());
        } else if (words.length == 2 && !SET.equalsIgnoreCase(words[1])) {
            sender.sendReadRequest(
                    Opcode.OPCODE_VERIFY_SECURITY_PIN_COMMAND_VALUE,
                    VerifySecurityPinCommand.newBuilder().setPin(words[1]).build());
        } else {
            chat.showSystemNotice(text("pin.usage"));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_SECURITY_PIN_STATUS_RESPONSE_VALUE) {
            GetSecurityPinStatusResponse status = GetSecurityPinStatusResponse.parseFrom(frame.payload());
            String key = !status.getHasPin()
                    ? "pin.status.none"
                    : status.getUnlocked() ? "pin.status.open" : "pin.status.locked";
            chat.showSystemNotice(text(key));
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_SECURITY_PIN_RESPONSE_VALUE) {
            SetSecurityPinResponse response = SetSecurityPinResponse.parseFrom(frame.payload());
            announce("pin.set.", response.getOutcome(), response.getAttemptsLeft());
            return true;
        }
        if (opcode == Opcode.OPCODE_VERIFY_SECURITY_PIN_RESPONSE_VALUE) {
            VerifySecurityPinResponse response = VerifySecurityPinResponse.parseFrom(frame.payload());
            announce("pin.verify.", response.getOutcome(), response.getAttemptsLeft());
            return true;
        }
        return false;
    }

    private void announce(String prefix, SecurityPinOutcome outcome, int attemptsLeft) {
        String suffix =
                outcome.name().substring("SECURITY_PIN_OUTCOME_".length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, prefix + suffix, attemptsLeft));
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
