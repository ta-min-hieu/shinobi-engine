package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Cổng build: fuzz MỌI opcode client→server qua pipeline thật ({@link GatewayPipelineTest#newPipeline()},
 * Facade thật trên Port giả) — xem {@link OpcodeFuzzer} cho bất biến và cách chạy lại một ca đỏ.
 *
 * <p>Tầng không-DB: nhanh, phủ rộng, bắt mọi thứ nổ TRƯỚC adapter (parse, ánh xạ enum, value
 * object, use case). Thứ chỉ nổ ở Postgres thật (ràng buộc cột, tràn kiểu, NUL...) thuộc về
 * {@link GatewayOpcodeFuzzDatabaseTest}.
 *
 * <p>Ngân sách mặc định giữ cổng {@code check} dưới vài chục giây; chạy tay nhiều vòng hơn bằng
 * {@code ./gradlew :server:server-gateway:test --tests '*GatewayOpcodeFuzzTest' -Dkage.fuzz.iterations=500}.
 */
@Timeout(120)
class GatewayOpcodeFuzzTest {

    private static final int DEFAULT_ITERATIONS = 40;
    private static final String FUZZ_USERNAME = "fuzzer";
    private static final String FUZZ_CHARACTER_NAME = "FuzzHero";

    @Test
    void everyClientOpcodeShouldSurviveHostilePayloads_inEveryConnectionStage() throws InvalidProtocolBufferException {
        OpcodeFuzzer.Settings settings =
                OpcodeFuzzer.Settings.fromSystemProperties("kage.fuzz.iterations", DEFAULT_ITERATIONS);
        List<OpcodeFuzzer.Violation> violations;
        String report;
        long distinctReplies;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            OpcodeFuzzer fuzzer = new OpcodeFuzzer(settings, GatewayOpcodeFuzzTest::openAt, errorLog);
            violations = fuzzer.run().stream()
                    .filter(violation -> !isFakePortGap(violation))
                    .toList();
            report = fuzzer.report(violations);
            distinctReplies = fuzzer.outcomes().keySet().stream()
                    .filter(outcome -> outcome.startsWith("OPCODE_"))
                    .count();
        }
        assertThat(violations).as(report).isEmpty();
        // Chống xanh RỖNG: thiết lập kết nối hỏng thì mọi payload chỉ nhận 401 và bộ fuzz "không thấy lỗi".
        assertThat(distinctReplies)
                .as("số loại phản hồi thành công khác nhau — bộ fuzz phải đi tới handler thật")
                .isGreaterThanOrEqualTo(OpcodeFuzzer.MIN_DISTINCT_REPLIES);
    }

    /**
     * Port giả của {@link GatewayPipelineTest} cố ý không cài nhiều thao tác (bang, nhóm, giao dịch,
     * thư...) — chúng ném {@code UnsupportedOperationException("không dùng ở test này")}. Payload đi
     * TỚI được đó nghĩa là đã qua hết lớp kiểm của gateway và use case; phần còn lại thuộc tầng DB
     * thật ({@link GatewayOpcodeFuzzDatabaseTest}), không phải lỗi ở đây.
     */
    private static boolean isFakePortGap(OpcodeFuzzer.Violation violation) {
        return violation.symptom().contains("java.lang.UnsupportedOperationException: không dùng ở test này");
    }

    private static OpcodeFuzzer.FuzzConnection openAt(OpcodeFuzzer.Stage stage) throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        long characterId = 0;
        if (stage.loggedIn()) {
            GatewayPipelineTest.connect(channel, FUZZ_USERNAME);
            characterId = GatewayPipelineTest.createCharacter(channel, FUZZ_CHARACTER_NAME, 2);
            if (stage.inWorld()) {
                GatewayPipelineTest.selectCharacter(channel, characterId, 3);
            }
        }
        GatewayPipelineTest.readOutboundFrames(channel); // bỏ mọi gói phát kèm lúc thiết lập
        return new OpcodeFuzzer.FuzzConnection(channel, stage.loggedIn() ? FUZZ_CHARACTER_NAME : "", characterId);
    }
}
