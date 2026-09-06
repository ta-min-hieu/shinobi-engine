package dev.kage.tools.loadtest;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Tham số dòng lệnh dạng {@code --key=value} — KHÔNG dùng thư viện CLI parser ngoài (~10 tham số
 * không đáng thêm dependency, CLAUDE.md §4). Tách khỏi {@code LoadTestConfig} (2026-09-06) để
 * {@code probe.ProbeConfig} dùng chung đúng cú pháp tham số, thay vì mỗi công cụ tự hiểu một kiểu.
 *
 * <p>Tham số không nhận ra thì BỎ QUA im lặng — giữ nguyên hành vi bản cũ (Gradle chèn thêm tham
 * số của riêng nó vào {@code --args} là chuyện thường).
 */
public final class CommandLineArguments {

    private static final String PREFIX = "--";
    /** Vị trí nhỏ nhất của dấu {@code =} để phần key không rỗng: {@code --k=v}. */
    private static final int MIN_EQUALS_INDEX = PREFIX.length() + 1;

    private final Map<String, String> values;

    private CommandLineArguments(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** @param args mảng tham số thô của {@code main} */
    public static CommandLineArguments parse(String[] args) {
        Map<String, String> raw = new HashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (arg.startsWith(PREFIX) && equals >= MIN_EQUALS_INDEX) {
                raw.put(arg.substring(PREFIX.length(), equals), arg.substring(equals + 1));
            }
        }
        return new CommandLineArguments(raw);
    }

    public String text(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    /** @throws NumberFormatException nếu người dùng gõ sai kiểu — dừng ngay còn hơn chạy với số bịa */
    public int integer(String key, int defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /** @throws NumberFormatException nếu người dùng gõ sai kiểu — dừng ngay còn hơn chạy với số bịa */
    public long number(String key, long defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    /**
     * Rỗng khi tham số KHÔNG được truyền — khác hẳn "truyền 0". Cần phân biệt vì {@code moveToX=0}
     * là một toạ độ hợp lệ, không phải "đừng di chuyển".
     *
     * @throws NumberFormatException nếu người dùng gõ sai kiểu
     */
    public OptionalDouble optionalDouble(String key) {
        String value = values.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(value));
    }
}
