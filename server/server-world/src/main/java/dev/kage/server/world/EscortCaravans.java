package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Đoàn hộ tống đang đi trong MỘT World (ADR-114 D1/D2) — chỉ chạy trên tick thread của World đó. Một người một đoàn.
 *
 * <p>Đoàn không phải entity ECS: nó không đánh, không bị nhắm, không vào AoI. Quái "đang có mục tiêu" (đang giao chiến)
 * đứng trong {@link #MONSTER_REACH_METERS} quanh đoàn gây sát thương bằng công của nó mỗi {@link #HIT_INTERVAL_SECONDS}
 * — không đổi AI quái nào. Danh sách mảng duyệt bằng chỉ số: không cấp phát mỗi tick.
 */
final class EscortCaravans {

    /** Người hộ tống phải đứng trong chừng này mét thì đoàn mới đi. */
    static final float GUARD_RADIUS_METERS = 6f;

    /** Quái đang giao chiến trong chừng này mét quanh đoàn thì đánh vào đoàn. */
    static final float MONSTER_REACH_METERS = 2f;

    static final float HIT_INTERVAL_SECONDS = 1f;

    /** Nhịp báo vị trí khi đang đi/đứng chờ (ngoài các lần máu/trạng thái đổi). */
    static final float REPORT_INTERVAL_SECONDS = 1f;

    /** Tra vị trí X của người hộ tống; {@link Float#NaN} nếu không ở World này. */
    @FunctionalInterface
    interface GuardLookup {
        float xOf(long characterId);
    }

    /** Tổng công của quái đang giao chiến trong {@code reach} mét quanh {@code x}. */
    @FunctionalInterface
    interface ThreatLookup {
        int attackNear(float x, float reach);
    }

    /** Một đoàn đang đi. */
    private static final class Caravan {
        private final long characterId;
        private final EscortRoute route;
        private float x;
        private int hp;
        private float hitTimer;
        private float reportTimer;
        private WorldOutboundSink.EscortState lastState;

        Caravan(long characterId, EscortRoute route) {
            this.characterId = characterId;
            this.route = route;
            this.x = route.startX();
            this.hp = route.maxHp();
            this.lastState = WorldOutboundSink.EscortState.WAITING;
        }

        WorldOutboundSink.EscortSnapshot snapshot(WorldOutboundSink.EscortState state) {
            return new WorldOutboundSink.EscortSnapshot(
                    characterId, route.code(), x, Math.max(hp, 0), route.maxHp(), state);
        }

        boolean arrived() {
            return route.endX() >= route.startX() ? x >= route.endX() : x <= route.endX();
        }
    }

    private final List<Caravan> caravans = new ArrayList<>();

    /** Bắt đầu (thay đoàn cũ nếu có — đoàn cũ báo {@code ABANDONED}). */
    void start(long characterId, EscortRoute route, EscortListener sink) {
        cancel(characterId, sink);
        Caravan caravan = new Caravan(characterId, route);
        caravans.add(caravan);
        sink.onEscortChanged(caravan.snapshot(WorldOutboundSink.EscortState.WAITING));
    }

    /** Huỷ đoàn của người này (bỏ quest, rời World, ngắt kết nối) — báo {@code ABANDONED}. Không có đoàn thì thôi. */
    void cancel(long characterId, EscortListener sink) {
        for (int i = caravans.size() - 1; i >= 0; i--) {
            Caravan caravan = caravans.get(i);
            if (caravan.characterId == characterId) {
                removeAt(i);
                sink.onEscortChanged(caravan.snapshot(WorldOutboundSink.EscortState.ABANDONED));
            }
        }
    }

    /** Một tick: đi, chịu đòn, tới nơi hoặc gục. */
    void tick(float deltaSeconds, GuardLookup guards, ThreatLookup threats, EscortListener sink) {
        for (int i = caravans.size() - 1; i >= 0; i--) {
            Caravan caravan = caravans.get(i);
            float guardX = guards.xOf(caravan.characterId);
            if (Float.isNaN(guardX)) {
                removeAt(i);
                sink.onEscortChanged(caravan.snapshot(WorldOutboundSink.EscortState.ABANDONED));
                continue;
            }
            boolean moving = Math.abs(guardX - caravan.x) <= GUARD_RADIUS_METERS;
            if (moving) {
                float step = caravan.route.speedMps() * deltaSeconds;
                caravan.x += Math.signum(caravan.route.endX() - caravan.route.startX()) * step;
            }
            boolean hurt = applyThreat(caravan, deltaSeconds, threats);
            WorldOutboundSink.EscortState state = stateOf(caravan, moving);
            if (state == WorldOutboundSink.EscortState.ARRIVED || state == WorldOutboundSink.EscortState.FAILED) {
                removeAt(i);
                sink.onEscortChanged(caravan.snapshot(state));
                continue;
            }
            caravan.reportTimer += deltaSeconds;
            if (hurt || state != caravan.lastState || caravan.reportTimer >= REPORT_INTERVAL_SECONDS) {
                caravan.reportTimer = 0f;
                caravan.lastState = state;
                sink.onEscortChanged(caravan.snapshot(state));
            }
        }
    }

    /** Số đoàn đang đi — cho test. */
    int size() {
        return caravans.size();
    }

    private static boolean applyThreat(Caravan caravan, float deltaSeconds, ThreatLookup threats) {
        caravan.hitTimer += deltaSeconds;
        if (caravan.hitTimer < HIT_INTERVAL_SECONDS) {
            return false;
        }
        caravan.hitTimer -= HIT_INTERVAL_SECONDS;
        int damage = threats.attackNear(caravan.x, MONSTER_REACH_METERS);
        caravan.hp -= damage;
        return damage > 0;
    }

    private static WorldOutboundSink.EscortState stateOf(Caravan caravan, boolean moving) {
        if (caravan.hp <= 0) {
            return WorldOutboundSink.EscortState.FAILED;
        }
        if (caravan.arrived()) {
            caravan.x = caravan.route.endX();
            return WorldOutboundSink.EscortState.ARRIVED;
        }
        return moving ? WorldOutboundSink.EscortState.MOVING : WorldOutboundSink.EscortState.WAITING;
    }

    private void removeAt(int index) {
        int last = caravans.size() - 1;
        caravans.set(index, caravans.get(last));
        caravans.remove(last);
    }
}
