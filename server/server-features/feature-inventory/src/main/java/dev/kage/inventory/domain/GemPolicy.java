package dev.kage.inventory.domain;

/** Luật khảm Linh Ngọc (ADR-064 D3) — hàm thuần. */
public final class GemPolicy {

    /** Linh thạch mỗi lần khảm (sink), thành hay hỏng đều mất. */
    public static final long SOCKET_FEE = 200L;

    /** Xác suất thành công của một lần khảm thường. */
    public static final double SUCCESS_RATE = 0.5;

    /** Hỏng liên tiếp chừng này lần thì lần kế chắc chắn thành công. */
    public static final int PITY_AFTER_FAILURES = 3;

    private GemPolicy() {}

    /**
     * Lần khảm này thành công không.
     *
     * @param failures số lần hỏng liên tiếp TRƯỚC lần này
     * @param roll số ngẫu nhiên đều trong {@code [0, 1)} — chỉ sinh ở server
     */
    public static boolean succeeds(int failures, double roll) {
        return failures >= PITY_AFTER_FAILURES || roll < SUCCESS_RATE;
    }
}
