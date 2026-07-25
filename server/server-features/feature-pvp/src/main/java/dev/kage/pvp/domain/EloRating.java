package dev.kage.pvp.domain;

/**
 * Công thức Elo cổ điển (K-factor cố định) — quyết định lát cắt 4 Phase 8: "MMR/ranking cơ bản",
 * KHÔNG season/decay (xem feature-pvp/CLAUDE.md). Thuần tính toán, không chạm Port/DB.
 */
public final class EloRating {

    /** Điểm khởi điểm cho nhân vật CHƯA từng đấu (chưa có dòng {@code pvp_rating} nào). */
    public static final int DEFAULT_RATING = 1000;

    // K=32 — giá trị chuẩn phổ biến (cờ vua FIDE dùng cho kỳ thủ mới), đủ cho MVP, chưa đo/tinh
    // chỉnh theo dữ liệu thật (Điều 30 — benchmark trước khi đổi).
    private static final int K_FACTOR = 32;

    private EloRating() {
    }

    /** Kết quả rating mới của CẢ 2 sau 1 trận — {@code loserRating} luôn là người THUA. */
    public record Result(int newWinnerRating, int newLoserRating) {
    }

    public static Result computeNewRatings(int winnerRating, int loserRating) {
        double expectedWinner = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
        int newWinnerRating = winnerRating + Math.round((float) (K_FACTOR * (1.0 - expectedWinner)));
        int newLoserRating = loserRating + Math.round((float) (K_FACTOR * (0.0 - (1.0 - expectedWinner))));
        return new Result(newWinnerRating, newLoserRating);
    }
}
