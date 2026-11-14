package dev.kage.character.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import dev.kage.character.domain.NewPlayersPort;

/** API tân thủ hôm nay (ADR-417): ngày theo giờ Việt Nam (cùng mốc reset ngày). Chạm DB — Virtual Thread. */
public final class NewPlayersFacade {

    /** Múi giờ "hôm nay". */
    public static final ZoneId DAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Số tên mới nhất trả về. */
    public static final int NEWEST = 5;

    /** Không có ai (composition test). */
    public static final NewPlayersFacade DISABLED = new NewPlayersFacade(new NewPlayersPort() {
        @Override
        public int countSince(Instant since) {
            return 0;
        }

        @Override
        public List<String> newestSince(Instant since, int limit) {
            return List.of();
        }
    });

    /** Số liệu một ngày. */
    public record Today(int count, List<String> newest) {
        /** Chép danh sách (bất biến). */
        public Today {
            newest = List.copyOf(newest);
        }
    }

    private final NewPlayersPort port;

    /** Dựng ở composition root. */
    public NewPlayersFacade(NewPlayersPort port) {
        this.port = port;
    }

    /** Tân thủ của ngày chứa {@code now}. */
    public Today today(Instant now) {
        Instant start = startOfDay(now);
        return new Today(port.countSince(start), port.newestSince(start, NEWEST));
    }

    /** 00:00 giờ Việt Nam của ngày chứa {@code now}. Tĩnh để test. */
    public static Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, DAY_ZONE).atStartOfDay(DAY_ZONE).toInstant();
    }
}
