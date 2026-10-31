package dev.kage.server.database.chat;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.kage.chat.domain.QuizPort;
import dev.kage.chat.domain.QuizQuestion;
import dev.kage.chat.domain.QuizRules;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link QuizPort} (ADR-122, V102) — ngân hàng câu hỏi là tham số, đọc MỘT lần rồi giữ. */
public final class PostgresQuizAdapter implements QuizPort {

    private static final String QUESTIONS =
            "SELECT code, question_key, answer_key, accepted FROM quiz_question ORDER BY id";

    private static final Pattern BAR = Pattern.compile("[|]");

    private final DataSource dataSource;
    private volatile List<QuizQuestion> cached;

    public PostgresQuizAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<QuizQuestion> questions() {
        List<QuizQuestion> loaded = cached;
        if (loaded == null) {
            loaded = List.copyOf(JdbcOperations.query(
                    dataSource,
                    QUESTIONS,
                    rs -> new QuizQuestion(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            BAR.splitAsStream(rs.getString(4))
                                    .map(QuizRules::normalize)
                                    .filter(answer -> !answer.isEmpty())
                                    .collect(Collectors.toUnmodifiableSet()))));
            cached = loaded;
        }
        return loaded;
    }
}
