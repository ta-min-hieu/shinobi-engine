package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Trần chung cho mọi lệnh chạm DB (2026-09-24, NETWORK.md §13) — trước đó chỉ di chuyển và chat bị
 * giới hạn: một client spam {@code ListInventoryRequest}/{@code BuyItemCommand}... hàng nghìn lần mỗi
 * giây, mỗi lần một Virtual Thread và một câu query.
 *
 * <p>Lệnh dùng để đo là {@code ListCharactersRequest}: chạm DB, đi qua {@code executeBlocking} như mọi
 * lệnh khác, và chỉ cần đăng nhập (không cần vào world) — bộ đếm là CHUNG cho cả nhóm, nên loại lệnh
 * nào cũng đo cùng một thứ.
 *
 * <p>Tách lớp riêng thay vì thêm vào {@code GatewayPipelineTest}: lớp đó đã quá lớn, cùng lý do trần
 * CyclomaticComplexity cả lớp của PMD.
 */
@Timeout(30)
class DbCommandRateLimitTest {

    // Phải khớp GatewayChannelHandler.MAX_DB_COMMANDS_PER_SECOND.
    private static final int LIMIT_PER_SECOND = 40;

    @Test
    void shouldRejectSpam_beforeItReachesTheDatabase() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "spammer");
        int spam = 200;

        sendListCharacters(channel, spam);

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        long answered = countOpcode(frames, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        long rateLimited = countRateLimitedErrors(frames);
        // Burst có thể vắt qua MỘT lần đổi cửa sổ trên máy chậm ⇒ tối đa 2 × trần được qua. Connect
        // ở trên đã ăn 1 suất của cửa sổ đầu.
        assertThat(answered).isLessThanOrEqualTo(2L * LIMIT_PER_SECOND);
        assertThat(rateLimited).isEqualTo(spam - answered);
        assertThat(channel.isOpen())
                .as("một burst ngắn chỉ bị từ chối, chưa bị ngắt")
                .isTrue();
    }

    /**
     * Client thật đăng nhập, vào world rồi mở MỌI cửa sổ trong cùng một giây: 18 lệnh chạm DB (xem
     * comment {@code MAX_DB_COMMANDS_PER_SECOND}). Connect (1) + 17 lệnh nữa — không lệnh nào được
     * phép bị từ chối.
     */
    @Test
    void shouldNeverLimitARealClientEnteringTheWorldAndOpeningEveryPanel() {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "realplayer");

        sendListCharacters(channel, 17);

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(countRateLimitedErrors(frames)).isZero();
        assertThat(countOpcode(frames, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE))
                .isEqualTo(17L);
    }

    /**
     * Vượt ngưỡng LIÊN TIẾP qua nhiều cửa sổ ⇒ ngắt kết nối. Thời gian thật (không giả được: handler
     * dựng RateLimiter bằng System::nanoTime) — mỗi vòng cách nhau hơn 1 giây nhưng dưới 2 giây để
     * các cửa sổ vi phạm nằm LIỀN nhau (xem RateLimiter#rotateWindowIfElapsed).
     */
    @Test
    void shouldDisconnect_whenSpamIsSustained() throws InterruptedException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "sustainedspammer");

        for (int round = 0; round < 5 && channel.isOpen(); round++) {
            sendListCharacters(channel, 3 * LIMIT_PER_SECOND);
            GatewayPipelineTest.readOutboundFrames(channel);
            Thread.sleep(1_100);
        }

        assertThat(channel.isOpen()).isFalse();
    }

    private static void sendListCharacters(EmbeddedChannel channel, int count) {
        byte[] payload = ListCharactersRequest.newBuilder().build().toByteArray();
        for (int i = 0; i < count && channel.isOpen(); i++) {
            PacketHeader header =
                    new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 100 + i);
            GatewayPipelineTest.writeInboundFrame(channel, header, payload);
        }
    }

    private static long countOpcode(List<PacketCodec.Frame> frames, int opcode) {
        return frames.stream().filter(f -> f.header().opcode() == opcode).count();
    }

    private static long countRateLimitedErrors(List<PacketCodec.Frame> frames) {
        return frames.stream()
                .filter(f -> f.header().opcode() == Opcode.OPCODE_ERROR_VALUE)
                .map(f -> parseError(f.payload()))
                .filter(e -> "error.rate_limited".equals(e.getErrorKey()))
                .count();
    }

    private static ErrorResponse parseError(byte[] payload) {
        try {
            return ErrorResponse.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("ErrorResponse hỏng", e);
        }
    }
}
