package dev.kage.server.gateway;

import java.io.File;

/**
 * Cấu hình TLS cho kết nối game (ADR-027) — đọc từ biến môi trường, không hardcode secret (Điều
 * 42, SECURITY.md §13). Tên biến khớp {@code deploy/docker/docker-compose.prod.yml}.
 *
 * <p>Mặc định {@code enabled=true} — NETWORK.md §2 (Accepted) "TLS bắt buộc ở Production. Dev có
 * thể tắt qua flag tường minh, không mặc định tắt". Cần {@code KAGE_TLS_ENABLED=false} tường minh
 * để tắt (dev/test only — KHÔNG dùng cho Production).
 */
public record TlsConfig(boolean enabled, File certChainFile, File keyFile) {

    public static TlsConfig fromEnvironment() {
        boolean enabled = parseEnabled(System.getenv("KAGE_TLS_ENABLED"));
        if (!enabled) {
            return disabled();
        }
        String certPath = System.getenv("KAGE_TLS_CERT_PATH");
        String keyPath = System.getenv("KAGE_TLS_KEY_PATH");
        if (certPath == null || certPath.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu biến môi trường bắt buộc: KAGE_TLS_CERT_PATH (TLS đang bật, ADR-027)");
        }
        if (keyPath == null || keyPath.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu biến môi trường bắt buộc: KAGE_TLS_KEY_PATH (TLS đang bật, ADR-027)");
        }
        return new TlsConfig(true, new File(certPath), new File(keyPath));
    }

    /** Tắt TLS tường minh — dùng cho test/dev cục bộ. KHÔNG dùng cho Production (ADR-027). */
    public static TlsConfig disabled() {
        return new TlsConfig(false, null, null);
    }

    // Không dùng Boolean.parseBoolean() thẳng — nó coi MỌI chuỗi khác "true" là false, nghĩa là gõ
    // nhầm giá trị (vd "flase") sẽ ÂM THẦM tắt TLS ở production thay vì báo lỗi rõ ràng.
    private static boolean parseEnabled(String value) {
        if (value == null) {
            return true;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalStateException("KAGE_TLS_ENABLED phải là 'true' hoặc 'false', nhận: '" + value + "'");
    }
}
