package dev.kage.quest.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bảng truy nã của một ngày (ADR-101 D1) — hàm thuần: chọn TẤT ĐỊNH {@link #BOARD_SIZE} truy nã từ ngày, nên mọi người
 * cùng thấy một bảng mà không cần lưu bảng.
 */
public final class BountyBoard {

    /** Số truy nã trên bảng mỗi ngày. */
    public static final int BOARD_SIZE = 3;

    /** Số lần hoàn thành tối đa mỗi ngày. */
    public static final int MAX_COMPLETIONS_PER_DAY = 5;

    private BountyBoard() {}

    /** Bảng của {@code day}: sắp mẫu theo mã, bắt đầu từ {@code epochDay mod n}, lấy liên tiếp (vòng lại). */
    public static List<BountyTemplate> of(LocalDate day, List<BountyTemplate> templates) {
        if (templates.isEmpty()) {
            return List.of();
        }
        List<BountyTemplate> sorted = new ArrayList<>(templates);
        sorted.sort(Comparator.comparing(BountyTemplate::code));
        int size = Math.min(BOARD_SIZE, sorted.size());
        int start = (int) Math.floorMod(day.toEpochDay(), (long) sorted.size());
        List<BountyTemplate> board = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            board.add(sorted.get((start + i) % sorted.size()));
        }
        return List.copyOf(board);
    }
}
