package dev.kage.engine.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Nơi CHỨA service, dùng ở composition root — KHÔNG phải Service Locator (ENGINE.md §6).
 *
 * <p><b>Ranh giới bắt buộc</b>: {@code get()} chỉ được gọi lúc khởi tạo (composition root) để
 * lấy service rồi truyền qua constructor. KHÔNG truyền {@code ServiceRegistry} vào constructor
 * của System/gameplay class — đó là dấu hiệu Registry thoái hoá thành Service Locator thật,
 * vi phạm Điều 12 (Explicit over Implicit).
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    public <T> T get(Class<T> type) {
        var instance = services.get(type);
        if (instance == null) {
            // Fail fast (Điều 16) — thiếu service lúc khởi động phải nổ ngay, không NPE mù mờ về sau.
            throw new IllegalStateException("Service chưa đăng ký: " + type.getName());
        }
        return type.cast(instance);
    }
}
