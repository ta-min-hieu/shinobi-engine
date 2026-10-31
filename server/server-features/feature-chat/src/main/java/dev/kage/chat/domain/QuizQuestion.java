package dev.kage.chat.domain;

import java.util.Set;

/**
 * Một câu hỏi đáp tông môn (ADR-122, bảng {@code quiz_question}) — chữ câu hỏi và đáp án hiển thị là khoá i18n; các
 * cách trả lời được chấp nhận đã chuẩn hoá bằng {@link QuizRules#normalize}.
 */
public record QuizQuestion(String code, String questionKey, String answerKey, Set<String> accepted) {

    public QuizQuestion {
        accepted = Set.copyOf(accepted);
    }

    /** Câu chat này có phải đáp án đúng không. */
    public boolean matches(String chatBody) {
        return accepted.contains(QuizRules.normalize(chatBody));
    }
}
