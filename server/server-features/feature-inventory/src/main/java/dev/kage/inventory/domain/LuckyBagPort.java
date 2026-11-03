package dev.kage.inventory.domain;

import java.util.List;
import java.util.function.ToIntFunction;

/** Túi phúc (ADR-163) — mở một túi ra MỘT phần quà rút theo trọng số; bảng quà công khai. */
public interface LuckyBagPort {

    /** Một phần quà: vàng ({@code itemTemplateId == 0}) hoặc một chồng đồ. */
    record Reward(long gold, long itemTemplateId, int quantity, int weight) {}

    /** Một loại túi phúc: người hỏi đang có bao nhiêu túi, và bảng quà. */
    record Bag(long bagItemTemplateId, int owned, List<Reward> rewards) {
        /** Bản sao bất biến. */
        public Bag {
            rewards = List.copyOf(rewards);
        }
    }

    /** Kết quả mở túi. */
    enum Outcome {
        OK,
        /** Vật phẩm này không phải túi phúc. */
        NOT_A_BAG,
        /** Không còn túi nào trong túi đồ. */
        NO_BAG,
        BAG_FULL,
        /** Vàng chạm trần — không mở. */
        GOLD_FULL
    }

    /** Kết quả + phần quà vừa rút (rỗng nghĩa khi không OK: vàng 0, đồ 0). */
    record Opened(Outcome outcome, Reward reward) {

        /** Từ chối — không đổi gì. */
        public static Opened refused(Outcome outcome) {
            return new Opened(outcome, new Reward(0L, 0L, 0, 0));
        }
    }

    /** Mọi loại túi phúc kèm bảng quà, theo thứ tự hiển thị. */
    List<Bag> bags(long characterId);

    /**
     * Mở MỘT túi loại {@code bagItemTemplateId} — một transaction: khoá túi đồ, trừ một túi, rút quà bằng {@code pick}
     * (chỉ số trong mảng trọng số), cấp quà, audit.
     */
    Opened open(long characterId, long bagItemTemplateId, ToIntFunction<int[]> pick);
}
