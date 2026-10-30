package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetSecurityPinStatusRequest;
import dev.kage.protocol.GetSecurityPinStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetSecurityPinCommand;
import dev.kage.protocol.SetSecurityPinResponse;
import dev.kage.protocol.VerifySecurityPinCommand;
import dev.kage.protocol.VerifySecurityPinResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các bước lô 23 (đi qua {@code --lot21=}/{@code --afterQuest=} — cùng chuỗi bước): {@code pinStatus},
 * {@code setPin:<mới>[:<cũ>]}, {@code verifyPin:<mã>}; cầu mua ở {@link Lot23MarketSteps}.
 */
final class Lot23Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final Lot23MarketSteps market;

    Lot23Scenario(GameConnection connection) {
        this.connection = connection;
        this.market = new Lot23MarketSteps(this::ask, new Lot23WorldSteps(connection, this::ask));
    }

    /** Một bước; {@code "bước không hiểu"} nếu không thuộc lô 23. */
    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "pinStatus" ->
                ask(
                        Opcode.OPCODE_GET_SECURITY_PIN_STATUS_REQUEST_VALUE,
                        GetSecurityPinStatusRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_SECURITY_PIN_STATUS_RESPONSE_VALUE,
                        p -> {
                            GetSecurityPinStatusResponse r = GetSecurityPinStatusResponse.parseFrom(p);
                            return "cóPIN=" + r.getHasPin() + " mở=" + r.getUnlocked() + " khoáTới="
                                    + r.getLockedUntilEpochMs();
                        });
            case "setPin" -> setPin(arg);
            case "verifyPin" ->
                ask(
                        Opcode.OPCODE_VERIFY_SECURITY_PIN_COMMAND_VALUE,
                        VerifySecurityPinCommand.newBuilder().setPin(arg).build(),
                        Opcode.OPCODE_VERIFY_SECURITY_PIN_RESPONSE_VALUE,
                        p -> {
                            VerifySecurityPinResponse r = VerifySecurityPinResponse.parseFrom(p);
                            return r.getOutcome() + " cònLượt=" + r.getAttemptsLeft();
                        });
            default -> market.step(verb, arg);
        };
    }

    private String setPin(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        return ask(
                Opcode.OPCODE_SET_SECURITY_PIN_COMMAND_VALUE,
                SetSecurityPinCommand.newBuilder()
                        .setNewPin(parts[0])
                        .setCurrentPin(parts.length == 2 ? parts[1] : "")
                        .build(),
                Opcode.OPCODE_SET_SECURITY_PIN_RESPONSE_VALUE,
                p -> {
                    SetSecurityPinResponse r = SetSecurityPinResponse.parseFrom(p);
                    return r.getOutcome() + " cònLượt=" + r.getAttemptsLeft();
                });
    }

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

    @FunctionalInterface
    interface Reply {
        String describe(byte[] payload) throws InvalidProtocolBufferException;
    }

    /** Hỏi một gói, chờ đúng gói trả lời (hoặc lỗi cùng sequenceId) — dùng chung cho các phần của lô 23. */
    @FunctionalInterface
    interface Asker {
        String ask(int opcode, MessageLite command, int responseOpcode, Reply reply) throws IOException;
    }
}
