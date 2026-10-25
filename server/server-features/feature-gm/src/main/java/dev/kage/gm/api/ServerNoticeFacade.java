package dev.kage.gm.api;

import java.time.Instant;
import java.util.Optional;

import dev.kage.gm.domain.ServerNoticePort;

/**
 * Thông báo server (ADR-103). Đọc mỗi lần có người vào world ⇒ giữ bản sao trong RAM, chỉ ghi DB khi ADMIN đặt/xoá
 * (một tiến trình server — single-realm, ADR-010).
 */
public final class ServerNoticeFacade {

    /** Không bao giờ có thông báo (test). */
    public static final ServerNoticeFacade DISABLED = new ServerNoticeFacade(ServerNoticePort.NONE);

    private final ServerNoticePort port;
    private volatile Optional<String> cached;

    /** Dựng ở composition root — nạp thông báo đã lưu một lần. */
    public ServerNoticeFacade(ServerNoticePort port) {
        this.port = port;
        this.cached = port.current();
    }

    /** Thông báo đang đặt. Không chạm DB. */
    public Optional<String> current() {
        return cached;
    }

    /** ADMIN đặt thông báo mới. Chạm DB — gọi trên Virtual Thread. */
    public synchronized void set(String body, long updatedBy, Instant at) {
        port.set(body, updatedBy, at);
        cached = Optional.of(body);
    }

    /** ADMIN xoá thông báo. Chạm DB — gọi trên Virtual Thread. */
    public synchronized void clear() {
        port.clear();
        cached = Optional.empty();
    }
}
