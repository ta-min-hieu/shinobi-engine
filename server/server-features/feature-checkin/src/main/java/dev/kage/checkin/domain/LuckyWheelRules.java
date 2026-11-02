package dev.kage.checkin.domain;

/** Luật vòng quay may mắn (ADR-151) — hằng số; ngày tính theo giờ Việt Nam như quẻ bói ({@link FortuneRules#day}). */
public final class LuckyWheelRules {

    /** Lượt miễn phí mỗi ngày. */
    public static final int FREE_SPINS_PER_DAY = 1;

    /** Lượt mua tối đa mỗi ngày (sau lượt miễn phí). */
    public static final int PAID_SPINS_PER_DAY = 10;

    /** Giá một lượt mua (linh thạch). */
    public static final long SPIN_COST = 1_000L;

    private LuckyWheelRules() {}
}
