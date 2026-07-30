package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptFriendRequestCommand;
import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.AcceptFriendRequestResponse;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsRequest;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PendingFriendRequestView;
import dev.kage.protocol.RejectFriendRequestCommand;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RejectFriendRequestResponse;
import dev.kage.protocol.RemoveFriendCommand;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.RemoveFriendResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Friend (1100-1199, NETWORK.md §4).
 */
class FriendPacketsTest {

    @Test
    void shouldRoundTripSendFriendRequestCommand() throws InvalidProtocolBufferException {
        SendFriendRequestCommand command =
                SendFriendRequestCommand.newBuilder().setTargetName("naruto").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        SendFriendRequestCommand parsed = SendFriendRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripSendFriendRequestResponse() throws InvalidProtocolBufferException {
        SendFriendRequestResponse response = SendFriendRequestResponse.newBuilder()
                .setOutcome(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        SendFriendRequestResponse parsed = SendFriendRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS);
    }

    @Test
    void shouldRoundTripAcceptFriendRequestCommand() throws InvalidProtocolBufferException {
        AcceptFriendRequestCommand command =
                AcceptFriendRequestCommand.newBuilder().setFriendshipId(7L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        AcceptFriendRequestCommand parsed = AcceptFriendRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getFriendshipId()).isEqualTo(7L);
    }

    @Test
    void shouldRoundTripAcceptFriendRequestResponse() throws InvalidProtocolBufferException {
        AcceptFriendRequestResponse response = AcceptFriendRequestResponse.newBuilder()
                .setOutcome(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        AcceptFriendRequestResponse parsed = AcceptFriendRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripRejectFriendRequestCommand() throws InvalidProtocolBufferException {
        RejectFriendRequestCommand command =
                RejectFriendRequestCommand.newBuilder().setFriendshipId(9L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_REJECT_FRIEND_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        RejectFriendRequestCommand parsed = RejectFriendRequestCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getFriendshipId()).isEqualTo(9L);
    }

    @Test
    void shouldRoundTripRejectFriendRequestResponse() throws InvalidProtocolBufferException {
        RejectFriendRequestResponse response = RejectFriendRequestResponse.newBuilder()
                .setOutcome(RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_REJECT_FRIEND_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        RejectFriendRequestResponse parsed = RejectFriendRequestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripRemoveFriendCommand() throws InvalidProtocolBufferException {
        RemoveFriendCommand command =
                RemoveFriendCommand.newBuilder().setTargetName("sasuke").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_REMOVE_FRIEND_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        RemoveFriendCommand parsed = RemoveFriendCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripRemoveFriendResponse() throws InvalidProtocolBufferException {
        RemoveFriendResponse response = RemoveFriendResponse.newBuilder()
                .setOutcome(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_NOT_FRIENDS)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_REMOVE_FRIEND_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        RemoveFriendResponse parsed = RemoveFriendResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_NOT_FRIENDS);
    }

    @Test
    void shouldRoundTripListFriendsRequest() throws InvalidProtocolBufferException {
        ListFriendsRequest request = ListFriendsRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        ListFriendsRequest parsed =
                ListFriendsRequest.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed).isEqualTo(request);
    }

    @Test
    void shouldRoundTripListFriendsResponse() throws InvalidProtocolBufferException {
        FriendView friend = FriendView.newBuilder()
                .setCharacterId(3L)
                .setName("naruto")
                .setOnline(true)
                .build();
        ListFriendsResponse response =
                ListFriendsResponse.newBuilder().addFriends(friend).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListFriendsResponse parsed = ListFriendsResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getFriendsList()).hasSize(1);
        assertThat(parsed.getFriends(0).getCharacterId()).isEqualTo(3L);
        assertThat(parsed.getFriends(0).getName()).isEqualTo("naruto");
        assertThat(parsed.getFriends(0).getOnline()).isTrue();
    }

    @Test
    void shouldRoundTripListPendingFriendRequestsRequest() throws InvalidProtocolBufferException {
        ListPendingFriendRequestsRequest request =
                ListPendingFriendRequestsRequest.newBuilder().build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_REQUEST_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        ListPendingFriendRequestsRequest parsed = ListPendingFriendRequestsRequest.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed).isEqualTo(request);
    }

    @Test
    void shouldRoundTripListPendingFriendRequestsResponse() throws InvalidProtocolBufferException {
        PendingFriendRequestView pending = PendingFriendRequestView.newBuilder()
                .setFriendshipId(1L)
                .setRequesterId(2L)
                .setRequesterName("sasuke")
                .build();
        ListPendingFriendRequestsResponse response = ListPendingFriendRequestsResponse.newBuilder()
                .addRequests(pending)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListPendingFriendRequestsResponse parsed = ListPendingFriendRequestsResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRequestsList()).hasSize(1);
        assertThat(parsed.getRequests(0).getFriendshipId()).isEqualTo(1L);
        assertThat(parsed.getRequests(0).getRequesterId()).isEqualTo(2L);
        assertThat(parsed.getRequests(0).getRequesterName()).isEqualTo("sasuke");
    }

    @Test
    void shouldRoundTripFriendOnlineEvent() throws InvalidProtocolBufferException {
        FriendOnlineEvent event =
                FriendOnlineEvent.newBuilder().setCharacterId(11L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        FriendOnlineEvent parsed =
                FriendOnlineEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCharacterId()).isEqualTo(11L);
    }

    @Test
    void shouldRoundTripFriendOfflineEvent() throws InvalidProtocolBufferException {
        FriendOfflineEvent event =
                FriendOfflineEvent.newBuilder().setCharacterId(12L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        FriendOfflineEvent parsed =
                FriendOfflineEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getCharacterId()).isEqualTo(12L);
    }
}
