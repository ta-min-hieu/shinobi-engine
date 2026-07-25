package dev.kage.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.chat.domain.ChatChannel;
import dev.kage.chat.domain.ChatMessage;
import org.junit.jupiter.api.Test;

class SendChatMessageUseCaseTest {

    @Test
    void shouldReturnOkAndLogAudit_whenMessageValid() {
        FakeChatAuditPort auditPort = new FakeChatAuditPort();
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(auditPort);

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "chào mọi người");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.OK);
        assertThat(auditPort.loggedAction).isEqualTo("CHAT_MESSAGE");
    }

    @Test
    void shouldReturnMessageBlank_whenBodyBlank() {
        SendChatMessageUseCase useCase = new SendChatMessageUseCase(new FakeChatAuditPort());

        SendChatMessageUseCase.Result result = useCase.execute(5, ChatChannel.WORLD, "   ");

        assertThat(result).isEqualTo(SendChatMessageUseCase.Result.MESSAGE_BLANK);
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

        @Override
        public void log(long characterId, String action, String detailJson) {
            loggedAction = action;
        }
    }
}
