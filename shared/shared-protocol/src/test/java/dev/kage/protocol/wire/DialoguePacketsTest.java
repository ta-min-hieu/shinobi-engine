package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.DialogueEndedResponse;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DialogueOptionView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.TalkToNpcCommand;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Dialogue (600-699 dùng chung Quest/NPC, NETWORK.md §4).
 */
class DialoguePacketsTest {

    @Test
    void shouldRoundTripTalkToNpcCommand() throws InvalidProtocolBufferException {
        var command = TalkToNpcCommand.newBuilder().setNpcCode("town_elder").build();
        var header = new PacketHeader(Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = TalkToNpcCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getNpcCode()).isEqualTo("town_elder");
    }

    @Test
    void shouldRoundTripDialogueNodeResponse() throws InvalidProtocolBufferException {
        var option = DialogueOptionView.newBuilder().setLabelKey("dialogue.option.bye").setAction("end").build();
        var response = DialogueNodeResponse.newBuilder()
                .setNodeId("start")
                .setTextKey("dialogue.town_elder.greeting")
                .addOptions(option)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = DialogueNodeResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getNodeId()).isEqualTo("start");
        assertThat(parsed.getTextKey()).isEqualTo("dialogue.town_elder.greeting");
        assertThat(parsed.getOptionsList()).hasSize(1);
        assertThat(parsed.getOptions(0).getLabelKey()).isEqualTo("dialogue.option.bye");
        assertThat(parsed.getOptions(0).getAction()).isEqualTo("end");
    }

    @Test
    void shouldRoundTripSelectDialogueOptionCommand() throws InvalidProtocolBufferException {
        var command = SelectDialogueOptionCommand.newBuilder()
                .setNpcCode("town_elder")
                .setCurrentNodeId("start")
                .setOptionIndex(1)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = SelectDialogueOptionCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getNpcCode()).isEqualTo("town_elder");
        assertThat(parsed.getCurrentNodeId()).isEqualTo("start");
        assertThat(parsed.getOptionIndex()).isEqualTo(1);
    }

    @Test
    void shouldRoundTripDialogueEndedResponse() throws InvalidProtocolBufferException {
        var response = DialogueEndedResponse.newBuilder().build();
        var header = new PacketHeader(Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = DialogueEndedResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed).isEqualTo(response);
    }
}
