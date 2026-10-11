package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Hai luật của {@code ConnectRequest} (quyết định chủ dự án 2026-10-11, docs/README.md dòng 44(ii) và
 * 68(c)): client phải cùng phiên bản giao thức với server, và một kết nối đang ở world không được đăng
 * nhập lần nữa.
 *
 * <p>Lớp RIÊNG, mượn pipeline của {@link GatewayPipelineTest} — cùng lý do
 * {@link ProtocolViolationLimitTest}.
 */
class ConnectRequestRulesTest {

    private static final int OTHER_PROTOCOL_VERSION = ProtocolVersion.CURRENT + 1;

    @Test
    void shouldRefuseLogin_whenTheClientSpeaksAnotherProtocolVersion() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, OTHER_PROTOCOL_VERSION, 1),
                ConnectRequest.newBuilder().setUsername("oldclient").build().toByteArray());

        assertThat(onlyErrorKey(channel)).isEqualTo("error.client_outdated");
        // Bị từ chối nghĩa là CHƯA đăng nhập: lệnh cần tài khoản vẫn nhận 401.
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                ListCharactersRequest.getDefaultInstance().toByteArray());
        assertThat(onlyErrorKey(channel)).isEqualTo("error.not_authenticated");
    }

    /**
     * Trước bản sửa, lệnh này ghi đè tài khoản của kết nối trong khi nó vẫn điều khiển nhân vật của tài
     * khoản cũ. Nay bị từ chối, và kết nối giữ nguyên phiên đang chơi.
     */
    @Test
    void shouldRefuseASecondLogin_whileTheConnectionIsInTheWorld() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "firstaccount");
        long characterId = GatewayPipelineTest.createCharacter(channel, "InWorldHero", 2);
        GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(channel);

        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 4),
                ConnectRequest.newBuilder().setUsername("secondaccount").build().toByteArray());

        assertThat(onlyErrorKey(channel)).isEqualTo("error.already_in_world");
        GatewayChannelHandler handler = channel.pipeline().get(GatewayChannelHandler.class);
        assertThat(handler.boundCharacterId())
                .as("kết nối vẫn gắn đúng nhân vật đang chơi")
                .isEqualTo(characterId);
    }

    /** Chưa vào world thì đăng nhập lại (đổi tài khoản ở màn hình chọn nhân vật) vẫn được. */
    @Test
    void shouldStillAllowLoggingInAgain_beforeEnteringTheWorld() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "firstaccount");

        GatewayPipelineTest.connect(channel, "secondaccount");

        assertThat(channel.isOpen()).isTrue();
    }

    private static String onlyErrorKey(EmbeddedChannel channel) throws InvalidProtocolBufferException {
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames).extracting(frame -> frame.header().opcode()).containsExactly(Opcode.OPCODE_ERROR_VALUE);
        return ErrorResponse.parseFrom(frames.get(0).payload()).getErrorKey();
    }
}
