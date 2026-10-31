package dev.kage.chat.api;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import dev.kage.chat.domain.QuizPort;
import dev.kage.chat.domain.QuizQuestion;
import dev.kage.chat.domain.QuizRules;

/**
 * Hỏi đáp tông môn (ADR-122) — máy trạng thái trong RAM, không I/O: phía gọi đưa đồng hồ vào ({@link #tick}, mỗi vài
 * giây) và câu chat kênh thế giới vào ({@link #onWorldChat}), nhận lại các tín hiệu cần phát. Thread-safe (mọi hàm
 * {@code synchronized} — hai câu đúng cùng lúc chỉ MỘT người thắng). Mỗi phiên rút câu không lặp từ ngân hàng.
 */
public final class SectQuiz {

    /** Tín hiệu cần phát cho mọi người online. */
    public sealed interface Signal {

        /** Mở câu hỏi. */
        record Ask(int index, int total, String questionKey, Instant endsAt) implements Signal {}

        /**
         * Chốt câu hỏi: có người đúng trước tiên, hoặc hết giờ.
         *
         * @param winner rỗng nếu hết giờ không ai đúng
         */
        record Reveal(int index, String answerKey, Optional<Winner> winner) implements Signal {}
    }

    /**
     * Người trả lời đúng trước tiên.
     *
     * @param rewardKey khoá chống trùng thư thưởng — tất định theo phiên + câu
     */
    public record Winner(long characterId, String name, long rewardGold, String rewardKey) {}

    /** Không có ngân hàng câu hỏi (test). */
    public static final SectQuiz DISABLED = new SectQuiz(List::of, List.of(), 0L);

    private final QuizPort port;
    private final List<LocalTime> starts;
    private final SplittableRandom random;

    private String sessionKey = "";
    private int index = -1;
    private final Deque<QuizQuestion> pool = new ArrayDeque<>();
    private Optional<QuizQuestion> open = Optional.empty();

    /** Dựng ở composition root; {@code starts} là giờ bắt đầu phiên theo giờ Việt Nam, {@code seed} gieo thứ tự câu. */
    public SectQuiz(QuizPort port, List<LocalTime> starts, long seed) {
        this.port = port;
        this.starts = List.copyOf(starts);
        this.random = new SplittableRandom(seed);
    }

    /** Nhịp đồng hồ — đóng câu hết giờ, mở câu mới tới giờ. */
    public synchronized List<Signal> tick(Instant now) {
        List<Signal> signals = new ArrayList<>();
        Optional<QuizRules.Slot> slot = QuizRules.slotAt(now, starts);
        boolean sameOpenSlot = slot.isPresent()
                && slot.get().inWindow()
                && slot.get().sessionKey().equals(sessionKey)
                && slot.get().index() == index;
        if (open.isPresent() && !sameOpenSlot) {
            signals.add(new Signal.Reveal(index, open.get().answerKey(), Optional.empty()));
            open = Optional.empty();
        }
        if (slot.isEmpty() || !slot.get().inWindow()) {
            return signals;
        }
        QuizRules.Slot current = slot.get();
        if (current.sessionKey().equals(sessionKey) && current.index() == index) {
            return signals; // câu này đã mở (và có thể đã có người đúng)
        }
        if (!current.sessionKey().equals(sessionKey)) {
            sessionKey = current.sessionKey();
            refill();
        }
        index = current.index();
        QuizQuestion next = pool.pollFirst();
        if (next != null) {
            open = Optional.of(next);
            signals.add(new Signal.Ask(index, QuizRules.QUESTIONS, next.questionKey(), current.windowEnd()));
        }
        return signals;
    }

    /** Một câu chat kênh thế giới: đúng và là người đầu tiên ⇒ chốt câu với người này. */
    public synchronized Optional<Signal.Reveal> onWorldChat(long characterId, String name, String body) {
        if (open.isEmpty() || !open.get().matches(body)) {
            return Optional.empty();
        }
        Signal.Reveal reveal = new Signal.Reveal(
                index,
                open.get().answerKey(),
                Optional.of(new Winner(characterId, name, QuizRules.REWARD_GOLD, "quiz:" + sessionKey + ":" + index)));
        open = Optional.empty();
        return Optional.of(reveal);
    }

    private void refill() {
        List<QuizQuestion> bank = new ArrayList<>(port.questions());
        Collections.shuffle(bank, random);
        pool.clear();
        pool.addAll(bank);
    }
}
