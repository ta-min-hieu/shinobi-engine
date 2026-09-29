package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Tên bang sai luật bị server từ chối bằng {@code ErrorResponse} {@code error.guild_name_invalid}
 * (quyết định chủ dự án 2026-09-29) — KHÔNG thêm outcome vào guild.proto, và không bang nào được
 * tạo.
 *
 * <p>"Không tạo bang" đo bằng chính fake của {@link GatewayPipelineTest#fakeGuildFacade()}: hàm
 * {@code createGuild} của port ném ngay khi bị gọi — tên sai lọt xuống tới đó thì server trả lỗi
 * 500 chung chứ không phải khoá này. Lớp riêng vì {@code GatewayServerIntegrationTest} đã chạm trần
 * độ phức tạp PMD của cả lớp.
 */
@Timeout(30)
class GuildNameValidationGatewayTest {

    private static final int CREATE_GUILD_SEQUENCE = 40;

    @Test
    void shouldRejectInvalidGuildName_withErrorKeyAndWithoutTouchingThePort() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "guildfounder");
        long characterId = GatewayPipelineTest.createCharacter(channel, "Founder", 2);
        GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(channel); // sự kiện vào world đi kèm — không thuộc test này

        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(
                        Opcode.OPCODE_CREATE_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, CREATE_GUILD_SEQUENCE),
                CreateGuildCommand.newBuilder().setName("Thanh Vân Môn").build().toByteArray());
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        ErrorResponse error = ErrorResponse.parseFrom(frames.get(0).payload());
        assertThat(error.getErrorKey()).isEqualTo("error.guild_name_invalid");
        assertThat(error.getRelatedSequenceId()).isEqualTo(CREATE_GUILD_SEQUENCE);
    }
}
