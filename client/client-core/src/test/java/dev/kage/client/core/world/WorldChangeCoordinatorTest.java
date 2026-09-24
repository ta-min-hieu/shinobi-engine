package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/** ADR-051 — lối vào duy nhất của mọi cú đổi World phía client. */
class WorldChangeCoordinatorTest {

    private final WorldChangeCoordinator coordinator = new WorldChangeCoordinator();
    private final List<WorldChangedEvent> received = new ArrayList<>();

    @BeforeEach
    void listen() {
        coordinator.setOnWorldChanged(received::add);
    }

    @Test
    void shouldHandOverTheEventAndRememberTheWorldKind() throws InvalidProtocolBufferException {
        WorldChangedEvent event = WorldChangedEvent.newBuilder()
                .setWorldKind(WorldKind.WORLD_KIND_MAIN)
                .setReason(WorldChangeReason.WORLD_CHANGE_REASON_DEFEATED)
                .setX(12.5f)
                .setY(0f)
                .setCurrentHp(206)
                .setMaxHp(206)
                .build();

        boolean handled = coordinator.dispatch(frame(Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE, event.toByteArray()));

        assertThat(handled).isTrue();
        assertThat(received).containsExactly(event);
        assertThat(coordinator.currentKind()).isEqualTo(WorldKind.WORLD_KIND_MAIN);
    }

    @Test
    void shouldIgnoreEveryOtherPacket_includingTheOldResponsesThatUsedToTriggerTheClear()
            throws InvalidProtocolBufferException {
        boolean handled = coordinator.dispatch(frame(
                Opcode.OPCODE_MOVE_RESPONSE_VALUE,
                MoveResponse.getDefaultInstance().toByteArray()));

        assertThat(handled).isFalse();
        assertThat(received).isEmpty();
    }

    @Test
    void shouldStartBackInTheMainWorldAfterReset() throws InvalidProtocolBufferException {
        WorldChangedEvent intoDungeon = WorldChangedEvent.newBuilder()
                .setWorldKind(WorldKind.WORLD_KIND_DUNGEON)
                .setReason(WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED)
                .build();
        coordinator.dispatch(frame(Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE, intoDungeon.toByteArray()));
        assertThat(coordinator.currentKind()).isEqualTo(WorldKind.WORLD_KIND_DUNGEON);

        coordinator.reset();

        assertThat(coordinator.currentKind()).isEqualTo(WorldKind.WORLD_KIND_MAIN);
    }

    private static PacketCodec.Frame frame(int opcode, byte[] payload) {
        return new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 0), payload);
    }
}
