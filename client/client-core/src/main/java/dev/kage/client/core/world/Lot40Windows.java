package dev.kage.client.core.world;

/**
 * Luồng lô 40 (ADR-247…) — bình chọn trong bang (ADR-248), lịch sử nhặt đồ (ADR-253)… MỘT thành phần của {@link
 * Lot39Windows}.
 */
record Lot40Windows(
        GuildPollCoordinator guildPoll,
        LootHistoryCoordinator lootHistory,
        QuickReplyCoordinator quickReply,
        KeywordFilterCoordinator keywordFilter,
        LocationBookmarkCoordinator bookmarks,
        PartyDamageMeterCoordinator damageMeter,
        BagSearchCoordinator bagSearch,
        Lot41Windows lot41) {

    static Lot40Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot40Windows(
                new GuildPollCoordinator(sender, feeds.chat()),
                new LootHistoryCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new QuickReplyCoordinator(sender, feeds.chat()),
                new KeywordFilterCoordinator(feeds.chat()),
                new LocationBookmarkCoordinator(feeds.chat()),
                new PartyDamageMeterCoordinator(feeds.chat(), System::currentTimeMillis),
                new BagSearchCoordinator(feeds.chat()),
                Lot41Windows.create(sender, feeds)); // lô 41 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildPoll);
        panels.register(lootHistory);
        panels.register(quickReply);
        panels.register(keywordFilter);
        panels.register(bookmarks);
        panels.register(damageMeter);
        panels.register(bagSearch);
        lot41.registerInto(panels);
    }
}
