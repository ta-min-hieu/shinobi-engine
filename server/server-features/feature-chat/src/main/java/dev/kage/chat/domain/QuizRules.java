package dev.kage.chat.domain;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Luật hỏi đáp tông môn (ADR-122) — hàm thuần. Mỗi phiên {@value #QUESTIONS} câu, mỗi câu mở {@link #WINDOW} rồi nghỉ
 * tới hết {@link #PERIOD}; giờ bắt đầu phiên tính theo giờ Việt Nam. Đáp án so sau khi chuẩn hoá (bỏ dấu, chữ thường,
 * gộp khoảng trắng) để "Thanh Hư Tông" và "thanh hu tong" cùng đúng.
 */
public final class QuizRules {

    public static final int QUESTIONS = 10;
    public static final Duration WINDOW = Duration.ofSeconds(40);
    public static final Duration PERIOD = Duration.ofSeconds(50);
    public static final long REWARD_GOLD = 100L;
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern COMMA = Pattern.compile(",");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Câu thứ {@code index} của phiên {@code sessionKey} đang ở thời điểm đang xét.
     *
     * @param inWindow đang trong thời gian trả lời
     * @param windowEnd lúc hết giờ trả lời của câu này
     */
    public record Slot(String sessionKey, int index, boolean inWindow, Instant windowEnd) {}

    private QuizRules() {}

    /** Ô thời gian chứa {@code now} nếu đang trong một phiên bắt đầu ở một trong {@code starts} (giờ VN). */
    public static Optional<Slot> slotAt(Instant now, List<LocalTime> starts) {
        ZonedDateTime local = now.atZone(VIETNAM);
        LocalDate today = local.toLocalDate();
        long sessionMillis = PERIOD.toMillis() * QUESTIONS;
        for (LocalTime start : starts) {
            Instant begin = today.atTime(start).atZone(VIETNAM).toInstant();
            long elapsed = Duration.between(begin, now).toMillis();
            if (elapsed >= 0 && elapsed < sessionMillis) {
                int index = (int) (elapsed / PERIOD.toMillis());
                Instant windowEnd = begin.plus(PERIOD.multipliedBy(index)).plus(WINDOW);
                return Optional.of(new Slot(today + "T" + start, index, now.isBefore(windowEnd), windowEnd));
            }
        }
        return Optional.empty();
    }

    /** Chuẩn hoá một câu trả lời: bỏ dấu tiếng Việt (kể cả đ), chữ thường, chỉ giữ chữ/số, gộp khoảng trắng. */
    public static String normalize(String raw) {
        String stripped = MARKS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFD))
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return NOT_WORD.matcher(stripped.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .strip();
    }

    /** Đọc danh sách giờ "HH:mm,HH:mm" (biến môi trường {@code KAGE_QUIZ_TIMES}); rỗng/hỏng ⇒ {@code fallback}. */
    public static List<LocalTime> parseStarts(String raw, List<LocalTime> fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return COMMA.splitAsStream(raw)
                    .map(String::strip)
                    .map(LocalTime::parse)
                    .toList();
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
