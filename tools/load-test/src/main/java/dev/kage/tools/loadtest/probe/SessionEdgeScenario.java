package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Ba lệnh lẻ gửi từ TRONG world, mỗi lệnh kiểm một luật mà trước đây probe không có đường chạm tới:
 * gỡ trang bị (túi đầy ⇒ từ chối — README dòng 65a), gửi chat (ép một dòng — 41ii), và đăng nhập lại
 * trên kết nối đang chơi ({@code error.already_in_world} — 68c).
 *
 * <p>Gom chung một lớp vì cả ba cùng dạng "gửi một lệnh, câu trả lời tự thành dòng TSV qua
 * {@link ProbeSession}" — không kịch bản nào có trạng thái riêng đáng một lớp.
 */
final class SessionEdgeScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private static final String SLOT_NAME_PREFIX = "EQUIPMENT_SLOT_";
    private static final String CHANNEL_NAME_PREFIX = "CHAT_CHANNEL_";

    /** Hai ký tự {@code \n} gõ trên dòng lệnh — đổi thành xuống dòng THẬT để thử luật ép một dòng. */
    private static final String ESCAPED_LINE_BREAK = "\\n";

    private final GameConnection connection;
    private final ProbeSession session;

    SessionEdgeScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /** Các bước chạy TRƯỚC khi đi bộ: gỡ đồ, rồi thử đăng nhập lại. */
    void runBeforeWalking(ProbeConfig.EdgePlan plan) throws IOException {
        for (String slotName : plan.unequipSlots()) {
            unequip(slotName);
        }
        if (plan.reloginInWorld()) {
            relogin(plan.reloginUsername());
        }
    }

    /** Gửi chat nếu {@code plan} có nội dung — chạy sau khi đã tới chỗ, để kênh AREA đo đúng vị trí. */
    void sendChat(ProbeConfig.EdgePlan plan) throws IOException {
        if (plan.chatBody().isEmpty()) {
            return;
        }
        ChatChannel channel =
                ChatChannel.valueOf(CHANNEL_NAME_PREFIX + plan.chatChannel().toUpperCase(Locale.ROOT));
        connection.send(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(channel)
                        .setBody(plan.chatBody().replace(ESCAPED_LINE_BREAK, "\n"))
                        .build()
                        .toByteArray());
        connection.readUntilOpcode(Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP);
        session.note("đã gửi chat kênh " + channel.name());
    }

    private void unequip(String slotName) throws IOException {
        EquipmentSlot slot = EquipmentSlot.valueOf(SLOT_NAME_PREFIX + slotName.toUpperCase(Locale.ROOT));
        connection.send(
                Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE,
                UnequipItemCommand.newBuilder().setSlot(slot).build().toByteArray());
        UnequipItemResponse response = UnequipItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("gỡ ô " + slot.name() + ": success=" + response.getSuccess() + " errorKey='"
                + response.getErrorKey() + "'");
    }

    /**
     * Gửi {@code ConnectRequest} LẦN HAI trên kết nối đang ở world. Câu trả lời (4 nếu server nhận,
     * 5 nếu từ chối) tự thành dòng TSV; đọc tới đúng một trong hai rồi thôi.
     */
    private void relogin(String username) throws IOException {
        session.note("gửi ConnectRequest lần hai (username=" + username + ") khi đang ở world");
        connection.send(
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            int opcode = connection.readFrame().header().opcode();
            if (opcode == Opcode.OPCODE_ERROR_VALUE || opcode == Opcode.OPCODE_CONNECT_RESPONSE_VALUE) {
                return;
            }
        }
        throw new IOException("Không thấy câu trả lời cho ConnectRequest lần hai sau " + MAX_FRAMES_TO_SKIP + " frame");
    }
}
