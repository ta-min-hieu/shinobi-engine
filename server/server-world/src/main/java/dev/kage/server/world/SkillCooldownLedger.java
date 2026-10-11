package dev.kage.server.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Sổ hồi chiêu sống LÂU HƠN thực thể nhân vật — để thoát ra vào lại không xoá hồi chiêu (chủ dự án
 * chốt 2026-10-11, README dòng 37iii). Trước đó hồi chiêu chỉ nằm trong {@code SkillCooldownComponent}
 * của thực thể, mà thực thể bị huỷ lúc rời World ⇒ relog là dùng lại được Tuyệt Kỹ ngay.
 *
 * <p>Máu và Khí KHÔNG đi qua sổ này: chủ dự án giữ nguyên "vào lại là đầy".
 *
 * <p><b>Một sổ dùng chung cho mọi World của tiến trình</b> ({@code server-gateway} tạo một cái rồi
 * trao cho từng {@link WorldFacade}), nên hồi chiêu cũng theo nhân vật qua đấu trường / hầm ngục /
 * đổi kênh. ⚠️ Giới hạn có ý thức: rời World cũ và vào World mới là hai lệnh trên HAI luồng tick, nếu
 * lệnh vào chạy trước lệnh rời thì lượt đó không có gì để đọc — hồi chiêu reset như trước. Relog thì
 * không dính (cách nhau cả giây).
 *
 * <p>Lưu MỐC HẾT HẠN theo đồng hồ đơn điệu chứ không lưu "còn bao nhiêu giây": thời gian offline vẫn
 * trôi, đúng như người chơi mong đợi. Sổ chỉ nằm trong bộ nhớ — server khởi động lại là sạch, và đó
 * là cố ý (không đáng một bảng DB cho vài chục giây hồi chiêu).
 *
 * <p>An toàn đa luồng: mỗi World gọi từ luồng tick của riêng nó.
 */
public final class SkillCooldownLedger {

    private static final float NANOS_PER_SECOND = 1_000_000_000f;

    private final Map<Long, long[]> readyAtNanosByCharacterId = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;
    private final boolean enabled;

    /** Sổ dùng đồng hồ đơn điệu của JVM. */
    public SkillCooldownLedger() {
        this(System::nanoTime);
    }

    /** @param nanoClock đồng hồ đơn điệu tính bằng nano giây — test tiêm đồng hồ giả */
    SkillCooldownLedger(LongSupplier nanoClock) {
        this(nanoClock, true);
    }

    private SkillCooldownLedger(LongSupplier nanoClock, boolean enabled) {
        this.nanoClock = nanoClock;
        this.enabled = enabled;
    }

    /**
     * Sổ KHÔNG nhớ gì — mặc định của một {@link WorldFacade} chưa được trao sổ thật (test, công cụ):
     * hành vi y như trước khi có luật này.
     */
    static SkillCooldownLedger forgetful() {
        return new SkillCooldownLedger(System::nanoTime, false);
    }

    /**
     * Ghi lại hồi chiêu của nhân vật vừa rời World. Không còn ô nào đang hồi thì xoá hẳn dòng của họ
     * — sổ chỉ giữ những người đang thật sự phải chờ.
     *
     * @param remainingSeconds số giây còn lại của từng ô chiêu (không bị sửa)
     */
    void remember(long characterId, float[] remainingSeconds) {
        if (!enabled) {
            return;
        }
        long now = nanoClock.getAsLong();
        dropExpired(now);
        long[] readyAt = new long[remainingSeconds.length];
        boolean anyPending = false;
        for (int slot = 0; slot < remainingSeconds.length; slot++) {
            if (remainingSeconds[slot] > 0f) {
                readyAt[slot] = now + (long) (remainingSeconds[slot] * NANOS_PER_SECOND);
                anyPending = true;
            }
        }
        if (anyPending) {
            readyAtNanosByCharacterId.put(characterId, readyAt);
        } else {
            readyAtNanosByCharacterId.remove(characterId);
        }
    }

    /**
     * Điền hồi chiêu còn lại vào {@code remainingSeconds} của nhân vật vừa vào World; không có dòng
     * nào thì để nguyên (toàn 0 = sẵn sàng).
     */
    void restoreInto(long characterId, float[] remainingSeconds) {
        long[] readyAt = readyAtNanosByCharacterId.get(characterId);
        if (readyAt == null) {
            return;
        }
        long now = nanoClock.getAsLong();
        for (int slot = 0; slot < remainingSeconds.length && slot < readyAt.length; slot++) {
            remainingSeconds[slot] = readyAt[slot] == 0L ? 0f : Math.max(0f, (readyAt[slot] - now) / NANOS_PER_SECOND);
        }
    }

    /** Số nhân vật đang có dòng trong sổ — cho test kiểm sổ không phình. */
    int trackedCharacterCount() {
        return readyAtNanosByCharacterId.size();
    }

    private void dropExpired(long now) {
        readyAtNanosByCharacterId.values().removeIf(readyAt -> allReady(readyAt, now));
    }

    private static boolean allReady(long[] readyAt, long now) {
        for (long slotReadyAt : readyAt) {
            if (slotReadyAt != 0L && slotReadyAt - now > 0L) {
                return false;
            }
        }
        return true;
    }
}
