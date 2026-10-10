package dev.kage.engine.scripting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Biên timeout: luồng canh nổ ĐÚNG lúc eval/extractor vừa xong, nên {@code close(true)} của luồng
 * huỷ chạy đồng thời với {@code close()} của try-with-resources hoặc với extractor đang đọc
 * {@link Value}. Hợp đồng với caller trên tick thread (SkillSystem/BossAiSystem chỉ bắt
 * {@link ScriptExecutionException}): eval CHỈ được trả giá trị hoặc ném đúng loại đó — bất kỳ
 * {@code IllegalStateException}/{@code PolyglotException} trần nào lọt ra là mất trọn một tick.
 */
class ScriptSandboxTimeoutRaceTest {

    private static final int ITERATIONS = 3000;

    /** Thời gian chạy rải quanh trần 1–5ms: có lần xong trước, có lần bị cắt, có lần cắt sát biên. */
    private static final String STRADDLING_SCRIPT = "let s = 0; for (let i = 0; i < n; i++) { s += i; }"
            + " ({ get effect() { let t = 0; for (let j = 0; j < n; j++) { t += j; } return 'damage'; },"
            + " get value() { return s; }, stat: 'atk', duration: 1, armor_pierce: 0 })";

    @Test
    @Timeout(300)
    void timeoutBoundary_evalOnlyReturnsOrThrowsScriptExecutionException() {
        ConcurrentLinkedQueue<Throwable> uncaught = new ConcurrentLinkedQueue<>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
        int completed = 0;
        int cutOff = 0;
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                ScriptSandbox sandbox = new ScriptSandbox(100_000_000L, Duration.ofMillis(1 + i % 5));
                long loopSize = 200L + (i * 37L) % 20_000L;
                try {
                    String read = sandbox.eval(
                            STRADDLING_SCRIPT, Map.of("n", loopSize), ScriptSandboxTimeoutRaceTest::readEverything);
                    assertThat(read).startsWith("damage");
                    completed++;
                } catch (ScriptExecutionException expected) {
                    // Bất kỳ loại lỗi KHÁC đi thẳng ra khỏi test và làm nó đỏ — đó chính là phép thử.
                    cutOff++;
                }
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }

        assertThat(uncaught).as("luồng huỷ không được ném lỗi không ai bắt").isEmpty();
        // Bài test chỉ có nghĩa nếu CẢ HAI phía của biên đều xảy ra.
        assertThat(completed).isPositive();
        assertThat(cutOff).isPositive();
    }

    /** Giống SkillSystem: đọc nhiều member, mỗi lần đọc getter là chạy code khách. */
    private static String readEverything(Value value) {
        StringBuilder out = new StringBuilder(value.getMember("effect").asString());
        for (String key : value.getMemberKeys()) {
            out.append(':').append(value.getMember(key));
        }
        return out.toString();
    }
}
