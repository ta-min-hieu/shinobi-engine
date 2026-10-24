package dev.kage.guild.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.SpiritVein;
import dev.kage.guild.domain.SpiritVeinPort;

/** API Tranh đoạt Linh Mạch (ADR-090). Chạm DB — chỉ gọi trên Virtual Thread hoặc luồng nền. */
public final class SpiritVeinFacade {

    /** Composition không có Linh Mạch (test) — không vùng nào. */
    public static final SpiritVeinFacade DISABLED = new SpiritVeinFacade(new SpiritVeinPort() {
        @Override
        public List<SpiritVein> veins() {
            return List.of();
        }

        @Override
        public Optional<Holder> holderOf(String veinCode, Instant now) {
            return Optional.empty();
        }

        @Override
        public void addPoint(String veinCode, Instant windowStart, long guildId, Instant at) {
            // Không có Linh Mạch nào để ghi điểm.
        }

        @Override
        public List<Score> scores(String veinCode, Instant windowStart) {
            return List.of();
        }

        @Override
        public Optional<Score> settle(String veinCode, Instant windowStart, Instant heldUntil) {
            return Optional.empty();
        }

        @Override
        public List<Payout> pendingPayouts() {
            return List.of();
        }

        @Override
        public void markPaid(Payout payout) {
            // Hàng đợi luôn rỗng.
        }
    });

    private final SpiritVeinPort port;

    /** Dựng ở composition root. */
    public SpiritVeinFacade(SpiritVeinPort port) {
        this.port = port;
    }

    /** Mọi Linh Mạch. */
    public List<SpiritVein> veins() {
        return port.veins();
    }

    /** Bang đang giữ Linh Mạch. */
    public Optional<SpiritVeinPort.Holder> holderOf(String veinCode, Instant now) {
        return port.holderOf(veinCode, now);
    }

    /** Cộng 1 điểm chiếm giữ. */
    public void addPoint(String veinCode, Instant windowStart, long guildId, Instant at) {
        port.addPoint(veinCode, windowStart, guildId, at);
    }

    /** Bảng điểm một khung. */
    public List<SpiritVeinPort.Score> scores(String veinCode, Instant windowStart) {
        return port.scores(veinCode, windowStart);
    }

    /** Chốt khung — xem {@link SpiritVeinPort#settle}. */
    public Optional<SpiritVeinPort.Score> settle(String veinCode, Instant windowStart, Instant heldUntil) {
        return port.settle(veinCode, windowStart, heldUntil);
    }

    /** Thư thưởng còn chờ gửi. */
    public List<SpiritVeinPort.Payout> pendingPayouts() {
        return port.pendingPayouts();
    }

    /** Đánh dấu đã gửi. */
    public void markPaid(SpiritVeinPort.Payout payout) {
        port.markPaid(payout);
    }
}
