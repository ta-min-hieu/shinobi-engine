package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.netty.channel.embedded.EmbeddedChannel;

import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Bộ fuzz XÁC ĐỊNH cho mọi opcode client→server ở gateway (rà 2026-10-04).
 *
 * <p>Với mỗi opcode × mỗi {@link Stage} kết nối, gửi {@code iterations} payload độc
 * ({@link FuzzPayloads}) rồi khẳng định bốn bất biến sau MỖI payload:
 *
 * <ol>
 *   <li>không có {@code ErrorResponse} mã {@code ≥ 500} — 5xx nghĩa là input của client làm nổ một
 *       exception mà server không lường trước (lưới cuối {@code error.server_internal} hoặc nhánh
 *       "lỗi không mong đợi" của từng handler);
 *   <li>kết nối vẫn mở — đóng nghĩa là exception đã lọt tới {@code exceptionCaught};
 *   <li>kết nối vẫn phục vụ được một {@code Ping} ngay sau đó;
 *   <li>không có log ERROR nào của {@code dev.kage.*} ({@link ErrorLogCapture}).
 * </ol>
 *
 * <p><b>Tái lập</b>: mọi thứ suy ra từ một seed. Đỏ thì báo cáo in seed, opcode, giai đoạn và
 * {@code caseSeed} của payload đầu tiên vi phạm; chạy lại đúng ca đó bằng
 * {@code -Dkage.fuzz.seed=<seed> -Dkage.fuzz.opcode=<OPCODE_...>}. Chạy nhiều vòng hơn bằng
 * {@code -Dkage.fuzz.iterations=<n>} (xem {@code server-gateway/build.gradle.kts}).
 *
 * <p>Kết nối được thay mới mỗi {@link #PAYLOADS_PER_CONNECTION} payload — dưới trần 40 lệnh chạm
 * DB/giây của {@code GatewayChannelHandler}, để bộ fuzz không tự đo cái rate limiter (vượt trần
 * liên tục thì bị ngắt là CHỦ ĐÍCH, đã có test riêng).
 */
final class OpcodeFuzzer {

    /** Giai đoạn của kết nối lúc payload độc tới. */
    enum Stage {
        /** Kết nối vừa mở, chưa {@code ConnectRequest}. */
        BEFORE_LOGIN(1L),
        /** Đã đăng nhập + có một nhân vật, chưa chọn nhân vật nào. */
        BEFORE_CHARACTER_SELECT(2L),
        /** Đã chọn nhân vật, đang ở trong world. */
        IN_WORLD(3L),
        /**
         * Như {@link #IN_WORLD}, nhưng mỗi kết nối nhận một CHUỖI opcode ngẫu nhiên trộn lẫn — bắt lỗi
         * chỉ lộ ra khi trạng thái do lệnh trước để lại (vào hầm ngục rồi chọn nhân vật, lập nhóm rồi
         * rời...). Chỉ chạy khi không lọc opcode, hoặc lọc đúng {@value #MIXED_FILTER}.
         */
        IN_WORLD_MIXED(4L);

        /** Trộn vào {@code caseSeed} — hằng số tường minh, thêm giai đoạn mới không xáo seed cũ. */
        private final long seedSalt;

        Stage(long seedSalt) {
            this.seedSalt = seedSalt;
        }

        /** Kết nối ở giai đoạn này đã đăng nhập (và có sẵn một nhân vật). */
        boolean loggedIn() {
            return this != BEFORE_LOGIN;
        }

        /** Kết nối ở giai đoạn này đã chọn nhân vật và đang trong world. */
        boolean inWorld() {
            return this == IN_WORLD || this == IN_WORLD_MIXED;
        }
    }

    /** Giá trị {@code kage.fuzz.opcode} chỉ chạy chuỗi trộn ({@link Stage#IN_WORLD_MIXED}). */
    static final String MIXED_FILTER = "MIXED";

    /** Số lệnh mỗi chuỗi trộn — dưới {@link #PAYLOADS_PER_CONNECTION}, cùng lý do rate limit. */
    static final int MIXED_SEQUENCE_LENGTH = 30;

    private static final int MIXED_HISTORY_SHOWN = 8;

    /**
     * Lệnh mà IM LẶNG là thiết kế: chiến đấu "sai điều kiện thì bỏ qua âm thầm" (combat.proto), di
     * chuyển/nhảy được trả lời bằng tick của world — mà world trong test này không tick. Mọi lệnh khác
     * có panel đang chờ đúng {@code sequenceId}; im lặng là để nó treo.
     */
    private static final Set<Opcode> SILENT_BY_DESIGN = EnumSet.of(
            Opcode.OPCODE_MOVE_COMMAND,
            Opcode.OPCODE_JUMP_COMMAND,
            Opcode.OPCODE_SET_TARGET_COMMAND,
            Opcode.OPCODE_ATTACK_COMMAND,
            Opcode.OPCODE_USE_SKILL_COMMAND);

    /** Bên A gửi phần lớn lệnh; bên B đủ thường để đáp lại lời mời/thách đấu của A. */
    private static final int FIRST_ACTOR_PERCENT = 60;

    /**
     * Một kết nối đã thiết lập xong. {@code characterName}/{@code characterId} là nhân vật của nó
     * ({@code ""}/{@code 0} khi chưa đăng nhập) — bộ fuzz gieo chúng vào {@link FuzzMemory} để lệnh
     * nhắm theo tên/id có lúc trúng một nhân vật CÓ THẬT.
     */
    record FuzzConnection(EmbeddedChannel channel, String characterName, long characterId) {}

    /** Mở một kết nối MỚI đã đi tới đúng {@code stage} (đã đọc hết gói phát lúc thiết lập). */
    @FunctionalInterface
    interface ChannelFactory {
        FuzzConnection open(Stage stage) throws InvalidProtocolBufferException;
    }

    /** Một vi phạm bất biến — {@code count} là số payload cùng triệu chứng trên cùng opcode/giai đoạn. */
    record Violation(Opcode opcode, Stage stage, String symptom, long caseSeed, String payload, int count) {}

    /** Cấu hình chạy — đọc từ system property, mặc định là ngân sách của cổng {@code check}. */
    record Settings(long seed, int iterations, String opcodeFilter) {

        static final long DEFAULT_SEED = 0x4B41_4745_F022_2026L;

        static Settings fromSystemProperties(String iterationsProperty, int defaultIterations) {
            long seed = Long.decode(System.getProperty("kage.fuzz.seed", Long.toString(DEFAULT_SEED)));
            int iterations = Integer.getInteger(iterationsProperty, defaultIterations);
            return new Settings(seed, iterations, System.getProperty("kage.fuzz.opcode", ""));
        }
    }

    static final int PAYLOADS_PER_CONNECTION = 35;

    /**
     * Sàn số loại {@code *_RESPONSE}/{@code *_EVENT} khác nhau mà payload độc nhận về (đo 2026-10-04:
     * ~50 ở tầng Port giả, ~60 ở tầng DB với ngân sách mặc định) — thấp hơn nghĩa là bộ fuzz bị chặn
     * ở cửa (thiết lập kết nối hỏng), xanh khi đó là xanh rỗng.
     */
    static final int MIN_DISTINCT_REPLIES = 40;

    private static final int PING_SEQUENCE_ID = 0x7FFF_0001;
    private static final int FUZZ_SEQUENCE_ID = 4_242;
    private static final int SERVER_ERROR_THRESHOLD = 500;
    private static final int PAYLOAD_PREVIEW_BYTES = 48;
    private static final int PAYLOAD_PREVIEW_CHARS = 240;

    /**
     * Không phải triệu chứng: server đóng kết nối vì nó đã gửi quá
     * {@code GatewayChannelHandler.MAX_PROTOCOL_VIOLATIONS} gói vi phạm giao thức — đúng thiết kế từ
     * 2026-10-11. Mọi lần đóng kết nối KHÁC vẫn là vi phạm "kết nối bị đóng".
     */
    private static final String CLOSED_AT_VIOLATION_LIMIT = "(đóng đúng thiết kế: quá giới hạn vi phạm giao thức)";

    private final Settings settings;
    private final ChannelFactory channels;
    private final ErrorLogCapture errorLog;
    private final Map<String, Violation> violations = new LinkedHashMap<>();
    // Câu trả lời ĐẦU TIÊN của mỗi payload, đếm theo loại — chứng cứ bộ fuzz đi SÂU tới handler, không
    // chỉ dội vào parse/kiểm phiên (một bộ fuzz xanh vì mọi payload đều bị chặn ở cửa là vô nghĩa).
    private final Map<String, Integer> outcomes = new TreeMap<>();

    OpcodeFuzzer(Settings settings, ChannelFactory channels, ErrorLogCapture errorLog) {
        this.settings = settings;
        this.channels = channels;
        this.errorLog = errorLog;
    }

    /** Chạy hết ngân sách; trả các vi phạm (đã gộp theo opcode × giai đoạn × triệu chứng). */
    List<Violation> run() throws InvalidProtocolBufferException {
        for (Opcode opcode : clientToServerOpcodes()) {
            if (selected(opcode.name())) {
                fuzzEveryStage(opcode);
            }
        }
        if (selected(MIXED_FILTER)) {
            for (int sequence = 0; sequence < settings.iterations(); sequence++) {
                fuzzMixedSequence(sequence);
            }
        }
        reportStrayErrors();
        return new ArrayList<>(violations.values());
    }

    private boolean selected(String name) {
        return settings.opcodeFilter().isEmpty() || settings.opcodeFilter().equals(name);
    }

    private void fuzzEveryStage(Opcode opcode) throws InvalidProtocolBufferException {
        for (Stage stage : Stage.values()) {
            if (stage != Stage.IN_WORLD_MIXED) {
                fuzz(opcode, stage);
            }
        }
    }

    private void reportStrayErrors() {
        // Log ERROR từ luồng KHÁC (tick của hầm ngục/đấu trường...) có thể rơi ngoài cửa sổ của mọi
        // payload — chưa vi phạm nào giải thích thì nó vẫn phải được báo.
        if (violations.isEmpty() && errorLog.errorCount() > 0) {
            record(new Violation(
                    Opcode.OPCODE_UNSPECIFIED,
                    Stage.IN_WORLD_MIXED,
                    "log ERROR ngoài cửa sổ payload: " + errorLog.errorsSince(0).getFirst(),
                    settings.seed(),
                    "(không gắn được với payload nào)",
                    errorLog.errorCount()));
        }
    }

    /**
     * Số payload theo loại câu trả lời đầu tiên ({@code ERROR 400 error.invalid_payload},
     * {@code OPCODE_..._RESPONSE}...).
     */
    Map<String, Integer> outcomes() {
        return Map.copyOf(outcomes);
    }

    /** Báo cáo đọc được — in kèm seed và lệnh chạy lại. */
    String report(List<Violation> found) {
        StringBuilder text = new StringBuilder()
                .append(found.size())
                .append(" vi phạm (seed=0x")
                .append(Long.toHexString(settings.seed()))
                .append(", iterations=")
                .append(settings.iterations())
                .append(")\n");
        for (Violation violation : found) {
            text.append("\n- ")
                    .append(violation.opcode())
                    .append(" @ ")
                    .append(violation.stage())
                    .append(" ×")
                    .append(violation.count())
                    .append(": ")
                    .append(violation.symptom())
                    .append("\n    caseSeed=0x")
                    .append(Long.toHexString(violation.caseSeed()))
                    .append("  payload=")
                    .append(violation.payload())
                    .append("\n    chạy lại: -Dkage.fuzz.seed=0x")
                    .append(Long.toHexString(settings.seed()))
                    .append(" -Dkage.fuzz.opcode=")
                    .append(violation.stage() == Stage.IN_WORLD_MIXED ? MIXED_FILTER : violation.opcode())
                    .append('\n');
        }
        return text.toString();
    }

    static List<Opcode> clientToServerOpcodes() {
        List<Opcode> opcodes = new ArrayList<>();
        for (Opcode opcode : Opcode.values()) {
            if (opcode != Opcode.UNRECOGNIZED && opcode != Opcode.OPCODE_UNSPECIFIED && isClientToServer(opcode)) {
                opcodes.add(opcode);
            }
        }
        return opcodes;
    }

    /** Cùng quy ước hướng với {@code OpcodeDispatchCoverageTest}/{@code OpcodeRegistryTest}. */
    private static boolean isClientToServer(Opcode opcode) {
        String name = opcode.name();
        return opcode == Opcode.OPCODE_PING || name.endsWith("_COMMAND") || name.endsWith("_REQUEST");
    }

    private void fuzz(Opcode opcode, Stage stage) throws InvalidProtocolBufferException {
        EmbeddedChannel channel = null;
        int sentOnChannel = 0;
        FuzzMemory memory = new FuzzMemory();
        try {
            for (int iteration = 0; iteration < settings.iterations(); iteration++) {
                if (channel == null || sentOnChannel >= PAYLOADS_PER_CONNECTION) {
                    close(channel);
                    channel = open(stage, memory);
                    sentOnChannel = 0;
                }
                long caseSeed = caseSeed(opcode, stage, iteration);
                byte[] payload = new FuzzPayloads(FuzzPayloads.random(caseSeed), memory).next(opcode);
                sentOnChannel++;
                String symptom;
                try {
                    symptom = sendAndCheck(channel, opcode, payload, memory);
                } catch (RuntimeException e) {
                    // Luồng khung ra hỏng (vd độ dài âm) — nói rõ CA NÀO, không chỉ stack của bộ đọc.
                    throw new IllegalStateException(
                            "ca fuzz hỏng: " + opcode + " @ " + stage + " vòng " + iteration + " seed=" + caseSeed, e);
                }
                if (CLOSED_AT_VIOLATION_LIMIT.equals(symptom)) {
                    // Đúng thiết kế (2026-10-11): server đóng kết nối gửi quá nhiều gói vi phạm giao
                    // thức. Không phải vi phạm — mở kết nối mới rồi fuzz tiếp.
                    close(channel);
                    channel = null;
                } else if (!symptom.isEmpty()) {
                    record(new Violation(opcode, stage, symptom, caseSeed, describe(opcode, payload), 1));
                    // Kết nối đã ở trạng thái hỏng (hoặc đã đóng) — ca sau bắt đầu lại sạch.
                    close(channel);
                    channel = null;
                }
            }
        } finally {
            close(channel);
        }
    }

    /**
     * HAI kết nối trong world, {@link #MIXED_SEQUENCE_LENGTH} lệnh ngẫu nhiên nối nhau, mỗi lệnh do một
     * trong hai bên gửi — lời mời/thách đấu/giao dịch của bên này có lúc được bên kia đáp lại bằng
     * đúng id vừa nhận ({@link FuzzMemory} dùng CHUNG). Vi phạm ghi kèm các lệnh ngay trước nó: triệu
     * chứng có thể do lệnh trước để lại, không do chính payload cuối.
     */
    private void fuzzMixedSequence(int sequence) throws InvalidProtocolBufferException {
        long sequenceSeed = settings.seed() ^ (sequence + 1L) * 0xD6E8_FEB8_6659_FD93L ^ Stage.IN_WORLD_MIXED.seedSalt;
        FuzzMemory memory = new FuzzMemory();
        FuzzPayloads payloads = new FuzzPayloads(FuzzPayloads.random(sequenceSeed), memory);
        List<Opcode> commands = clientToServerOpcodes();
        commands.remove(Opcode.OPCODE_PING);
        List<String> history = new ArrayList<>();
        List<EmbeddedChannel> actors = new ArrayList<>();
        try {
            actors.add(open(Stage.IN_WORLD_MIXED, memory));
            actors.add(open(Stage.IN_WORLD_MIXED, memory));
            for (int step = 0; step < MIXED_SEQUENCE_LENGTH; step++) {
                int actor = payloads.chance(FIRST_ACTOR_PERCENT) ? 0 : 1;
                Opcode opcode = payloads.pick(commands);
                byte[] payload = payloads.next(opcode);
                String symptom = sendAndCheck(actors.get(actor), opcode, payload, memory);
                if (CLOSED_AT_VIOLATION_LIMIT.equals(symptom)) {
                    // Bên này vừa bị đóng đúng thiết kế — chuỗi dừng ở đây, không ghi vi phạm.
                    return;
                }
                if (!symptom.isEmpty()) {
                    int from = Math.max(0, history.size() - MIXED_HISTORY_SHOWN);
                    String context = "bước " + step + " (bên " + (char) ('A' + actor) + ") sau "
                            + history.subList(from, history.size()) + "; ";
                    record(new Violation(
                            opcode,
                            Stage.IN_WORLD_MIXED,
                            symptom,
                            sequenceSeed,
                            context + describe(opcode, payload),
                            1));
                    return;
                }
                history.add((char) ('A' + actor) + ":" + opcode.name().substring("OPCODE_".length()));
            }
        } finally {
            for (EmbeddedChannel channel : actors) {
                close(channel);
            }
        }
    }

    private EmbeddedChannel open(Stage stage, FuzzMemory memory) throws InvalidProtocolBufferException {
        FuzzConnection connection = channels.open(stage);
        memory.rememberText(connection.characterName());
        memory.rememberNumber(connection.characterId());
        return connection.channel();
    }

    private long caseSeed(Opcode opcode, Stage stage, int iteration) {
        return settings.seed()
                ^ opcode.getNumber() * 0x9E37_79B9_7F4A_7C15L
                ^ stage.seedSalt * 0xC2B2_AE3D_27D4_EB4FL
                ^ (iteration + 1L) * 0x1656_67B1_9E37_79F9L;
    }

    /** Chuỗi rỗng = mọi bất biến giữ; khác rỗng = mô tả triệu chứng đầu tiên thấy. */
    private String sendAndCheck(EmbeddedChannel channel, Opcode opcode, byte[] payload, FuzzMemory memory)
            throws InvalidProtocolBufferException {
        int errorMark = errorLog.errorCount();
        // Lấy handler TRƯỚC khi gửi: kênh đóng rồi thì pipeline đã gỡ hết handler, hỏi sau là null.
        GatewayChannelHandler handler = channel.pipeline().get(GatewayChannelHandler.class);
        GatewayPipelineTest.writeInboundFrame(
                channel, new PacketHeader(opcode.getNumber(), ProtocolVersion.CURRENT, FUZZ_SEQUENCE_ID), payload);
        List<PacketCodec.Frame> replies = GatewayPipelineTest.readOutboundFrames(channel);
        tally(opcode, replies);
        memory.harvest(replies);
        String serverError = serverError(replies);
        if (!serverError.isEmpty()) {
            return serverError + errorLogSuffix(errorMark);
        }
        if (replies.isEmpty() && !SILENT_BY_DESIGN.contains(opcode)) {
            return "không có câu trả lời — client chờ mãi" + errorLogSuffix(errorMark);
        }
        String unspecified = unspecifiedOutcome(replies);
        if (!unspecified.isEmpty()) {
            return unspecified + errorLogSuffix(errorMark);
        }
        if (!channel.isOpen()) {
            return closedSymptom(handler, errorMark);
        }
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, PING_SEQUENCE_ID),
                PingCommand.newBuilder().setClientTimestampMs(1L).build().toByteArray());
        List<PacketCodec.Frame> afterPing = GatewayPipelineTest.readOutboundFrames(channel);
        String lateError = serverError(afterPing);
        if (!lateError.isEmpty()) {
            return lateError + errorLogSuffix(errorMark);
        }
        if (!containsPong(afterPing)) {
            return "không còn trả lời Ping" + errorLogSuffix(errorMark);
        }
        List<String> errors = errorLog.errorsSince(errorMark);
        return errors.isEmpty() ? "" : "log ERROR: " + errors.getFirst();
    }

    /** Kênh đã đóng: đúng thiết kế nếu nó vừa vượt giới hạn vi phạm giao thức, ngược lại là một vi phạm. */
    private String closedSymptom(GatewayChannelHandler handler, int errorMark) {
        if (handler != null && handler.exceededProtocolViolationLimit()) {
            return CLOSED_AT_VIOLATION_LIMIT;
        }
        return "kết nối bị đóng" + errorLogSuffix(errorMark);
    }

    /**
     * Mọi enum {@code *Outcome} đều ghi "{@code _UNSPECIFIED = 0} — KHÔNG dùng cho kết quả thật"; một
     * câu trả lời mang giá trị đó nghĩa là server rơi vào nhánh không ai gán kết quả, và client không có
     * câu nào để hiện.
     */
    private static String unspecifiedOutcome(List<PacketCodec.Frame> replies) throws InvalidProtocolBufferException {
        for (PacketCodec.Frame reply : replies) {
            Opcode opcode = Opcode.forNumber(reply.header().opcode());
            if (opcode == null || opcode == Opcode.OPCODE_ERROR || opcode == Opcode.OPCODE_PONG) {
                continue;
            }
            Message message =
                    FuzzPayloads.defaultInstanceOf(opcode).getParserForType().parseFrom(reply.payload());
            FieldDescriptor outcome = message.getDescriptorForType().findFieldByName("outcome");
            if (outcome != null
                    && outcome.getJavaType() == FieldDescriptor.JavaType.ENUM
                    && ((EnumValueDescriptor) message.getField(outcome)).getNumber() == 0) {
                return opcode + " mang outcome UNSPECIFIED";
            }
        }
        return "";
    }

    private String errorLogSuffix(int errorMark) {
        List<String> errors = errorLog.errorsSince(errorMark);
        return errors.isEmpty() ? "" : "\n    log ERROR: " + errors.getFirst();
    }

    private static String serverError(List<PacketCodec.Frame> frames) throws InvalidProtocolBufferException {
        for (PacketCodec.Frame frame : frames) {
            if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                if (error.getErrorCode() >= SERVER_ERROR_THRESHOLD) {
                    return error.getErrorCode() + " " + error.getErrorKey();
                }
            }
        }
        return "";
    }

    private void tally(Opcode sent, List<PacketCodec.Frame> replies) throws InvalidProtocolBufferException {
        String outcome = "(im lặng) " + sent;
        if (!replies.isEmpty()) {
            PacketCodec.Frame first = replies.getFirst();
            if (first.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(first.payload());
                outcome = "ERROR " + error.getErrorCode() + " " + error.getErrorKey();
            } else {
                Opcode replyOpcode = Opcode.forNumber(first.header().opcode());
                outcome = replyOpcode == null ? "opcode " + first.header().opcode() : replyOpcode.name();
            }
        }
        outcomes.merge(outcome, 1, Integer::sum);
    }

    private static boolean containsPong(List<PacketCodec.Frame> frames) {
        for (PacketCodec.Frame frame : frames) {
            if (frame.header().opcode() == Opcode.OPCODE_PONG_VALUE
                    && frame.header().sequenceId() == PING_SEQUENCE_ID) {
                return true;
            }
        }
        return false;
    }

    /** Gộp theo opcode × giai đoạn × dòng ĐẦU của triệu chứng; giữ ca đầu tiên làm mẫu tái lập. */
    private void record(Violation found) {
        String key = found.opcode() + "|" + found.stage() + "|"
                + found.symptom().lines().findFirst().orElse("");
        violations.merge(
                key,
                found,
                (first, again) -> new Violation(
                        first.opcode(),
                        first.stage(),
                        first.symptom(),
                        first.caseSeed(),
                        first.payload(),
                        first.count() + again.count()));
    }

    private static String describe(Opcode opcode, byte[] payload) {
        String hex = HexFormat.of().formatHex(payload, 0, Math.min(payload.length, PAYLOAD_PREVIEW_BYTES));
        String text;
        try {
            text = FuzzPayloads.defaultInstanceOf(opcode)
                    .getParserForType()
                    .parseFrom(payload)
                    .toString()
                    .replace('\n', ' ');
        } catch (InvalidProtocolBufferException e) {
            text = "(không parse được: " + e.getMessage() + ")";
        }
        if (text.length() > PAYLOAD_PREVIEW_CHARS) {
            text = text.substring(0, PAYLOAD_PREVIEW_CHARS) + "…";
        }
        return payload.length + "B hex=" + hex + (payload.length > PAYLOAD_PREVIEW_BYTES ? "…" : "") + " {" + text
                + "}";
    }

    private static void close(EmbeddedChannel channel) {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }
}
