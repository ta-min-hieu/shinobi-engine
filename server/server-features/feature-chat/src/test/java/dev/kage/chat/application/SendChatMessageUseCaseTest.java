package dev.kage.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMessage;
import org.junit.jupiter.api.Test;

class SendChatMessageUseCaseTest {

    @Test
    void shouldReturnOkAndLogAudit_whenMessageValid() {
        var auditPort = new FakeChatAuditPort();
        var useCase = new SendChatMessageUseCase(auditPort);

        var result = useCase.execute(5, ChatChannel.WORLD, "chào mọi người");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.OK);
        assertThat(auditPort.loggedAction).isEqualTo("CHAT_MESSAGE");
    }

    @Test
    void shouldReturnMessageBlank_whenBodyBlank() {
        var useCase = new SendChatMessageUseCase(new FakeChatAuditPort());

        var result = useCase.execute(5, ChatChannel.WORLD, "   ");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_BLANK);
    }

    @Test
    void shouldReturnMessageTooLong_whenBodyExceedsMax() {
        var useCase = new SendChatMessageUseCase(new FakeChatAuditPort());
        var tooLong = "a".repeat(ChatMessage.MAX_BODY_LENGTH + 1);

        var result = useCase.execute(5, ChatChannel.WORLD, tooLong);

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_TOO_LONG);
    }

    @Test
    void shouldReturnContainsBannedWord_andNotLogAudit_whenBodyHasBannedWord() {
        var auditPort = new FakeChatAuditPort();
        var useCase = new SendChatMessageUseCase(auditPort);

        var result = useCase.execute(5, ChatChannel.WORLD, "mày ngu quá");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.CONTAINS_BANNED_WORD);
        assertThat(auditPort.loggedAction).isNull();
    }

    static final class FakeChatAuditPort implements ChatAuditPort {
        private String loggedAction;

        @Override
        public void log(long characterId, String action, String detailJson) {
            loggedAction = action;
        }
    }
}
