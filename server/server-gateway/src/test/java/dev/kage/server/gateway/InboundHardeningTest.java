package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Đường VÀO của gateway trước dữ liệu client không đáng tin (NETWORK.md §10/§12/§13) — rà
 * 2026-10-02: framing hỏng, kết nối im lặng, client không đọc phản hồi, và spam lệnh chiến đấu.
 *
 * <p>Tách lớp riêng thay vì thêm vào {@code GatewayPipelineTest} — cùng lý do
 * {@code DbCommandRateLimitTest}.
 */
@Timeout(30)
class InboundHardeningTest {

    // ---- Framing: mọi frame hỏng phải ĐÓNG kết nối sạch sẽ, không ném ra khỏi pipeline ----

    @Test
    void shouldCloseConnection_whenFrameIsShorterThanTheHeader() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        writeRawLengthPrefixed(channel, 3, new byte[] {1, 2, 3});

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldCloseConnection_whenFrameIsEmpty() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        writeRawLengthPrefixed(channel, 0, new byte[0]);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldCloseConnection_whenDeclaredLengthIsNegative() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        writeRawLengthPrefixed(channel, -1, new byte[16]);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldCloseConnection_whenDeclaredLengthExceedsTheFrameCap() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        // Chỉ KHAI BÁO độ dài khổng lồ — server phải từ chối ngay, không ngồi chờ gom đủ 2 GB.
        writeRawLengthPrefixed(channel, Integer.MAX_VALUE, new byte[16]);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldAnswerInvalidPayload_andKeepConnection_whenProtobufIsMalformed() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        // 0x0A = field 1 kiểu length-delimited, rồi độ dài 0x7F nhưng chỉ còn 1 byte: payload cụt.
        byte[] truncated = {0x0A, 0x7F, 0x01};

        GatewayPipelineTest.writeInboundFrame(
                channel, new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 9), truncated);

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        assertThat(ErrorResponse.parseFrom(frames.getFirst().payload()).getErrorKey())
                .isEqualTo("error.invalid_payload");
        assertThat(channel.isOpen()).isTrue();
    }

    // ---- Kết nối im lặng (NETWORK.md §10: không nhận gì ~45s ⇒ coi là chết, dọn dẹp) ----

    /**
     * {@code IdleStateHandler} của Netty 4.1 đo bằng đồng hồ thật, không theo {@code advanceTimeBy}
     * của {@code EmbeddedChannel} — nên tách làm hai: pipeline THẬT có hạn đúng 45s, và sự kiện hết
     * hạn thì đóng kết nối.
     */
    @Test
    void shouldInstallTheReaderIdleTimeout_fromNetworkMdSection10() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        IdleStateHandler idle = channel.pipeline().get(IdleStateHandler.class);

        assertThat(idle).as("server không có hạn im lặng nào").isNotNull();
        assertThat(idle.getReaderIdleTimeInMillis()).isEqualTo(45_000L);
    }

    @Test
    void shouldCloseConnection_whenTheReaderIdleTimeoutFires() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        assertThat(channel.isOpen())
                .as("kết nối nửa mở phải bị đóng — channelInactive mới dọn được nhân vật khỏi world")
                .isFalse();
    }

    @Test
    void shouldKeepConnection_onOtherUserEvents() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
        sendPing(channel);

        assertThat(channel.isOpen()).isTrue();
    }

    // ---- Client không đọc phản hồi: không được để hàng đợi ghi của server phình vô hạn ----

    @Test
    void shouldStopReading_whileTheClientIsNotDrainingResponses_andResumeAfterwards() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();

        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        channel.runPendingTasks();
        assertThat(channel.isWritable()).isFalse();
        assertThat(channel.config().isAutoRead())
                .as("đang không ghi ra được thì phải ngừng đọc lệnh mới (mỗi lệnh đẻ thêm một phản hồi)")
                .isFalse();

        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true);
        channel.runPendingTasks();
        assertThat(channel.config().isAutoRead()).isTrue();
    }

    // ---- Lệnh chiến đấu: mỗi gói là một WorldCommand trong hàng đợi KHÔNG giới hạn của tick thread ----

    @Test
    void shouldDisconnect_whenCombatCommandSpamIsSustained() throws InterruptedException {
        EmbeddedChannel channel = enterWorld("combatspammer", "Spammer");

        for (int round = 0; round < 5 && channel.isOpen(); round++) {
            sendCombatCommands(channel, 300);
            GatewayPipelineTest.readOutboundFrames(channel);
            Thread.sleep(1_100);
        }

        assertThat(channel.isOpen()).isFalse();
    }

    /**
     * Client thật: đánh thường theo nhịp 1 đòn/giây (kèm vài lần gửi lại), đổi mục tiêu khi click,
     * dùng skill khi hết hồi chiêu — vài lệnh mỗi giây. Một burst bấm loạn cũng không bị ngắt.
     */
    @Test
    void shouldNeverDisconnectARealClient_mashingCombatButtonsForAFewSeconds() throws InterruptedException {
        EmbeddedChannel channel = enterWorld("combatmasher", "Masher");

        for (int round = 0; round < 3; round++) {
            sendCombatCommands(channel, 15);
            GatewayPipelineTest.readOutboundFrames(channel);
            Thread.sleep(1_100);
        }

        assertThat(channel.isOpen()).isTrue();
    }

    private static EmbeddedChannel enterWorld(String username, String characterName) throws InterruptedException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, username);
        try {
            long characterId = GatewayPipelineTest.createCharacter(channel, characterName, 2);
            GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("setup: vào world thất bại", e);
        }
        GatewayPipelineTest.readOutboundFrames(channel);
        return channel;
    }

    private static void sendCombatCommands(EmbeddedChannel channel, int count) {
        byte[] attack = AttackCommand.newBuilder().build().toByteArray();
        byte[] target =
                SetTargetCommand.newBuilder().setTargetEntityId(-1L).build().toByteArray();
        byte[] skill = UseSkillCommand.newBuilder()
                .setSkillCode("fire_ball")
                .setTargetEntityId(-1L)
                .build()
                .toByteArray();
        for (int i = 0; i < count && channel.isOpen(); i++) {
            int kind = i % 3;
            int opcode =
                    switch (kind) {
                        case 0 -> Opcode.OPCODE_ATTACK_COMMAND_VALUE;
                        case 1 -> Opcode.OPCODE_SET_TARGET_COMMAND_VALUE;
                        default -> Opcode.OPCODE_USE_SKILL_COMMAND_VALUE;
                    };
            byte[] payload =
                    switch (kind) {
                        case 0 -> attack;
                        case 1 -> target;
                        default -> skill;
                    };
            GatewayPipelineTest.writeInboundFrame(
                    channel, new PacketHeader(opcode, ProtocolVersion.CURRENT, 100 + i), payload);
        }
    }

    private static void sendPing(EmbeddedChannel channel) {
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 50),
                PingCommand.newBuilder().setClientTimestampMs(1L).build().toByteArray());
        GatewayPipelineTest.readOutboundFrames(channel);
    }

    private static void writeRawLengthPrefixed(EmbeddedChannel channel, int declaredLength, byte[] body) {
        ByteBuf buf = Unpooled.buffer(4 + body.length);
        buf.writeInt(declaredLength);
        buf.writeBytes(body);
        channel.writeInbound(buf);
    }
}
