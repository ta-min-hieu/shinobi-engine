package dev.kage.engine.scripting;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.io.IOAccess;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * README dòng 70(c): một lần {@link ScriptSandbox#eval} tốn bao nhiêu, và dùng chung MỘT {@link Engine}
 * (Context vẫn mới mỗi lần — không rò trạng thái giữa hai script) có rẻ hơn rõ không. Đo TRƯỚC khi
 * đổi production (BENCHMARK.md, Điều 9).
 *
 * <p>Script đo là một công thức sát thương cỡ script skill thật (vài phép tính, đọc host API). Hai phép
 * đo chép tay ({@code perContextEngine}/{@code sharedEngine}) dùng ĐÚNG cấu hình Context của
 * {@code ScriptSandbox.buildContext()}, chỉ khác có/không gắn Engine dựng sẵn. Đo lần đầu (2026-10-17,
 * máy dev, JDK 25): 363 µs khi mỗi Context tự dựng Engine, 88 µs trên Engine chung.
 *
 * <p>Chạy: {@code ./gradlew :engine:engine-scripting:jmh}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)
@org.openjdk.jmh.annotations.Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class ScriptContextBenchmark {

    private static final String DAMAGE_SCRIPT =
            "var base = ctx.atk * 2 - ctx.def; base > 1 ? base * (ctx.crit ? 1.5 : 1) : 1";
    private static final Map<String, Object> HOST_API = Map.of("ctx", Map.of("atk", 40, "def", 12, "crit", true));
    private static final long MAX_STATEMENTS = 10_000L;

    private ScriptSandbox sandbox;
    private Engine engine;

    @Setup(Level.Trial)
    public void setUp() {
        sandbox = new ScriptSandbox(MAX_STATEMENTS);
        engine = Engine.newBuilder("js")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .in(InputStream.nullInputStream())
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        engine.close();
    }

    /** Production ({@link ScriptSandbox#eval}) — từ 2026-10-17 dùng Engine chung. */
    @Benchmark
    public double sandboxEval() {
        return sandbox.eval(DAMAGE_SCRIPT, HOST_API, value -> value.asDouble());
    }

    /** Cách CŨ (trước 2026-10-17): mỗi Context tự dựng một Engine ngầm. */
    @Benchmark
    public double perContextEngine() {
        return evalIn(Context.newBuilder("js"));
    }

    /** Context mới mỗi lần, trên một Engine dùng chung — cấu hình chép tay để so với cách cũ. */
    @Benchmark
    public double sharedEngine() {
        return evalIn(Context.newBuilder("js").engine(engine));
    }

    private static double evalIn(Context.Builder builder) {
        try (Context context = builder.sandbox(SandboxPolicy.CONSTRAINED)
                .allowHostAccess(
                        HostAccess.newBuilder().allowMutableTargetMappings().build())
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowExperimentalOptions(false)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .in(InputStream.nullInputStream())
                .option("sandbox.MaxStackFrames", "128")
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(MAX_STATEMENTS, null)
                        .build())
                .build()) {
            context.initialize("js");
            context.getBindings("js")
                    .putMember(
                            "ctx",
                            org.graalvm.polyglot.proxy.ProxyObject.fromMap(Map.of("atk", 40, "def", 12, "crit", true)));
            return context.eval("js", DAMAGE_SCRIPT).asDouble();
        }
    }
}
