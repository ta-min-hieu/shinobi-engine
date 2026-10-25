package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.AcceptSwornCommand;
import dev.kage.protocol.AcceptSwornResponse;
import dev.kage.protocol.BuyReputationItemCommand;
import dev.kage.protocol.BuyReputationItemResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ClaimActivityChestCommand;
import dev.kage.protocol.ClaimActivityChestResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetActivityRequest;
import dev.kage.protocol.GetActivityResponse;
import dev.kage.protocol.GetBestiaryRequest;
import dev.kage.protocol.GetBestiaryResponse;
import dev.kage.protocol.GetReputationRequest;
import dev.kage.protocol.GetReputationResponse;
import dev.kage.protocol.InviteSwornCommand;
import dev.kage.protocol.InviteSwornResponse;
import dev.kage.protocol.LeaveSwornCommand;
import dev.kage.protocol.LeaveSwornResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RedeemGiftCodeCommand;
import dev.kage.protocol.RedeemGiftCodeResponse;
import dev.kage.protocol.ReportPlayerCommand;
import dev.kage.protocol.ReportPlayerResponse;
import dev.kage.protocol.ReportReason;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
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
            default -> collections(verb, arg);
        };
    }

    /** Phần 2: sổ sưu tầm, danh vọng, kết nghĩa. */
    private String collections(String verb, String arg) throws IOException {
        return switch (verb) {
            case "bestiary" ->
                ask(
                        Opcode.OPCODE_GET_BESTIARY_REQUEST_VALUE,
                        GetBestiaryRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BESTIARY_RESPONSE_VALUE,
                        p -> describe(GetBestiaryResponse.parseFrom(p)));
            case "reputation" ->
                ask(
                        Opcode.OPCODE_GET_REPUTATION_REQUEST_VALUE,
                        GetReputationRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_REPUTATION_RESPONSE_VALUE,
                        p -> describe(GetReputationResponse.parseFrom(p)));
            case "buyRep" ->
                ask(
                        Opcode.OPCODE_BUY_REPUTATION_ITEM_COMMAND_VALUE,
                        BuyReputationItemCommand.newBuilder()
                                .setItemTemplateId(Long.parseLong(arg))
                                .build(),
                        Opcode.OPCODE_BUY_REPUTATION_ITEM_RESPONSE_VALUE,
                        p -> {
                            BuyReputationItemResponse r = BuyReputationItemResponse.parseFrom(p);
                            return r.getOutcome() + " " + describe(r.getStatus());
                        });
            case "swornInvite" ->
                ask(
                        Opcode.OPCODE_INVITE_SWORN_COMMAND_VALUE,
                        InviteSwornCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_INVITE_SWORN_RESPONSE_VALUE,
                        p -> InviteSwornResponse.parseFrom(p).getOutcome().name());
            case "swornAccept" ->
                ask(
                        Opcode.OPCODE_ACCEPT_SWORN_COMMAND_VALUE,
                        AcceptSwornCommand.newBuilder().setInviterName(arg).build(),
                        Opcode.OPCODE_ACCEPT_SWORN_RESPONSE_VALUE,
                        p -> AcceptSwornResponse.parseFrom(p).getOutcome().name());
            case "swornLeave" ->
                ask(
                        Opcode.OPCODE_LEAVE_SWORN_COMMAND_VALUE,
                        LeaveSwornCommand.getDefaultInstance(),
                        Opcode.OPCODE_LEAVE_SWORN_RESPONSE_VALUE,
                        p -> LeaveSwornResponse.parseFrom(p).getOutcome().name());
            case "swornChat" -> swornChat(arg);
            default -> "bước không hiểu";
        };
    }

    /** Gửi một tin kênh kết nghĩa rồi chờ chính tin đó dội về (server gửi cho mọi huynh đệ, gồm người gửi). */
    private String swornChat(String body) throws IOException {
        int sequenceId = connection.send(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_SWORN)
                        .setBody(body)
                        .build()
                        .toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int got = frame.header().opcode();
            if (got == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE
                    && frame.header().sequenceId() == sequenceId) {
                SendChatMessageResponse r = SendChatMessageResponse.parseFrom(frame.payload());
                if (r.getOutcome() != SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK) {
                    return r.getOutcome().name();
                }
            }
            if (got == Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
                ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
                if (event.getChannel() == ChatChannel.CHAT_CHANNEL_SWORN) {
                    return "dội về: " + event.getSenderName() + ": " + event.getBody();
                }
            }
        }
        return "không thấy tin kết nghĩa";
    }

    private static String describe(GetBestiaryResponse r) {
        StringBuilder line = new StringBuilder("trang=" + r.getEntriesCount());
        r.getEntriesList().stream().filter(e -> e.getKills() > 0).forEach(e -> line.append(" | ")
                .append(e.getMonsterCode())
                .append(' ')
                .append(e.getKills())
                .append('/')
                .append(e.getKillsRequired())
                .append(e.getUnlocked() ? " MỞ" : ""));
        return line.toString();
    }

    private static String describe(GetReputationResponse r) {
        StringBuilder line = new StringBuilder("tíchLuỹ=" + r.getEarned() + " còn=" + r.getAvailable() + " bậc="
                + r.getTier() + " bậcKế=" + r.getNextTierAt());
        r.getItemsList().forEach(i -> line.append(" | ")
                .append(i.getItemTemplateId())
                .append(':')
                .append(i.getItemCode())
                .append(" giá=")
                .append(i.getPrice())
                .append(" bậc=")
                .append(i.getRequiredTier()));
        return line.toString();
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
