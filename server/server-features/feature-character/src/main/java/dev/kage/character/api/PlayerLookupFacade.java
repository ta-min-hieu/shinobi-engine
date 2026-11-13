package dev.kage.character.api;

import java.time.LocalDate;
import java.util.List;

import dev.kage.character.domain.PlayerLookupPort;

/**
 * API xếp hạng chuỗi đăng nhập (ADR-351), xếp hạng người giới thiệu (ADR-352) và tìm người chơi theo tên (ADR-353).
 * Chỉ đọc. Chạm DB — Virtual Thread.
 */
public final class PlayerLookupFacade {

    /** Số dòng mỗi bảng / mỗi lần tìm. */
    public static final int SHOWN = 10;

    /** Chuỗi tìm ngắn nhất. */
    public static final int MIN_QUERY = 2;

    /** Chuỗi tìm dài nhất (dài hơn tên nhân vật là vô nghĩa). */
    public static final int MAX_QUERY = 16;

    /** Không có dữ liệu (composition test). */
    public static final PlayerLookupFacade DISABLED = new PlayerLookupFacade(new PlayerLookupPort() {
        @Override
        public List<Ranked> topStreaks(LocalDate since, int limit) {
            return List.of();
        }

        @Override
        public List<Ranked> topReferrers(int limit) {
            return List.of();
        }

        @Override
        public List<Hit> search(String fragment, int limit) {
            return List.of();
        }
    });

    private final PlayerLookupPort port;

    /** Dựng ở composition root. */
    public PlayerLookupFacade(PlayerLookupPort port) {
        this.port = port;
    }

    /** Top chuỗi còn sống tính tới {@code today} (vào hôm nay hoặc hôm qua). */
    public List<PlayerLookupPort.Ranked> topStreaks(LocalDate today) {
        return port.topStreaks(today.minusDays(1), SHOWN);
    }

    /** Top người giới thiệu. */
    public List<PlayerLookupPort.Ranked> topReferrers() {
        return port.topReferrers(SHOWN);
    }

    /** Tìm theo tên; chuỗi không hợp lệ ⇒ rỗng. */
    public List<PlayerLookupPort.Hit> search(String query) {
        String text = query.strip();
        return validQuery(text) ? port.search(text, SHOWN) : List.of();
    }

    /** {@value #MIN_QUERY}–{@value #MAX_QUERY} ký tự, không ký tự điều khiển. Tĩnh để test. */
    public static boolean validQuery(String text) {
        return text.length() >= MIN_QUERY
                && text.length() <= MAX_QUERY
                && text.chars().noneMatch(Character::isISOControl);
    }
}
