package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Chat (800-899, NETWORK.md §4).
 */
class ChatPacketsTest {

    @Test
    void shouldRoundTripSendChatMessageCommand() throws InvalidProtocolBufferException {
        var command = SendChatMessageCommand.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                .setTargetName("sasuke")
                .setBody("chào bạn")
                .build();
        var header = new PacketHeader(Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = SendChatMessageCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_PRIVATE);
        assertThat(parsed.getTargetName()).isEqualTo("sasuke");
        assertThat(parsed.getBody()).isEqualTo("chào bạn");
    }

    @Test
    void shouldRoundTripSendChatMessageResponse() throws InvalidProtocolBufferException {
        var response = SendChatMessageResponse.newBuilder()
                .setOutcome(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = SendChatMessageResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED);
    }

    @Test
    void shouldRoundTripChatMessageEvent() throws InvalidProtocolBufferException {
        var event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderId(7L)
                .setSenderName("naruto")
                .setBody("xin chào thế giới")
                .build();
        var header = new PacketHeader(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = ChatMessageEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
        assertThat(parsed.getSenderId()).isEqualTo(7L);
        assertThat(parsed.getSenderName()).isEqualTo("naruto");
        assertThat(parsed.getBody()).isEqualTo("xin chào thế giới");
    }
}
