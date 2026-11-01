package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StartTribulationCommand;
import dev.kage.protocol.StartTribulationResponse;
import dev.kage.protocol.TribulationEvent;
import dev.kage.protocol.TribulationOutcome;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thiên Kiếp (ADR-126) — lệnh chat {@code /dokiep} khi đã đứng ở tầng 9 và đủ EXP. Server đưa vào đài độ kiếp (World
 * riêng) và giáng lôi; client chỉ hiện dòng hệ thống cho từng đợt và kết quả — máu tụt hiện qua vạch máu như mọi đòn.
 */
final class TribulationCoordinator implements WindowFlow {

    private static final String COMMAND = "/dokiep";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    TribulationCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_START_TRIBULATION_COMMAND_VALUE, StartTribulationCommand.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_START_TRIBULATION_RESPONSE_VALUE) {
            StartTribulationResponse response = StartTribulationResponse.parseFrom(frame.payload());
            if (response.getOutcome() == TribulationOutcome.TRIBULATION_OUTCOME_OK) {
                chat.showSystemNotice(KageI18n.formatOrKey(
                        i18n,
                        "tribulation.started",
                        response.getWaves(),
                        response.getStrikePercent(),
                        response.getIntervalSeconds()));
            } else {
                String suffix = response.getOutcome()
                        .name()
                        .substring("TRIBULATION_OUTCOME_".length())
                        .toLowerCase(Locale.ROOT);
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "tribulation.outcome." + suffix));
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_TRIBULATION_EVENT_VALUE) {
            TribulationEvent event = TribulationEvent.parseFrom(frame.payload());
            String suffix = event.getKind()
                    .name()
                    .substring("TRIBULATION_EVENT_KIND_".length())
                    .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "tribulation.event." + suffix, event.getWave(), event.getWaves(), event.getSecondsLeft()));
            return true;
        }
        return false;
    }
}
