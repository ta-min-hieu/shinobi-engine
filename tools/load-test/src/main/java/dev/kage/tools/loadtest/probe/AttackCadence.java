package dev.kage.tools.loadtest.probe;

/**
 * Lịch gửi {@code AttackCommand} của vòng quan sát — tách khỏi {@link GameProbe} để kiểm được bằng
 * test thuần (không socket, không đồng hồ thật).
 *
 * <p><b>Vì sao không còn là "gửi một lần rồi chờ đúng nhịp"</b> (kiểm sống V50, 2026-10-01): ở 5/10
 * lượt, nhát đầu của người chơi tới trễ ~1,15–1,3 s sau lúc "đi bộ xong". Nguyên nhân có hai lớp,
 * cả hai thuộc probe:
 *
 * <ol>
 *   <li><b>Nhát mở màn bị bỏ im lặng.</b> Lúc vừa đứng lại, con quái thường vẫn còn đang đuổi tới,
 *       ngoài tầm 2m. {@code CombatSystem} bỏ lệnh ngoài tầm mà không báo gì và KHÔNG nhớ lại ý định
 *       đánh (ô {@code shouldDropAttackRequestSilently_whenTargetStillOutOfRange...} của
 *       {@code CombatSystemTest} ở server-world),
 *       nên lần thử kế tiếp là sau trọn {@code attackIntervalMillis}. Trong lúc đó quái vào tầm và
 *       đánh 1–2 nhát — tức probe tự sinh ra nhát "phủ đầu" thứ ba mà luật {@code DuelMargin} không
 *       giả định. ⇒ Trước nhát ĐẦU TIÊN được server xác nhận (một {@code DamageEvent} do chính mình
 *       ra, trúng hay hụt đều tính), probe thử lại mỗi {@link #FIRST_STRIKE_RETRY_MILLIS} — như một
 *       người chơi bấm ngay khi con quái áp sát. Spam ở đây vô hại: cooldown là của server, không
 *       ra được hai đòn sớm hơn.
 *   <li><b>Nhịp bị kéo dài thêm tới 200ms.</b> Vòng đọc chặn tới {@code OBSERVE_READ_TIMEOUT_MILLIS}
 *       mỗi lần, nên lệnh "đến hạn" có thể chờ thêm trọn cửa sổ đó: 1100ms cấu hình thành
 *       1100–1300ms thật (đúng dải "~1,1–1,25 s" đo được). {@link #readTimeoutMillis} cắt cửa sổ đọc
 *       đúng tới hạn gửi kế tiếp.
 * </ol>
 *
 * <p>Sau nhát đầu, nhịp trở về đúng {@code attackIntervalMillis} — vẫn phải LỚN HƠN cooldown server
 * (bẫy nhịp đánh: dùng 1100ms).
 */
final class AttackCadence {

    /** Khoảng thử lại trước nhát đầu: ~6 tick server, nhỏ hơn hẳn 1 giây cooldown. */
    static final long FIRST_STRIKE_RETRY_MILLIS = 100L;

    /** Sàn cửa sổ đọc — xem {@link #readTimeoutMillis}. */
    static final long MIN_READ_TIMEOUT_MILLIS = 20L;

    private final long intervalMillis;
    private long nextSendAtMillis;
    private boolean firstStrikeConfirmed;

    /**
     * @param intervalMillis nhịp gửi sau khi nhát đầu đã ra
     * @param startMillis thời điểm bắt đầu — lệnh đầu tiên đến hạn ngay
     */
    AttackCadence(long intervalMillis, long startMillis) {
        this.intervalMillis = intervalMillis;
        this.nextSendAtMillis = startMillis;
    }

    /**
     * Đã tới lúc gửi lệnh tiếp theo chưa — đồng thời chuyển sang nhịp thường khi thấy nhát đầu.
     *
     * @param nowMillis đồng hồ hiện tại
     * @param ownAttacksResolved số đòn đánh do CHÍNH nhân vật ra mà server đã xác nhận tới giờ
     * @return {@code true} nếu phải gửi một {@code AttackCommand} ngay bây giờ
     */
    boolean isDue(long nowMillis, int ownAttacksResolved) {
        if (!firstStrikeConfirmed && ownAttacksResolved > 0) {
            // Nhát đầu vừa ra — đo nhịp từ lúc THẤY nó, không từ lần thử lại cuối (có thể đã bị
            // cooldown bỏ), để nhát thứ hai không tới sớm hơn nhịp đã hẹn.
            firstStrikeConfirmed = true;
            nextSendAtMillis = nowMillis + intervalMillis;
        }
        return nowMillis >= nextSendAtMillis;
    }

    /** Ghi nhận vừa gửi một lệnh lúc {@code nowMillis}. */
    void onSent(long nowMillis) {
        nextSendAtMillis = nowMillis + (firstStrikeConfirmed ? intervalMillis : FIRST_STRIKE_RETRY_MILLIS);
    }

    /**
     * Cửa sổ chờ đọc frame tiếp theo: không vượt quá hạn gửi kế tiếp, để lệnh không bị trễ thêm.
     *
     * <p>Có SÀN {@link #MIN_READ_TIMEOUT_MILLIS}, không xuống 1ms: {@code GameConnection.readFrame}
     * đọc tiền tố độ dài rồi mới đọc thân, nên một lần hết giờ GIỮA frame làm lệch dòng byte. Cửa sổ
     * càng hẹp càng dễ cắt ngang. Trễ thêm tối đa 20ms (~1 tick server) — so với tới 200ms trước đây.
     *
     * @param nowMillis đồng hồ hiện tại
     * @param maxMillis trần cửa sổ đọc khi chưa tới hạn gửi
     * @return số mili-giây trong {@code [MIN_READ_TIMEOUT_MILLIS, maxMillis]}
     */
    int readTimeoutMillis(long nowMillis, int maxMillis) {
        long untilDue = nextSendAtMillis - nowMillis;
        return (int) Math.max(MIN_READ_TIMEOUT_MILLIS, Math.min(maxMillis, untilDue));
    }
}
