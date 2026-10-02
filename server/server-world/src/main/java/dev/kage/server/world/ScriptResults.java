package dev.kage.server.world;

import java.util.Optional;

import org.graalvm.polyglot.Value;

import dev.kage.engine.scripting.ScriptExecutionException;

/**
 * Đọc từng field của object một script skill/boss trả về, KIỂM HÌNH DẠNG trước khi đọc.
 *
 * <p>Vì sao cần (sửa 2026-10-02): {@code ScriptSandbox.eval} chỉ bọc {@code PolyglotException}
 * thành {@link ScriptExecutionException}. Extractor gọi thẳng {@code getMember("x").asString()} thì
 * script trả thiếu field ném {@code NullPointerException}, trả sai kiểu ném
 * {@code ClassCastException} — cả hai lọt qua {@code catch (ScriptExecutionException)} của caller,
 * bay ra khỏi {@code drainInbox}/{@code ecsWorld.update} và làm MẤT TRỌN tick đó của cả World. Script
 * hợp lệ lúc khởi động (validate bằng ctx giả) vẫn có thể rẽ sang nhánh trả sai hình dạng lúc chạy
 * thật. Mọi lỗi hình dạng ở đây đều quy về {@link ScriptExecutionException} — đúng hợp đồng
 * "script lỗi" mà caller đã biết xử lý.
 */
final class ScriptResults {

    private ScriptResults() {}

    static String requireString(Value result, String key) {
        return member(result, key).map(m -> string(m, key)).orElseThrow(() -> malformed(key, "chuỗi"));
    }

    static double requireFiniteNumber(Value result, String key) {
        Optional<Value> member = member(result, key);
        if (member.isEmpty()) {
            throw malformed(key, "số hữu hạn");
        }
        return finiteNumber(member.get(), key);
    }

    static String optionalString(Value result, String key, String fallback) {
        return member(result, key).map(m -> string(m, key)).orElse(fallback);
    }

    static double optionalFiniteNumber(Value result, String key, double fallback) {
        Optional<Value> member = member(result, key);
        return member.isPresent() ? finiteNumber(member.get(), key) : fallback;
    }

    private static String string(Value member, String key) {
        if (!member.isString()) {
            throw malformed(key, "chuỗi");
        }
        return member.asString();
    }

    private static double finiteNumber(Value member, String key) {
        if (!member.isNumber() || !member.fitsInDouble()) {
            throw malformed(key, "số hữu hạn");
        }
        double number = member.asDouble();
        if (!Double.isFinite(number)) {
            throw malformed(key, "số hữu hạn");
        }
        return number;
    }

    /** {@code null}/{@code undefined} của JS coi như THIẾU field — giống {@code hasMember == false}. */
    private static Optional<Value> member(Value result, String key) {
        if (result == null || !result.hasMember(key)) {
            return Optional.empty();
        }
        Value member = result.getMember(key);
        return member == null || member.isNull() ? Optional.empty() : Optional.of(member);
    }

    private static ScriptExecutionException malformed(String key, String expected) {
        return new ScriptExecutionException(
                "Script trả về sai hình dạng: field '" + key + "' phải là " + expected, null);
    }
}
