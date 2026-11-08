package dev.kage.client.core.world;

/**
 * Luồng lô 41 (ADR-255…) — xem thông tin quái (ADR-255), lịch bang (ADR-256), dự tính cường hoá (ADR-257), xếp hạng
 * bạn bè (ADR-258)… MỘT thành phần của {@link Lot40Windows}.
 */
record Lot41Windows(
        MonsterInfoCoordinator monsterInfo,
        GuildEventCoordinator guildEvents,
        EnhancePreviewCoordinator enhancePreview,
        FriendRankingCoordinator friendRanking) {

    static Lot41Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot41Windows(
                new MonsterInfoCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new GuildEventCoordinator(sender, feeds.chat()),
                new EnhancePreviewCoordinator(sender, feeds.chat()),
                new FriendRankingCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(monsterInfo);
        panels.register(guildEvents);
        panels.register(enhancePreview);
        panels.register(friendRanking);
    }
}
