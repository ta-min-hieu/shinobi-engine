package dev.kage.client.core.world;

import java.util.function.IntConsumer;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.MoveSpeedChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** ADR-070 D4 — nhận tốc độ phi kiếm của chính mình, chuyển cho phần dự đoán di chuyển. Không vẽ gì. */
final class MoveSpeedCoordinator implements WindowFlow {

    private final IntConsumer onPercent;

    MoveSpeedCoordinator(IntConsumer onPercent) {
        this.onPercent = onPercent;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_MOVE_SPEED_CHANGED_EVENT_VALUE) {
            return false;
        }
        onPercent.accept(MoveSpeedChangedEvent.parseFrom(frame.payload()).getMoveSpeedPercent());
        return true;
    }

    /** Rời màn hình — vào lại world thì server báo lại. */
    @Override
    public void reset() {
        onPercent.accept(0);
    }
}
