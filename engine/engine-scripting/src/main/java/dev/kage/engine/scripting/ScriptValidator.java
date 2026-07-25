package dev.kage.engine.scripting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validate toàn bộ script trên classpath LÚC KHỞI ĐỘNG (SCRIPTING.md §8 — "Fail fast lúc khởi
 * động là bắt buộc, không đợi người chơi phát hiện lỗi cú pháp"). Composition root
 * ({@code GatewayBootstrap}) tự gom danh sách {@code scriptRef} từ mọi nguồn (quest/dialogue qua
 * DB, skill qua hằng số Java — xem {@code ServerDatabaseBootstrap}/{@code WorldFacade}) rồi gọi
 * {@link #validateAll(List)} một lần duy nhất TRƯỚC khi world tick/server bắt đầu nhận kết nối.
 */
public final class ScriptValidator {

    private final ScriptSandbox sandbox;

    public ScriptValidator(ScriptSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * @throws IllegalStateException nếu {@code scriptRef} không tồn tại trên classpath
     * @throws ScriptExecutionException nếu bất kỳ script nào lỗi cú pháp
     */
    public void validateAll(List<String> scriptRefs) {
        for (String scriptRef : scriptRefs) {
            sandbox.validateSyntax(loadScript(scriptRef), scriptRef);
        }
    }

    private static String loadScript(String scriptRef) {
        try (InputStream in = ScriptValidator.class.getResourceAsStream(scriptRef)) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy script trên classpath: " + scriptRef);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi đọc script: " + scriptRef, e);
        }
    }
}
