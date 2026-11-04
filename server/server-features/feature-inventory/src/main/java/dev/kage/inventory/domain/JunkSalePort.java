package dev.kage.inventory.domain;

import java.time.Instant;
import java.util.List;

/**
 * Bán nhanh đồ rác (ADR-170) — bán cho cửa hàng một lần MỌI chồng thuộc loại {@code item_template.junk}, trừ chồng đã
 * khoá (ADR-159), đang mặc, đã cường hoá/khảm, hoặc đang chào giao dịch.
 */
public interface JunkSalePort {

    /** Một loại rác sẽ bán: tổng số cái và giá thu mua mỗi cái. */
    record Line(long itemTemplateId, int quantity, long priceEach) {}

    /** Kết quả bán: các dòng đã bán và tổng vàng; {@code goldFull} = vàng chạm trần, KHÔNG bán gì (rollback). */
    record Sold(List<Line> lines, long gold, boolean goldFull) {
        /** Bản sao bất biến. */
        public Sold {
            lines = List.copyOf(lines);
        }
    }

    /** Liệt kê những gì SẼ bán (chỉ đọc) — để người chơi xem trước khi bấm. */
    List<Line> preview(long characterId);

    /** Bán hết trong MỘT transaction (khoá túi, xoá chồng, cộng vàng, audit). Không có gì để bán ⇒ danh sách rỗng. */
    Sold sellAll(long characterId, Instant at);
}
