package dev.kage.quest.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import dev.kage.quest.domain.BountyBoard;
import dev.kage.quest.domain.BountyPort;
import dev.kage.quest.domain.BountyTemplate;

/** API bảng truy nã (ADR-101). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class BountyFacade {

    /** Ngày theo giờ Việt Nam — cùng mốc điểm danh (ADR-060). */
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Bảng hôm nay kèm trạng thái của người hỏi. */
    public record Board(List<BountyTemplate> bounties, Optional<BountyPort.Active> active, int completedToday) {

        /** Danh sách bất biến — record không tự copy. */
        public Board {
            bounties = List.copyOf(bounties);
        }
    }

    /** Composition không có truy nã (test). */
    public static final BountyFacade DISABLED = new BountyFacade(
            new BountyPort() {
                @Override
                public List<BountyTemplate> templates() {
                    return List.of();
                }

                @Override
                public Optional<Active> active(long characterId) {
                    return Optional.empty();
                }

                @Override
                public int completedOn(long characterId, LocalDate day) {
                    return 0;
                }

                @Override
                public AcceptOutcome accept(
                        long characterId, LocalDate day, BountyTemplate template, int maxCompletionsPerDay) {
                    return AcceptOutcome.NOT_ON_BOARD;
                }

                @Override
                public boolean abandon(long characterId) {
                    return false;
                }

                @Override
                public Optional<Progress> onKill(long characterId, String monsterCode) {
                    return Optional.empty();
                }
            },
            Clock.systemUTC());

    private final BountyPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public BountyFacade(BountyPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VIETNAM));
    }

    /** Bảng hôm nay. */
    public Board board(long characterId) {
        LocalDate day = today();
        return new Board(
                BountyBoard.of(day, port.templates()), port.active(characterId), port.completedOn(characterId, day));
    }

    /** Nhận truy nã {@code code} — phải có trên bảng hôm nay và đủ cấp ({@code level} do gateway đọc). */
    public BountyPort.AcceptOutcome accept(long characterId, String code, int level) {
        LocalDate day = today();
        Optional<BountyTemplate> template = BountyBoard.of(day, port.templates()).stream()
                .filter(t -> t.code().equals(code))
                .findFirst();
        if (template.isEmpty()) {
            return BountyPort.AcceptOutcome.NOT_ON_BOARD;
        }
        if (level < template.get().minLevel()) {
            return BountyPort.AcceptOutcome.LEVEL_TOO_LOW;
        }
        return port.accept(characterId, day, template.get(), BountyBoard.MAX_COMPLETIONS_PER_DAY);
    }

    /** Bỏ truy nã đang làm. */
    public boolean abandon(long characterId) {
        return port.abandon(characterId);
    }

    /** Một lần hạ quái của người ra đòn cuối. */
    public Optional<BountyPort.Progress> onKill(long characterId, String monsterCode) {
        return port.onKill(characterId, monsterCode);
    }
}
