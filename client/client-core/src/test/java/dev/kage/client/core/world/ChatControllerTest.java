package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.SendChatMessageOutcome;

/**
 * {@link ChatController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code DialogueControllerTest}.
 */
class ChatControllerTest {

    private final ChatController controller = new ChatController();

    @Test
    @DisplayName("Tự đăng nhập lại: nhật ký mang sang màn mới giữ nguyên thứ tự, vẫn chịu trần MAX_LINES")
    void restoresCarriedOverHistory_keepingOnlyTheNewestLines() {
        ChatController previous = new ChatController();
        for (int i = 0; i < ChatController.MAX_LINES; i++) {
            previous.appendSystem("dòng " + i);
        }
        List<ChatController.Line> carried = new ChatHistory(previous.lines()).lines();
        controller.appendSystem("gợi ý của màn mới");

        controller.restoreLines(carried);
        assertThat(controller.lines()).containsExactlyElementsOf(previous.lines());

        List<ChatController.Line> tooMany = new java.util.ArrayList<>(carried);
        tooMany.add(new ChatController.Line(ChatChannel.CHAT_CHANNEL_UNSPECIFIED, "", "dòng mới nhất"));
        controller.restoreLines(tooMany);
        assertThat(controller.lines()).hasSize(ChatController.MAX_LINES);
        assertThat(controller.lines().get(ChatController.MAX_LINES - 1).body()).isEqualTo("dòng mới nhất");
    }

    @Test
    @DisplayName("Dòng thường đi theo kênh đang chọn, mặc định là Thế giới")
    void composesOnCurrentChannel() {
        ChatController.ComposeResult result = controller.compose("chào cả nhà");

        assertThat(result)
                .isEqualTo(new ChatController.ComposeResult.Send(ChatChannel.CHAT_CHANNEL_WORLD, "", "chào cả nhà"));
    }

    @Test
    @DisplayName("Chưa vào nhóm: xoay vòng Thế giới → Giao dịch → Khu vực, không có kênh Nhóm")
    void togglesBetweenWorldAndAreaWhenPartyless() {
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_TRADE); // ADR-222

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_AREA);

        // Kênh Nhóm KHÔNG được xuất hiện ở đây — một nút chỉ để nhận NOT_IN_PARTY là nút hỏng.
        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
    }

    @Test
    @DisplayName("Đang trong nhóm: kênh Nhóm chen vào giữa Khu vực và Thế giới")
    void includesPartyChannelWhenInParty() {
        controller.setPartyAvailable(true);

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_TRADE); // ADR-222

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_AREA);

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_PARTY);

        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
    }

    @Test
    @DisplayName("Rời nhóm trong lúc ĐANG chọn kênh Nhóm thì rơi về Thế giới ngay")
    void fallsBackToWorldWhenLeavingPartyOnPartyChannel() {
        controller.setPartyAvailable(true);
        controller.toggleChannel();
        controller.toggleChannel();
        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_PARTY);

        // Không rơi về thì câu tiếp theo gõ vào sẽ bay đi rồi bật lại lỗi, trong khi người chơi
        // tưởng mình đang nói với nhóm.
        controller.setPartyAvailable(false);

        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
    }

    @Test
    @DisplayName("Đang ở kênh Khu vực mà rời nhóm thì KHÔNG bị kéo về Thế giới oan")
    void keepsAreaChannelWhenLeavingParty() {
        controller.setPartyAvailable(true);
        controller.toggleChannel();
        controller.toggleChannel();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_AREA);

        controller.setPartyAvailable(false);

        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_AREA);
    }

    @Test
    @DisplayName("Kênh đang chọn quyết định kênh gửi")
    void composesOnAreaAfterToggle() {
        controller.toggleChannel(); // Thế giới → Giao dịch (ADR-222)
        controller.toggleChannel();

        ChatController.ComposeResult result = controller.compose("ai ở gần đây không");

        assertThat(result)
                .isEqualTo(
                        new ChatController.ComposeResult.Send(ChatChannel.CHAT_CHANNEL_AREA, "", "ai ở gần đây không"));
    }

    @Test
    @DisplayName("/w <tên> <nội dung> gửi kênh riêng, KHÔNG đổi kênh đang chọn")
    void composesPrivateWithoutChangingChannel() {
        ChatController.ComposeResult result = controller.compose("/w HuongDan xin chào bạn");

        assertThat(result)
                .isEqualTo(new ChatController.ComposeResult.Send(
                        ChatChannel.CHAT_CHANNEL_PRIVATE, "HuongDan", "xin chào bạn"));
        // Nhắn riêng một câu không được biến kênh mặc định thành riêng vĩnh viễn — câu tiếp theo gõ
        // bình thường phải quay lại Thế giới.
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
    }

    @Test
    @DisplayName("/w chỉ có tên mà không có nội dung bị chặn tại client")
    void rejectsPrivateWithoutBody() {
        assertThat(controller.compose("/w HuongDan"))
                .isEqualTo(new ChatController.ComposeResult.Reject("chat.error.private_needs_body"));
        assertThat(controller.compose("/w HuongDan    "))
                .isEqualTo(new ChatController.ComposeResult.Reject("chat.error.private_needs_body"));
    }

    @Test
    @DisplayName("Ô nhập rỗng/toàn khoảng trắng không gửi gì và cũng không báo lỗi")
    void ignoresBlankInput() {
        assertThat(controller.compose("")).isEqualTo(ChatController.ComposeResult.Nothing.INSTANCE);
        assertThat(controller.compose("     ")).isEqualTo(ChatController.ComposeResult.Nothing.INSTANCE);
    }

    @Test
    @DisplayName("Quá dài bị chặn ở client, cả kênh thường lẫn kênh riêng")
    void rejectsTooLongBody() {
        String tooLong = "x".repeat(ChatController.MAX_BODY_LENGTH + 1);

        assertThat(controller.compose(tooLong))
                .isEqualTo(new ChatController.ComposeResult.Reject("chat.error.too_long"));
        assertThat(controller.compose("/w HuongDan " + tooLong))
                .isEqualTo(new ChatController.ComposeResult.Reject("chat.error.too_long"));
    }

    @Test
    @DisplayName("Đúng trần MAX_BODY_LENGTH thì vẫn gửi được")
    void acceptsBodyAtExactLimit() {
        String exact = "x".repeat(ChatController.MAX_BODY_LENGTH);

        assertThat(controller.compose(exact))
                .isEqualTo(new ChatController.ComposeResult.Send(ChatChannel.CHAT_CHANNEL_WORLD, "", exact));
    }

    @Test
    @DisplayName("Nhật ký giữ đúng MAX_LINES dòng mới nhất, cũ trước mới sau")
    void keepsOnlyNewestLines() {
        for (int index = 0; index < ChatController.MAX_LINES + 3; index++) {
            controller.onMessage(message("Nguoi" + index, "tin " + index));
        }

        List<ChatController.Line> lines = controller.lines();
        assertThat(lines).hasSize(ChatController.MAX_LINES);
        assertThat(lines.get(0).body()).isEqualTo("tin 3");
        assertThat(lines.get(lines.size() - 1).body()).isEqualTo("tin " + (ChatController.MAX_LINES + 2));
    }

    @Test
    @DisplayName("Dòng hệ thống không có người gửi")
    void systemLineHasNoSender() {
        controller.appendSystem("Nhắn chậm thôi!");

        ChatController.Line line = controller.lines().get(0);
        assertThat(line.senderName()).isEmpty();
        assertThat(line.body()).isEqualTo("Nhắn chậm thôi!");
        assertThat(line.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_UNSPECIFIED);
    }

    @Test
    @DisplayName("Tiếng Việt có dấu đi qua sanitize nguyên vẹn (và không tạo chuỗi mới)")
    void keepsVietnameseIntact() {
        String vietnamese = "Cảm ơn cậu — hẹn gặp lại ở bãi tập!";

        assertThat(ChatController.sanitize(vietnamese)).isSameAs(vietnamese);
    }

    @Test
    @DisplayName("Ký tự font UI không vẽ được thành '?' thay vì biến mất im lặng")
    void replacesUnrenderableCharacters() {
        // Emoji + chữ Hán: người chơi khác gõ được, font UI (FontFactory.VIETNAMESE_CHARACTERS)
        // KHÔNG có glyph — không đổi thì người nhận thấy một câu cụt mà không hiểu vì sao.
        assertThat(ChatController.sanitize("hello 世界")).isEqualTo("hello ??");
        assertThat(ChatController.sanitize("ok😀")).isEqualTo("ok??"); // emoji = 2 char UTF-16
    }

    @Test
    @DisplayName("Tin nhắn từ mạng được sanitize cả tên lẫn nội dung")
    void sanitizesIncomingMessage() {
        controller.onMessage(message("Ninja世", "chào 界"));

        ChatController.Line line = controller.lines().get(0);
        assertThat(line.senderName()).isEqualTo("Ninja?");
        assertThat(line.body()).isEqualTo("chào ?");
    }

    @Test
    @DisplayName("OK không sinh dòng hệ thống; mọi kết quả khác đều có lời giải thích")
    void mapsEveryOutcomeToAMessage() {
        assertThat(ChatController.outcomeMessageKey(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK))
                .isEmpty();
        for (SendChatMessageOutcome outcome : SendChatMessageOutcome.values()) {
            if (outcome == SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK) {
                continue;
            }
            assertThat(ChatController.outcomeMessageKey(outcome))
                    .as("outcome %s phải có khoá i18n", outcome)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("clear() xoá nhật ký VÀ trả kênh về mặc định")
    void clearResetsEverything() {
        controller.toggleChannel();
        controller.onMessage(message("Ai Do", "tin cũ"));

        controller.clear();

        assertThat(controller.lines()).isEmpty();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
    }

    private static ChatMessageEvent message(String senderName, String body) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderId(1L)
                .setSenderName(senderName)
                .setBody(body)
                .build();
    }
}
