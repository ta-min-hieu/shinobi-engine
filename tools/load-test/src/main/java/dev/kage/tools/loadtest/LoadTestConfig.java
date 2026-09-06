package dev.kage.tools.loadtest;

/**
 * Cấu hình 1 lần chạy — parse từ {@code --key=value} qua {@link CommandLineArguments}. Mặc định là
 * SOAK TEST 2 giờ (BENCHMARK.md §5 "Soak test quan trọng hơn peak test"), không phải peak test
 * ngắn — đổi {@code durationSeconds} cho peak test.
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
        CommandLineArguments raw = CommandLineArguments.parse(args);
        return new LoadTestConfig(
                raw.text("host", DEFAULT_HOST),
                raw.integer("port", DEFAULT_PORT),
                raw.integer("metricsPort", DEFAULT_METRICS_PORT),
                raw.integer("initialClients", DEFAULT_INITIAL_CLIENTS),
                raw.integer("maxClients", DEFAULT_MAX_CLIENTS),
                raw.integer("rampStepClients", DEFAULT_RAMP_STEP_CLIENTS),
                raw.integer("rampStepIntervalSeconds", DEFAULT_RAMP_STEP_INTERVAL_SECONDS),
                raw.integer("durationSeconds", DEFAULT_DURATION_SECONDS),
                raw.integer("reportIntervalSeconds", DEFAULT_REPORT_INTERVAL_SECONDS));
    }
}
