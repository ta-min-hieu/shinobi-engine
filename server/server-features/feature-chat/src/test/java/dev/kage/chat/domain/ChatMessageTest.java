package dev.kage.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void shouldRejectBlankBody() {
        assertThatThrownBy(() -> new ChatMessage(1, ChatChannel.WORLD, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBodyLongerThanMax() {
        String tooLong = "a".repeat(ChatMessage.MAX_BODY_LENGTH + 1);

        assertThatThrownBy(() -> new ChatMessage(1, ChatChannel.WORLD, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptBodyAtMaxLength() {
        String atMax = "a".repeat(ChatMessage.MAX_BODY_LENGTH);

        new ChatMessage(1, ChatChannel.WORLD, atMax);
    }

    /**
     * Tin chat chứa xuống dòng từng giả được tin hệ thống (quyết định chủ dự án 2026-10-11): dòng thứ
     * hai hiện ra như một tin riêng. Dựng ký tự ngắt bằng mã điểm để mẫu thử nhìn thấy được trong mã.
     */
    @Test
    void singleLineShouldTurnEveryLineBreakAndControlCharacterIntoASpace() {
        String lineFeed = Character.toString(0x0A);
        String carriageReturn = Character.toString(0x0D);
        String tab = Character.toString(0x09);
        String lineSeparator = Character.toString(0x2028);
        String paragraphSeparator = Character.toString(0x2029);

        assertThat(ChatMessage.singleLine("chào" + lineFeed + "[Hệ thống] bạn được 9999 vàng"))
                .isEqualTo("chào [Hệ thống] bạn được 9999 vàng");
        assertThat(ChatMessage.singleLine("a" + carriageReturn + lineFeed + "b"))
                .isEqualTo("a  b");
        assertThat(ChatMessage.singleLine("a" + tab + "b" + lineSeparator + "c" + paragraphSeparator + "d"))
                .isEqualTo("a b c d");
    }

    /**
     * Dòng 71(d) (2026-10-17): dấu đảo chiều {@code U+202E} vẽ phần sau của câu ngược lại — giả được
     * tin hệ thống mà không cần xuống dòng. Ký tự độ rộng 0 và các dấu cô lập chiều cũng bị thay.
     */
    @Test
    void singleLineShouldTurnInvisibleFormatCharactersIntoASpace() {
        String rightToLeftOverride = Character.toString(0x202E);
        String zeroWidthSpace = Character.toString(0x200B);
        String firstStrongIsolate = Character.toString(0x2068);
        String popDirectionalIsolate = Character.toString(0x2069);

        assertThat(ChatMessage.singleLine("a" + rightToLeftOverride + "b")).isEqualTo("a b");
        assertThat(ChatMessage.singleLine("ad" + zeroWidthSpace + "min")).isEqualTo("ad min");
        assertThat(ChatMessage.singleLine(firstStrongIsolate + "x" + popDirectionalIsolate))
                .isEqualTo(" x ");
    }

    @Test
    void singleLineShouldLeaveAnOrdinaryMessageUntouched() {
        String ordinary = "Nguyên Anh kỳ — đi hầm ngục không? 🐉";

        assertThat(ChatMessage.singleLine(ordinary)).isSameAs(ordinary);
    }
}
