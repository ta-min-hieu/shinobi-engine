package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ClaimActivityChestCommand;
import dev.kage.protocol.ClaimActivityChestResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetActivityRequest;
import dev.kage.protocol.GetActivityResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RedeemGiftCodeCommand;
import dev.kage.protocol.RedeemGiftCodeResponse;
import dev.kage.protocol.ReportPlayerCommand;
import dev.kage.protocol.ReportPlayerResponse;
import dev.kage.protocol.ReportReason;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các bước lô 22 (đi qua {@code --lot21=} — cùng chuỗi bước): {@code gm:<lệnh không có /gm>} (in dòng trả lời GM đầu
 * tiên), {@code giftcode:<MÃ>}, {@code report:<tên>:<chat|cheat|scam|other>}, {@code activity},
 * {@code claimChest:<mốc>}.
 */
final class Lot22Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;

    Lot22Scenario(GameConnection connection) {
        this.connection = connection;
    }

    /** Một bước; {@code "bước không hiểu"} nếu không thuộc lô 22. */
    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "gm" -> gm(arg);
            case "giftcode" ->
                ask(
                        Opcode.OPCODE_REDEEM_GIFT_CODE_COMMAND_VALUE,
                        RedeemGiftCodeCommand.newBuilder().setCode(arg).build(),
                        Opcode.OPCODE_REDEEM_GIFT_CODE_RESPONSE_VALUE,
                        p -> RedeemGiftCodeResponse.parseFrom(p).getOutcome().name());
            case "report" -> report(arg);
            case "activity" ->
                ask(
                        Opcode.OPCODE_GET_ACTIVITY_REQUEST_VALUE,
                        GetActivityRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_ACTIVITY_RESPONSE_VALUE,
                        p -> describe(GetActivityResponse.parseFrom(p)));
            case "claimChest" ->
                ask(
                        Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_COMMAND_VALUE,
                        ClaimActivityChestCommand.newBuilder()
                                .setMilestone(Integer.parseInt(arg))
                                .build(),
                        Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_RESPONSE_VALUE,
                        p -> {
                            ClaimActivityChestResponse r = ClaimActivityChestResponse.parseFrom(p);
                            return r.getOutcome() + " " + describe(r.getStatus());
                        });
            default -> "bước không hiểu";
        };
    }

    private String report(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        ReportReason reason = parts.length == 2
                ? ReportReason.valueOf("REPORT_REASON_" + parts[1].toUpperCase(java.util.Locale.ROOT))
                : ReportReason.REPORT_REASON_UNSPECIFIED;
        return ask(
                Opcode.OPCODE_REPORT_PLAYER_COMMAND_VALUE,
                ReportPlayerCommand.newBuilder()
                        .setTargetName(parts[0])
                        .setReason(reason)
                        .setNote("probe")
                        .build(),
                Opcode.OPCODE_REPORT_PLAYER_RESPONSE_VALUE,
                p -> ReportPlayerResponse.parseFrom(p).getOutcome().name());
    }

    /** Gửi {@code /gm <arg>} lên kênh thế giới rồi chờ dòng trả lời hệ thống đầu tiên. */
    private String gm(String arg) throws IOException {
        connection.send(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                        .setBody("/gm " + arg)
                        .build()
                        .toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            if (frame.header().opcode() == Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
                ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
                if (event.getSenderName().isEmpty()) {
                    return event.getBody();
                }
            }
        }
        return "không thấy trả lời GM";
    }

    private static String describe(GetActivityResponse r) {
        StringBuilder line = new StringBuilder("điểm=" + r.getPoints());
        r.getActivitiesList().forEach(a -> line.append(" | ")
                .append(a.getCode())
                .append(' ')
                .append(a.getCount())
                .append('/')
                .append(a.getMaxCount()));
        r.getChestsList().forEach(c -> line.append(" | rương")
                .append(c.getMilestone())
                .append(c.getClaimed() ? " đãNhận" : c.getReached() ? " đủ" : " chưa"));
        return line.toString();
    }

    @FunctionalInterface
    private interface Reply {
        String describe(byte[] payload) throws InvalidProtocolBufferException;
    }

    /** Gửi một lệnh, chờ phản hồi của nó hoặc {@code ErrorResponse} của ĐÚNG lệnh đó. */
    private String ask(int opcode, MessageLite command, int responseOpcode, Reply reply) throws IOException {
        int sequenceId = connection.send(opcode, command.toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int got = frame.header().opcode();
            if (got == responseOpcode) {
                return reply.describe(frame.payload());
            }
            if (got == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                if (error.getRelatedSequenceId() == sequenceId) {
                    return "LỖI " + error.getErrorCode() + " " + error.getErrorKey();
                }
            }
        }
        throw new IOException("Không thấy opcode " + responseOpcode + " sau " + MAX_FRAMES_TO_SKIP + " frame");
    }
}
