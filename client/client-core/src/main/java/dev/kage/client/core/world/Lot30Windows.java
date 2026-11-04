package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 30 — nhận hết thư (ADR-167), tháo Linh Ngọc (ADR-168), sổ vàng (ADR-169), bán rác
 * (ADR-170). Gom thành MỘT thành phần của
 * {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot30Windows(
        MailBulkCoordinator mailBulk,
        GemRemovalCoordinator gemRemoval,
        GoldHistoryCoordinator goldHistory,
        JunkSaleCoordinator junkSale) {

    static Lot30Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot30Windows(
                new MailBulkCoordinator(sender, feeds.chat()),
                new GemRemovalCoordinator(sender, feeds),
                GoldHistoryCoordinator.create(skin, sender, feeds),
                JunkSaleCoordinator.create(skin, sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(mailBulk);
        panels.register(gemRemoval);
        panels.register(goldHistory.panel(), goldHistory);
        panels.register(junkSale.panel(), junkSale);
    }
}
