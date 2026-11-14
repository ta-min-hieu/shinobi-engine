package dev.kage.client.core.world;

/**
 * Luồng lô 64 (ADR-439…) — bốn bảng xếp hạng (ADR-439…442), {@code /xoachat} / ẩn chat khi giao chiến / {@code
 * /tatieng} (ADR-443/444/446). Dòng HUD ô túi (ADR-445) nối thẳng ở {@code ExpansionWindows}. MỘT thành phần của
 * {@link Lot63Windows}.
 */
record Lot64Windows(CollectionRankingCoordinator rankings, ChatQuietCoordinator quiet) {

    static Lot64Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot64Windows(
                new CollectionRankingCoordinator(sender, feeds.chat()), new ChatQuietCoordinator(feeds.chat()));
    }

    void registerInto(PanelHub panels) {
        panels.register(rankings);
        panels.register(quiet);
    }
}
