package dev.kage.engine.scripting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScriptValidatorTest {

    private final ScriptValidator validator = new ScriptValidator(new ScriptSandbox(100_000));

    @Test
    void shouldPass_whenAllScriptsWellFormed() {
        validator.validateAll(List.of("/scripts/valid_fixture.js"));
        // Không ném gì — pass.
    }

    @Test
    void shouldThrowScriptExecutionException_whenAnyScriptHasSyntaxError() {
        List<String> scripts = List.of("/scripts/valid_fixture.js", "/scripts/broken_fixture.js");

        assertThatThrownBy(() -> validator.validateAll(scripts)).isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void shouldThrowIllegalStateException_whenScriptRefNotFoundOnClasspath() {
        assertThatThrownBy(() -> validator.validateAll(List.of("/scripts/khong_ton_tai.js")))
                .isInstanceOf(IllegalStateException.class);
    }
}
