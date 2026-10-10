package dev.kage.server.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import dev.kage.protocol.AcceptPartyInviteOutcome;
import dev.kage.protocol.AcceptPartyInviteResponse;
import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Một kết nối client giả của {@code DisconnectAnywhereFuzzDatabaseTest}: gom gói server gửi tới và
 * nhặt ra những id mà bước ngẫu nhiên kế tiếp cần (phiên giao dịch, lời thách đấu, lời mời nhóm) —
 * ghi thẳng vào {@link Hints} của NHÂN VẬT, nên kết nối sau của cùng nhân vật dùng tiếp được.
 */
final class DisconnectFuzzClient extends SimpleChannelInboundHandler<PacketCodec.Frame> {

    /** Id mới nhất mà một nhân vật đã thấy trên dây — sống qua mọi lần kết nối lại của nó. */
    static final class Hints {
        volatile long tradeSessionId;
        volatile long challengeId;
        volatile long partyInviteId;
        volatile long duelOpponentId;
    }

    /**
     * Những việc bài fuzz THẬT SỰ làm được, đếm theo câu trả lời của server — chống xanh rỗng: chuỗi
     * bước chỉ toàn bị từ chối thì không lần ngắt kết nối nào rơi vào giữa một việc đang dở.
     */
    static final Map<String, LongAdder> COVERAGE = new ConcurrentHashMap<>();

    private final BlockingQueue<PacketCodec.Frame> inbound = new LinkedBlockingQueue<>();
    private final Hints hints;
    private volatile Channel channel;
    private int nextSequenceId = 1;

    DisconnectFuzzClient(Hints hints) {
        this.hints = hints;
    }

    void bind(Channel connected) {
        this.channel = connected;
    }

    Channel channel() {
        return channel;
    }

    boolean isOpen() {
        Channel current = channel;
        return current != null && current.isActive();
    }

    /** Gửi không chờ gì — ghi hỏng (kênh vừa bị server đóng) là chuyện thường của bài fuzz. */
    // FutureReturnValueIgnored: cố ý không chờ — bước kế tiếp phải chạy đua với lệnh đang bay.
    @SuppressWarnings("FutureReturnValueIgnored")
    void send(int opcode, byte[] payload) {
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, nextSequenceId++);
        channel.writeAndFlush(new PacketCodec.Frame(header, payload));
    }

    /** Đóng không chờ — lần dọn phía server chạy đua với mọi thứ còn đang bay. */
    // FutureReturnValueIgnored: cố ý không chờ, xem trên.
    @SuppressWarnings("FutureReturnValueIgnored")
    void closeWithoutWaiting() {
        channel.close();
    }

    /** Gói đầu tiên mang {@code opcode} trong những gói CHƯA đọc; gói khác bị bỏ (đã nhặt id xong). */
    Optional<PacketCodec.Frame> await(int opcode, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        long remaining = timeout.toNanos();
        while (remaining > 0) {
            PacketCodec.Frame frame = inbound.poll(remaining, TimeUnit.NANOSECONDS);
            if (frame != null && frame.header().opcode() == opcode) {
                return Optional.of(frame);
            }
            remaining = deadline - System.nanoTime();
        }
        return Optional.empty();
    }

    /** Bỏ mọi gói đã tới — gọi trước một câu hỏi mà câu trả lời phải là gói MỚI. */
    void discardReceived() {
        inbound.clear();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame)
            throws InvalidProtocolBufferException {
        rememberIds(frame);
        inbound.add(frame);
    }

    private void rememberIds(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        byte[] payload = frame.payload();
        countIfDone(frame.header().opcode(), payload);
        switch (frame.header().opcode()) {
            case Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE ->
                rememberTradeSession(RequestTradeResponse.parseFrom(payload).getSessionId());
            case Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE ->
                rememberTradeSession(TradeRequestedEvent.parseFrom(payload).getSessionId());
            case Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE ->
                rememberTradeSession(GetTradeStatusResponse.parseFrom(payload).getSessionId());
            case Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE ->
                hints.challengeId =
                        DuelChallengeReceivedEvent.parseFrom(payload).getChallengeId();
            case Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE ->
                hints.duelOpponentId = DuelStartedEvent.parseFrom(payload).getOpponentCharacterId();
            case Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_RESPONSE_VALUE -> {
                ListPendingPartyInvitesResponse invites = ListPendingPartyInvitesResponse.parseFrom(payload);
                if (invites.getInvitesCount() > 0) {
                    hints.partyInviteId = invites.getInvites(0).getInviteId();
                }
            }
            default -> {
                // gói không mang id nào bài fuzz cần
            }
        }
    }

    private static void countIfDone(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        boolean done =
                switch (opcode) {
                    case Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE -> true;
                    case Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE ->
                        EnterDungeonResponse.parseFrom(payload).getOutcome()
                                == EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK;
                    case Opcode.OPCODE_ACCEPT_PARTY_INVITE_RESPONSE_VALUE ->
                        AcceptPartyInviteResponse.parseFrom(payload).getOutcome()
                                == AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_OK;
                    case Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE ->
                        AcceptTradeRequestResponse.parseFrom(payload).getOutcome()
                                == AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_OK;
                    case Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE ->
                        ConfirmTradeResponse.parseFrom(payload).getOutcome()
                                == ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED;
                    case Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE ->
                        SelectCharacterResponse.parseFrom(payload).getSuccess();
                    default -> false;
                };
        if (done) {
            COVERAGE.computeIfAbsent(Opcode.forNumber(opcode).name(), name -> new LongAdder())
                    .increment();
        }
    }

    private void rememberTradeSession(long sessionId) {
        if (sessionId > 0) {
            hints.tradeSessionId = sessionId;
        }
    }
}
