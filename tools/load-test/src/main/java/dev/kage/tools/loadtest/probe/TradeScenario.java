package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.UUID;

import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Mở một phiên giao dịch giữa HAI probe — đường headless để kiểm luật "đứt mạng giữa giao dịch ⇒ huỷ,
 * bên còn lại được báo bằng {@code TradeClosedEvent}" (README dòng 40iv).
 *
 * <p>Bên {@code --tradeAccept=true} chạy TRƯỚC và đợi {@code TradeRequestedEvent} (1412); bên
 * {@code --tradeRequest=<tên>} gửi lời mời. Kịch bản CỐ Ý dừng ở "phiên đã mở": chào đồ + xác nhận đã
 * có test tích hợp phủ, còn thứ chỉ đo được khi chạy sống là chuyện gì xảy ra với phiên khi một bên
 * biến mất. {@code --tradeReport=true} hỏi lại trạng thái sau vòng quan sát.
 */
final class TradeScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    /** Nhả vòng đọc định kỳ để còn kiểm hạn chót — hết thời gian chờ không phải lỗi. */
    private static final int POLL_READ_TIMEOUT_MILLIS = 200;

    private static final long MILLIS_PER_SECOND = 1000L;

    private final GameConnection connection;
    private final ProbeSession session;
    private final int restoreReadTimeoutMillis;

    TradeScenario(GameConnection connection, ProbeSession session, int restoreReadTimeoutMillis) {
        this.connection = connection;
        this.session = session;
        this.restoreReadTimeoutMillis = restoreReadTimeoutMillis;
    }

    /** Mở phiên theo {@code plan}: chấp nhận lời mời đến, hoặc gửi lời mời; không yêu cầu thì không làm gì. */
    void open(ProbeConfig.TradePlan plan) throws IOException {
        if (plan.acceptIncoming()) {
            acceptIncoming(plan.waitSeconds());
            confirmSteps(plan.confirmTrade());
        } else if (!plan.requestTargetName().isEmpty()) {
            connection.send(
                    Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE,
                    RequestTradeCommand.newBuilder()
                            .setTargetName(plan.requestTargetName())
                            .build()
                            .toByteArray());
            connection.readUntilOpcode(Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
            session.note("đã gửi lời mời giao dịch tới " + plan.requestTargetName());
        }
    }

    /** In trạng thái phiên giao dịch (còn mở hay không, đối phương là ai) nếu {@code plan} yêu cầu. */
    void reportIfRequested(ProbeConfig.TradePlan plan) throws IOException {
        if (!plan.report()) {
            return;
        }
        connection.send(
                Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                GetTradeStatusRequest.getDefaultInstance().toByteArray());
        GetTradeStatusResponse status = GetTradeStatusResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("trạng thái giao dịch: đangGiaoDịch=" + status.getInTrade() + " phiên=" + status.getSessionId()
                + " giaiĐoạn=" + status.getPhase().name() + " đốiPhương=" + status.getCounterpartName());
    }

    /** ADR-075 — xem {@link ProbeConfig.TradePlan#confirmTrade}. */
    private void confirmSteps(java.util.List<String> steps) throws IOException {
        String idempotencyKey = UUID.randomUUID().toString();
        for (String step : steps) {
            GetTradeStatusResponse status = status();
            long version = status.getOfferVersion();
            if ("stale".equals(step)) {
                connection.send(
                        Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE,
                        SetTradeOfferCommand.newBuilder()
                                .setSessionId(status.getSessionId())
                                .build()
                                .toByteArray());
                connection.readUntilOpcode(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
                session.note("tự đặt lại lời chào: phiên bản " + version + " → " + status().getOfferVersion());
            }
            int sequenceId = connection.send(
                    Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE,
                    ConfirmTradeCommand.newBuilder()
                            .setSessionId(status.getSessionId())
                            .setIdempotencyKey(idempotencyKey)
                            .setOfferVersion(version)
                            .build()
                            .toByteArray());
            session.note("xác nhận (" + step + ", phiên bản " + version + "): " + confirmReply(sequenceId));
        }
    }

    private GetTradeStatusResponse status() throws IOException {
        connection.send(
                Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                GetTradeStatusRequest.getDefaultInstance().toByteArray());
        return GetTradeStatusResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
    }

    /** Kết quả xác nhận, hoặc {@code ErrorResponse} của ĐÚNG lệnh đó. */
    private String confirmReply(int sequenceId) throws IOException {
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE) {
                return ConfirmTradeResponse.parseFrom(frame.payload())
                        .getOutcome()
                        .name();
            }
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                if (error.getRelatedSequenceId() == sequenceId) {
                    return "BỊ TỪ CHỐI " + error.getErrorKey() + " (" + error.getErrorCode() + ")";
                }
            }
        }
        throw new IOException("Không thấy trả lời xác nhận sau " + MAX_FRAMES_TO_SKIP + " frame");
    }

    private void acceptIncoming(int waitSeconds) throws IOException {
        Optional<PacketCodec.Frame> incoming = await(Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE, waitSeconds);
        if (incoming.isEmpty()) {
            session.note("KHÔNG có lời mời giao dịch nào trong " + waitSeconds + "s — bỏ qua");
            return;
        }
        long sessionId = TradeRequestedEvent.parseFrom(incoming.get().payload()).getSessionId();
        connection.send(
                Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE,
                AcceptTradeRequestCommand.newBuilder()
                        .setSessionId(sessionId)
                        .build()
                        .toByteArray());
        connection.readUntilOpcode(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
        session.note("đã chấp nhận lời mời giao dịch phiên=" + sessionId);
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
