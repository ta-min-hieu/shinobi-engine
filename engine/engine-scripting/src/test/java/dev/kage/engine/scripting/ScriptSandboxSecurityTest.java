package dev.kage.engine.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Bộ test thoát script sandbox (OWASP.md §4 TOC mục 4, SCRIPTING.md §4 "Script KHÔNG được").
 *
 * <p>Không lặp lại happy-path đã có ở {@link ScriptSandboxTest} (bao gồm
 * {@code shouldRejectJavaTypeAccess_whenScriptTriesToEscapeSandbox} và
 * {@code shouldStopInfiniteLoop_viaStatementLimit}) — lớp này KIỂM CHỨNG THÊM từng mục "Script
 * KHÔNG được" bằng một vector tấn công cụ thể, độc lập, đúng như CLAUDE.md §0 yêu cầu trước khi
 * xem sandbox {@code CONSTRAINED}-equivalent là đã full-strength.
 *
 * <p>Không có test nào ở đây gọi thành công một API nguy hiểm — mọi assertion là "phải ném lỗi
 * TRƯỚC KHI lệnh nguy hiểm chạy" (VD {@code Runtime.exec} không bao giờ tới lượt chạy vì
 * {@code Java.type} đã ném ReferenceError trước).
 */
class ScriptSandboxSecurityTest {

    private final ScriptSandbox sandbox = new ScriptSandbox(100_000);

    // --- 1. Host access (HostAccess.NONE) -----------------------------------------------------

    @Test
    void hostAccess_javaGlobalObject_shouldBeUndefined() {
        // Không chỉ Java.type() phải ném lỗi (đã test ở ScriptSandboxTest) — bản thân global
        // "Java" không được TỒN TẠI dưới HostAccess.NONE.
        Value result = sandbox.eval("typeof Java", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }

    @Test
    void hostAccess_runtimeExec_shouldBeBlocked_beforeProcessSpawns() {
        // Vector nguy hiểm nhất: Runtime.getRuntime().exec(...) chạy lệnh hệ thống. Java.type
        // phải ném lỗi TRƯỚC KHI biểu thức đi tới .getRuntime()/.exec — không có tiến trình nào
        // thực sự được spawn dù test này có PASS hay FAIL.
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.lang.Runtime').getRuntime().exec('cmd')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void hostAccess_processBuilder_shouldBeBlocked() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.lang.ProcessBuilder')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void hostAccess_functionConstructorEscape_shouldNotReachJavaClassLoader() {
        // Chiêu JS cổ điển để lấy global object thật (không qua "this"): dùng constructor của
        // Function. Dưới sandbox này KHÔNG có object Java thô nào bị lộ (SCRIPTING.md §4 "điểm
        // chết người" — object Java có method trả object khác → tới Class → ClassLoader), nên
        // global object lấy được vẫn chỉ là global object JS thuần, KHÔNG có "Java"/"Polyglot".
        Value result = sandbox.eval(
                "(function(){ return typeof (function(){}).constructor('return this')().Java; })()", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }

    // --- 2. File I/O (IOAccess.NONE) -----------------------------------------------------------

    @Test
    void fileIO_javaNioFiles_shouldBeBlocked() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.nio.file.Files')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void fileIO_javaIoFileOutputStream_shouldBeBlocked_beforeAnyWriteHappens() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.io.FileOutputStream')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    // --- 3. Thread/process creation (allowCreateThread/allowCreateProcess = false) -------------

    @Test
    void threadCreation_javaLangThread_shouldBeBlocked() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.lang.Thread')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void threadCreation_noJsThreadOrWorkerGlobal_isExposed() {
        // GraalJS không polyfill Worker/postMessage kiểu trình duyệt hay worker_threads kiểu
        // Node — xác nhận không có đường tạo luồng nào ngoài Java.type (đã chặn ở trên).
        Value result = sandbox.eval("typeof Worker + ',' + typeof postMessage", Map.of());

        assertThat(result.asString()).isEqualTo("undefined,undefined");
    }

    // --- 4. Environment variable access (EnvironmentAccess.NONE) -------------------------------

    @Test
    void environmentAccess_systemGetenv_shouldBeBlocked() {
        assertThatThrownBy(() -> sandbox.eval("Java.type('java.lang.System').getenv()", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void environmentAccess_processEnvGlobal_shouldBeUndefined() {
        // GraalJS không polyfill "process.env" kiểu Node theo mặc định — xác nhận thay vì đoán.
        Value result = sandbox.eval("typeof process", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }

    // --- 5. Runaway script (ResourceLimits.statementLimit) --------------------------------------

    @Test
    @Timeout(10) // Backstop JUnit — nếu statementLimit KHÔNG hoạt động, test này treo thay vì trôi qua CI xanh giả.
    void runawayLoop_infiniteLoopWithAllocation_shouldBeStoppedByStatementLimit() {
        ScriptSandbox tightSandbox = new ScriptSandbox(50_000);

        assertThatThrownBy(() -> tightSandbox.eval("let arr = []; while (true) { arr.push(arr.length); }", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    @Timeout(10)
    void runawayLoop_deepUnboundedRecursion_shouldFailSafely_notCrashHost() {
        // Đệ quy vô hạn không phải vòng lặp "statement" điển hình nhưng vẫn phải bị chặn an
        // toàn (StackOverflowError phía GraalJS phải được bọc lại thành ScriptExecutionException,
        // không được làm crash JVM host hay thoát ra ngoài dạng lỗi không kiểm soát).
        assertThatThrownBy(() -> sandbox.eval("function f(n) { return f(n + 1); } f(0);", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    // --- 6. Cross-script state isolation (Context mới mỗi lần eval) ----------------------------

    @Test
    void crossScriptIsolation_globalSetByFirstScript_shouldNotLeakToSecondScript() {
        sandbox.eval("globalThis.leakedSecret = 'from-first-script';", Map.of());

        Value result = sandbox.eval("typeof globalThis.leakedSecret", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }

    @Test
    void crossScriptIsolation_hostApiBindingFromFirstScript_shouldNotLeakToSecondScript() {
        // Quest A nhận hostApi "quest" với dữ liệu riêng — Quest B (không truyền hostApi) không
        // được thấy biến "quest" của Quest A dù chạy trên cùng ScriptSandbox instance.
        sandbox.eval("quest.secretGold", Map.of("quest", Map.of("secretGold", 9999)));

        Value result = sandbox.eval("typeof quest", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }

    // --- 7. Polyglot global object (PolyglotAccess.NONE) ----------------------------------------

    @Test
    void polyglotAccess_polyglotGlobalObject_shouldBeUndefined() {
        // PolyglotAccess.NONE phải ẩn global "Polyglot" (API cho phép script JS gọi sang
        // ngôn ngữ Graal khác) — nếu global này vẫn lộ, đó là một gap thật cần báo cáo, không
        // được âm thầm bỏ qua (xem javadoc lớp).
        Value result = sandbox.eval("typeof Polyglot", Map.of());

        assertThat(result.asString()).isEqualTo("undefined");
    }
}
