package dev.kage.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfanityFilterTest {

    @Test
    void shouldDetectBannedWord_caseInsensitive() {
        assertThat(ProfanityFilter.containsBannedWord("mày NGU vừa thôi")).isTrue();
    }

    @Test
    void shouldAllowCleanMessage() {
        assertThat(ProfanityFilter.containsBannedWord("chào mọi người")).isFalse();
    }

    /**
     * Lỗi thật (2026-10-02): so khớp CHUỖI CON nên từ cấm "ngu" chặn mọi chữ BẮT ĐẦU bằng nó —
     * gồm cả cảnh giới "Nguyên Anh" (ADR-038), họ "Nguyễn" và "nguy hiểm". Người chơi Việt không
     * gõ nổi tên cảnh giới của chính mình trong kênh chat.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "vừa đột phá Nguyên Anh",
                "Nguyễn Văn A đâu rồi",
                "chỗ này nguy hiểm lắm",
                "thiếu nguyên liệu luyện đan",
                "nguồn linh mạch ở phía tây",
                "NGUYÊN ANH"
            })
    void shouldAllowWordsThatOnlyStartWithBannedWord(String message) {
        assertThat(ProfanityFilter.containsBannedWord(message)).isFalse();
    }

    /** Bộ gõ trên một số máy gửi dạng TỔ HỢP (NFD): "ngủ" = n g u + dấu hỏi rời — không phải "ngu". */
    @Test
    void shouldAllowDecomposedVietnameseWord() {
        String decomposed = Normalizer.normalize("buồn ngủ quá", Normalizer.Form.NFD);

        assertThat(ProfanityFilter.containsBannedWord(decomposed)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ngu", "đồ ngu!", "NGU.", "ngu,quá", "đúng là chó chết", "CHÓ CHẾT"})
    void shouldStillDetectBannedWordStandingAlone(String message) {
        assertThat(ProfanityFilter.containsBannedWord(message)).isTrue();
    }

    @Test
    void shouldDetectBannedPhrase_whenTypedDecomposed() {
        String decomposed = Normalizer.normalize("đồ chó chết", Normalizer.Form.NFD);

        assertThat(ProfanityFilter.containsBannedWord(decomposed)).isTrue();
    }

    /** Dòng 41(i): từ "nặng" viết dính chữ/số vẫn bị chặn — bản nguyên-từ làm lọt các kiểu này. */
    @ParameterizedTest
    @ValueSource(strings = {"thằngngu", "đồ thằngngu123", "THẰNGNGU", "chóchết", "đồchóchếtkia"})
    void shouldDetectHeavyWordsGluedToOtherLetters(String message) {
        assertThat(ProfanityFilter.containsBannedWord(message)).isTrue();
    }
}
