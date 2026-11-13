package dev.kage.character.domain;

import java.time.LocalDate;
import java.util.List;

/** Xếp hạng chuỗi đăng nhập (ADR-351), người giới thiệu (ADR-352) và tìm người chơi theo tên (ADR-353). */
public interface PlayerLookupPort {

    /** Một dòng xếp hạng: tên và giá trị. */
    record Ranked(String name, int value) {}

    /** Một người khớp: id, tên, cấp. */
    record Hit(long characterId, String name, int level) {}

    /** Chuỗi đăng nhập dài nhất trong các chuỗi còn sống (ngày cuối ≥ {@code since}), dài trước. */
    List<Ranked> topStreaks(LocalDate since, int limit);

    /** Người có nhiều người được giới thiệu đã nhận quà nhất. */
    List<Ranked> topReferrers(int limit);

    /** Nhân vật có tên chứa {@code fragment} (không phân biệt hoa thường), tên ngắn/khớp đầu trước. */
    List<Hit> search(String fragment, int limit);
}
