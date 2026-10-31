package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuizQuestionEvent;
import dev.kage.protocol.QuizRevealEvent;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bước hỏi đáp tông môn (ADR-122): {@code quiz:<giây>:<khoá câu>=<đáp án>;...} — chờ câu hỏi (808) tới trong số giây
 * cho trước; câu nào có trong bảng thì gõ đáp án vào kênh thế giới, rồi in gói chốt (809). Câu không có trong bảng thì
 * chỉ chờ hết giờ để thấy gói chốt "không ai đúng". In mọi gói 808/809 thấy được.
 */
final class Lot23QuizSteps {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final Pattern PAIRS = Pattern.compile(";");

    private final GameConnection connection;

    Lot23QuizSteps(GameConnection connection) {
        this.connection = connection;
    }

    String quiz(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        long deadline = System.currentTimeMillis() + Long.parseLong(parts[0]) * MILLIS_PER_SECOND;
        Map<String, String> answers = new HashMap<>();
        if (parts.length == 2) {
            PAIRS.splitAsStream(parts[1])
                    .map(pair -> pair.split("=", 2))
                    .filter(pair -> pair.length == 2)
                    .forEach(pair -> answers.put(pair[0], pair[1]));
        }
        StringBuilder seen = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            seen.append(describe(frame, answers));
        }
        return seen.isEmpty() ? "không thấy câu hỏi nào" : seen.toString().strip();
    }

    private String describe(PacketCodec.Frame frame, Map<String, String> answers) throws IOException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_QUIZ_QUESTION_EVENT_VALUE) {
            QuizQuestionEvent ask = QuizQuestionEvent.parseFrom(frame.payload());
            String answer = answers.get(ask.getQuestionKey());
            if (answer == null) {
                return " [HỎI " + ask.getIndex() + ' ' + ask.getQuestionKey() + ']';
            }
            connection.send(
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                    SendChatMessageCommand.newBuilder()
                            .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                            .setBody(answer)
                            .build()
                            .toByteArray());
            return " [HỎI " + ask.getIndex() + ' ' + ask.getQuestionKey() + " → gõ \"" + answer + "\"]";
        }
        if (opcode == Opcode.OPCODE_QUIZ_REVEAL_EVENT_VALUE) {
            QuizRevealEvent reveal = QuizRevealEvent.parseFrom(frame.payload());
            return " [CHỐT " + reveal.getIndex() + ' ' + reveal.getAnswerKey() + " thắng="
                    + (reveal.getWinnerName().isEmpty() ? "-" : reveal.getWinnerName()) + " vàng="
                    + reveal.getRewardGold() + ']';
        }
        return opcode == Opcode.OPCODE_NEW_MAIL_EVENT_VALUE ? " [THƯ MỚI]" : "";
    }
}
