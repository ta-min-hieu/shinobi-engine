package dev.kage.shop.domain;

/**
 * Một dòng trong danh mục cửa hàng — giá tĩnh, đọc từ bảng {@code shop_item} (DATABASE.md §2.8).
 * {@code buyPrice}/{@code sellPrice} độc lập nhau (KHÔNG phải công thức % cố định) — chủ dự án
 * chọn tự cân bằng từng item thay vì áp 1 tỉ lệ chung (giống tinh thần {@code EnhancementTable}).
 */
public record ShopListing(long itemTemplateId, long buyPrice, long sellPrice) {

    public ShopListing {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (buyPrice < 0 || sellPrice < 0) {
            throw new IllegalArgumentException("Giá không được âm: buyPrice=" + buyPrice + ", sellPrice=" + sellPrice);
        }
    }

    /** Tổng tiền mua {@code quantity} món — bão hoà, xem {@link #saturatingTotal}. */
    public long totalBuyCost(int quantity) {
        return saturatingTotal(buyPrice, quantity);
    }

    /** Tổng tiền bán {@code quantity} món — bão hoà, xem {@link #saturatingTotal}. */
    public long totalSellCredit(int quantity) {
        return saturatingTotal(sellPrice, quantity);
    }

    /**
     * {@code price × quantity}, tràn {@code long} thì kẹp ở {@link Long#MAX_VALUE} (chủ dự án chốt
     * 2026-10-14, README dòng 25(iv)) — phép nhân thường tràn thành số ÂM, và trừ một số âm là cộng vàng.
     * Kẹp thay vì ném: tổng lớn hơn mọi số dư có thể có thì mua rơi đúng vào "không đủ vàng", bán rơi
     * đúng vào trần vàng ({@code GoldLedger.MAX_GOLD}) — hai câu trả lời đã có sẵn, không thêm lỗi 500.
     */
    private static long saturatingTotal(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
