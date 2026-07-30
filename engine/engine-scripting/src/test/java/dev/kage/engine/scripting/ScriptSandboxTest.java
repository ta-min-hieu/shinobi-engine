package dev.kage.engine.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

class ScriptSandboxTest {

    private final ScriptSandbox sandbox = new ScriptSandbox(100_000);

    @Test
    void shouldEvaluateSimpleExpression() {
        Value result = sandbox.eval("1 + 1", Map.of());

        assertThat(result.asInt()).isEqualTo(2);
    }

    @Test
    void shouldCallWhitelistedHostFunction_andReturnPlainData() {
        Value result = sandbox.eval("quest.killed + 1", Map.of("quest", Map.of("killed", 9)));

        assertThat(result.asInt()).isEqualTo(10);
    }

    @Test
    void shouldRejectJavaTypeAccess_whenScriptTriesToEscapeSandbox() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.lang.System')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void shouldStopInfiniteLoop_viaStatementLimit() {
        assertThatThrownBy(() -> sandbox.eval("while (true) {}", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void shouldRejectMemberAccess_onPlainEvalResult_afterContextClosed() {
        // PHÁT HIỆN THẬT (không đoán trước): Value trả về từ eval(script, hostApi) KHÔNG dùng
        // được cho object/array navigation — Context đã đóng trước khi caller nhận Value (try-
        // with-resources). Chỉ scalar (asInt/asDouble/asString) sống sót — xem test khác ở trên.
        Value result = sandbox.eval("({a: 1})", Map.of());
        assertThatThrownBy(() -> result.getMember("a")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldExtractStructuredResult_whenExtractorRunsBeforeContextCloses() {
        String text = sandbox.eval(
                "({text: 'hello', options: ['a', 'b']})",
                Map.of(),
                value -> value.getMember("text").asString() + ":"
                        + value.getMember("options").getArraySize());

        assertThat(text).isEqualTo("hello:2");
    }

    @Test
    void validateSyntaxShouldPass_whenScriptWellFormed() {
        sandbox.validateSyntax("function on_accept(ctx) { return 1 + 1; }", "test.js");
        // Không ném gì — pass.
    }

    @Test
    void validateSyntaxShouldThrow_whenScriptHasSyntaxError() {
        assertThatThrownBy(() -> sandbox.validateSyntax("function on_accept(ctx) { return 1 +", "broken.js"))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void validateSyntaxShouldNotExecuteScript_onlyParse() {
        // Nếu validateSyntax lỡ CHẠY script thay vì chỉ parse, while(true) sẽ treo test này —
        // dùng chính kịch bản shouldStopInfiniteLoop_viaStatementLimit để chứng minh KHÔNG chạy.
        sandbox.validateSyntax("while (true) {}", "infinite.js");
        // Không ném gì, không treo — pass.
    }
}
