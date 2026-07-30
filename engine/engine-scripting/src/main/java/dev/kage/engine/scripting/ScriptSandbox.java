package dev.kage.engine.scripting;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Sandbox chạy script GraalJS không tin cậy (Điều 27, ADR-016, SCRIPTING.md §4).
 *
 * <p><b>Đã hạ từ {@code SandboxPolicy.UNTRUSTED} xuống cấu hình tương đương
 * {@code SandboxPolicy.CONSTRAINED}, tự khai báo bằng tay — không dùng API {@code .sandbox(...)}
 * trực tiếp</b>: {@code ISOLATED}/{@code UNTRUSTED} (như ADR-016 dự tính) bắt buộc thêm artifact
 * {@code org.graalvm.polyglot:js-isolate-community} để chạy script trong một isolate JVM riêng
 * (heap/JIT tách biệt host — lớp phòng vệ mạnh hơn cấu hình tay dưới đây). Khi thử thêm artifact
 * đó (07/2026), phát hiện 2 vấn đề đóng gói thật: (1) POM không dùng Gradle variant-aware OS/arch
 * selection — kéo theo tất cả biến thể nền tảng thay vì chỉ nền tảng đang chạy; (2) bản
 * {@code js-isolate-darwin-amd64-community:25.1.3} không tồn tại trên Maven Central, làm resolve
 * lỗi ngay cả trên máy Windows không liên quan gì tới macOS. Đây là vấn đề đóng gói của GraalVM,
 * không phải lỗi cấu hình — cần quyết định nền tảng server mục tiêu thật (nhiều khả năng chỉ
 * Linux trong Docker, xem ROADMAP) trước khi ghim đúng 1 classifier nền tảng, thay vì kéo cả 5.
 *
 * <p>Cấu hình tay dưới đây vẫn chặn đúng danh sách "Script KHÔNG được" của SCRIPTING.md §4:
 * không host access ({@code Java.type}...), không I/O, không thread/process mới, không đọc biến
 * môi trường, cộng {@code statementLimit} chặn vòng lặp vô hạn. Thiếu so với ISOLATED/UNTRUSTED:
 * KHÔNG có heap riêng (script và host dùng chung heap JVM — script cấp phát bộ nhớ lớn vẫn ảnh
 * hưởng host tới khi bị {@code statementLimit} chặn) và không có JIT/Spectre mitigation riêng.
 *
 * <p>Mỗi {@link #eval} tạo một {@link Context} MỚI — không tái sử dụng giữa các lần gọi khác
 * nhau, tránh state rò rỉ giữa hai script không liên quan (VD hai quest khác nhau).
 */
public final class ScriptSandbox {

    private final long maxStatements;

    public ScriptSandbox(long maxStatements) {
        this.maxStatements = maxStatements;
    }

    /**
     * Chạy {@code script} với host API {@code hostApi} là facade dữ liệu thuần (SCRIPTING.md
     * §4 — KHÔNG expose object Java thô). Ném {@link ScriptExecutionException} nếu script lỗi
     * cú pháp, vượt giới hạn tài nguyên, hoặc cố thoát sandbox.
     */
    public Value eval(String script, Map<String, Object> hostApi) {
        return eval(script, hostApi, Function.identity());
    }

    /**
     * Như {@link #eval(String, Map)} nhưng {@code extractor} chạy TRONG lúc {@link Context} còn
     * mở, trước khi bị đóng — bắt buộc dùng cách này nếu script trả về giá trị CÓ CẤU TRÚC (object/
     * array), không phải scalar (số/chuỗi/boolean). PHÁT HIỆN THẬT: gọi {@code Value.getMember}/
     * {@code getArrayElement} trên {@link Value} trả về từ {@link #eval(String, Map)} SAU KHI
     * {@link Context} đã đóng (try-with-resources đóng context trước khi trả kết quả về caller)
     * ném {@link IllegalStateException} — chỉ scalar (VD {@code asDouble()}) sống sót qua việc đóng
     * context, không phải điều hiển nhiên, xác nhận bằng test thật trước khi thêm overload này
     * (không đoán trước).
     */
    public <T> T eval(String script, Map<String, Object> hostApi, Function<Value, T> extractor) {
        try (Context context = buildContext()) {
            Value bindings = context.getBindings("js");
            for (Map.Entry<String, Object> entry : hostApi.entrySet()) {
                bindings.putMember(entry.getKey(), toProxy(entry.getValue()));
            }
            Value result = context.eval("js", script);
            return extractor.apply(result); // PHẢI chạy ở đây — context vẫn còn mở.
        } catch (PolyglotException e) {
            throw new ScriptExecutionException("Script thất bại: " + e.getMessage(), e);
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

    private Context buildContext() {
        return Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowExperimentalOptions(false)
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(maxStatements, null)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Object toProxy(Object value) {
        if (value instanceof Map<?, ?> map) {
            return ProxyObject.fromMap((Map<String, Object>) map);
        }
        return value;
    }
}
