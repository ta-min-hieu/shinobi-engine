package dev.kage.world.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.world.domain.CavePort;
import dev.kage.world.domain.CaveRules;

/**
 * Động phủ (ADR-129). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. Lời mời thăm chỉ ở RAM
 * (khởi động lại server thì mất — mời lại là xong).
 */
public final class CaveFacade {

    /** Composition không có động phủ (test). */
    public static final CaveFacade DISABLED = new CaveFacade(new CavePort() {
        @Override
        public List<Decoration> catalog() {
            return List.of();
        }

        @Override
        public Cave cave(long characterId, Instant now) {
            return new Cave(1, now, List.of());
        }

        @Override
        public Result collect(long characterId, Instant now) {
            return new Result(Outcome.NOTHING_TO_COLLECT, 0L);
        }

        @Override
        public Result upgrade(long characterId, Instant now) {
            return new Result(Outcome.MAX_LEVEL, 0L);
        }

        @Override
        public Result decorate(long characterId, String code, Instant now) {
            return new Result(Outcome.UNKNOWN_DECORATION, 0L);
        }
    });

    /** Một lời mời: khách nào, của chủ nào. */
    private record Invite(long guestId, long ownerId) {}

    private final CavePort port;
    private final Map<Invite, Instant> invites = new ConcurrentHashMap<>();
    private volatile List<CavePort.Decoration> cachedCatalog;

    /** Dựng ở composition root. */
    public CaveFacade(CavePort port) {
        this.port = port;
    }

    /** Danh mục bày trí — nội dung tĩnh (Điều 27), đọc DB một lần. */
    public List<CavePort.Decoration> catalog() {
        List<CavePort.Decoration> loaded = cachedCatalog;
        if (loaded == null) {
            loaded = List.copyOf(port.catalog());
            cachedCatalog = loaded;
        }
        return loaded;
    }

    /** Động phủ của một người (dựng nếu chưa có). */
    public CavePort.Cave cave(long characterId, Instant now) {
        return port.cave(characterId, now);
    }

    /** Tổng thưởng bày trí (%) của các món đã bày. */
    public int bonusPercentOf(CavePort.Cave cave) {
        int total = 0;
        for (CavePort.Decoration decoration : catalog()) {
            if (cave.decorations().contains(decoration.code())) {
                total += decoration.bonusPercent();
            }
        }
        return total;
    }

    /** Linh thạch đang nằm trong kho lúc {@code now}. */
    public long stored(CavePort.Cave cave, Instant now) {
        return CaveRules.accrued(cave.veinLevel(), bonusPercentOf(cave), cave.lastCollectedAt(), now);
    }

    /** Thu linh thạch. */
    public CavePort.Result collect(long characterId, Instant now) {
        return port.collect(characterId, now);
    }

    /** Nâng linh mạch. */
    public CavePort.Result upgrade(long characterId, Instant now) {
        return port.upgrade(characterId, now);
    }

    /** Mua + bày một món. */
    public CavePort.Result decorate(long characterId, String code, Instant now) {
        return port.decorate(characterId, code, now);
    }

    /** Ghi lời mời {@code guestId} thăm động phủ của {@code ownerId}, hết hạn sau {@link CaveRules#INVITE_TTL}. */
    public void invite(long ownerId, long guestId, Instant now) {
        invites.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        invites.put(new Invite(guestId, ownerId), now.plus(CaveRules.INVITE_TTL));
    }

    /** {@code guestId} có đang được mời vào động phủ của {@code ownerId} không. Chủ luôn vào được nhà mình. */
    public boolean mayEnter(long guestId, long ownerId, Instant now) {
        if (guestId == ownerId) {
            return true;
        }
        Instant expires = invites.get(new Invite(guestId, ownerId));
        return expires != null && expires.isAfter(now);
    }
}
