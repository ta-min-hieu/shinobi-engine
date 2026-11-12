package dev.kage.client.core.world;

/**
 * Luồng lô 45 (ADR-287…) — bảng cống hiến bang (ADR-287), lịch sử mua cửa hàng NPC (ADR-288), thư đã gửi (ADR-290),
 * bố cục khung chat: ghim tin / thu gọn khi rảnh / cỡ chữ (ADR-291/292/294), đếm combo (ADR-293). Quà mốc gắn bó
 * (ADR-289) chỉ ở server (tới bằng thư). MỘT thành phần của {@link Lot44Windows}.
 */
record Lot45Windows(
        GuildContributionCoordinator contributions,
        PurchaseHistoryCoordinator purchases,
        SentMailCoordinator sentMail,
        ChatLayoutCoordinator chatLayout,
        ComboCoordinator combo) {

    static Lot45Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot45Windows(
                new GuildContributionCoordinator(sender, feeds.chat()),
                new PurchaseHistoryCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new SentMailCoordinator(sender, feeds.chat()),
                new ChatLayoutCoordinator(feeds.chat()),
                new ComboCoordinator(feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(contributions);
        panels.register(purchases);
        panels.register(sentMail);
        panels.register(chatLayout);
        panels.register(combo);
    }
}
