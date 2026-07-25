package dev.kage.server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.TimeZone;
import javax.sql.DataSource;

/** Dựng connection pool HikariCP (ADR-004) từ {@link DatabaseConfig}. */
public final class PostgresDataSourceFactory {

    // Chưa đo (BENCHMARK.md — Phase 9) — khởi điểm hợp lý cho 1 world instance, chỉnh sau khi có số liệu.
    private static final int DEFAULT_MAX_POOL_SIZE = 10;

    static {
        // ⚠️ PHÁT HIỆN THẬT: pgjdbc lấy TimeZone.getDefault() của JVM gửi cho PostgreSQL lúc
        // connect (ConnectionFactoryImpl.createPostgresTimeZone()) — KHÔNG có connection property
        // nào override được việc này (đã thử "timezone" dataSourceProperty, pgjdbc không nhận,
        // âm thầm bỏ qua). Máy dev múi giờ "Asia/Saigon" (alias tzdata cũ) khiến postgres:17 FATAL
        // ngay lúc mở pool vì không nhận ra tên đó.
        //
        // Sửa đúng gốc, không phải vá riêng cho Postgres: ép JVM chạy UTC nội bộ — thực hành chuẩn
        // cho mọi backend server (không phụ thuộc múi giờ máy host, khớp DATABASE.md dùng
        // TIMESTAMPTZ). Đặt ở static initializer để chạy đúng 1 lần, trước connection đầu tiên,
        // bất kể caller là GatewayBootstrap.main() thật hay test.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private PostgresDataSourceFactory() {
    }

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.user());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Đóng pool tạo bởi {@link #create}. Nhận {@link DataSource} (không phải {@link
     * HikariDataSource}) để caller không cần biết chi tiết Hikari — chỉ class này biết.
     */
    public static void close(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }
}
