package dev.kage.client.core.world;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.MoveSpeedChangedEvent;
import dev.kage.protocol.MovementModifierChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhận hai thứ quyết tốc độ dự đoán của chính mình, chuyển cho {@link LocalIntentSender}. Không vẽ gì:
 *
 * <ul>
 *   <li>ADR-070 D4 — phần trăm tốc độ phi kiếm (310);
 *   <li>ADR-073 — hệ số tốc độ do hiệu ứng trạng thái + đang choáng/gục (311).
 * </ul>
 */
final class MoveSpeedCoordinator implements WindowFlow {

    private final LocalIntentSender intents;

    MoveSpeedCoordinator(LocalIntentSender intents) {
        this.intents = intents;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MOVE_SPEED_CHANGED_EVENT_VALUE) {
            intents.onMoveSpeedPercent(
                    MoveSpeedChangedEvent.parseFrom(frame.payload()).getMoveSpeedPercent());
            return true;
        }
        if (opcode == Opcode.OPCODE_MOVEMENT_MODIFIER_CHANGED_EVENT_VALUE) {
            MovementModifierChangedEvent event = MovementModifierChangedEvent.parseFrom(frame.payload());
            intents.onMovementModifier(event.getStatusSpeedMultiplier(), event.getImmobilized());
            return true;
        }
        return false;
    }

    /** Rời màn hình — vào lại world thì server báo lại; World mới không mang hiệu ứng cũ (ADR-073 D4). */
    @Override
    public void reset() {
        intents.onMoveSpeedPercent(0);
        intents.onMovementModifier(1f, false);
    }
}
