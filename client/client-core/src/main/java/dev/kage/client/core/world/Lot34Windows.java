package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 34 (ADR-199…) — đếm ngược làm mới (ADR-200), mua lại đồ đã bán (ADR-202)… Là MỘT thành phần của {@link
 * Lot33Windows} (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot34Windows(ResetTimersCoordinator resetTimers, BuybackCoordinator buyback) {

    @SuppressWarnings("unused") // skin dành cho các cửa sổ lô 34 tiếp theo
    static Lot34Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot34Windows(
                new ResetTimersCoordinator(sender, feeds.chat()),
                new BuybackCoordinator(sender, feeds.chat(), feeds.itemCatalog()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(resetTimers);
        panels.register(buyback);
    }
}
