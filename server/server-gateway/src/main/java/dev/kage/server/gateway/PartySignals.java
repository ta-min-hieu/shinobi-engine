package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/**
 * Tín hiệu trong nhóm — kiểm tra sẵn sàng (ADR-263), đếm ngược (ADR-264) và nhịp gieo xúc xắc (ADR-265). Toàn bộ trong
 * RAM của gateway: mất khi khởi động lại = lượt đang mở mất, chấp nhận như mọi trạng thái RAM khác (xúc xắc chia đồ
 * ADR-150). Thông báo là dòng chat hệ thống song ngữ (kênh UNSPECIFIED), cùng khuôn {@link GuildEventReminder}.
 *
 * <p>Hẹn giờ qua {@link CompletableFuture#delayedExecutor} trên Virtual Thread — không chạm tick, không chạm DB.
 */
final class PartySignals {

    /** Thời hạn trả lời một lượt kiểm tra sẵn sàng. */
    static final int READY_CHECK_SECONDS = 30;

    static final int MIN_COUNTDOWN_SECONDS = 3;
    static final int MAX_COUNTDOWN_SECONDS = 10;

    static final int DEFAULT_DICE_MAX = 100;
    static final int MIN_DICE_MAX = 2;
    static final int MAX_DICE_MAX = 1000;

    /** Khoảng cách tối thiểu giữa hai lần gieo của một nhân vật. */
    static final long DICE_COOLDOWN_MILLIS = 3_000L;

    /** Quá số mục này thì dọn các nhịp đã hết hạn (không giữ mãi mọi người từng gieo). */
    private static final int DICE_MEMORY = 4096;

    private static final Executor VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    /** Gửi một dòng chat hệ thống tới một nhân vật (nếu đang online). */
    @FunctionalInterface
    interface Outbox {
        void send(long characterId, byte[] chatEvent);
    }

    /** Người nhận của một tín hiệu: thành viên online lúc mở, cách tra tên, nơi gửi dòng chat. */
    record Audience(List<Long> members, LongFunction<String> names, Outbox outbox) {

        Audience {
            members = List.copyOf(members);
        }
    }

    /** Câu trả lời của một thành viên. */
    enum Answer {
        PENDING,
        READY,
        NOT_READY
    }

    /** Kết quả mở lượt kiểm tra / đếm ngược. */
    enum StartOutcome {
        OK,
        ALREADY_ACTIVE
    }

    /** Một lượt kiểm tra đang mở; {@code answers} giữ thứ tự thành viên lúc mở. */
    private static final class ReadyCheck {
        final Map<Long, Answer> answers = new LinkedHashMap<>();
        final LongFunction<String> names;
        final Outbox outbox;

        ReadyCheck(List<Long> members, LongFunction<String> names, Outbox outbox) {
            for (long member : members) {
                answers.put(member, Answer.PENDING);
            }
            this.names = names;
            this.outbox = outbox;
        }
    }

    private final Map<Long, ReadyCheck> readyChecks = new ConcurrentHashMap<>();
    private final Set<Long> countdowns = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> lastDiceAt = new ConcurrentHashMap<>();
    private final SplittableRandom random = new SplittableRandom();
    private final LongSupplier clockMillis;
    private final boolean timers;

    PartySignals() {
        this(System::currentTimeMillis, true);
    }

    /** Cho test: đồng hồ riêng; {@code timers = false} ⇒ không hẹn giờ (test tự gọi {@link #resolveReadyCheck}). */
    PartySignals(LongSupplier clockMillis, boolean timers) {
        this.clockMillis = clockMillis;
        this.timers = timers;
    }

    // ===== ADR-263 — kiểm tra sẵn sàng =====

    /**
     * Mở một lượt kiểm tra cho {@code partyId}: trưởng nhóm tự tính là sẵn sàng, mọi thành viên nhận lời mời, hết hạn
     * sau {@value #READY_CHECK_SECONDS} giây thì chốt (hoặc sớm hơn khi ai cũng đã trả lời).
     */
    StartOutcome startReadyCheck(long partyId, long leaderId, Audience audience) {
        List<Long> members = audience.members();
        LongFunction<String> names = audience.names();
        Outbox outbox = audience.outbox();
        ReadyCheck check = new ReadyCheck(members, names, outbox);
        if (readyChecks.putIfAbsent(partyId, check) != null) {
            return StartOutcome.ALREADY_ACTIVE;
        }
        synchronized (check) {
            check.answers.put(leaderId, Answer.READY);
        }
        byte[] invite = line("Kiểm tra sẵn sàng: " + names.apply(leaderId) + " hỏi cả nhóm — gõ /sansang co hoặc "
                + "/sansang khong trong " + READY_CHECK_SECONDS + " giây. / Ready check from " + names.apply(leaderId)
                + " — type /readycheck yes or /readycheck no within " + READY_CHECK_SECONDS + " seconds.");
        for (long member : members) {
            outbox.send(member, invite);
        }
        if (timers) {
            CompletableFuture.delayedExecutor(READY_CHECK_SECONDS, TimeUnit.SECONDS, VIRTUAL)
                    .execute(() -> resolveReadyCheck(partyId, check));
        }
        return StartOutcome.OK;
    }

    /**
     * Ghi câu trả lời của {@code characterId}. Sai khi nhóm không có lượt nào, hoặc người này không có mặt lúc mở. Ai
     * cũng đã trả lời ⇒ chốt ngay.
     */
    boolean respondReadyCheck(long partyId, long characterId, boolean ready) {
        ReadyCheck check = readyChecks.get(partyId);
        if (check == null) {
            return false;
        }
        boolean complete;
        synchronized (check) {
            if (!check.answers.containsKey(characterId)) {
                return false;
            }
            check.answers.put(characterId, ready ? Answer.READY : Answer.NOT_READY);
            complete = !check.answers.containsValue(Answer.PENDING);
        }
        if (complete) {
            resolveReadyCheck(partyId, check);
        }
        return true;
    }

    /** Nhóm có lượt kiểm tra đang mở không. */
    boolean hasReadyCheck(long partyId) {
        return readyChecks.containsKey(partyId);
    }

    /** Chốt và báo kết quả — chạy đúng MỘT lần dù hết giờ và "đủ người" tới cùng lúc. */
    void resolveReadyCheck(long partyId, Object handle) {
        if (!(handle instanceof ReadyCheck check) || !readyChecks.remove(partyId, check)) {
            return;
        }
        Map<Long, Answer> answers;
        synchronized (check) {
            answers = new LinkedHashMap<>(check.answers);
        }
        byte[] result = line(summary(answers, check.names));
        for (long member : answers.keySet()) {
            check.outbox.send(member, result);
        }
    }

    /** Lượt đang mở của nhóm (cho test chốt bằng tay). */
    Optional<Object> readyCheckHandle(long partyId) {
        return Optional.ofNullable(readyChecks.get(partyId));
    }

    /** Dòng kết quả song ngữ. Tĩnh để test. */
    static String summary(Map<Long, Answer> answers, LongFunction<String> names) {
        List<String> ready = new ArrayList<>();
        List<String> notReady = new ArrayList<>();
        List<String> silent = new ArrayList<>();
        answers.forEach((id, answer) -> bucket(answer, ready, notReady, silent).add(names.apply(id)));
        if (notReady.isEmpty() && silent.isEmpty()) {
            return "Cả nhóm đã sẵn sàng! / Everyone is ready!";
        }
        return "Kết quả kiểm tra — sẵn sàng: " + list(ready) + "; chưa: " + list(notReady) + "; không trả lời: "
                + list(silent) + ". / Ready check — ready: " + list(ready) + "; not ready: " + list(notReady)
                + "; no answer: " + list(silent) + ".";
    }

    private static List<String> bucket(Answer answer, List<String> ready, List<String> notReady, List<String> silent) {
        return switch (answer) {
            case READY -> ready;
            case NOT_READY -> notReady;
            case PENDING -> silent;
        };
    }

    private static String list(List<String> names) {
        return names.isEmpty() ? "—" : String.join(", ", names);
    }

    // ===== ADR-264 — đếm ngược =====

    /** Mở đếm ngược {@code seconds} giây cho {@code partyId}; một nhóm chỉ đếm một lượt một lúc. */
    StartOutcome startCountdown(long partyId, int seconds, long leaderId, Audience audience) {
        List<Long> members = audience.members();
        Outbox outbox = audience.outbox();
        String leaderName = audience.names().apply(leaderId);
        if (!countdowns.add(partyId)) {
            return StartOutcome.ALREADY_ACTIVE;
        }
        byte[] opening = line(leaderName + " bắt đầu đếm ngược " + seconds + " giây. / " + leaderName + " started a "
                + seconds + "-second countdown.");
        for (long member : members) {
            outbox.send(member, opening);
        }
        if (!timers) {
            countdowns.remove(partyId);
            return StartOutcome.OK;
        }
        for (int left = seconds; left >= 0; left--) {
            int value = left;
            CompletableFuture.delayedExecutor(seconds - left + 1L, TimeUnit.SECONDS, VIRTUAL)
                    .execute(() -> countdownTick(partyId, value, members, outbox));
        }
        return StartOutcome.OK;
    }

    private void countdownTick(long partyId, int left, List<Long> members, Outbox outbox) {
        byte[] tick = line(countdownText(left));
        for (long member : members) {
            outbox.send(member, tick);
        }
        if (left == 0) {
            countdowns.remove(partyId);
        }
    }

    /** Chữ của một nhịp đếm. Tĩnh để test. */
    static String countdownText(int left) {
        return left == 0 ? "Bắt đầu! / Go!" : left + "…";
    }

    /** Số giây hợp lệ không. Tĩnh để test. */
    static boolean validCountdown(int seconds) {
        return seconds >= MIN_COUNTDOWN_SECONDS && seconds <= MAX_COUNTDOWN_SECONDS;
    }

    // ===== ADR-265 — gieo xúc xắc =====

    /** Trần thật của lần gieo ({@code 0} ⇒ 100), hoặc rỗng khi ngoài 2–1000. Tĩnh để test. */
    static Optional<Integer> diceMax(int requested) {
        int max = requested == 0 ? DEFAULT_DICE_MAX : requested;
        return max >= MIN_DICE_MAX && max <= MAX_DICE_MAX ? Optional.of(max) : Optional.empty();
    }

    /** Gieo 1..max cho {@code characterId}; rỗng khi gieo lại trong {@value #DICE_COOLDOWN_MILLIS} ms. */
    Optional<Integer> rollDice(long characterId, int max) {
        long now = clockMillis.getAsLong();
        if (lastDiceAt.size() > DICE_MEMORY) {
            lastDiceAt.values().removeIf(at -> now - at >= DICE_COOLDOWN_MILLIS);
        }
        boolean[] allowed = {false};
        lastDiceAt.compute(characterId, (id, last) -> {
            if (last != null && now - last < DICE_COOLDOWN_MILLIS) {
                return last;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            return Optional.empty();
        }
        synchronized (random) {
            return Optional.of(random.nextInt(1, max + 1));
        }
    }

    /** Dòng chat kết quả gieo. Tĩnh để test. */
    static String diceText(String name, int value, int max) {
        return name + " gieo xúc xắc: " + value + " (1–" + max + "). / " + name + " rolled " + value + " (1–" + max
                + ").";
    }

    /** Quên nhịp gieo của người đã rời game (gọi lúc dọn phiên). */
    void forget(long characterId) {
        lastDiceAt.remove(characterId);
    }

    /** Dòng chat hệ thống. */
    static byte[] line(String body) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build()
                .toByteArray();
    }
}
