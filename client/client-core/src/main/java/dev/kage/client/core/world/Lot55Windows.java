package dev.kage.client.core.world;

/**
 * Luồng lô 55 (ADR-367…) — kênh lãnh đạo bang (ADR-367), nhớ kênh chat / giờ máy / đếm quái (ADR-371/372/374). Cấp
 * tối thiểu chat thế giới (ADR-368), chống spam tin riêng (ADR-369), báo đổi lời nhắn (ADR-370) ở server; màu đồ theo
 * cường hoá (ADR-373) trong {@code InventoryPanel}. MỘT thành phần của {@link Lot54Windows}.
 */
record Lot55Windows(OfficerChatCoordinator officerChat, ChatHudExtrasCoordinator hudExtras, Lot56Windows lot56) {

    static Lot55Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot55Windows(
                new OfficerChatCoordinator(sender, feeds.chat()),
                new ChatHudExtrasCoordinator(feeds.chat()),
                Lot56Windows.create(sender, feeds)); // lô 56 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(officerChat);
        panels.register(hudExtras);
        lot56.registerInto(panels);
    }
}
