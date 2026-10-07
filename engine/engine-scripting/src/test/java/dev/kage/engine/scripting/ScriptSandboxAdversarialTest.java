package dev.kage.engine.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Script THÙ ĐỊCH nhắm vào từng bảo đảm mà {@link ScriptSandbox}/ADR-016/SCRIPTING.md §4, §7 tuyên
 * bố (2026-10-06). {@link ScriptSandboxSecurityTest} đã phủ các cửa {@code Java.type} trực tiếp; lớp
 * này đi các đường VÒNG: object Java lọt vào binding, exception của host, regex, đệ quy bắt lỗi,
 * microtask, giá trị trả về kỳ quặc, và — quan trọng nhất cho server — "lỗi script không được giết
 * tick thread".
 *
 * <p>Mọi test có {@code @Timeout} làm chốt chặn: một bảo đảm hỏng phải hiện thành test ĐỎ, không
 * phải CI treo.
 */
class ScriptSandboxAdversarialTest {

    /** Đủ rộng cho JVM nguội trên máy CI tải nặng, đủ hẹp để test regex không chạy lâu. */
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private static final String CATASTROPHIC_REGEX = "/^(a+)+\\1b$/.test('a'.repeat(40) + 'c')";

    private final ScriptSandbox sandbox = new ScriptSandbox(100_000);
    private final ScriptSandbox timedSandbox = new ScriptSandbox(100_000_000L, SHORT_TIMEOUT);

    // --- 1. Object Java lọt vào binding: không có member nào với tới được ----------------------

    @Test
    void rawJavaObjectInBindings_exposesNoMethods_evenRuntime() {
        // "Điểm chết người" của SCRIPTING.md §4: caller lỡ đưa object Java thô. Dưới host access
        // rỗng nó là một hộp kín — không exec, không getClass, không gì cả.
        Map<String, Object> hostApi = Map.of("rt", Runtime.getRuntime(), "cls", String.class);

        Value probe = sandbox.eval(
                "[typeof rt.exec, typeof rt.getClass, typeof cls.forName, typeof cls.getClassLoader,"
                        + " typeof cls.static.forName, String(Object.keys(rt))].join(',')",
                hostApi);

        assertThat(probe.asString()).isEqualTo("undefined,undefined,undefined,undefined,undefined,");
        assertThatThrownBy(() -> sandbox.eval("rt.exec('cmd')", hostApi)).isInstanceOf(ScriptExecutionException.class);
        assertThatThrownBy(() -> sandbox.eval("cls.forName('java.lang.Runtime')", hostApi))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void hostExceptionCaughtByScript_isOpaque_noReflectionThroughIt() {
        // Callback host ném exception (VD ItemTake số lượng âm ở feature-quest) — script bắt được
        // nó bằng try/catch, nhưng thứ bắt được không được là cửa sang java.lang.Class.
        Map<String, Object> hostApi = Map.of("ctx", Map.of("boom", (ProxyExecutable) args -> {
            throw new IllegalArgumentException("host-boom");
        }));

        Value probe = sandbox.eval(
                "try { ctx.boom(); 'no-throw' } catch (e) {"
                        + " [typeof e.getClass, typeof e.getMessage, typeof e.getStackTrace].join(',') }",
                hostApi);

        assertThat(probe.asString()).isEqualTo("undefined,undefined,undefined");
        assertThatThrownBy(() -> sandbox.eval("try { ctx.boom() } catch (e) { e.getClass() }", hostApi))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void hostFunction_constructorChain_reachesOnlyJsGlobal() {
        Map<String, Object> hostApi = Map.of("ctx", Map.of("f", (ProxyExecutable) args -> 1));

        Value probe = sandbox.eval(
                "[typeof ctx.f.getClass, ctx.f.constructor.constructor('return typeof Java')(),"
                        + " ctx.f.constructor.constructor('return typeof Polyglot')()].join(',')",
                hostApi);

        assertThat(probe.asString()).isEqualTo("undefined,undefined,undefined");
    }

    @Test
    void nestedMapInHostApi_isOpaque_notAReflectionHandle() {
        // toProxy chỉ dựng ProxyObject cho map CẤP 1 — map lồng vào là object Java, phải kín.
        Value probe = sandbox.eval(
                "[typeof nested.inner, typeof nested.inner.getClass, typeof nested.inner.get,"
                        + " String(Object.keys(nested.inner))].join(',')",
                Map.of("nested", Map.of("inner", Map.of("k", "v"))));

        assertThat(probe.asString()).isEqualTo("object,undefined,undefined,");
    }

    @Test
    void javaInteropGlobals_doNotExist() {
        Value probe = sandbox.eval(
                "[typeof Java, typeof Packages, typeof java, typeof javax, typeof Polyglot,"
                        + " typeof require, typeof process, typeof quit, typeof exit].join(',')",
                Map.of());

        assertThat(probe.asString())
                .isEqualTo("undefined,undefined,undefined,undefined,undefined,undefined,"
                        + "undefined,undefined,undefined");
    }

    @Test
    void graalGlobal_exposesOnlyVersionInfo_pinnedBehaviour() {
        // GHIM: global "Graal" CÓ tồn tại (lộ số phiên bản engine — chỉ là lộ thông tin), nhưng
        // không mang hàm nào dẫn sang Java.
        Value probe = sandbox.eval(
                "Object.getOwnPropertyNames(Graal).filter(k => typeof Graal[k] === 'function'"
                        + " && k !== 'isGraalRuntime').join(',') + '|' + typeof Graal.getClass",
                Map.of());

        assertThat(probe.asString()).isEqualTo("|undefined");
    }

    @Test
    void loadAndDynamicImport_cannotReachTheFileSystem() {
        // load() tồn tại trong GraalJS nhưng IOAccess.NONE phải chặn nó; import() động trả về
        // promise bị từ chối — đọc kết quả qua callback vì microtask chạy SAU câu lệnh cuối.
        assertThatThrownBy(() -> sandbox.eval("load('build.gradle.kts')", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);

        AtomicReference<String> outcome = new AtomicReference<>("not-settled");
        sandbox.eval(
                "import('./build.gradle.kts').then(() => report('loaded'), () => report('rejected'));",
                Map.of("report", (ProxyExecutable) args -> {
                    outcome.set(args[0].asString());
                    return 0;
                }));

        assertThat(outcome.get()).isEqualTo("rejected");
    }

    @Test
    void printAndConsole_doNotReachServerStdout() {
        // Trước 2026-10-06: ghi thẳng ra System.out của server — một kênh I/O mà IOAccess.NONE
        // không phủ (script trong vòng 10k câu lệnh in được hàng trăm MB log).
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            sandbox.eval("print('LEAK-PRINT'); console.log('LEAK-CONSOLE'); console.error('LEAK-ERR'); 1", Map.of());
        } finally {
            System.setOut(original);
        }

        assertThat(captured.toString(StandardCharsets.UTF_8)).doesNotContain("LEAK");
    }

    // --- 2. Rò rỉ trạng thái giữa các lần eval / giữa người chơi --------------------------------

    @Test
    void hostApiMap_cannotBeMutatedByScript_soItCannotCarryStateToTheNextCall() {
        // Caller nào lỡ dùng chung một HashMap cho nhiều lần gọi thì script vẫn không ghi được vào
        // đó — nếu ghi được, script người chơi A cài giá trị cho lần gọi của người chơi B.
        HashMap<String, Object> shared = new HashMap<>();
        shared.put("hp", 10);
        Map<String, Object> hostApi = Map.of("ctx", shared);

        sandbox.eval("ctx.hp = 999; ctx.injected = 'evil'; delete ctx.hp; 1", hostApi);

        assertThat(shared).containsExactly(Map.entry("hp", 10));
        assertThat(sandbox.eval("typeof ctx.injected + ':' + ctx.hp", hostApi).asString())
                .isEqualTo("undefined:10");
    }

    @Test
    void hostApiMap_strictModeWrite_failsLoudly() {
        HashMap<String, Object> shared = new HashMap<>();

        assertThatThrownBy(() -> sandbox.eval("'use strict'; ctx.injected = 1;", Map.of("ctx", shared)))
                .isInstanceOf(ScriptExecutionException.class);
        assertThat(shared).isEmpty();
    }

    @Test
    void prototypePollution_doesNotLeakToNextEval() {
        sandbox.eval("Object.prototype.polluted = 'yes'; Array.prototype.map = null; Math.max = () => 1; 1", Map.of());

        Value probe = sandbox.eval("typeof ({}).polluted + ':' + typeof [].map + ':' + Math.max(2, 3)", Map.of());

        assertThat(probe.asString()).isEqualTo("undefined:function:3");
    }

    @Test
    @Timeout(60)
    void concurrentEvals_onDifferentThreads_doNotSeeEachOthersGlobals()
            throws InterruptedException, ExecutionException {
        // Hai người chơi cast skill trên hai luồng khác nhau cùng lúc: mỗi lần eval có Context
        // riêng, nên globalThis của lần này không bao giờ thấy giá trị của lần kia.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String id = "p" + i;
                results.add(pool.submit(() -> sandbox.eval(
                                "if (typeof globalThis.owner !== 'undefined') { 'LEAK:' + globalThis.owner } else {"
                                        + " globalThis.owner = me; let s = 0;"
                                        + " for (let i = 0; i < 2000; i++) { s += i; }"
                                        + " globalThis.owner }",
                                Map.of("me", id))
                        .asString()));
            }
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).get()).isEqualTo("p" + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- 3. Thời gian: statementLimit KHÔNG đủ, timeout theo đồng hồ mới đủ ---------------------

    @Test
    @Timeout(30)
    void catastrophicRegex_fewStatements_isStoppedByWallClockTimeout() {
        // Chỉ vài câu lệnh — statementLimit 100 triệu không bao giờ chạm. Trước bản vá: treo luồng
        // gọi hơn 15 giây (đo thật; 2^40 bước backtrack), tức là treo tick thread.
        long start = System.nanoTime();

        assertThatThrownBy(() -> timedSandbox.eval(CATASTROPHIC_REGEX, Map.of()))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("vượt thời gian");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @Timeout(30)
    void catastrophicRegex_insideTryCatchLoop_cannotSwallowTheTimeout() {
        assertThatThrownBy(() ->
                        timedSandbox.eval("for (;;) { try { " + CATASTROPHIC_REGEX + " } catch (e) {} }", Map.of()))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("vượt thời gian");
    }

    @Test
    @Timeout(30)
    void infiniteLoop_withHugeStatementBudget_isStoppedByTimeout() {
        assertThatThrownBy(() -> timedSandbox.eval("while (true) {}", Map.of()))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("vượt thời gian");
    }

    @Test
    @Timeout(60)
    void watchdog_staysAlive_acrossRepeatedTimeouts_andSandboxStillWorksAfterwards() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> timedSandbox.eval("while (true) {}", Map.of()))
                    .isInstanceOf(ScriptExecutionException.class);
        }

        assertThat(timedSandbox.eval("6 * 7", Map.of()).asInt()).isEqualTo(42);
    }

    @Test
    @Timeout(30)
    void hangingProxyTrap_readByExtractor_isInsideTheTimeoutToo() {
        // Script trả về Proxy có trap treo — vòng lặp chạy lúc HOST đọc (extractor), không phải
        // lúc script chạy. Extractor nằm trong cùng ngân sách nên vẫn bị cắt.
        assertThatThrownBy(() -> timedSandbox.eval(
                        "new Proxy({}, { has() { while (true) {} }, get() { while (true) {} } })",
                        Map.of(),
                        value -> value.getMember("effect")))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("vượt thời gian");
    }

    @Test
    void timeout_mustBePositive() {
        assertThatThrownBy(() -> new ScriptSandbox(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- 4. Đệ quy ----------------------------------------------------------------------------

    @Test
    @Timeout(30)
    void recursionBomb_thatCatchesStackOverflow_isStoppedByUncatchableFrameLimit() {
        // Trước bản vá: f bắt RangeError rồi gọi lại chính nó — ở đáy stack số lần gọi nhân đôi
        // mỗi tầng. Đo thật: statementLimit cần 4 giây tới hơn 30 giây tuỳ cỡ stack, còn
        // close(true)/sandbox.MaxCPUTime KHÔNG dừng được nó trong 30 giây. Trần frame là lỗi cạn
        // tài nguyên — script không catch được.
        long start = System.nanoTime();

        assertThatThrownBy(() ->
                        sandbox.eval("function f() { try { return f(); } catch (e) { return f(); } } f()", Map.of()))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("frame limit");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void legitimateRecursion_wellBelowTheFrameLimit_stillWorks() {
        // Trần frame không được chặt tới mức làm vỡ script hợp lệ (nội dung hiện tại sâu < 5).
        Value result = sandbox.eval(
                "function depth(n) { return n === 0 ? 0 : 1 + depth(n - 1); } depth("
                        + (ScriptSandbox.MAX_STACK_FRAMES / 2) + ")",
                Map.of());

        assertThat(result.asInt()).isEqualTo(ScriptSandbox.MAX_STACK_FRAMES / 2);
    }

    // --- 5. Microtask / async ------------------------------------------------------------------

    @Test
    @Timeout(30)
    void endlessPromiseChain_isCountedByStatementLimit() {
        assertThatThrownBy(() -> sandbox.eval("function f() { Promise.resolve().then(f); } f(); 1", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    @Timeout(30)
    void spinningMicrotask_afterLastStatement_isStillStopped() {
        // Microtask chạy SAU khi câu lệnh cuối đã trả kết quả — nó vẫn nằm trong eval, không được
        // trốn khỏi giới hạn.
        assertThatThrownBy(() -> timedSandbox.eval("Promise.resolve().then(() => { while (true) {} }); 1", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    @Timeout(30)
    void endlessAwaitLoop_isStopped() {
        assertThatThrownBy(() -> sandbox.eval("(async () => { while (true) { await null; } })(); 1", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    @Test
    void unhandledPromiseRejection_isSilent_pinnedBehaviour() {
        // GHIM hành vi hiện tại (không phải bảo đảm): lỗi trong nhánh async KHÔNG thành
        // ScriptExecutionException. Hook quest viết dạng async mà ném sau await sẽ không bật
        // scriptFailed ở QuestScriptRunner — nội dung phải viết hook đồng bộ.
        Value result = sandbox.eval(
                "Promise.reject(new Error('lost')); (async () => { throw new Error('lost2'); })(); 7", Map.of());

        assertThat(result.asInt()).isEqualTo(7);
    }

    @Test
    @Timeout(30)
    void atomicsWait_cannotBlockTheCallingThread() {
        // Chặn luồng mà KHÔNG tốn CPU/câu lệnh — thứ duy nhất mà cả statementLimit lẫn đếm CPU đều
        // không thấy. GraalJS trong cấu hình này từ chối luôn.
        assertThatThrownBy(() -> sandbox.eval("Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0)", Map.of()))
                .isInstanceOf(ScriptExecutionException.class);
    }

    // --- 6. Giá trị/lỗi kỳ quặc đi vào host ----------------------------------------------------

    @Test
    @Timeout(30)
    void tickLoop_survivesEveryHostileScript_onlyScriptExecutionExceptionEverEscapes() {
        // Mô phỏng đúng cách SkillSystem/BossAiSystem gọi trên tick thread: chỉ catch
        // ScriptExecutionException. Bất kỳ lỗi KHÁC lọt ra là mất trọn tick của cả World.
        List<String> hostile = List.of(
                "throw null",
                "throw undefined",
                "throw Symbol('s')",
                "throw 42",
                "throw new Proxy({}, { get() { throw new Error('trap'); } })",
                "throw { toString() { while (true) {} }, get message() { while (true) {} } }",
                "let e = new Error('x'); Object.defineProperty(e, 'stack', { get() { while (true) {} } }); throw e",
                "JSON.parse('['.repeat(200000) + ']'.repeat(200000))",
                "eval('('.repeat(100000) + '1' + ')'.repeat(100000))",
                "new RegExp('('.repeat(100000) + ')'.repeat(100000))",
                "let x = 'a'; for (let i = 0; i < 40; i++) { x = x + x; } x.length",
                "new ArrayBuffer(Number.MAX_SAFE_INTEGER)",
                "({ get effect() { throw new Error('getter'); }, value: 1 })",
                "new Proxy({}, { has() { throw new Error('has'); }, ownKeys() { throw 1; } })",
                "new Proxy({}, { ownKeys() { while (true) {} } })",
                "NaN",
                "Infinity",
                "-0",
                "undefined",
                "null",
                "Symbol('r')",
                "10n ** 30n",
                "function f(n) { return f(n + 1); } f(0)",
                "syntax error here (");
        int survived = 0;
        for (String script : hostile) {
            try {
                timedSandbox.eval(script, Map.of(), ScriptSandboxAdversarialTest::readLikeSkillSystem);
            } catch (ScriptExecutionException expected) {
                // Đúng hợp đồng: caller thật log và huỷ cast/coi như basic_attack.
            }
            survived++;
        }

        assertThat(survived).isEqualTo(hostile.size());
        assertThat(timedSandbox.eval("1 + 1", Map.of()).asInt()).isEqualTo(2);
    }

    @Test
    void nonFiniteNumbers_crossIntoHostAsNumbers_hostMustCheck() {
        // GHIM: sandbox không lọc NaN/Infinity — ScriptResults.requireFiniteNumber (server-world) là
        // nơi chặn. Bỏ lớp đó đi thì NaN đi thẳng vào máu/sát thương.
        double nan = sandbox.eval("0 / 0", Map.of()).asDouble();
        double inf = sandbox.eval("1 / 0", Map.of()).asDouble();

        assertThat(Double.isNaN(nan)).isTrue();
        assertThat(Double.isInfinite(inf)).isTrue();
    }

    @Test
    void throwingGetter_onReturnedObject_surfacesAsScriptExecutionException() {
        assertThatThrownBy(() -> sandbox.eval(
                        "({ get effect() { throw new Error('boom'); } })",
                        Map.of(),
                        value -> value.getMember("effect")))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void mathRandomAndClock_areAvailable_scriptsAreNotDeterministic_pinnedBehaviour() {
        // GHIM: không tài liệu nào hứa script tất định, và thực tế không tất định — đừng xây
        // replay/rollback dựa trên việc chạy lại script.
        Value probe = sandbox.eval("typeof Math.random() + ':' + (Date.now() > 0)", Map.of());

        assertThat(probe.asString()).isEqualTo("number:true");
    }

    private static String readLikeSkillSystem(Value value) {
        if (value == null || !value.hasMember("effect")) {
            return "";
        }
        Value effect = value.getMember("effect");
        int keys = value.getMemberKeys().size();
        return effect.isString() ? effect.asString() + keys : String.valueOf(keys);
    }
}
