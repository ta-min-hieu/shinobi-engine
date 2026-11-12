package dev.kage.mail.api;

import java.util.List;

import dev.kage.mail.domain.SentMailPort;

/** API thư đã gửi (ADR-290): {@value #LIMIT} thư gần nhất. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class SentMailFacade {

    /** Số thư trả về. */
    public static final int LIMIT = 20;

    /** Không có thư (composition test). */
    public static final SentMailFacade DISABLED = new SentMailFacade((id, limit) -> List.of());

    private final SentMailPort port;

    /** Dựng ở composition root. */
    public SentMailFacade(SentMailPort port) {
        this.port = port;
    }

    /** Các thư gần nhất {@code senderId} đã gửi. */
    public List<SentMailPort.Sent> recent(long senderId) {
        return port.recent(senderId, LIMIT);
    }
}
