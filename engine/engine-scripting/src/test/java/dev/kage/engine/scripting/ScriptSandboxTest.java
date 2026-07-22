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
}
