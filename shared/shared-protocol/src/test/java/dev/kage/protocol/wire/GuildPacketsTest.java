package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.ApproveGuildJoinRequestCommand;
import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.ApproveGuildJoinRequestResponse;
import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.CreateGuildResponse;
import dev.kage.protocol.DisbandGuildCommand;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.DisbandGuildResponse;
import dev.kage.protocol.GetMyGuildRequest;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.KickGuildMemberCommand;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.KickGuildMemberResponse;
import dev.kage.protocol.LeaveGuildCommand;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.LeaveGuildResponse;
import dev.kage.protocol.ListGuildMembersRequest;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsRequest;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.RejectGuildJoinRequestCommand;
import dev.kage.protocol.RejectGuildJoinRequestOutcome;
import dev.kage.protocol.RejectGuildJoinRequestResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Guild (700-799, NETWORK.md §4).
 */
class GuildPacketsTest {

    @Test
    void shouldRoundTripCreateGuildCommand() throws InvalidProtocolBufferException {
        var command = CreateGuildCommand.newBuilder().setName("Konoha").build();
        var header = new PacketHeader(Opcode.OPCODE_CREATE_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = CreateGuildCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getName()).isEqualTo("Konoha");
    }

    @Test
    void shouldRoundTripCreateGuildResponse() throws InvalidProtocolBufferException {
        var response = CreateGuildResponse.newBuilder()
                .setOutcome(CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK)
                .setGuildId(3L)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_CREATE_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = CreateGuildResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK);
        assertThat(parsed.getGuildId()).isEqualTo(3L);
    }

    @Test
    void shouldRoundTripSendGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        var command = SendGuildJoinRequestCommand.newBuilder().setGuildName("Konoha").build();
        var header =
                new PacketHeader(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = SendGuildJoinRequestCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getGuildName()).isEqualTo("Konoha");
    }

    @Test
    void shouldRoundTripSendGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        var response = SendGuildJoinRequestResponse.newBuilder()
                .setOutcome(SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND)
                .build();
        var header =
                new PacketHeader(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = SendGuildJoinRequestResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome())
                .isEqualTo(SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND);
    }

    @Test
    void shouldRoundTripApproveGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        var command = ApproveGuildJoinRequestCommand.newBuilder().setRequestId(9L).build();
        var header =
                new PacketHeader(Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = ApproveGuildJoinRequestCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestId()).isEqualTo(9L);
    }

    @Test
    void shouldRoundTripApproveGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        var response = ApproveGuildJoinRequestResponse.newBuilder()
                .setOutcome(ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD)
                .build();
        var header =
                new PacketHeader(Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = ApproveGuildJoinRequestResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome())
                .isEqualTo(ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD);
    }

    @Test
    void shouldRoundTripRejectGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        var command = RejectGuildJoinRequestCommand.newBuilder().setRequestId(11L).build();
        var header =
                new PacketHeader(Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = RejectGuildJoinRequestCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestId()).isEqualTo(11L);
    }

    @Test
    void shouldRoundTripRejectGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        var response = RejectGuildJoinRequestResponse.newBuilder()
                .setOutcome(RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK)
                .build();
        var header =
                new PacketHeader(Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = RejectGuildJoinRequestResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripLeaveGuildCommandAndResponse() throws InvalidProtocolBufferException {
        var command = LeaveGuildCommand.newBuilder().build();
        var header = new PacketHeader(Opcode.OPCODE_LEAVE_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(LeaveGuildCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload())).isEqualTo(command);

        var response = LeaveGuildResponse.newBuilder()
                .setOutcome(LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE)
                .build();
        var responseHeader = new PacketHeader(Opcode.OPCODE_LEAVE_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        var parsedResponse = LeaveGuildResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE);
    }

    @Test
    void shouldRoundTripDisbandGuildCommandAndResponse() throws InvalidProtocolBufferException {
        var command = DisbandGuildCommand.newBuilder().build();
        var header = new PacketHeader(Opcode.OPCODE_DISBAND_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 6);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(DisbandGuildCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload())).isEqualTo(command);

        var response =
                DisbandGuildResponse.newBuilder().setOutcome(DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK).build();
        var responseHeader = new PacketHeader(Opcode.OPCODE_DISBAND_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        var parsedResponse = DisbandGuildResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripKickGuildMemberCommand() throws InvalidProtocolBufferException {
        var command = KickGuildMemberCommand.newBuilder().setTargetName("sasuke").build();
        var header = new PacketHeader(Opcode.OPCODE_KICK_GUILD_MEMBER_COMMAND_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = KickGuildMemberCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripKickGuildMemberResponse() throws InvalidProtocolBufferException {
        var response = KickGuildMemberResponse.newBuilder()
                .setOutcome(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_KICK_GUILD_MEMBER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = KickGuildMemberResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF);
    }

    @Test
    void shouldRoundTripListGuildMembersResponse() throws InvalidProtocolBufferException {
        ListGuildMembersRequest.newBuilder().build(); // não field nào — build để chắc không lỗi
        var member = GuildMemberView.newBuilder()
                .setCharacterId(3L)
                .setName("naruto")
                .setIsLeader(true)
                .setOnline(true)
                .build();
        var response = ListGuildMembersResponse.newBuilder().addMembers(member).build();
        var header = new PacketHeader(Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 8);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = ListGuildMembersResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMembersList()).hasSize(1);
        assertThat(parsed.getMembers(0).getCharacterId()).isEqualTo(3L);
        assertThat(parsed.getMembers(0).getName()).isEqualTo("naruto");
        assertThat(parsed.getMembers(0).getIsLeader()).isTrue();
        assertThat(parsed.getMembers(0).getOnline()).isTrue();
    }

    @Test
    void shouldRoundTripListPendingGuildJoinRequestsResponse() throws InvalidProtocolBufferException {
        ListPendingGuildJoinRequestsRequest.newBuilder().build();
        var pending = PendingGuildJoinRequestView.newBuilder()
                .setRequestId(1L)
                .setCharacterId(2L)
                .setCharacterName("sasuke")
                .build();
        var response = ListPendingGuildJoinRequestsResponse.newBuilder().addRequests(pending).build();
        var header = new PacketHeader(
                Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = ListPendingGuildJoinRequestsResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestsList()).hasSize(1);
        assertThat(parsed.getRequests(0).getRequestId()).isEqualTo(1L);
        assertThat(parsed.getRequests(0).getCharacterId()).isEqualTo(2L);
        assertThat(parsed.getRequests(0).getCharacterName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripGetMyGuildResponse() throws InvalidProtocolBufferException {
        GetMyGuildRequest.newBuilder().build();
        var response = GetMyGuildResponse.newBuilder()
                .setInGuild(true)
                .setGuildId(5L)
                .setGuildName("Konoha")
                .setIsLeader(false)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 10);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = GetMyGuildResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInGuild()).isTrue();
        assertThat(parsed.getGuildId()).isEqualTo(5L);
        assertThat(parsed.getGuildName()).isEqualTo("Konoha");
        assertThat(parsed.getIsLeader()).isFalse();
    }
}
