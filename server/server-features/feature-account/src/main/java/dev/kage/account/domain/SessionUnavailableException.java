package dev.kage.account.domain;

/**
 * Kho session (Redis) không dùng được lúc này — ném từ {@link SessionPort} thay cho exception riêng của
 * thư viện Redis, để tầng application phân biệt được "kho phiên hỏng" với mọi lỗi khác mà không phải
 * biết Redis là gì (Điều 23).
 */
public final class SessionUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
