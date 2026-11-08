package dev.kage.guild.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.GuildPollPort;

/**
 * API bình chọn trong bang (ADR-248): câu hỏi ≤ {@value #MAX_QUESTION} ký tự, {@value #MIN_OPTIONS}–{@value
 * #MAX_OPTIONS} lựa chọn mỗi cái ≤ {@value #MAX_OPTION} ký tự, mở 24 giờ. Kiểm từ cấm là việc của gateway (bộ lọc
 * chat ở đó). Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class GuildPollFacade {

    public static final int MAX_QUESTION = 80;
    public static final int MAX_OPTION = 32;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;

    /** Thời gian mở một cuộc. */
    public static final Duration DURATION = Duration.ofHours(24);

    /** Không có bang nào (composition test). */
    public static final GuildPollFacade DISABLED = new GuildPollFacade(new GuildPollPort() {
        @Override
        public CreateOutcome create(Draft draft) {
            return CreateOutcome.NOT_IN_GUILD;
        }

        @Override
        public VoteOutcome vote(long characterId, int option, Instant now) {
            return VoteOutcome.NOT_IN_GUILD;
        }

        @Override
        public Optional<Poll> latest(long characterId) {
            return Optional.empty();
        }
    });

    private final GuildPollPort port;

    /** Dựng ở composition root. */
    public GuildPollFacade(GuildPollPort port) {
        this.port = port;
    }

    /** Câu hỏi và lựa chọn hợp lệ (đã cắt khoảng trắng hai đầu, không rỗng, không quá dài). Tĩnh để test. */
    public static boolean isValid(String question, List<String> options) {
        if (question.isBlank() || question.strip().length() > MAX_QUESTION) {
            return false;
        }
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            return false;
        }
        return options.stream()
                .allMatch(option -> !option.isBlank() && option.strip().length() <= MAX_OPTION);
    }

    /** Tạo cuộc mới lúc {@code now} (mở {@link #DURATION}). */
    public GuildPollPort.CreateOutcome create(long characterId, String question, List<String> options, Instant now) {
        if (!isValid(question, options)) {
            return GuildPollPort.CreateOutcome.INVALID;
        }
        List<String> cleaned = options.stream().map(String::strip).toList();
        return port.create(new GuildPollPort.Draft(characterId, question.strip(), cleaned, now.plus(DURATION), now));
    }

    /** Bỏ / đổi phiếu. */
    public GuildPollPort.VoteOutcome vote(long characterId, int option, Instant now) {
        return port.vote(characterId, option, now);
    }

    /** Cuộc gần nhất của bang người này. */
    public Optional<GuildPollPort.Poll> latest(long characterId) {
        return port.latest(characterId);
    }
}
