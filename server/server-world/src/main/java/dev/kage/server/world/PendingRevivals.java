package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Nhân vật đang nằm chờ hồi sinh ở world chính (ADR-117): gục xuống thì nằm tại chỗ tối đa {@link #WAIT_SECONDS}
 * giây chờ người chơi chọn (về điểm hồi sinh, hoặc Hồi Hồn Đan đứng dậy tại chỗ); hết giờ tự về điểm hồi sinh như
 * trước ADR-117.
 * Chỉ tick thread chạm. Cấp phát một bản ghi lúc gục — không cấp phát mỗi tick.
 */
final class PendingRevivals {

    /** Thời gian nằm chờ chọn trước khi tự về điểm hồi sinh. */
    static final float WAIT_SECONDS = 30f;

    /** Hồi sinh tại chỗ được bao nhiêu phần trăm máu tối đa. */
    static final int IN_PLACE_HP_PERCENT = 30;

    private static final int PERCENT = 100;

    /** Một người đang nằm. */
    private static final class Pending {
        private final long characterId;
        private final boolean killedByPlayer;
        private float secondsLeft = WAIT_SECONDS;

        private Pending(long characterId, boolean killedByPlayer) {
            this.characterId = characterId;
            this.killedByPlayer = killedByPlayer;
        }
    }

    private final List<Pending> pending = new ArrayList<>();

    /** Vừa gục; {@code killedByPlayer} — PvP (luận võ ngoài đấu trường, bang chiến, truy nã) không được hồi tại chỗ. */
    void begin(long characterId, boolean killedByPlayer) {
        cancel(characterId);
        pending.add(new Pending(characterId, killedByPlayer));
    }

    boolean isPending(long characterId) {
        return indexOf(characterId) >= 0;
    }

    /** Có được đứng dậy tại chỗ không (đang nằm VÀ không do người chơi hạ). */
    boolean canReviveInPlace(long characterId) {
        int index = indexOf(characterId);
        return index >= 0 && !pending.get(index).killedByPlayer;
    }

    /** Bỏ khỏi danh sách (đã chọn, rời World). */
    void cancel(long characterId) {
        int index = indexOf(characterId);
        if (index >= 0) {
            pending.remove(index);
        }
    }

    /** Đếm ngược; ai hết giờ thì bỏ khỏi danh sách rồi gọi {@code expired}. */
    void tick(float deltaTime, LongConsumer expired) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending entry = pending.get(i);
            entry.secondsLeft -= deltaTime;
            if (entry.secondsLeft <= 0f) {
                pending.remove(i);
                expired.accept(entry.characterId);
            }
        }
    }

    /** Máu khi đứng dậy tại chỗ — ít nhất 1. */
    static int inPlaceHp(int maxHp) {
        return Math.max(1, maxHp * IN_PLACE_HP_PERCENT / PERCENT);
    }

    private int indexOf(long characterId) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).characterId == characterId) {
                return i;
            }
        }
        return -1;
    }
}
