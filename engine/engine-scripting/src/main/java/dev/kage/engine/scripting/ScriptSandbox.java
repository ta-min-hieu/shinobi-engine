package dev.kage.engine.scripting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Sandbox chạy script GraalJS (Điều 27, ADR-016, SCRIPTING.md §4).
 *
 * <p><b>Mức cô lập: {@link SandboxPolicy#CONSTRAINED} THẬT — không phải {@code ISOLATED}/
 * {@code UNTRUSTED} như ADR-016 dự tính</b>: hai mức đó bắt buộc thêm artifact
 * {@code org.graalvm.polyglot:js-isolate-community} để chạy script trong một isolate JVM riêng
 * (heap/JIT tách biệt host). Khi thử thêm artifact đó (07/2026), phát hiện 2 vấn đề đóng gói thật:
 * (1) POM không dùng Gradle variant-aware OS/arch selection — kéo theo tất cả biến thể nền tảng;
 * (2) bản {@code js-isolate-darwin-amd64-community:25.1.3} không tồn tại trên Maven Central, làm
 * resolve lỗi ngay cả trên Windows. Cần chốt nền tảng server mục tiêu trước khi ghim 1 classifier.
 *
 * <p>Trước 2026-10-06 lớp này chỉ "tương đương CONSTRAINED" bằng cấu hình tay, và đo thật thì
 * KHÔNG tương đương: GraalVM tự kiểm cấu hình cũ bằng {@code .sandbox(CONSTRAINED)} và từ chối nó
 * ở hai điểm — {@code HostAccess.NONE} vẫn bật mutable target mapping, và {@code print}/
 * {@code console.log} của script ghi thẳng ra {@code System.out} của server. Nay GraalVM tự
 * VALIDATE cấu hình lúc dựng mỗi {@link Context}: cấu hình lệch khỏi CONSTRAINED là ném ngay.
 *
 * <p>Các giới hạn tài nguyên (đều đo thật bằng script thù địch, xem
 * {@code ScriptSandboxAdversarialTest}):
 * <ul>
 *   <li>{@code statementLimit} — chặn vòng lặp vô hạn ở mức câu lệnh JS.</li>
 *   <li>{@link #timeout} theo đồng hồ thật (SCRIPTING.md §7 #7 "Timeout mỗi lần gọi") —
 *       {@code Context.close(true)} từ luồng canh. Cần vì một câu lệnh DUY NHẤT có thể chạy vô
 *       hạn: regex backtracking ({@code /^(a+)+\1b$/}) chỉ tốn vài câu lệnh nhưng treo luồng gọi
 *       hàng phút — đã đo, trước bản vá nó treo tick thread không giới hạn.</li>
 *   <li>{@value #MAX_STACK_FRAMES} frame JS — vượt là cạn tài nguyên, script KHÔNG bắt được bằng
 *       {@code try/catch}. Cần vì đệ quy có {@code try/catch} bắt {@code RangeError} rồi đệ quy
 *       tiếp bùng nổ theo cấp số nhân ở đáy stack, và trong trạng thái đó ngay cả
 *       {@code close(true)} lẫn {@code statementLimit} cũng không dừng nó trong 30 giây (đo thật).</li>
 * </ul>
 *
 * <p><b>Lỗ hổng CÒN LẠI ở mức CONSTRAINED</b> (chỉ ISOLATED mới đóng được — ADR-016):
 * <ul>
 *   <li>KHÔNG giới hạn heap: {@code sandbox.MaxHeapMemory} báo "not supported on the current
 *       Truffle runtime" khi không có isolate. Một câu lệnh ({@code new Array(5e7).fill(1.5)}) đủ
 *       đòi hàng trăm MB của heap CHUNG với server — {@code statementLimit} không giúp gì.</li>
 *   <li>Builtin chạy lâu không có safepoint (VD {@code 2n ** 20000000n}: ~5 giây) không bị
 *       {@code close(true)} ngắt giữa chừng — chỉ dừng sau khi builtin tự xong.</li>
 * </ul>
 *
 * <p>Mỗi {@link #eval} tạo một {@link Context} MỚI — không tái sử dụng giữa các lần gọi, tránh state
 * rò rỉ giữa hai script (VD hai quest khác nhau, hai người chơi khác nhau). Context gắn vào một
 * {@link Engine} DÙNG CHUNG (README dòng 70(c), 2026-10-17): đo bằng {@code ScriptContextBenchmark},
 * một lần eval script sát thương cỡ thật tốn ~363 µs khi mỗi Context tự dựng Engine ngầm, ~88 µs trên
 * Engine dùng chung. Trạng thái JS vẫn nằm trong Context nên không rò giữa hai lần eval; Engine chỉ
 * giữ phần dùng lại được (khởi tạo ngôn ngữ, cache code). Mỗi trần câu lệnh một Engine (cùng một
 * {@link ResourceLimits}) và sống cả đời JVM — sandbox không có vòng đời đóng, mà World dựng/huỷ liên
 * tục (hầm ngục, đấu trường) nên một Engine mỗi sandbox sẽ rò. {@code hostApi} được
 * bọc CHỈ ĐỌC: script không ghi được vào {@code Map} của caller, kể cả khi caller lỡ truyền một map
 * sống lâu.
 */
public final class ScriptSandbox {

    /**
     * Ngân sách thời gian thật mặc định cho MỘT lần {@link #eval}, tính từ lúc parse script (KHÔNG
     * tính dựng/khởi tạo {@link Context}). Đây là CHỐT CHẶN chống treo, không phải ngân sách hiệu
     * năng: đủ rộng để lần eval nguội đầu tiên của JVM không vấp nhầm, đủ hẹp để một script tồi
     * không giữ tick thread quá nửa giây. Siết thêm theo từng call site là việc của chủ dự án.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);

    /**
     * Trần cho script KHÔNG chạy trên tick (hội thoại, quest — chạy trên Virtual Thread của gateway;
     * chạy thử skill lúc dựng World): 5 s, chủ dự án chốt 2026-10-11 cho skill và 2026-10-17 cho hội
     * thoại/quest (README dòng 70). 500 ms từng cắt nhầm script hợp lệ khi máy tải nặng — trên server
     * nghẽn CPU/GC dài nghĩa là từ chối người chơi nói chuyện NPC hay nhận quest. Vẫn là chốt chống treo.
     */
    public static final Duration OFF_TICK_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Độ sâu frame JS tối đa. Script nội dung hiện tại sâu chưa tới 5 frame; stack JS tự nhiên của
     * GraalJS ở chế độ interpreter cạn ở khoảng 850 frame trên luồng 1MB (đo thật) — trần phải nằm
     * DƯỚI mức đó thì mới là lỗi cạn tài nguyên không bắt được thay vì {@code RangeError} bắt được.
     */
    static final int MAX_STACK_FRAMES = 128;

    private static final ScheduledThreadPoolExecutor WATCHDOG = createWatchdog();

    /** Engine + ResourceLimits dùng chung theo trần câu lệnh — xem javadoc lớp. */
    private static final ConcurrentMap<Long, SharedEngine> ENGINES = new ConcurrentHashMap<>();

    private final long maxStatements;
    private final Duration timeout;

    /** Một Engine GraalJS và đúng một cấu hình giới hạn cho mọi Context của nó. */
    private record SharedEngine(Engine engine, ResourceLimits limits) {}

    public ScriptSandbox(long maxStatements) {
        this(maxStatements, DEFAULT_TIMEOUT);
    }

    /**
     * @param maxStatements trần câu lệnh JS mỗi lần {@link #eval}
     * @param timeout trần thời gian thật mỗi lần {@link #eval}, phải dương
     */
    public ScriptSandbox(long maxStatements, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout phải dương: " + timeout);
        }
        this.maxStatements = maxStatements;
        this.timeout = timeout;
    }

    /**
     * Chạy {@code script} với host API {@code hostApi} là facade dữ liệu thuần (SCRIPTING.md
     * §4 — KHÔNG expose object Java thô). Ném {@link ScriptExecutionException} nếu script lỗi
     * cú pháp, vượt giới hạn tài nguyên/thời gian, hoặc cố thoát sandbox.
     */
    public Value eval(String script, Map<String, Object> hostApi) {
        return eval(script, hostApi, Function.identity());
    }

    /**
     * Như {@link #eval(String, Map)} nhưng {@code extractor} chạy TRONG lúc {@link Context} còn
     * mở, trước khi bị đóng — bắt buộc dùng cách này nếu script trả về giá trị CÓ CẤU TRÚC (object/
     * array), không phải scalar (số/chuỗi/boolean). PHÁT HIỆN THẬT: gọi {@code Value.getMember}/
     * {@code getArrayElement} trên {@link Value} trả về từ {@link #eval(String, Map)} SAU KHI
     * {@link Context} đã đóng ném {@link IllegalStateException} — chỉ scalar sống sót.
     *
     * <p>{@code extractor} nằm TRONG ngân sách thời gian/câu lệnh: getter hay {@code Proxy} trap mà
     * script cài vào giá trị trả về chạy lúc extractor đọc, và lỗi của chúng cũng thành
     * {@link ScriptExecutionException}.
     */
    public <T> T eval(String script, Map<String, Object> hostApi, Function<Value, T> extractor) {
        try (Context context = buildContext()) {
            // Khởi tạo realm JS TRƯỚC khi bấm giờ — lần đầu trong JVM tốn hàng trăm ms (đo thật) và
            // đó là chi phí của host, không phải của script.
            context.initialize("js");
            Value bindings = context.getBindings("js");
            for (Map.Entry<String, Object> entry : hostApi.entrySet()) {
                bindings.putMember(entry.getKey(), toProxy(entry.getValue()));
            }
            ScheduledFuture<?> deadline =
                    WATCHDOG.schedule(() -> cancel(context), timeout.toNanos(), TimeUnit.NANOSECONDS);
            try {
                Value result = context.eval("js", script);
                return extractor.apply(result); // PHẢI chạy ở đây — context vẫn còn mở.
            } finally {
                deadline.cancel(false);
            }
        } catch (PolyglotException e) {
            throw new ScriptExecutionException(describe(e), e);
        }
    }

    /**
     * Parse (KHÔNG chạy) {@code script} — chỉ bắt lỗi cú pháp, không tốn chi phí thực thi. Dùng
     * cho validate toàn bộ script lúc khởi động (SCRIPTING.md §8 "Fail fast lúc khởi động là bắt
     * buộc"), KHÔNG dùng lúc gameplay thật (đó là việc của {@link #eval}).
     *
     * @param scriptName tên hiển thị trong thông báo lỗi (VD đường dẫn resource) — GraalJS dùng
     *     giá trị này làm {@code Source} name, không ảnh hưởng kết quả parse.
     * @throws ScriptExecutionException nếu {@code script} có lỗi cú pháp
     */
    public void validateSyntax(String script, String scriptName) {
        try (Context context = buildContext()) {
            context.parse(Source.newBuilder("js", script, scriptName).build());
        } catch (PolyglotException | IOException e) {
            throw new ScriptExecutionException("Script lỗi cú pháp: " + scriptName, e);
        }
    }

    private String describe(PolyglotException e) {
        if (e.isCancelled() && !e.isResourceExhausted()) {
            return "Script vượt thời gian " + timeout.toMillis() + "ms";
        }
        return "Script thất bại: " + e.getMessage();
    }

    private Context buildContext() {
        SharedEngine shared = ENGINES.computeIfAbsent(maxStatements, ScriptSandbox::newSharedEngine);
        return Context.newBuilder("js")
                .engine(shared.engine())
                // GraalVM tự kiểm mọi dòng dưới đây khớp CONSTRAINED, lệch là ném lúc build().
                .sandbox(SandboxPolicy.CONSTRAINED)
                // Không member/class Java nào, và không ánh xạ ngược object JS thành Map/List Java
                // khả biến (HostAccess.NONE vẫn bật cái sau — CONSTRAINED từ chối).
                .allowHostAccess(
                        HostAccess.newBuilder().allowMutableTargetMappings().build())
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowExperimentalOptions(false)
                // print()/console.log() trước đây ghi thẳng ra stdout của server — một kênh I/O.
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .in(InputStream.nullInputStream())
                .option("sandbox.MaxStackFrames", Integer.toString(MAX_STACK_FRAMES))
                .resourceLimits(shared.limits())
                .build();
    }

    /** Engine cũng phải khai CONSTRAINED và nuốt stdout/stderr — GraalVM kiểm cả Engine lẫn Context. */
    private static SharedEngine newSharedEngine(long statementLimit) {
        Engine engine = Engine.newBuilder("js")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .in(InputStream.nullInputStream())
                .build();
        ResourceLimits limits =
                ResourceLimits.newBuilder().statementLimit(statementLimit, null).build();
        return new SharedEngine(engine, limits);
    }

    @SuppressWarnings("unchecked")
    private static Object toProxy(Object value) {
        if (value instanceof Map<?, ?> map) {
            return ProxyObject.fromMap(Collections.unmodifiableMap((Map<String, Object>) map));
        }
        return value;
    }

    /**
     * {@code close(true)} CHỜ tới khi luồng đang chạy script thoát ra — và đo thật đã thấy nó chờ
     * vô hạn (đệ quy {@code try/catch} ở đáy stack, trước khi có {@link #MAX_STACK_FRAMES}). Nếu
     * gọi thẳng trên luồng canh, một lần kẹt như thế làm MỌI hẹn giờ sau đó không bao giờ nổ. Nên
     * luồng canh chỉ đẩy việc huỷ sang một luồng riêng — hiếm (chỉ khi script quá giờ), nên tạo
     * luồng mỗi lần là rẻ.
     */
    private static void cancel(Context context) {
        Thread.ofPlatform().name("kage-script-cancel").daemon(true).start(() -> context.close(true));
    }

    /**
     * Một luồng nền dùng chung cho mọi sandbox: chỉ đặt hẹn giờ, không bao giờ chạy script và
     * không bao giờ chờ (xem {@link #cancel}). Daemon để không giữ JVM sống; huỷ hẹn là gỡ khỏi
     * hàng đợi ngay (không tích luỹ hàng nghìn hẹn đã huỷ khi script xong sớm — trường hợp thường
     * gặp).
     */
    private static ScheduledThreadPoolExecutor createWatchdog() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().name("kage-script-watchdog").daemon(true).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
