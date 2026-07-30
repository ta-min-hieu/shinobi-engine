package dev.kage.server.database;

import java.util.Optional;
import java.util.TimeZone;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/** Dựng connection pool HikariCP (ADR-004) từ {@link DatabaseConfig}. */
public final class PostgresDataSourceFactory {

    // Đo thật (Phase 9 lát cắt 6-7, 2026-07-28, BENCHMARK.md §5.2-5.3): pool=10 backlog tới ~1790
    // request chờ lúc burst tạo nhân vật ở N=1000 (mỗi client mới làm vài round-trip DB tuần tự:
    // tạo account/nhân vật/chọn nhân vật) — CHƯA gây lỗi client trong các lần đo (kịp trong socket
    // timeout 5s) nhưng là rủi ro thật khi N lớn hơn hoặc soak dài. Tăng lên 20 — vẫn là khởi điểm
    // hợp lý dựa trên bằng chứng thật (không phải số cuối cùng đã tune kỹ), an toàn so với
    // max_connections mặc định của Postgres (100, deploy/docker/docker-compose.yml không override).
    // Chỉnh tiếp nếu N lớn hơn vẫn thấy backlog (HikariCP `leakDetectionThreshold`/pool metrics —
    // chưa expose qua Micrometer, xem BENCHMARK.md §5.2 "Việc chưa làm").
    private static final int DEFAULT_MAX_POOL_SIZE = 20;

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

    private PostgresDataSourceFactory() {}

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

    /**
     * OBSERVABILITY.md §3.3 {@code kage.db.pool.active}/{@code pending} — đọc trực tiếp
     * {@link HikariPoolMXBean}, không cần Micrometer trong module này (Điều 23, cùng cách
     * {@link ServerDatabaseBootstrap#totalGold()} trả {@code long} thuần thay vì {@code Gauge}).
     */
    public static int activeConnections(DataSource dataSource) {
        return poolMxBean(dataSource)
                .map(HikariPoolMXBean::getActiveConnections)
                .orElse(0);
    }

    /** Số thread đang CHỜ có connection — {@code > 0} kéo dài nghĩa là pool quá nhỏ (OBSERVABILITY.md §3.3). */
    public static int pendingThreads(DataSource dataSource) {
        return poolMxBean(dataSource)
                .map(HikariPoolMXBean::getThreadsAwaitingConnection)
                .orElse(0);
    }

    private static Optional<HikariPoolMXBean> poolMxBean(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return Optional.of(hikariDataSource.getHikariPoolMXBean());
        }
        return Optional.empty();
    }
}
