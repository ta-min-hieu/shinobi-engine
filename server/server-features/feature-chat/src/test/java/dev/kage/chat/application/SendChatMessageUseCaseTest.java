package dev.kage.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMessage;

class SendChatMessageUseCaseTest {

    @Test
    void shouldReturnOkAndLogAudit_whenMessageValid() {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(auditPort);

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "chào mọi người");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.OK);
        assertThat(auditPort.loggedAction).isEqualTo("CHAT_MESSAGE");
    }

    /** Dòng 71(e): audit có nội dung tin (đã thoát JSON) và người nhận tin riêng. */
    @Test
    void shouldAuditTheBodyAndTheRecipient_ofAPrivateMessage() {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(auditPort);

        useCase.execute(5, ChatChannel.PRIVATE, "nói \"nhỏ\" \\ thôi", java.util.OptionalLong.of(9));

        assertThat(auditPort.loggedDetail)
                .isEqualTo("{\"channel\":\"PRIVATE\",\"recipientId\":9,\"body\":\"nói \\\"nhỏ\\\" \\\\ thôi\"}");
    }

    @Test
    void shouldAuditTheBodyWithoutRecipient_ofAWorldMessage() {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();

        new SendChatMessageUseCase(auditPort).execute(5, ChatChannel.WORLD, "chào");

        assertThat(auditPort.loggedDetail).isEqualTo("{\"channel\":\"WORLD\",\"body\":\"chào\"}");
    }

    @Test
    void shouldReturnMessageBlank_whenBodyBlank() {
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(new FakeChatAuditPort());

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "   ");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_BLANK);
    }

    /**
     * Lỗi thật (2026-10-02): {@link String#isBlank()} chỉ nhận khoảng trắng của
     * {@link Character#isWhitespace}, nên tin TOÀN ký tự vô hình (khoảng trắng không ngắt, ký tự
     * độ rộng 0, ký tự điều khiển) lọt qua thành một dòng chat trống phát đi cả server.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {"\u00A0\u00A0", "\u200B\u200B\u200B", "\u2007\u202F", "\uFEFF", " \u200D\u00A0 ", "\u0000\u0007"
            })
    void shouldReturnMessageBlank_whenBodyHasOnlyInvisibleCharacters(String body) {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(auditPort);

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, body);

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_BLANK);
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldReturnOk_whenVisibleTextSurroundedByInvisibleCharacters() {
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(new FakeChatAuditPort());

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "\u00A0chào\u200B");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.OK);
    }

    @Test
    void shouldReturnMessageTooLong_whenBodyExceedsMax() {
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(new FakeChatAuditPort());
        String tooLong = "a".repeat(ChatMessage.MAX_BODY_LENGTH + 1);

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, tooLong);

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_TOO_LONG);
    }

    @Test
    void shouldReturnContainsBannedWord_andNotLogAudit_whenBodyHasBannedWord() {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(auditPort);

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "mày ngu quá");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.CONTAINS_BANNED_WORD);
        assertThat(auditPort.loggedAction).isNull();
    }

    static final class FakeChatAuditPort implements ChatAuditPort {
        private String loggedAction;
        private String loggedDetail;

        @Override
        public void log(long characterId, String action, String detailJson) {
            loggedAction = action;
            loggedDetail = detailJson;
        }
    }
}
