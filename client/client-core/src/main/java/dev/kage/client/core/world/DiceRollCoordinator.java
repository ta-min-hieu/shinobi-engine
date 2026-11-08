package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalInt;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RollDiceCommand;
import dev.kage.protocol.RollDiceOutcome;
import dev.kage.protocol.RollDiceResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gieo xúc xắc (ADR-265) — {@code /gieo [max]} (hoặc {@code /roll}), mặc định 1–100. SERVER bốc số (luật vùng: random
 * gameplay chỉ sinh ở server) và gửi dòng kết quả tới cả nhóm; lớp này chỉ gửi ý định và báo lý do bị từ chối.
 */
final class DiceRollCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    DiceRollCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Trần từ đối số (rỗng ⇒ 0 = mặc định của server); rỗng khi không phải số. Tĩnh để test. */
    static OptionalInt max(String argument) {
        String trimmed = argument.strip();
        if (trimmed.isEmpty()) {
            return OptionalInt.of(0);
        }
        return trimmed.matches("[0-9]{1,4}") ? OptionalInt.of(Integer.parseInt(trimmed)) : OptionalInt.empty();
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/gieo".equals(command) && !"/roll".equals(command)) {
            return false;
        }
        OptionalInt max = max(parts.length > 1 ? parts[1] : "");
        if (max.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dice.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_ROLL_DICE_COMMAND_VALUE,
                RollDiceCommand.newBuilder().setMax(max.getAsInt()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_ROLL_DICE_RESPONSE_VALUE) {
            return false;
        }
        RollDiceOutcome outcome = RollDiceResponse.parseFrom(frame.payload()).getOutcome();
        if (outcome == RollDiceOutcome.ROLL_DICE_OUTCOME_RATE_LIMITED) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dice.rate_limited"));
        } else if (outcome != RollDiceOutcome.ROLL_DICE_OUTCOME_OK) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dice.usage"));
        }
        return true;
    }
}
