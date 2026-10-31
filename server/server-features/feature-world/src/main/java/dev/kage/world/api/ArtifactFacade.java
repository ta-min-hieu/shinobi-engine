package dev.kage.world.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.world.domain.ArtifactPort;

/**
 * Pháp bảo (ADR-127) — tra chiêu theo mẫu món + giữ hồi chiêu theo nhân vật (RAM, server quyết; khởi động lại server
 * thì hồi chiêu về 0 — chấp nhận, như hồi chiêu skill). Chạm DB lần đầu — gọi trên Virtual Thread.
 */
public final class ArtifactFacade {

    /** Composition không có DB (test) — không món nào là pháp bảo. */
    public static final ArtifactFacade DISABLED = new ArtifactFacade(Map::of);

    /** Kết quả xin dùng chiêu. */
    public sealed interface Use {

        /** Dùng được — hồi chiêu đã bắt đầu tính. */
        record Ready(ArtifactPort.ArtifactSkill skill) implements Use {}

        /** Còn hồi chiêu. */
        record Cooling(long secondsLeft) implements Use {}

        /** Món đang mặc ở ô pháp bảo không có chiêu (hoặc không mặc gì). */
        record NoSkill() implements Use {}
    }

    private final ArtifactPort port;
    private final Map<Long, Instant> readyAt = new ConcurrentHashMap<>();
    private volatile Map<Long, ArtifactPort.ArtifactSkill> cached;

    /** Dựng ở composition root. */
    public ArtifactFacade(ArtifactPort port) {
        this.port = port;
    }

    /** Chiêu của một mẫu món. */
    public Optional<ArtifactPort.ArtifactSkill> skillOf(long itemTemplateId) {
        Map<Long, ArtifactPort.ArtifactSkill> loaded = cached;
        if (loaded == null) {
            loaded = Map.copyOf(port.skills());
            cached = loaded;
        }
        return Optional.ofNullable(loaded.get(itemTemplateId));
    }

    /** Xin dùng chiêu của pháp bảo mẫu {@code itemTemplateId} lúc {@code now}; được thì bắt đầu hồi chiêu NGAY. */
    public Use use(long characterId, Optional<Long> itemTemplateId, Instant now) {
        Optional<ArtifactPort.ArtifactSkill> skill = itemTemplateId.flatMap(this::skillOf);
        if (skill.isEmpty()) {
            return new Use.NoSkill();
        }
        Instant next = now.plus(Duration.ofSeconds(skill.get().cooldownSeconds()));
        Instant previous =
                readyAt.compute(characterId, (id, ready) -> ready == null || !ready.isAfter(now) ? next : ready);
        if (!previous.equals(next)) {
            return new Use.Cooling(Math.max(1L, Duration.between(now, previous).toSeconds()));
        }
        return new Use.Ready(skill.get());
    }
}
