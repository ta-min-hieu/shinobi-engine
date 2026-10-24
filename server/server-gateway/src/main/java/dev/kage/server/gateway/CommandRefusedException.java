package dev.kage.server.gateway;

import java.io.Serial;

/**
 * Một lệnh bị từ chối bằng {@code ErrorResponse} (mã + khoá i18n) từ SÂU bên trong một lớp {@code *Commands} chạy trên
 * Virtual Thread — {@code GatewayChannelHandler.executeBlockingOn} bắt và trả lời client, thay vì mọi lớp lệnh phải
 * cầm {@code ChannelHandlerContext}. Dùng cho luật cắt ngang nhiều lệnh, ví dụ "đã chặn nhau" (ADR-091).
 */
final class CommandRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String errorKey;

    CommandRefusedException(int code, String errorKey) {
        super(errorKey, null, false, false);
        this.code = code;
        this.errorKey = errorKey;
    }

    int code() {
        return code;
    }

    String errorKey() {
        return errorKey;
    }
}
