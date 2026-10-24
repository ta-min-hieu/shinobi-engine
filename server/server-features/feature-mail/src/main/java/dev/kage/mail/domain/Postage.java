package dev.kage.mail.domain;

/**
 * Phí thư — gold sink của thư người chơi (ADR-076, chủ dự án chọn 2026-10-24 qua lượt hỏi lô 19).
 *
 * <p><b>Ai trả, khi nào.</b> Chỉ thư NGƯỜI CHƠI có đính kèm (vàng hoặc vật phẩm) — thư chào hỏi rỗng
 * miễn phí, thư hệ thống ({@code senderId} rỗng) không bao giờ mất phí. Phí bị trừ cùng câu trừ vàng
 * đính kèm, trong cùng transaction gửi thư, và KHÔNG đến tay ai: đó là chỗ vàng rời khỏi nền kinh tế
 * (GDD §10 "Gold Sink bắt buộc").
 *
 * <p><b>Công thức.</b> {@value #BASE_FEE} + {@value #GOLD_RATE_PERCENT}% số vàng đính kèm (làm tròn
 * xuống). Phần trăm đánh vào vàng chứ không vào giá trị vật phẩm vì server không có "giá trị" chuẩn
 * cho một món — chính phường thị (ADR-063) mới định giá.
 */
public final class Postage {

    /** Phí nền cho mọi thư người chơi có đính kèm. */
    public static final long BASE_FEE = 10L;

    /** Phần trăm đánh trên số vàng đính kèm. */
    public static final long GOLD_RATE_PERCENT = 1L;

    private static final long PERCENT = 100L;

    private Postage() {}

    /**
     * Phí của một lá thư người chơi.
     *
     * @param goldAmount vàng đính kèm, đã qua {@link MailDraft#validate()} (không âm)
     * @param attachmentCount số dòng vật phẩm đính kèm
     * @return 0 nếu thư không đính kèm gì
     */
    public static long feeFor(long goldAmount, int attachmentCount) {
        if (goldAmount <= 0L && attachmentCount == 0) {
            return 0L;
        }
        return BASE_FEE + goldAmount / PERCENT * GOLD_RATE_PERCENT;
    }
}
