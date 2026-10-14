package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TradeOfferItemInput;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Hai giới hạn ở biên dây (quyết định chủ dự án 2026-10-11, docs/README.md dòng 44(i)/(iii)).
 *
 * <p>Trước đó một kết nối bơm được gói hỏng vô hạn — mỗi gói một câu trả lời và một stack trace trong
 * log — và một lời chào giao dịch chở được số dòng vật phẩm tuỳ ý trong khung 1 MiB.
 *
 * <p>Lớp RIÊNG chứ không thêm vào {@link GatewayPipelineTest}: lớp đó đã rất lớn; ở đây chỉ mượn
 * pipeline của nó.
 */
class ProtocolViolationLimitTest {

    private static final int UNKNOWN_OPCODE = 65_000;

    /** Trần tính cả tiền tố độ dài — xem {@link GatewayServer#MAX_FRAME_LENGTH}. */
    private static final int MAX_FRAME_BODY = GatewayServer.MAX_FRAME_LENGTH - GatewayServer.LENGTH_FIELD_LENGTH;

    /** opcode(2) + protocolVersion(1) + sequenceId(4) — header của mỗi khung (NETWORK.md §102). */
    private static final int HEADER_BYTES = 7;

    /** Bắt đầu bằng một tag protobuf cắt cụt — không message nào parse nổi. */
    private static final byte[] GARBAGE_PAYLOAD = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    @Test
    void shouldKeepTheConnection_whileViolationsStayWithinTheLimit() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        for (int i = 0; i < GatewayChannelHandler.MAX_PROTOCOL_VIOLATIONS; i++) {
            sendGarbage(channel, i);
        }

        assertThat(channel.isOpen())
                .as("%s gói hỏng vẫn nằm trong giới hạn", GatewayChannelHandler.MAX_PROTOCOL_VIOLATIONS)
                .isTrue();
        // Kết nối vẫn dùng được như thường.
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 99),
                PingCommand.getDefaultInstance().toByteArray());
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void shouldCloseTheConnection_onceMalformedPayloadsExceedTheLimit() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        for (int i = 0; i <= GatewayChannelHandler.MAX_PROTOCOL_VIOLATIONS; i++) {
            sendGarbage(channel, i);
        }

        assertThat(channel.isOpen()).isFalse();
    }

    /** Opcode lạ cũng là vi phạm giao thức và đếm chung một sổ với payload hỏng. */
    @Test
    void shouldCountUnknownOpcodesTowardsTheSameLimit() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        for (int i = 0; i < GatewayChannelHandler.MAX_PROTOCOL_VIOLATIONS; i++) {
            GatewayPipelineTest.writeInboundFrame(
                    channel, new PacketHeader(UNKNOWN_OPCODE, ProtocolVersion.CURRENT, i), new byte[0]);
        }
        assertThat(channel.isOpen()).isTrue();

        sendGarbage(channel, 100);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldRefuseATradeOfferWithMoreLinesThanABagHolds() {
        SetTradeOfferCommand.Builder oversized =
                SetTradeOfferCommand.newBuilder().setSessionId(1L);
        for (int i = 1; i <= EconomyCommandShape.MAX_TRADE_OFFER_LINES + 1; i++) {
            oversized.addItems(TradeOfferItemInput.newBuilder().setItemId(i).setQuantity(1));
        }
        SetTradeOfferCommand atTheLimit = oversized.clone().removeItems(0).build();

        assertThat(EconomyCommandShape.isWellFormed(oversized.build())).isFalse();
        assertThat(EconomyCommandShape.isWellFormed(atTheLimit)).isTrue();
    }

    /**
     * Kiểm sống 2026-10-14: khung quá trần đóng kết nối đúng, nhưng mỗi khung để lại một log ERROR kèm
     * stack trace — ai mở được kết nối là ghi được ERROR tuỳ ý. Lỗi giải mã là lỗi của client.
     */
    @Test
    void shouldCloseWithoutAnErrorLog_whenAFrameDeclaresMoreThanTheCap() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            ByteBuf lengthOnly = Unpooled.buffer(GatewayServer.LENGTH_FIELD_LENGTH);
            lengthOnly.writeInt(MAX_FRAME_BODY + 1);
            channel.writeInbound(lengthOnly);

            assertThat(channel.isOpen()).isFalse();
            assertThat(errorLog.errorsSince(0)).isEmpty();
        }
    }

    /** Biên trên: thân đúng bằng trần vẫn tới handler (payload rác ⇒ một vi phạm, kết nối còn sống). */
    @Test
    void shouldAcceptAFrameWhoseBodyIsExactlyTheCap() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 1),
                new byte[MAX_FRAME_BODY - HEADER_BYTES]);

        assertThat(channel.isOpen()).isTrue();
    }

    private static void sendGarbage(EmbeddedChannel channel, int sequenceId) {
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId),
                GARBAGE_PAYLOAD);
    }
}
