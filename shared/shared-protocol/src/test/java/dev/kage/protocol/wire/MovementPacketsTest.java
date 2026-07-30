package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code
 * PacketCodec} + protobuf thật cho toàn bộ packet Movement/World (300-399, NETWORK.md §4).
 */
class MovementPacketsTest {

    @Test
    void shouldRoundTripMoveCommand() throws InvalidProtocolBufferException {
        MoveCommand command =
                MoveCommand.newBuilder().setDirX(1f).setDirY(-0.5f).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_MOVE_COMMAND_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        MoveCommand parsed = MoveCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getDirX()).isEqualTo(1f);
        assertThat(parsed.getDirY()).isEqualTo(-0.5f);
    }

    @Test
    void shouldRoundTripMoveResponse() throws InvalidProtocolBufferException {
        MoveResponse response = MoveResponse.newBuilder().setX(12.5f).setY(-3f).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_MOVE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        MoveResponse parsed =
                MoveResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getX()).isEqualTo(12.5f);
        assertThat(parsed.getY()).isEqualTo(-3f);
    }

    @Test
    void shouldRoundTripCharacterMovedEvent() throws InvalidProtocolBufferException {
        CharacterMovedEvent event = CharacterMovedEvent.newBuilder()
                .setCharacterId(99L)
                .setX(1f)
                .setY(2f)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        CharacterMovedEvent parsed = CharacterMovedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCharacterId()).isEqualTo(99L);
        assertThat(parsed.getX()).isEqualTo(1f);
        assertThat(parsed.getY()).isEqualTo(2f);
    }

    @Test
    void shouldRoundTripCharacterSpawnedEvent() throws InvalidProtocolBufferException {
        CharacterSpawnedEvent event = CharacterSpawnedEvent.newBuilder()
                .setCharacterId(5L)
                .setX(3f)
                .setY(4f)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        CharacterSpawnedEvent parsed = CharacterSpawnedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCharacterId()).isEqualTo(5L);
        assertThat(parsed.getX()).isEqualTo(3f);
        assertThat(parsed.getY()).isEqualTo(4f);
    }

    @Test
    void shouldRoundTripCharacterDespawnedEvent() throws InvalidProtocolBufferException {
        CharacterDespawnedEvent event =
                CharacterDespawnedEvent.newBuilder().setCharacterId(5L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        CharacterDespawnedEvent parsed = CharacterDespawnedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCharacterId()).isEqualTo(5L);
    }
}
