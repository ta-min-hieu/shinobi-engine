package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.protocol.AcceptFriendRequestCommand;
import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.AcceptFriendRequestResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsRequest;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectFriendRequestCommand;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RejectFriendRequestResponse;
import dev.kage.protocol.RemoveFriendCommand;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.RemoveFriendResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Vòng đời bạn bè QUA DÂY giữa nhiều người chơi trên cùng một gateway (rà 2026-10-10): ai được trả
 * lời một lời mời, danh sách mỗi bên thấy gì, và ai còn nhận tin online/offline sau khi huỷ kết bạn.
 *
 * <p>Trước lớp này các handler bạn bè của gateway chỉ có đúng một test "gửi lời mời, nhận được phản
 * hồi". Lớp này xanh ngay từ lần chạy đầu — nó là bằng chứng, không phải bản vá: {@code characterId}
 * luôn lấy từ phiên nên không ai trả lời hộ được lời mời của người khác.
 */
@Timeout(30)
class FriendWireTest {

    private final SharedWorldPipelines gateway = new SharedWorldPipelines(inMemoryFriendFacade());
    private int nextSequenceId = 100;

    @AfterEach
    void stopGateway() {
        gateway.stop();
    }

    @Test
    void shouldMakeBothSidesFriends_andShowEachOtherOnline_afterAccept() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("wirealice", "Alice");
        EmbeddedChannel bob = enterWorld("wirebob", "Bob");

        assertThat(sendRequest(alice, "Bob")).isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK);
        ListPendingFriendRequestsResponse pending = listPending(bob);
        assertThat(pending.getRequestsList()).hasSize(1);
        assertThat(pending.getRequests(0).getRequesterName()).isEqualTo("Alice");
        assertThat(listPending(alice).getRequestsList())
                .as("người GỬI không thấy lời mời của chính mình trong hộp chờ")
                .isEmpty();

        assertThat(accept(bob, pending.getRequests(0).getFriendshipId()))
                .isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK);

        assertThat(listFriends(alice).getFriendsList()).singleElement().satisfies(friend -> {
            assertThat(friend.getName()).isEqualTo("Bob");
            assertThat(friend.getOnline()).isTrue();
        });
        assertThat(listFriends(bob).getFriendsList()).singleElement().satisfies(friend -> assertThat(friend.getName())
                .isEqualTo("Alice"));
        assertThat(sendRequest(bob, "Alice"))
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS);
    }

    // IDOR: friendship_id là số tăng dần, đoán được. Người thứ ba và chính người GỬI đều không được
    // chấp nhận/từ chối hộ — và lời mời phải còn nguyên sau các lần thử đó.
    @Test
    void shouldLetOnlyTheAddresseeAnswerARequest() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("idoralice", "Alice");
        EmbeddedChannel bob = enterWorld("idorbob", "Bob");
        EmbeddedChannel mallory = enterWorld("idormallory", "Mallory");
        sendRequest(alice, "Bob");
        long friendshipId = listPending(bob).getRequests(0).getFriendshipId();

        assertThat(accept(mallory, friendshipId))
                .isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
        assertThat(reject(mallory, friendshipId))
                .isEqualTo(RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
        assertThat(accept(alice, friendshipId))
                .as("người gửi tự chấp nhận lời mời của mình")
                .isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
        assertThat(reject(alice, friendshipId))
                .isEqualTo(RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);

        assertThat(listPending(bob).getRequestsList()).hasSize(1);
        assertThat(listFriends(alice).getFriendsList()).isEmpty();
        assertThat(listFriends(mallory).getFriendsList()).isEmpty();
    }

    @Test
    void shouldAnswerNotFound_forRequestIdsThatCannotExist() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("edgealice", "Alice");

        for (long friendshipId : new long[] {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertThat(accept(alice, friendshipId))
                    .as("accept %d", friendshipId)
                    .isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
            assertThat(reject(alice, friendshipId))
                    .as("reject %d", friendshipId)
                    .isEqualTo(RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_NOT_FOUND);
        }
    }

    /**
     * Mời ngược chiều một lời mời đang treo là CHẤP NHẬN nó (dòng 71(c), 2026-10-17) — trước đó Bob nhận
     * {@code REQUEST_ALREADY_PENDING} và lời mời của Alice nằm lại. Không đẻ dòng thứ hai.
     */
    @Test
    void shouldRefuseSelfRequests_andMakeFriends_whenBothSidesInvite() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("selfalice", "Alice");
        EmbeddedChannel bob = enterWorld("selfbob", "Bob");

        assertThat(sendRequest(alice, "Alice"))
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_CANNOT_FRIEND_SELF);
        assertThat(sendRequest(alice, "Bob")).isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK);
        assertThat(sendRequest(alice, "Bob"))
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING);
        assertThat(sendRequest(bob, "Alice"))
                .as("mời ngược chiều khi đã có lời mời treo ⇒ thành bạn")
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS);

        assertThat(listPending(bob).getRequestsList()).isEmpty();
        assertThat(listPending(alice).getRequestsList()).isEmpty();
        assertThat(listFriends(alice).getFriendsList()).hasSize(1);
        assertThat(listFriends(bob).getFriendsList()).hasSize(1);
        assertThat(sendRequest(bob, "Alice"))
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS);
    }

    // Tên độc không bao giờ được tới tầng DB (Postgres nổ với U+0000) hay thành lỗi 500 — mọi tên
    // sai luật tên nhân vật đều là "không có người này".
    @Test
    void shouldAnswerTargetNotFound_andLogNoError_forHostileNames() throws InvalidProtocolBufferException {
        int index = 0;
        try (ErrorLogCapture errors = ErrorLogCapture.attach()) {
            for (String name : HostileInputs.characterNames()) {
                // Mỗi tên một kết nối: trần lệnh chạm DB là theo kết nối (40 lệnh/giây).
                EmbeddedChannel alice = enterWorld("hostile" + index, "Hostile" + (char) ('A' + index));
                index++;
                assertThat(sendRequest(alice, name))
                        .as("mời %s", HostileInputs.describe(name))
                        .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND);
                assertThat(remove(alice, name))
                        .as("huỷ %s", HostileInputs.describe(name))
                        .isEqualTo(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_TARGET_NOT_FOUND);
            }
            assertThat(errors.errorsSince(0)).isEmpty();
        }
    }

    @Test
    void shouldStopPresenceEvents_onceTheFriendshipIsRemoved() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("presalice", "Alice");
        EmbeddedChannel bob = enterWorld("presbob", "Bob");
        EmbeddedChannel carol = enterWorld("prescarol", "Carol");
        befriend(alice, bob, "Bob");
        befriend(alice, carol, "Carol");

        assertThat(remove(bob, "Alice")).isEqualTo(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_OK);
        assertThat(remove(bob, "Alice"))
                .as("huỷ lần hai")
                .isEqualTo(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_NOT_FRIENDS);
        assertThat(listFriends(alice).getFriendsList())
                .extracting(friend -> friend.getName())
                .containsExactly("Carol");
        GatewayPipelineTest.readOutboundFrames(bob);
        GatewayPipelineTest.readOutboundFrames(carol);

        alice.close().syncUninterruptibly();

        assertThat(GatewayPipelineTest.readOutboundFrames(bob))
                .as("người đã huỷ kết bạn không còn nhận tin của Alice")
                .isEmpty();
        List<PacketCodec.Frame> carolFrames = GatewayPipelineTest.readOutboundFrames(carol);
        assertThat(carolFrames).hasSize(1);
        assertThat(carolFrames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE);
        assertThat(FriendOfflineEvent.parseFrom(carolFrames.getFirst().payload())
                        .getCharacterId())
                .isPositive();
        assertThat(listFriends(carol).getFriendsList())
                .singleElement()
                .satisfies(friend -> assertThat(friend.getOnline()).isFalse());
    }

    private void befriend(EmbeddedChannel requester, EmbeddedChannel addressee, String addresseeName)
            throws InvalidProtocolBufferException {
        assertThat(sendRequest(requester, addresseeName))
                .isEqualTo(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK);
        long friendshipId = listPending(addressee).getRequests(0).getFriendshipId();
        assertThat(accept(addressee, friendshipId))
                .isEqualTo(AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK);
    }

    private EmbeddedChannel enterWorld(String username, String characterName) throws InvalidProtocolBufferException {
        EmbeddedChannel channel = gateway.newChannel();
        GatewayPipelineTest.connect(channel, username);
        long characterId = GatewayPipelineTest.createCharacter(channel, characterName, 2);
        GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(channel);
        return channel;
    }

    private SendFriendRequestOutcome sendRequest(EmbeddedChannel channel, String targetName)
            throws InvalidProtocolBufferException {
        byte[] payload = SendFriendRequestCommand.newBuilder()
                .setTargetName(targetName)
                .build()
                .toByteArray();
        return SendFriendRequestResponse.parseFrom(roundTrip(
                        channel,
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE,
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE,
                        payload))
                .getOutcome();
    }

    private AcceptFriendRequestOutcome accept(EmbeddedChannel channel, long friendshipId)
            throws InvalidProtocolBufferException {
        byte[] payload = AcceptFriendRequestCommand.newBuilder()
                .setFriendshipId(friendshipId)
                .build()
                .toByteArray();
        return AcceptFriendRequestResponse.parseFrom(roundTrip(
                        channel,
                        Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_COMMAND_VALUE,
                        Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_RESPONSE_VALUE,
                        payload))
                .getOutcome();
    }

    private RejectFriendRequestOutcome reject(EmbeddedChannel channel, long friendshipId)
            throws InvalidProtocolBufferException {
        byte[] payload = RejectFriendRequestCommand.newBuilder()
                .setFriendshipId(friendshipId)
                .build()
                .toByteArray();
        return RejectFriendRequestResponse.parseFrom(roundTrip(
                        channel,
                        Opcode.OPCODE_REJECT_FRIEND_REQUEST_COMMAND_VALUE,
                        Opcode.OPCODE_REJECT_FRIEND_REQUEST_RESPONSE_VALUE,
                        payload))
                .getOutcome();
    }

    private RemoveFriendOutcome remove(EmbeddedChannel channel, String targetName)
            throws InvalidProtocolBufferException {
        byte[] payload = RemoveFriendCommand.newBuilder()
                .setTargetName(targetName)
                .build()
                .toByteArray();
        return RemoveFriendResponse.parseFrom(roundTrip(
                        channel,
                        Opcode.OPCODE_REMOVE_FRIEND_COMMAND_VALUE,
                        Opcode.OPCODE_REMOVE_FRIEND_RESPONSE_VALUE,
                        payload))
                .getOutcome();
    }

    private ListFriendsResponse listFriends(EmbeddedChannel channel) throws InvalidProtocolBufferException {
        return ListFriendsResponse.parseFrom(roundTrip(
                channel,
                Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE,
                Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE,
                ListFriendsRequest.getDefaultInstance().toByteArray()));
    }

    private ListPendingFriendRequestsResponse listPending(EmbeddedChannel channel)
            throws InvalidProtocolBufferException {
        return ListPendingFriendRequestsResponse.parseFrom(roundTrip(
                channel,
                Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_REQUEST_VALUE,
                Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_RESPONSE_VALUE,
                ListPendingFriendRequestsRequest.getDefaultInstance().toByteArray()));
    }

    /** Gửi một lệnh và trả payload của ĐÚNG MỘT gói trả lời — gói thừa hay gói lỗi đều làm test đỏ. */
    private byte[] roundTrip(EmbeddedChannel channel, int requestOpcode, int responseOpcode, byte[] payload) {
        int sequenceId = nextSequenceId++;
        GatewayPipelineTest.writeInboundFrame(
                channel, new PacketHeader(requestOpcode, ProtocolVersion.CURRENT, sequenceId), payload);
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode())
                .as("gói trả lời: %s", describe(frames.getFirst()))
                .isEqualTo(responseOpcode);
        assertThat(frames.getFirst().header().sequenceId()).isEqualTo(sequenceId);
        return frames.getFirst().payload();
    }

    private static String describe(PacketCodec.Frame frame) {
        if (frame.header().opcode() != Opcode.OPCODE_ERROR_VALUE) {
            return "opcode " + frame.header().opcode();
        }
        try {
            return "lỗi " + ErrorResponse.parseFrom(frame.payload()).getErrorKey();
        } catch (InvalidProtocolBufferException e) {
            return "gói lỗi không đọc được: " + e.getMessage();
        }
    }

    private static FriendFacade inMemoryFriendFacade() {
        FriendPort port = new InMemoryFriendPort();
        return new FriendFacade(new FriendUseCases(
                new SendFriendRequestUseCase(port),
                new AcceptFriendRequestUseCase(port),
                new RejectFriendRequestUseCase(port),
                new RemoveFriendUseCase(port),
                new ListFriendsUseCase(port),
                new ListPendingFriendRequestsUseCase(port)));
    }

    /**
     * Fake = bản in-memory THẬT của {@link FriendPort} (TESTING.md §4), giữ đúng hợp đồng của
     * {@code PostgresFriendAdapter}: một cặp nhiều nhất một dòng, chấp nhận/từ chối chỉ khớp đúng
     * người nhận của một dòng còn PENDING. Trả bản ghi bất biến nên không cần chép phòng thủ.
     */
    private static final class InMemoryFriendPort implements FriendPort {

        private final List<Friendship> rows = new ArrayList<>();
        private long nextId = 1;

        @Override
        public synchronized Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
            return rows.stream()
                    .filter(row -> row.involves(characterIdA) && row.involves(characterIdB))
                    .findFirst();
        }

        @Override
        public synchronized Friendship sendRequest(long requesterId, long addresseeId) {
            if (findBetween(requesterId, addresseeId).isPresent()) {
                throw new DuplicateFriendRequestException(requesterId, addresseeId);
            }
            Friendship created =
                    new Friendship(nextId++, requesterId, addresseeId, FriendshipStatus.PENDING, Instant.now(), null);
            rows.add(created);
            return created;
        }

        @Override
        public synchronized boolean accept(long friendshipId, long addresseeId) {
            Optional<Friendship> pending = findPending(friendshipId, addresseeId);
            pending.ifPresent(row -> rows.set(
                    rows.indexOf(row),
                    new Friendship(
                            row.id(),
                            row.requesterId(),
                            row.addresseeId(),
                            FriendshipStatus.ACCEPTED,
                            row.createdAt(),
                            Instant.now())));
            return pending.isPresent();
        }

        @Override
        public synchronized boolean reject(long friendshipId, long addresseeId) {
            Optional<Friendship> pending = findPending(friendshipId, addresseeId);
            pending.ifPresent(rows::remove);
            return pending.isPresent();
        }

        @Override
        public synchronized boolean remove(long characterId, long friendCharacterId) {
            return rows.removeIf(row -> row.status() == FriendshipStatus.ACCEPTED
                    && row.involves(characterId)
                    && row.involves(friendCharacterId));
        }

        @Override
        public synchronized List<Friendship> listAccepted(long characterId) {
            return rows.stream()
                    .filter(row -> row.status() == FriendshipStatus.ACCEPTED && row.involves(characterId))
                    .toList();
        }

        @Override
        public synchronized List<Friendship> listPendingIncoming(long characterId) {
            return rows.stream()
                    .filter(row -> row.status() == FriendshipStatus.PENDING && row.addresseeId() == characterId)
                    .toList();
        }

        private Optional<Friendship> findPending(long friendshipId, long addresseeId) {
            return rows.stream()
                    .filter(row -> row.id() == friendshipId
                            && row.addresseeId() == addresseeId
                            && row.status() == FriendshipStatus.PENDING)
                    .findFirst();
        }
    }
}
