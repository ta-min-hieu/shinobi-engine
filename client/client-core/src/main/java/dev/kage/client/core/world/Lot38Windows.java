package dev.kage.client.core.world;

/**
 * Luồng lô 38 (ADR-231…) — huỷ vật phẩm (ADR-231), phân giải hàng loạt (ADR-232), lịch sử cường hoá (ADR-233), giá gợi
 * ý phường thị (ADR-234)… Là MỘT thành phần của {@link Lot37Windows} (chuỗi lồng nhau vì {@code
 * ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot38Windows(
        ItemDestroyCoordinator destroy,
        BulkDismantleCoordinator bulkDismantle,
        EnhanceHistoryCoordinator enhanceHistory,
        MarketPriceHintCoordinator priceHint) {

    static Lot38Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot38Windows(
                new ItemDestroyCoordinator(sender, feeds.chat(), System::nanoTime),
                new BulkDismantleCoordinator(sender, feeds.chat()),
                new EnhanceHistoryCoordinator(sender, feeds.chat()),
                new MarketPriceHintCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(destroy);
        panels.register(bulkDismantle);
        panels.register(enhanceHistory);
        panels.register(priceHint);
    }
}
