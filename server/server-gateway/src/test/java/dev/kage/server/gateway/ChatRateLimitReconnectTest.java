package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Trần chat phải đi theo NGƯỜI, không theo kết nối (rà 2026-10-10).
 *
 * <p>Trước bản vá, năm bộ đếm chat là field của {@code GatewayChannelHandler} — mỗi kết nối một bộ
 * mới tinh. Hết lượt kênh Thế giới (5 tin/10 giây) thì chỉ cần ngắt rồi nối lại là có ngay 5 lượt
 * nữa: trần không còn là trần, và hình phạt "vượt liên tục thì ngắt kết nối" hoá thành PHẦN THƯỞNG
 * (ngắt = xoá sổ vi phạm). Một người chơi làm ngập kênh Thế giới của cả server bằng đúng một tài
 * khoản.
 */
@Timeout(30)
class ChatRateLimitReconnectTest {

    // Phải khớp trần kênh Thế giới trong ChatRateLimits.
    private static final int WORLD_LIMIT = 5;

    private final SharedWorldPipelines gateway = new SharedWorldPipelines();

    @AfterEach
    void stopGateway() {
        gateway.stop();
    }

    @Test
    void shouldStillBeRateLimited_afterReconnectingWithTheSameAccount() throws InvalidProtocolBufferException {
        EmbeddedChannel first = gateway.newChannel();
        GatewayPipelineTest.connect(first, "floodreconnect");
        long characterId = GatewayPipelineTest.createCharacter(first, "Flooder", 2);
        GatewayPipelineTest.selectCharacter(first, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(first);
        for (int i = 0; i < WORLD_LIMIT; i++) {
            assertThat(sendWorldChat(first, 10 + i)).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);
        }
        assertThat(sendWorldChat(first, 20)).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED);

        first.close().syncUninterruptibly();
        EmbeddedChannel second = gateway.newChannel();
        GatewayPipelineTest.connect(second, "floodreconnect");
        GatewayPipelineTest.selectCharacter(second, characterId, 2);
        GatewayPipelineTest.readOutboundFrames(second);

        assertThat(sendWorldChat(second, 10))
                .as("nối lại trong cùng cửa sổ 10 giây không được xoá sổ")
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED);
    }

    @Test
    void shouldNotChargeAnotherAccount_forSomeoneElsesFlood() throws InvalidProtocolBufferException {
        EmbeddedChannel flooder = gateway.newChannel();
        GatewayPipelineTest.connect(flooder, "floodloud");
        long flooderId = GatewayPipelineTest.createCharacter(flooder, "Loud", 2);
        GatewayPipelineTest.selectCharacter(flooder, flooderId, 3);
        EmbeddedChannel bystander = gateway.newChannel();
        GatewayPipelineTest.connect(bystander, "floodquiet");
        long bystanderId = GatewayPipelineTest.createCharacter(bystander, "Quiet", 2);
        GatewayPipelineTest.selectCharacter(bystander, bystanderId, 3);
        GatewayPipelineTest.readOutboundFrames(flooder);
        GatewayPipelineTest.readOutboundFrames(bystander);

        for (int i = 0; i <= WORLD_LIMIT; i++) {
            sendWorldChat(flooder, 10 + i);
        }
        GatewayPipelineTest.readOutboundFrames(bystander); // tin Thế giới của người kia

        assertThat(sendWorldChat(bystander, 10)).isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);
    }

    private static SendChatMessageOutcome sendWorldChat(EmbeddedChannel channel, int sequenceId)
            throws InvalidProtocolBufferException {
        SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setBody("xin chao")
                .build();
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, ProtocolVersion.CURRENT, sequenceId),
                command.toByteArray());
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        for (PacketCodec.Frame frame : frames) {
            if (frame.header().opcode() == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE) {
                return SendChatMessageResponse.parseFrom(frame.payload()).getOutcome();
            }
        }
        throw new AssertionError("không thấy SendChatMessageResponse trong " + frames.size() + " gói");
    }
}
