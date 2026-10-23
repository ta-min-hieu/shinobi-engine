package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;

import dev.kage.protocol.CancelFriendRequestCommand;
import dev.kage.protocol.CancelFriendRequestResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Lô 18 (ADR-074) — gửi và rút lời mời kết bạn theo tên, in kết quả từng bước. {@code --friendSteps=send:Ten,
 * withdraw:Ten,withdraw:Ten} kiểm được cả nhánh rút thành công lẫn rút lần hai (không còn lời mời).
 */
final class FriendScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    FriendScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(List<String> steps) throws IOException {
        for (String step : steps) {
            String[] parts = step.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "--friendSteps cần dạng send:Ten hoặc withdraw:Ten, nhận '" + step + "'");
            }
            if ("send".equals(parts[0])) {
                send(parts[1]);
            } else if ("withdraw".equals(parts[0])) {
                withdraw(parts[1]);
            } else {
                throw new IllegalArgumentException("--friendSteps: bước lạ '" + parts[0] + "'");
            }
        }
    }

    private void send(String name) throws IOException {
        connection.send(
                Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE,
                SendFriendRequestCommand.newBuilder()
                        .setTargetName(name)
                        .build()
                        .toByteArray());
        SendFriendRequestResponse response = SendFriendRequestResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("mời kết bạn " + name + ": " + response.getOutcome());
    }

    private void withdraw(String name) throws IOException {
        connection.send(
                Opcode.OPCODE_CANCEL_FRIEND_REQUEST_COMMAND_VALUE,
                CancelFriendRequestCommand.newBuilder()
                        .setTargetName(name)
                        .build()
                        .toByteArray());
        CancelFriendRequestResponse response = CancelFriendRequestResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CANCEL_FRIEND_REQUEST_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("rút lời mời kết bạn " + name + ": " + response.getOutcome());
    }
}
