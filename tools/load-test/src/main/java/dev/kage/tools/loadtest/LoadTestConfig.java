package dev.kage.tools.loadtest;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình 1 lần chạy — parse từ {@code --key=value} (không dùng thư viện CLI parser ngoài, Điều 9:
 * ~10 tham số không đáng thêm dependency). Mặc định là SOAK TEST 2 giờ (BENCHMARK.md §5 "Soak test
 * quan trọng hơn peak test"), không phải peak test ngắn — đổi {@code durationSeconds} cho peak test.
 */
record LoadTestConfig(
        String host,
        int port,
        int metricsPort,
        int initialClients,
        int maxClients,
        int rampStepClients,
        int rampStepIntervalSeconds,
        int durationSeconds,
        int reportIntervalSeconds) {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7777;
    private static final int DEFAULT_METRICS_PORT = 9404;
    private static final int DEFAULT_INITIAL_CLIENTS = 10;
    private static final int DEFAULT_MAX_CLIENTS = 100;
    private static final int DEFAULT_RAMP_STEP_CLIENTS = 10;
    private static final int DEFAULT_RAMP_STEP_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_DURATION_SECONDS = 2 * 60 * 60; // 2h — soak mặc định.
    private static final int DEFAULT_REPORT_INTERVAL_SECONDS = 60;

    static LoadTestConfig parse(String[] args) {
        Map<String, String> raw = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 2) {
                raw.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        return new LoadTestConfig(
                raw.getOrDefault("host", DEFAULT_HOST),
                intArg(raw, "port", DEFAULT_PORT),
                intArg(raw, "metricsPort", DEFAULT_METRICS_PORT),
                intArg(raw, "initialClients", DEFAULT_INITIAL_CLIENTS),
                intArg(raw, "maxClients", DEFAULT_MAX_CLIENTS),
                intArg(raw, "rampStepClients", DEFAULT_RAMP_STEP_CLIENTS),
                intArg(raw, "rampStepIntervalSeconds", DEFAULT_RAMP_STEP_INTERVAL_SECONDS),
                intArg(raw, "durationSeconds", DEFAULT_DURATION_SECONDS),
                intArg(raw, "reportIntervalSeconds", DEFAULT_REPORT_INTERVAL_SECONDS));
    }

    private static int intArg(Map<String, String> raw, String key, int defaultValue) {
        String value = raw.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }
}
