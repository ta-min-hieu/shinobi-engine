package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * {@code SelectCharacterResponse} là mốc "sẵn sàng" thật (chủ dự án chốt 2026-10-14, docs/README.md dòng
 * 73(c)): lệnh client gửi NGAY khi nhận câu trả lời đó phải được xử lý như lệnh của nhân vật đã vào world.
 *
 * <p>Trước đó gateway trả lời TRƯỚC rồi mới gắn nhân vật vào kết nối, nên một lệnh tới đúng khe đó nhận
 * {@code error.not_authenticated} (đo được thật bằng gameProbe). Socket thật không tái hiện khe đó tất
 * định được; ở đây pipeline {@link EmbeddedChannel} chặn đúng lúc server GHI câu trả lời và bơm ngay một
 * {@link MoveCommand} vào — cùng luồng, nên lệnh đó thấy đúng trạng thái của handler tại khoảnh khắc
 * câu trả lời rời server.
 */
class SelectResponseIsReadyMarkTest {

    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int MOVE_SEQUENCE_ID = 99;

    @Test
    void commandSentTheInstantTheSelectResponseLeaves_shouldNotBeRefusedAsUnauthenticated() throws Exception {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "ready_mark");
        long characterId = GatewayPipelineTest.createCharacter(channel, "ReadyMark", 2);
        channel.pipeline().addFirst(new MoveOnSelectResponse());

        // Không dùng GatewayPipelineTest.selectCharacter: nó chỉ giữ gói ĐẦU và vứt phần sau — mà
        // câu trả lời cho lệnh di chuyển chính là phần sau.
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray());

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames)
                .as("setup: câu trả lời chọn nhân vật đã đi ra và lệnh di chuyển đã được bơm vào")
                .anyMatch(frame -> frame.header().opcode() == Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);
        List<String> moveErrors = frames.stream()
                .filter(frame -> frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE)
                .filter(frame -> frame.header().sequenceId() == MOVE_SEQUENCE_ID)
                .map(SelectResponseIsReadyMarkTest::errorKeyOf)
                .toList();
        assertThat(moveErrors)
                .as("lệnh gửi ngay sau SelectCharacterResponse")
                .doesNotContain("error.not_authenticated");
    }

    private static String errorKeyOf(PacketCodec.Frame frame) {
        try {
            return ErrorResponse.parseFrom(frame.payload()).getErrorKey();
        } catch (InvalidProtocolBufferException e) {
            throw new AssertionError("ErrorResponse hỏng", e);
        }
    }

    /** Thấy câu trả lời chọn nhân vật đi ra thì bơm ngay một lệnh di chuyển vào, như client nhanh tay. */
    private static final class MoveOnSelectResponse extends ChannelOutboundHandlerAdapter {
        private boolean injected;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (!injected && msg instanceof ByteBuf buf && isSelectResponse(buf)) {
                injected = true;
                ctx.write(msg, promise);
                ctx.channel().pipeline().fireChannelRead(moveFrame());
                return;
            }
            ctx.write(msg, promise);
        }

        /**
         * {@code LengthFieldPrepender} ghi tiền tố độ dài thành MỘT buffer riêng, rồi tới buffer khung —
         * nên khung bắt đầu thẳng bằng opcode (xem {@code GatewayPipelineTest#readOutboundFrame}).
         */
        private static boolean isSelectResponse(ByteBuf buf) {
            return buf.readableBytes() > LENGTH_PREFIX_BYTES
                    && buf.getUnsignedShort(buf.readerIndex()) == Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE;
        }

        private static ByteBuf moveFrame() {
            byte[] frame = PacketCodec.encodeFrame(
                    new PacketHeader(Opcode.OPCODE_MOVE_COMMAND_VALUE, ProtocolVersion.CURRENT, MOVE_SEQUENCE_ID),
                    MoveCommand.newBuilder().setDirX(1f).build().toByteArray());
            ByteBuf buf = Unpooled.buffer(LENGTH_PREFIX_BYTES + frame.length);
            buf.writeInt(frame.length);
            buf.writeBytes(frame);
            return buf;
        }
    }
}
