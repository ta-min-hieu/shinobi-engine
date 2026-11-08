package dev.kage.party.api;

import java.time.Instant;
import java.util.List;

import dev.kage.party.domain.PartyGoldSplitPort;

/** Facade chia vàng cho nhóm (ADR-278) — kiểm số tiền rồi giao port. */
public final class PartyGoldSplitFacade {

    /** Tổng tối đa một lần chia. */
    public static final long MAX_AMOUNT = 1_000_000_000L;

    /** Composition không có DB (test). */
    public static final PartyGoldSplitFacade DISABLED = new PartyGoldSplitFacade(PartyGoldSplitPort.DISABLED);

    /** Kết quả một lần chia nhìn từ lớp gọi. */
    public enum Outcome {
        OK,
        NO_RECIPIENT,
        INVALID_AMOUNT,
        NOT_ENOUGH_GOLD
    }

    /** Kết quả + mỗi người nhận bao nhiêu + vàng còn lại của người chia. */
    public record Result(Outcome outcome, long each, long goldAfter) {}

    private final PartyGoldSplitPort port;

    /** @param port nơi trừ/cộng vàng trong một transaction (Postgres ở production) */
    public PartyGoldSplitFacade(PartyGoldSplitPort port) {
        this.port = port;
    }

    /** Mỗi người nhận bao nhiêu khi chia {@code amount} cho {@code recipients} người (phần dư ở lại người chia). */
    public static long share(long amount, int recipients) {
        return recipients <= 0 ? 0L : amount / recipients;
    }

    /**
     * Chia {@code amount} đều cho {@code recipients} (không gồm người chia). Số tiền ngoài 1 – {@value #MAX_AMOUNT},
     * hoặc nhỏ hơn số người nhận ⇒ {@link Outcome#INVALID_AMOUNT}; không ai nhận ⇒ {@link Outcome#NO_RECIPIENT}.
     */
    public Result split(long giverId, List<Long> recipients, long amount, Instant at) {
        if (recipients.isEmpty()) {
            return new Result(Outcome.NO_RECIPIENT, 0L, 0L);
        }
        long each = share(amount, recipients.size());
        if (amount < 1L || amount > MAX_AMOUNT || each < 1L) {
            return new Result(Outcome.INVALID_AMOUNT, 0L, 0L);
        }
        PartyGoldSplitPort.Split split = port.split(giverId, List.copyOf(recipients), each, at);
        return split.outcome() == PartyGoldSplitPort.Outcome.OK
                ? new Result(Outcome.OK, each, split.goldAfter())
                : new Result(Outcome.NOT_ENOUGH_GOLD, 0L, 0L);
    }
}
