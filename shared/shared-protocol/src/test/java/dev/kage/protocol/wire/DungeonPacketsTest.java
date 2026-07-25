package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.Opcode;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Dungeon (1500-1599, NETWORK.md §4).
 */
class DungeonPacketsTest {

    @Test
    void shouldRoundTripEnterDungeonCommandAndResponse() throws InvalidProtocolBufferException {
        EnterDungeonCommand command = EnterDungeonCommand.newBuilder().setDungeonCode("sample_dungeon").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        EnterDungeonCommand parsedCommand =
                EnterDungeonCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsedCommand.getDungeonCode()).isEqualTo("sample_dungeon");

        EnterDungeonResponse response = EnterDungeonResponse.newBuilder()
                .setOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        EnterDungeonResponse parsedResponse =
                EnterDungeonResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome())
                .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE);
    }

    @Test
    void shouldRoundTripExitDungeonCommandAndResponse() throws InvalidProtocolBufferException {
        ExitDungeonCommand command = ExitDungeonCommand.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(ExitDungeonCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload())).isEqualTo(command);

        ExitDungeonResponse response =
                ExitDungeonResponse.newBuilder().setOutcome(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK).build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        ExitDungeonResponse parsedResponse =
                ExitDungeonResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);
    }
}
