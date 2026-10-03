package dev.kage.pvp.domain;

import java.util.Optional;

/** Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresPvpRatingAdapter}) ở server-database. */
public interface PvpRatingPort {

    Optional<PvpRating> findByCharacterId(long characterId);

    /**
     * Đọc rating HIỆN TẠI của cả 2 người, đưa cho {@code computer} tính rating mới, rồi ghi CẢ 2
     * xuống — toàn bộ đọc-tính-ghi phải NGUYÊN TỬ (1 transaction, khoá row trước khi đọc) để 2 trận
     * của CÙNG 1 nhân vật chốt gần như đồng thời không đọc trúng rating CŨ rồi ghi đè lên kết quả
     * đã ghi trước đó. Trận đầu tiên của 1 hoặc cả 2 người (chưa có dòng {@code pvp_rating}) coi
     * rating hiện tại là {@link EloRating#DEFAULT_RATING}.
     *
     * <p>⚠️ Race đã có THẬT trước khi thêm method này (2026-09-19): {@code RecordDuelResultUseCase}
     * từng gọi {@link #findByCharacterId} 2 lần (đọc rời, không khoá) rồi mới ghi — nhân vật A thắng
     * trận 1, được thả về world chính TRƯỚC KHI rating trận 1 ghi xong, lập tức thắng tiếp trận 2
     * (settle trên Virtual Thread khác) → cả 2 lần đọc cùng thấy rating CŨ của A, tính 2 kết quả
     * khác nhau, ai ghi SAU thắng — mất nguyên 1 trận Elo dù {@code wins}/{@code losses} vẫn đúng
     * (2 field đó cộng dồn, không ghi đè). Khoá {@code instanceByCharacterId} ở
     * {@code ArenaInstanceRegistry} KHÔNG đóng được lỗ này (nó chỉ chặn thách đấu MỚI, không chặn
     * ghi rating; xem javadoc {@code ArenaInstanceRegistry.settleDuel}) — phải khoá ở đúng chỗ dữ
     * liệu bị đọc/ghi, tức tầng persist này.
     *
     * <p>{@code wins}/{@code losses} cộng dồn +1 ở đúng phía, {@code rating} ghi đè bằng giá trị
     * MỚI vừa tính (không cộng dồn).
     */
    EloRating.Result recordDuelResult(long winnerCharacterId, long loserCharacterId, EloComputer computer);

    /** Hàm THUẦN tính rating mới từ rating hiện tại — chạy TRONG transaction đã khoá row của Port. */
    @FunctionalInterface
    interface EloComputer {
        EloRating.Result compute(int winnerRating, int loserRating);
    }
}
