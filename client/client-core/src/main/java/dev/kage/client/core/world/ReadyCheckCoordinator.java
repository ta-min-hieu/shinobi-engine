package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RespondReadyCheckCommand;
import dev.kage.protocol.RespondReadyCheckOutcome;
import dev.kage.protocol.RespondReadyCheckResponse;
import dev.kage.protocol.StartReadyCheckCommand;
import dev.kage.protocol.StartReadyCheckOutcome;
import dev.kage.protocol.StartReadyCheckResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kiểm tra sẵn sàng nhóm (ADR-263) — {@code /sansang} (hoặc {@code /readycheck}) trưởng nhóm mở; {@code /sansang co |
 * khong} (hoặc {@code yes | no}) trả lời. Lời mời và kết quả do server gửi thẳng vào chat (dòng hệ thống); lớp này chỉ
 * gửi ý định và báo lý do bị từ chối.
 */
final class ReadyCheckCoordinator implements WindowFlow {

    private static final Map<String, Boolean> ANSWERS = Map.of(
            "co", true, "có", true, "yes", true, "y", true, "khong", false, "không", false, "no", false, "n", false);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ReadyCheckCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Câu trả lời từ đối số ({@code co}/{@code khong}…); rỗng = không phải câu trả lời. Tĩnh để test. */
    static Optional<Boolean> answer(String argument) {
        return Optional.ofNullable(ANSWERS.get(argument.strip().toLowerCase(Locale.ROOT)));
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/sansang".equals(command) && !"/readycheck".equals(command)) {
            return false;
        }
        String argument = parts.length > 1 ? parts[1] : "";
        if (argument.isBlank()) {
            sender.sendReadRequest(
                    Opcode.OPCODE_START_READY_CHECK_COMMAND_VALUE, StartReadyCheckCommand.getDefaultInstance());
            return true;
        }
        Optional<Boolean> ready = answer(argument);
        if (ready.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "readycheck.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_RESPOND_READY_CHECK_COMMAND_VALUE,
                RespondReadyCheckCommand.newBuilder().setReady(ready.get()).build());
        chat.showSystemNotice(
                KageI18n.textOrKey(i18n, ready.get() ? "readycheck.answered_yes" : "readycheck.answered_no"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_START_READY_CHECK_RESPONSE_VALUE) {
            StartReadyCheckOutcome outcome =
                    StartReadyCheckResponse.parseFrom(frame.payload()).getOutcome();
            switch (outcome) {
                case START_READY_CHECK_OUTCOME_OK -> {} // lời mời tới qua chat
                case START_READY_CHECK_OUTCOME_ALREADY_ACTIVE -> notice("readycheck.already_active");
                default -> notice("readycheck.not_leader");
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_RESPOND_READY_CHECK_RESPONSE_VALUE) {
            if (RespondReadyCheckResponse.parseFrom(frame.payload()).getOutcome()
                    != RespondReadyCheckOutcome.RESPOND_READY_CHECK_OUTCOME_OK) {
                notice("readycheck.no_check");
            }
            return true;
        }
        return false;
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }
}
