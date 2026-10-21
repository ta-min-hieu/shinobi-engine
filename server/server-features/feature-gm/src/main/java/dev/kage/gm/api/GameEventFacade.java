package dev.kage.gm.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.kage.gm.domain.GameEvent;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.gm.domain.GameEventPort;

/**
 * Sự kiện nhân đôi EXP/rơi đồ (ADR-067). Trạng thái sống trong bộ nhớ (đọc ở mỗi lần thưởng giết quái,
 * không chạm DB), ghi xuống DB để sống qua khởi động lại. Bật/tắt chỉ qua lệnh GM (đã có audit).
 */
public final class GameEventFacade {

    /** Không có sự kiện nào, không bao giờ (test). */
    public static final GameEventFacade DISABLED = new GameEventFacade(GameEventPort.NONE, Clock.systemUTC());

    private final GameEventPort port;
    private final Clock clock;
    private final Map<GameEventKind, GameEvent> active = new EnumMap<>(GameEventKind.class);

    /** Dựng ở composition root — nạp các sự kiện đã lưu (kể cả đã hết hạn; lọc theo đồng hồ khi hỏi). */
    public GameEventFacade(GameEventPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
        for (GameEvent event : port.loadAll()) {
            active.put(event.kind(), event);
        }
    }

    /** Hệ số hiện tại — 1 nếu không có sự kiện hoặc đã hết hạn. */
    public synchronized int multiplier(GameEventKind kind) {
        GameEvent event = active.get(kind);
        return event != null && event.activeAt(clock.instant()) ? event.multiplier() : 1;
    }

    /** Các sự kiện còn hiệu lực, theo thứ tự enum. */
    public synchronized List<GameEvent> active() {
        Instant now = clock.instant();
        List<GameEvent> result = new ArrayList<>();
        for (GameEvent event : active.values()) {
            if (event.activeAt(now)) {
                result.add(event);
            }
        }
        return result;
    }

    /** Bật (đè sự kiện cùng loại). */
    public synchronized GameEvent start(GameEventKind kind, int multiplier, Duration duration, long gmAuditLogId) {
        GameEvent event = new GameEvent(kind, multiplier, clock.instant().plus(duration));
        port.save(event, gmAuditLogId);
        active.put(kind, event);
        return event;
    }

    /** Tắt; {@code false} nếu không có gì đang chạy. */
    public synchronized boolean stop(GameEventKind kind) {
        boolean wasActive = multiplier(kind) > 1;
        port.clear(kind);
        active.remove(kind);
        return wasActive;
    }
}
