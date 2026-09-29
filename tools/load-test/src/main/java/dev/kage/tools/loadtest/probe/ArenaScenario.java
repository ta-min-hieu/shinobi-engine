package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;

import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.ExitDuelCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Vào trận Arena 1v1 — đường headless để kiểm ADR-051 cho đấu trường (vào trận ⇒ 308 ARENA/REQUESTED,
 * chốt trận ⇒ 308 MAIN/DUEL_ENDED). Arena cần HAI người nên chạy HAI probe: một bên
 * {@code --challengeDuel=<tên>}, một bên {@code --acceptDuel=true}. Bên chấp nhận phải chạy TRƯỚC —
 * thách một người chưa online nhận {@code TARGET_OFFLINE}.
 *
 * <p>Cả hai bên đợi {@code DuelStartedEvent} (1610) rồi mới trả quyền cho vòng quan sát, để
 * {@code --attackTarget=<characterId đối thủ>} nhắm đúng người đang đứng cùng World.
 *
 * <p>Mọi frame đọc ở đây đã tự thành dòng TSV qua {@link ProbeSession}; kịch bản này chỉ ghi mốc.
 */
final class ArenaScenario {

    /** Nhả vòng đọc định kỳ để còn kiểm hạn chót — hết thời gian chờ không phải lỗi. */
    private static final int POLL_READ_TIMEOUT_MILLIS = 200;

    private static final long MILLIS_PER_SECOND = 1000L;

    /** Đủ cho chốt trận (ghi rating vào DB) + 308 + 1611. */
    private static final int SETTLE_SECONDS = 3;

    private final GameConnection connection;
    private final ProbeSession session;
    private final int restoreReadTimeoutMillis;

    ArenaScenario(GameConnection connection, ProbeSession session, int restoreReadTimeoutMillis) {
        this.connection = connection;
        this.session = session;
        this.restoreReadTimeoutMillis = restoreReadTimeoutMillis;
    }

    /** Vào trận theo {@code plan}: chấp nhận, thách, hoặc không làm gì nếu không được yêu cầu. */
    void enter(ProbeConfig.DuelPlan plan) throws IOException {
        if (plan.acceptIncoming()) {
            acceptIncoming(plan.waitSeconds());
        } else if (plan.isRequested()) {
            challenge(plan.challengeTargetName(), plan.waitSeconds());
        }
    }

    /** Thách {@code targetName}, rồi đợi đối phương chấp nhận (tối đa {@code waitSeconds}). */
    void challenge(String targetName, int waitSeconds) throws IOException {
        connection.send(
                Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE,
                ChallengeDuelCommand.newBuilder()
                        .setTargetName(targetName)
                        .build()
                        .toByteArray());
        Optional<PacketCodec.Frame> response = await(Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE, waitSeconds);
        if (response.isEmpty()) {
            session.note("KHÔNG nhận được ChallengeDuelResponse — bỏ qua Arena");
            return;
        }
        ChallengeDuelResponse challenged =
                ChallengeDuelResponse.parseFrom(response.get().payload());
        session.note("thách đấu " + targetName + ": " + challenged.getOutcome().name() + " challengeId="
                + challenged.getChallengeId());
        awaitDuelStarted(waitSeconds);
    }

    /** Đợi lời thách đến (tối đa {@code waitSeconds}), chấp nhận, rồi đợi trận bắt đầu. */
    void acceptIncoming(int waitSeconds) throws IOException {
        Optional<PacketCodec.Frame> incoming = await(Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE, waitSeconds);
        if (incoming.isEmpty()) {
            session.note("KHÔNG có lời thách nào trong " + waitSeconds + "s — bỏ qua Arena");
            return;
        }
        long challengeId =
                DuelChallengeReceivedEvent.parseFrom(incoming.get().payload()).getChallengeId();
        connection.send(
                Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE,
                AcceptDuelCommand.newBuilder()
                        .setChallengeId(challengeId)
                        .build()
                        .toByteArray());
        Optional<PacketCodec.Frame> response = await(Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE, waitSeconds);
        String outcome = response.isEmpty()
                ? "không có phản hồi"
                : AcceptDuelResponse.parseFrom(response.get().payload())
                        .getOutcome()
                        .name();
        session.note("chấp nhận thách đấu challengeId=" + challengeId + ": " + outcome);
        awaitDuelStarted(waitSeconds);
    }

    /**
     * Bỏ cuộc (tính THUA) nếu {@code plan} yêu cầu, rồi đọc tiếp {@link #SETTLE_SECONDS} giây: 1607 +
     * 308 MAIN/DUEL_ENDED + 1611 tới sau lệnh này (chốt trận chạm DB trên executor riêng).
     */
    void forfeitIfRequested(ProbeConfig.DuelPlan plan) throws IOException {
        if (!plan.forfeitAfterObserve()) {
            return;
        }
        session.note("bỏ cuộc (ExitDuelCommand)");
        connection.send(
                Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE,
                ExitDuelCommand.getDefaultInstance().toByteArray());
        // Opcode 0 không tồn tại — đọc cho hết cửa sổ, mọi frame vẫn thành dòng TSV qua ProbeSession.
        await(0, SETTLE_SECONDS);
    }

    private void awaitDuelStarted(int waitSeconds) throws IOException {
        boolean started =
                await(Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE, waitSeconds).isPresent();
        session.note(started ? "trận Arena đã bắt đầu" : "trận Arena KHÔNG bắt đầu trong " + waitSeconds + "s");
    }

    private Optional<PacketCodec.Frame> await(int opcode, int waitSeconds) throws IOException {
        connection.setReadTimeoutMillis(POLL_READ_TIMEOUT_MILLIS);
        try {
            long deadline = System.currentTimeMillis() + waitSeconds * MILLIS_PER_SECOND;
            while (System.currentTimeMillis() < deadline) {
                try {
                    PacketCodec.Frame frame = connection.readFrame();
                    if (frame.header().opcode() == opcode) {
                        return Optional.of(frame);
                    }
                } catch (SocketTimeoutException expected) {
                    // Chưa có frame nào — xem javadoc POLL_READ_TIMEOUT_MILLIS, vòng lặp đi tiếp.
                }
            }
            return Optional.empty();
        } finally {
            connection.setReadTimeoutMillis(restoreReadTimeoutMillis);
        }
    }
}
