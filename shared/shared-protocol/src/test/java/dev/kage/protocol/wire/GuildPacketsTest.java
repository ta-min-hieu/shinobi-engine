package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ApproveGuildJoinRequestCommand;
import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.ApproveGuildJoinRequestResponse;
import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.CreateGuildResponse;
import dev.kage.protocol.DisbandGuildCommand;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.DisbandGuildResponse;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.KickGuildMemberCommand;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.KickGuildMemberResponse;
import dev.kage.protocol.LeaveGuildCommand;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.LeaveGuildResponse;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.RejectGuildJoinRequestCommand;
import dev.kage.protocol.RejectGuildJoinRequestOutcome;
import dev.kage.protocol.RejectGuildJoinRequestResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestResponse;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Guild (700-799, NETWORK.md §4).
 */
class GuildPacketsTest {

    @Test
    void shouldRoundTripCreateGuildCommand() throws InvalidProtocolBufferException {
        CreateGuildCommand command =
                CreateGuildCommand.newBuilder().setName("Konoha").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CREATE_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        CreateGuildCommand parsed =
                CreateGuildCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getName()).isEqualTo("Konoha");
    }

    @Test
    void shouldRoundTripCreateGuildResponse() throws InvalidProtocolBufferException {
        CreateGuildResponse response = CreateGuildResponse.newBuilder()
                .setOutcome(CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK)
                .setGuildId(3L)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CREATE_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        CreateGuildResponse parsed = CreateGuildResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK);
        assertThat(parsed.getGuildId()).isEqualTo(3L);
    }

    @Test
    void shouldRoundTripSendGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        SendGuildJoinRequestCommand command =
                SendGuildJoinRequestCommand.newBuilder().setGuildName("Konoha").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        SendGuildJoinRequestCommand parsed = SendGuildJoinRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getGuildName()).isEqualTo("Konoha");
    }

    @Test
    void shouldRoundTripSendGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        SendGuildJoinRequestResponse response = SendGuildJoinRequestResponse.newBuilder()
                .setOutcome(SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        SendGuildJoinRequestResponse parsed = SendGuildJoinRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome())
                .isEqualTo(SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND);
    }

    @Test
    void shouldRoundTripApproveGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        ApproveGuildJoinRequestCommand command =
                ApproveGuildJoinRequestCommand.newBuilder().setRequestId(9L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ApproveGuildJoinRequestCommand parsed = ApproveGuildJoinRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestId()).isEqualTo(9L);
    }

    @Test
    void shouldRoundTripApproveGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        ApproveGuildJoinRequestResponse response = ApproveGuildJoinRequestResponse.newBuilder()
                .setOutcome(ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ApproveGuildJoinRequestResponse parsed = ApproveGuildJoinRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome())
                .isEqualTo(ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD);
    }

    @Test
    void shouldRoundTripRejectGuildJoinRequestCommand() throws InvalidProtocolBufferException {
        RejectGuildJoinRequestCommand command =
                RejectGuildJoinRequestCommand.newBuilder().setRequestId(11L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        RejectGuildJoinRequestCommand parsed = RejectGuildJoinRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestId()).isEqualTo(11L);
    }

    @Test
    void shouldRoundTripRejectGuildJoinRequestResponse() throws InvalidProtocolBufferException {
        RejectGuildJoinRequestResponse response = RejectGuildJoinRequestResponse.newBuilder()
                .setOutcome(RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        RejectGuildJoinRequestResponse parsed = RejectGuildJoinRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripLeaveGuildCommandAndResponse() throws InvalidProtocolBufferException {
        LeaveGuildCommand command = LeaveGuildCommand.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LEAVE_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(LeaveGuildCommand.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(command);

        LeaveGuildResponse response = LeaveGuildResponse.newBuilder()
                .setOutcome(LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_LEAVE_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        LeaveGuildResponse parsedResponse = LeaveGuildResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE);
    }

    @Test
    void shouldRoundTripDisbandGuildCommandAndResponse() throws InvalidProtocolBufferException {
        DisbandGuildCommand command = DisbandGuildCommand.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_DISBAND_GUILD_COMMAND_VALUE, ProtocolVersion.CURRENT, 6);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(DisbandGuildCommand.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(command);

        DisbandGuildResponse response = DisbandGuildResponse.newBuilder()
                .setOutcome(DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_DISBAND_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        DisbandGuildResponse parsedResponse = DisbandGuildResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripKickGuildMemberCommand() throws InvalidProtocolBufferException {
        KickGuildMemberCommand command =
                KickGuildMemberCommand.newBuilder().setTargetName("sasuke").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_KICK_GUILD_MEMBER_COMMAND_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        KickGuildMemberCommand parsed = KickGuildMemberCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripKickGuildMemberResponse() throws InvalidProtocolBufferException {
        KickGuildMemberResponse response = KickGuildMemberResponse.newBuilder()
                .setOutcome(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_KICK_GUILD_MEMBER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        KickGuildMemberResponse parsed = KickGuildMemberResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF);
    }

    @Test
    void shouldRoundTripListGuildMembersResponse() throws InvalidProtocolBufferException {
        GuildMemberView member = GuildMemberView.newBuilder()
                .setCharacterId(3L)
                .setName("naruto")
                .setIsLeader(true)
                .setOnline(true)
                .build();
        ListGuildMembersResponse response =
                ListGuildMembersResponse.newBuilder().addMembers(member).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 8);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListGuildMembersResponse parsed = ListGuildMembersResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMembersList()).hasSize(1);
        assertThat(parsed.getMembers(0).getCharacterId()).isEqualTo(3L);
        assertThat(parsed.getMembers(0).getName()).isEqualTo("naruto");
        assertThat(parsed.getMembers(0).getIsLeader()).isTrue();
        assertThat(parsed.getMembers(0).getOnline()).isTrue();
    }

    @Test
    void shouldRoundTripListPendingGuildJoinRequestsResponse() throws InvalidProtocolBufferException {
        PendingGuildJoinRequestView pending = PendingGuildJoinRequestView.newBuilder()
                .setRequestId(1L)
                .setCharacterId(2L)
                .setCharacterName("sasuke")
                .build();
        ListPendingGuildJoinRequestsResponse response = ListPendingGuildJoinRequestsResponse.newBuilder()
                .addRequests(pending)
                .build();
        PacketHeader header = new PacketHeader(
                Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListPendingGuildJoinRequestsResponse parsed = ListPendingGuildJoinRequestsResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestsList()).hasSize(1);
        assertThat(parsed.getRequests(0).getRequestId()).isEqualTo(1L);
        assertThat(parsed.getRequests(0).getCharacterId()).isEqualTo(2L);
        assertThat(parsed.getRequests(0).getCharacterName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripGetMyGuildResponse() throws InvalidProtocolBufferException {
        GetMyGuildResponse response = GetMyGuildResponse.newBuilder()
                .setInGuild(true)
                .setGuildId(5L)
                .setGuildName("Konoha")
                .setIsLeader(false)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 10);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        GetMyGuildResponse parsed =
                GetMyGuildResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInGuild()).isTrue();
        assertThat(parsed.getGuildId()).isEqualTo(5L);
        assertThat(parsed.getGuildName()).isEqualTo("Konoha");
        assertThat(parsed.getIsLeader()).isFalse();
    }
}
