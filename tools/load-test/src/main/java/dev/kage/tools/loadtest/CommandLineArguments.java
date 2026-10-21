package dev.kage.tools.loadtest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Tham số dòng lệnh dạng {@code --key=value} — KHÔNG dùng thư viện CLI parser ngoài (~10 tham số
 * không đáng thêm dependency, CLAUDE.md §4). Tách khỏi {@code LoadTestConfig} (2026-09-06) để
 * {@code probe.ProbeConfig} dùng chung đúng cú pháp tham số, thay vì mỗi công cụ tự hiểu một kiểu.
 *
 * <p>Tham số không nhận ra thì BỎ QUA im lặng — giữ nguyên hành vi bản cũ (Gradle chèn thêm tham
 * số của riêng nó vào {@code --args} là chuyện thường). Riêng cờ trần {@code --key} (không dấu bằng)
 * được {@link #flag} hiểu là {@code true}: trước 2026-10-21 nó bị bỏ qua, và {@code --retargetOnKill}
 * gõ trần đã lặng lẽ không bật suốt nhiều lượt kiểm sống.
 */
public final class CommandLineArguments {

    private static final String PREFIX = "--";
    /** Vị trí nhỏ nhất của dấu {@code =} để phần key không rỗng: {@code --k=v}. */
    private static final int MIN_EQUALS_INDEX = PREFIX.length() + 1;

    private final Map<String, String> values;
    private final Set<String> bareFlags;

    private CommandLineArguments(Map<String, String> values, Set<String> bareFlags) {
        this.values = Map.copyOf(values);
        this.bareFlags = Set.copyOf(bareFlags);
    }

    /**
     * Tách {@code --key=value} thành bảng tra.
     *
     * @param args mảng tham số thô của {@code main}
     */
    public static CommandLineArguments parse(String[] args) {
        Map<String, String> raw = new HashMap<>();
        Set<String> bare = new HashSet<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (arg.startsWith(PREFIX) && equals >= MIN_EQUALS_INDEX) {
                raw.put(arg.substring(PREFIX.length(), equals), arg.substring(equals + 1));
            } else if (arg.startsWith(PREFIX) && equals < 0 && arg.length() > PREFIX.length()) {
                bare.add(arg.substring(PREFIX.length()));
            }
        }
        return new CommandLineArguments(raw, bare);
    }

    public String text(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    /**
     * Danh sách ngăn bằng dấu phẩy, đã bỏ khoảng trắng thừa và bỏ phần tử rỗng.
     *
     * @return rỗng khi tham số không được truyền HOẶC truyền chuỗi rỗng — với danh sách thì hai
     *     trường hợp đó nghĩa như nhau ("không có bước nào"), khác {@link #optionalDouble}
     */
    public List<String> list(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    /**
     * Cờ bật/tắt: {@code true}/{@code 1}/{@code yes} là bật, mọi giá trị khác là tắt.
     *
     * @param defaultValue giá trị khi tham số không được truyền
     */
    public boolean flag(String key, boolean defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return bareFlags.contains(key) || defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }

    /**
     * Đọc một tham số kiểu {@code int}, hoặc trả {@code defaultValue} nếu không có.
     *
     * @throws NumberFormatException nếu người dùng gõ sai kiểu — dừng ngay còn hơn chạy với số bịa
     */
    public int integer(String key, int defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /**
     * Đọc một tham số kiểu {@code long}, hoặc trả {@code defaultValue} nếu không có.
     *
     * @throws NumberFormatException nếu người dùng gõ sai kiểu — dừng ngay còn hơn chạy với số bịa
     */
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
