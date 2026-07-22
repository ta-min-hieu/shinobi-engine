package dev.kage.engine.scripting;

import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
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
        try (Context context = buildContext()) {
            Value bindings = context.getBindings("js");
            for (Map.Entry<String, Object> entry : hostApi.entrySet()) {
                bindings.putMember(entry.getKey(), toProxy(entry.getValue()));
            }
            return context.eval("js", script);
        } catch (PolyglotException e) {
            throw new ScriptExecutionException("Script thất bại: " + e.getMessage(), e);
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
