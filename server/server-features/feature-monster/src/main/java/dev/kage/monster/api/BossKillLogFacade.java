package dev.kage.monster.api;

import java.util.List;

import dev.kage.monster.domain.BossKillLogPort;

/** API lịch sử boss đã hạ (ADR-296). Chạm DB — chỉ gọi trên luồng nền / Virtual Thread. */
public final class BossKillLogFacade {

    /** Số dòng trả về. */
    public static final int LIMIT = 20;

    /** Không ghi, không có lịch sử (composition test). */
    public static final BossKillLogFacade DISABLED = new BossKillLogFacade(new BossKillLogPort() {
        @Override
        public void record(List<Kill> kills) {
            // không lưu gì
        }

        @Override
        public List<Entry> recent(long characterId, int limit) {
            return List.of();
        }
    });

    private final BossKillLogPort port;

    /** Dựng ở composition root. */
    public BossKillLogFacade(BossKillLogPort port) {
        this.port = port;
    }

    /** Ghi mọi người tham gia một trận (rỗng ⇒ không làm gì). */
    public void record(List<BossKillLogPort.Kill> kills) {
        if (!kills.isEmpty()) {
            port.record(kills);
        }
    }

    /** Các lần hạ boss gần nhất của {@code characterId}. */
    public List<BossKillLogPort.Entry> recent(long characterId) {
        return port.recent(characterId, LIMIT);
    }
}
