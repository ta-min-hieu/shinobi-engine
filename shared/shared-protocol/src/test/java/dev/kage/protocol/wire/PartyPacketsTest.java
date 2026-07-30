package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptPartyInviteOutcome;
import dev.kage.protocol.AcceptPartyInviteResponse;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberOutcome;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.KickPartyMemberCommand;
import dev.kage.protocol.KickPartyMemberOutcome;
import dev.kage.protocol.KickPartyMemberResponse;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.LeavePartyOutcome;
import dev.kage.protocol.LeavePartyResponse;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.protocol.RejectPartyInviteCommand;
import dev.kage.protocol.RejectPartyInviteOutcome;
import dev.kage.protocol.RejectPartyInviteResponse;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Party (1300-1399, NETWORK.md §4).
 */
class PartyPacketsTest {

    @Test
    void shouldRoundTripCreatePartyCommandAndResponse() throws InvalidProtocolBufferException {
        CreatePartyCommand command = CreatePartyCommand.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(CreatePartyCommand.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(command);

        CreatePartyResponse response = CreatePartyResponse.newBuilder()
                .setOutcome(CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK)
                .setPartyId(4L)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        CreatePartyResponse parsed = CreatePartyResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsed.getOutcome()).isEqualTo(CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK);
        assertThat(parsed.getPartyId()).isEqualTo(4L);
    }

    @Test
    void shouldRoundTripInvitePartyMemberCommand() throws InvalidProtocolBufferException {
        InvitePartyMemberCommand command =
                InvitePartyMemberCommand.newBuilder().setTargetName("naruto").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        InvitePartyMemberCommand parsed = InvitePartyMemberCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripInvitePartyMemberResponse() throws InvalidProtocolBufferException {
        InvitePartyMemberResponse response = InvitePartyMemberResponse.newBuilder()
                .setOutcome(InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_PARTY_FULL)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        InvitePartyMemberResponse parsed = InvitePartyMemberResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_PARTY_FULL);
    }

    @Test
    void shouldRoundTripAcceptPartyInviteCommand() throws InvalidProtocolBufferException {
        AcceptPartyInviteCommand command =
                AcceptPartyInviteCommand.newBuilder().setInviteId(9L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        AcceptPartyInviteCommand parsed = AcceptPartyInviteCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInviteId()).isEqualTo(9L);
    }

    @Test
    void shouldRoundTripAcceptPartyInviteResponse() throws InvalidProtocolBufferException {
        AcceptPartyInviteResponse response = AcceptPartyInviteResponse.newBuilder()
                .setOutcome(AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_ALREADY_IN_PARTY)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_ACCEPT_PARTY_INVITE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        AcceptPartyInviteResponse parsed = AcceptPartyInviteResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome())
                .isEqualTo(AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_ALREADY_IN_PARTY);
    }

    @Test
    void shouldRoundTripRejectPartyInviteCommandAndResponse() throws InvalidProtocolBufferException {
        RejectPartyInviteCommand command =
                RejectPartyInviteCommand.newBuilder().setInviteId(11L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_REJECT_PARTY_INVITE_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        RejectPartyInviteCommand parsed = RejectPartyInviteCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsed.getInviteId()).isEqualTo(11L);

        RejectPartyInviteResponse response = RejectPartyInviteResponse.newBuilder()
                .setOutcome(RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_REJECT_PARTY_INVITE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        RejectPartyInviteResponse parsedResponse = RejectPartyInviteResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripLeavePartyCommandAndResponse() throws InvalidProtocolBufferException {
        LeavePartyCommand command = LeavePartyCommand.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LEAVE_PARTY_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(LeavePartyCommand.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(command);

        LeavePartyResponse response = LeavePartyResponse.newBuilder()
                .setOutcome(LeavePartyOutcome.LEAVE_PARTY_OUTCOME_NOT_IN_PARTY)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_LEAVE_PARTY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        LeavePartyResponse parsedResponse = LeavePartyResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(LeavePartyOutcome.LEAVE_PARTY_OUTCOME_NOT_IN_PARTY);
    }

    @Test
    void shouldRoundTripKickPartyMemberCommand() throws InvalidProtocolBufferException {
        KickPartyMemberCommand command =
                KickPartyMemberCommand.newBuilder().setTargetName("sasuke").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_KICK_PARTY_MEMBER_COMMAND_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        KickPartyMemberCommand parsed = KickPartyMemberCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripKickPartyMemberResponse() throws InvalidProtocolBufferException {
        KickPartyMemberResponse response = KickPartyMemberResponse.newBuilder()
                .setOutcome(KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_CANNOT_KICK_SELF)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_KICK_PARTY_MEMBER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        KickPartyMemberResponse parsed = KickPartyMemberResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_CANNOT_KICK_SELF);
    }

    @Test
    void shouldRoundTripListPartyMembersResponse() throws InvalidProtocolBufferException {
        PartyMemberView member = PartyMemberView.newBuilder()
                .setCharacterId(3L)
                .setName("naruto")
                .setIsLeader(true)
                .setOnline(true)
                .build();
        ListPartyMembersResponse response =
                ListPartyMembersResponse.newBuilder().addMembers(member).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListPartyMembersResponse parsed = ListPartyMembersResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMembersList()).hasSize(1);
        assertThat(parsed.getMembers(0).getCharacterId()).isEqualTo(3L);
        assertThat(parsed.getMembers(0).getName()).isEqualTo("naruto");
        assertThat(parsed.getMembers(0).getIsLeader()).isTrue();
        assertThat(parsed.getMembers(0).getOnline()).isTrue();
    }

    @Test
    void shouldRoundTripListPendingPartyInvitesResponse() throws InvalidProtocolBufferException {
        PendingPartyInviteView pending = PendingPartyInviteView.newBuilder()
                .setInviteId(1L)
                .setPartyId(2L)
                .build();
        ListPendingPartyInvitesResponse response =
                ListPendingPartyInvitesResponse.newBuilder().addInvites(pending).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_RESPONSE_VALUE, ProtocolVersion.CURRENT, 8);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListPendingPartyInvitesResponse parsed = ListPendingPartyInvitesResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInvitesList()).hasSize(1);
        assertThat(parsed.getInvites(0).getInviteId()).isEqualTo(1L);
        assertThat(parsed.getInvites(0).getPartyId()).isEqualTo(2L);
    }

    @Test
    void shouldRoundTripGetMyPartyResponse() throws InvalidProtocolBufferException {
        GetMyPartyResponse response = GetMyPartyResponse.newBuilder()
                .setInParty(true)
                .setPartyId(5L)
                .setIsLeader(false)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_GET_MY_PARTY_RESPONSE_VALUE, ProtocolVersion.CURRENT, 9);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        GetMyPartyResponse parsed =
                GetMyPartyResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInParty()).isTrue();
        assertThat(parsed.getPartyId()).isEqualTo(5L);
        assertThat(parsed.getIsLeader()).isFalse();
    }
}
