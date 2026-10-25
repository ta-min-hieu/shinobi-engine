package dev.kage.world.api;

import java.util.List;
import java.util.Optional;

import dev.kage.world.domain.TowerPort;

/** API Trấn Yêu Tháp (ADR-113). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class TowerFacade {

    /** Số người trên bảng xếp hạng tầng. */
    public static final int TOP_SIZE = 10;

    /** Composition không có tháp (test) — không tầng nào. */
    public static final TowerFacade DISABLED = new TowerFacade(new TowerPort() {
        @Override
        public Optional<Floor> floor(int floor) {
            return Optional.empty();
        }

        @Override
        public int topFloor() {
            return 0;
        }

        @Override
        public int bestFloorOf(long characterId) {
            return 0;
        }

        @Override
        public Optional<Reward> recordCleared(long characterId, int floor) {
            return Optional.empty();
        }

        @Override
        public List<Climber> top(int limit) {
            return List.of();
        }
    });

    private final TowerPort port;

    /** Dựng ở composition root. */
    public TowerFacade(TowerPort port) {
        this.port = port;
    }

    /** Tầng kế tiếp của người này (cao nhất + 1); rỗng nếu đã qua hết tháp. */
    public Optional<TowerPort.Floor> nextFloorOf(long characterId) {
        return port.floor(port.bestFloorOf(characterId) + 1);
    }

    /** Tầng cao nhất đã qua (0 = chưa). */
    public int bestFloorOf(long characterId) {
        return port.bestFloorOf(characterId);
    }

    /** Số tầng của tháp. */
    public int topFloor() {
        return port.topFloor();
    }

    /** Xem {@link TowerPort#recordCleared}. */
    public Optional<TowerPort.Reward> recordCleared(long characterId, int floor) {
        return port.recordCleared(characterId, floor);
    }

    /** Bảng {@link #TOP_SIZE} người leo cao nhất. */
    public List<TowerPort.Climber> top() {
        return port.top(TOP_SIZE);
    }
}
