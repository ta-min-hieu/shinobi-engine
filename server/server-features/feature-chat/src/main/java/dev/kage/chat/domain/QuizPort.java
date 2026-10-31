package dev.kage.chat.domain;

import java.util.List;

/** Ngân hàng câu hỏi đáp tông môn (ADR-122, V102) — hiện thực ở server-database, đọc một lần lúc khởi động. */
@FunctionalInterface
public interface QuizPort {

    List<QuizQuestion> questions();
}
