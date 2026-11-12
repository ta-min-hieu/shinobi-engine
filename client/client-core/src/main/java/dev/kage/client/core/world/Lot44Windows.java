package dev.kage.client.core.world;

/**
 * Luồng lô 44 (ADR-279…) — tra cứu vật phẩm (ADR-279), lịch sử đấu (ADR-280), đếm online (ADR-281), FPS (ADR-283),
 * lịch sử tin riêng (ADR-284), tự nhận lời mời nhóm (ADR-286). Ẩn số sát thương người khác (ADR-285) là một dòng
 * cài đặt hiển thị. MỘT thành phần của {@link Lot43Windows}.
 */
record Lot44Windows(
        ItemLookupCoordinator itemLookup,
        DuelHistoryCoordinator duelHistory,
        OnlineCountCoordinator onlineCount,
        FpsCoordinator fps,
        WhisperHistoryCoordinator whispers,
        PartyAutoAcceptCoordinator autoParty,
        Lot45Windows lot45) {

    static Lot44Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot44Windows(
                new ItemLookupCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new DuelHistoryCoordinator(sender, feeds.chat()),
                new OnlineCountCoordinator(sender, feeds.chat()),
                new FpsCoordinator(feeds.chat()),
                new WhisperHistoryCoordinator(feeds.chat()),
                new PartyAutoAcceptCoordinator(sender, feeds.chat()),
                Lot45Windows.create(sender, feeds)); // lô 45 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(itemLookup);
        panels.register(duelHistory);
        panels.register(onlineCount);
        panels.register(fps);
        panels.register(whispers);
        panels.register(autoParty);
        lot45.registerInto(panels);
    }
}
