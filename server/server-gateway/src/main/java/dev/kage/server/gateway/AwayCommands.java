package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.protocol.SetAwayMessageCommand;
import dev.kage.protocol.SetAwayMessageOutcome;
import dev.kage.protocol.SetAwayMessageResponse;

/**
 * Tự trả lời khi vắng (ADR-178) — {@code /vang <lời nhắn>} treo một lời nhắn; ai nhắn riêng người đang vắng thì server
 * gửi lại lời nhắn đó cho người gửi (đánh dấu {@code auto_reply}). Trạng thái CHỈ trong bộ nhớ gateway: thoát game là
 * hết vắng, không ghi DB. Mỗi cặp (người vắng, người gửi) được trả lời tối đa một lần mỗi {@link #REPLY_INTERVAL} để
 * hai người cùng vắng không dội nhau và người gửi không bị lặp câu.
 */
final class AwayCommands {

    /** Độ dài tối đa (code point) của lời nhắn vắng. */
    static final int MAX_LENGTH = 60;

    /** Khoảng tối thiểu giữa hai lần tự trả lời cho cùng một người gửi. */
    static final Duration REPLY_INTERVAL = Duration.ofMinutes(5);

    /** Khoá một cặp (người vắng, người gửi). */
    private record Pair(long awayId, long senderId) {}

    private final Map<Long, String> messages = new ConcurrentHashMap<>();
    private final Map<Pair, Instant> lastReplies = new ConcurrentHashMap<>();
    private final Clock clock;

    AwayCommands(Clock clock) {
        this.clock = clock;
    }

    /** Virtual Thread (hay EventLoop — chỉ chạm bộ nhớ); {@code characterId} từ session. */
    SetAwayMessageResponse set(long characterId, SetAwayMessageCommand command) {
        String message = ChatMessage.singleLine(command.getMessage()).strip().replaceAll("\\s+", " ");
        if (message.codePointCount(0, message.length()) > MAX_LENGTH) {
            return refused(characterId, SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_TOO_LONG);
        }
        if (ProfanityFilter.containsBannedWord(message)) {
            return refused(characterId, SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_BANNED_WORD);
        }
        if (message.isEmpty()) {
            clear(characterId);
        } else {
            messages.put(characterId, message);
        }
        return SetAwayMessageResponse.newBuilder()
                .setOutcome(SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_OK)
                .setAway(!message.isEmpty())
                .setMessage(message)
                .build();
    }

    /**
     * Lời nhắn cần tự trả lời cho {@code senderId} vừa nhắn riêng {@code awayId}, nếu {@code awayId} đang vắng và cặp
     * này chưa được trả lời trong {@link #REPLY_INTERVAL}. Gọi SAU khi tin riêng đã được giao.
     */
    Optional<String> replyFor(long awayId, long senderId) {
        String message = messages.get(awayId);
        if (message == null || awayId == senderId) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Pair pair = new Pair(awayId, senderId);
        Instant last = lastReplies.get(pair);
        if (last != null && now.isBefore(last.plus(REPLY_INTERVAL))) {
            return Optional.empty();
        }
        lastReplies.put(pair, now);
        return Optional.of(message);
    }

    /** Thoát game hoặc tự tắt — bỏ lời nhắn và nhịp trả lời của người này. */
    void clear(long characterId) {
        messages.remove(characterId);
        lastReplies.keySet().removeIf(pair -> pair.awayId() == characterId);
    }

    private SetAwayMessageResponse refused(long characterId, SetAwayMessageOutcome outcome) {
        String current = messages.getOrDefault(characterId, "");
        return SetAwayMessageResponse.newBuilder()
                .setOutcome(outcome)
                .setAway(!current.isEmpty())
                .setMessage(current)
                .build();
    }
}
