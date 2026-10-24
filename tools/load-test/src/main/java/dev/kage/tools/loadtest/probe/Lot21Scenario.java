package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.AcceptDaoLuCommand;
import dev.kage.protocol.AcceptDaoLuResponse;
import dev.kage.protocol.AcceptDiscipleCommand;
import dev.kage.protocol.AcceptDiscipleResponse;
import dev.kage.protocol.BlockCharacterCommand;
import dev.kage.protocol.BlockCharacterResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.DissolveDaoLuCommand;
import dev.kage.protocol.DissolveDaoLuResponse;
import dev.kage.protocol.Emote;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetRelationshipsRequest;
import dev.kage.protocol.GetRelationshipsResponse;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.LeaveMentorshipCommand;
import dev.kage.protocol.LeaveMentorshipResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ProposeDaoLuCommand;
import dev.kage.protocol.ProposeDaoLuResponse;
import dev.kage.protocol.RelationMemberView;
import dev.kage.protocol.RequestMentorCommand;
import dev.kage.protocol.RequestMentorResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendEmoteCommand;
import dev.kage.protocol.SendEmoteResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.UnblockCharacterCommand;
import dev.kage.protocol.UnblockCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các tính năng lô 21 phần 1 — chặn (ADR-091), biểu cảm (ADR-092), sư đồ (ADR-093), đạo lữ (ADR-094). Mỗi bước một
 * chuỗi: {@code block:<tên>}, {@code unblock:<tên>}, {@code relations}, {@code emote:<GREET|BOW|...>},
 * {@code requestMentor:<tên>}, {@code acceptDisciple:<tên>}, {@code leaveMentor:<tên>}, {@code proposeDaoLu:<tên>},
 * {@code acceptDaoLu:<tên>}, {@code dissolveDaoLu}; và ba bước kiểm chặn: {@code whisper:<tên>},
 * {@code friendRequest:<tên>}, {@code partyInvite:<tên>} (in ra lỗi {@code error.blocked} nếu bị chặn).
 */
final class Lot21Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    Lot21Scenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(List<String> steps) throws IOException {
        for (String raw : steps) {
            String step = raw.strip();
            int colon = step.indexOf(':');
            String verb = colon < 0 ? step : step.substring(0, colon);
            String arg = colon < 0 ? "" : step.substring(colon + 1);
            String result =
                    switch (verb) {
                        case "block" ->
                            ask(
                                    Opcode.OPCODE_BLOCK_CHARACTER_COMMAND_VALUE,
                                    BlockCharacterCommand.newBuilder()
                                            .setTargetName(arg)
                                            .build(),
                                    Opcode.OPCODE_BLOCK_CHARACTER_RESPONSE_VALUE,
                                    p -> BlockCharacterResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        case "unblock" ->
                            ask(
                                    Opcode.OPCODE_UNBLOCK_CHARACTER_COMMAND_VALUE,
                                    UnblockCharacterCommand.newBuilder()
                                            .setTargetName(arg)
                                            .build(),
                                    Opcode.OPCODE_UNBLOCK_CHARACTER_RESPONSE_VALUE,
                                    p -> UnblockCharacterResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        case "relations" ->
                            ask(
                                    Opcode.OPCODE_GET_RELATIONSHIPS_REQUEST_VALUE,
                                    GetRelationshipsRequest.getDefaultInstance(),
                                    Opcode.OPCODE_GET_RELATIONSHIPS_RESPONSE_VALUE,
                                    p -> describe(GetRelationshipsResponse.parseFrom(p)));
                        case "emote" ->
                            ask(
                                    Opcode.OPCODE_SEND_EMOTE_COMMAND_VALUE,
                                    SendEmoteCommand.newBuilder()
                                            .setEmote(Emote.valueOf("EMOTE_" + arg.toUpperCase(Locale.ROOT)))
                                            .build(),
                                    Opcode.OPCODE_SEND_EMOTE_RESPONSE_VALUE,
                                    p -> SendEmoteResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        default -> mentorshipOrDaoLu(verb, arg);
                    };
            session.note("lô 21 " + step + " → " + result);
        }
    }

    private String mentorshipOrDaoLu(String verb, String arg) throws IOException {
        return switch (verb) {
            case "requestMentor" ->
                ask(
                        Opcode.OPCODE_REQUEST_MENTOR_COMMAND_VALUE,
                        RequestMentorCommand.newBuilder().setMentorName(arg).build(),
                        Opcode.OPCODE_REQUEST_MENTOR_RESPONSE_VALUE,
                        p -> RequestMentorResponse.parseFrom(p).getOutcome().name());
            case "acceptDisciple" ->
                ask(
                        Opcode.OPCODE_ACCEPT_DISCIPLE_COMMAND_VALUE,
                        AcceptDiscipleCommand.newBuilder().setDiscipleName(arg).build(),
                        Opcode.OPCODE_ACCEPT_DISCIPLE_RESPONSE_VALUE,
                        p -> AcceptDiscipleResponse.parseFrom(p).getOutcome().name());
            case "leaveMentor" ->
                ask(
                        Opcode.OPCODE_LEAVE_MENTORSHIP_COMMAND_VALUE,
                        LeaveMentorshipCommand.newBuilder().setOtherName(arg).build(),
                        Opcode.OPCODE_LEAVE_MENTORSHIP_RESPONSE_VALUE,
                        p -> LeaveMentorshipResponse.parseFrom(p).getOutcome().name());
            case "proposeDaoLu" ->
                ask(
                        Opcode.OPCODE_PROPOSE_DAO_LU_COMMAND_VALUE,
                        ProposeDaoLuCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_PROPOSE_DAO_LU_RESPONSE_VALUE,
                        p -> ProposeDaoLuResponse.parseFrom(p).getOutcome().name());
            case "acceptDaoLu" ->
                ask(
                        Opcode.OPCODE_ACCEPT_DAO_LU_COMMAND_VALUE,
                        AcceptDaoLuCommand.newBuilder().setProposerName(arg).build(),
                        Opcode.OPCODE_ACCEPT_DAO_LU_RESPONSE_VALUE,
                        p -> AcceptDaoLuResponse.parseFrom(p).getOutcome().name());
            case "dissolveDaoLu" ->
                ask(
                        Opcode.OPCODE_DISSOLVE_DAO_LU_COMMAND_VALUE,
                        DissolveDaoLuCommand.getDefaultInstance(),
                        Opcode.OPCODE_DISSOLVE_DAO_LU_RESPONSE_VALUE,
                        p -> DissolveDaoLuResponse.parseFrom(p).getOutcome().name());
            default -> blockChecks(verb, arg);
        };
    }

    private String blockChecks(String verb, String arg) throws IOException {
        return switch (verb) {
            case "whisper" ->
                ask(
                        Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                        SendChatMessageCommand.newBuilder()
                                .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                                .setTargetName(arg)
                                .setBody("probe lô 21")
                                .build(),
                        Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                        p -> SendChatMessageResponse.parseFrom(p).getOutcome().name());
            case "friendRequest" ->
                ask(
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE,
                        SendFriendRequestCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE,
                        p -> SendFriendRequestResponse.parseFrom(p).getOutcome().name());
            case "partyInvite" ->
                ask(
                        Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE,
                        InvitePartyMemberCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE,
                        p -> InvitePartyMemberResponse.parseFrom(p).getOutcome().name());
            default -> "bước không hiểu";
        };
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

    private static String describe(GetRelationshipsResponse response) {
        StringBuilder line = new StringBuilder();
        line.append("sưPhụ=").append(response.hasMentor() ? brief(response.getMentor()) : "-");
        line.append(" đồĐệ=[");
        response.getDisciplesList().forEach(view -> line.append(brief(view)).append(' '));
        line.append("] đạoLữ=").append(response.hasDaoLu() ? brief(response.getDaoLu()) : "-");
        line.append(" chặn=[");
        response.getBlockedList().forEach(view -> line.append(view.getName()).append(' '));
        return line.append(']').toString();
    }

    private static String brief(RelationMemberView view) {
        return view.getName() + "/Lv" + view.getLevel();
    }
}
