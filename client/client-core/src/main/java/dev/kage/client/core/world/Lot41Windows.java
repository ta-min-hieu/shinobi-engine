package dev.kage.client.core.world;

/**
 * Luồng lô 41 (ADR-255…) — xem thông tin quái (ADR-255), lịch bang (ADR-256), dự tính cường hoá (ADR-257), xếp hạng bạn
 * bè (ADR-258), hẹn giờ nhắc (ADR-259), đồng hồ server (ADR-260), sổ ghi chép (ADR-261). MỘT thành phần của {@link
 * Lot40Windows}.
 */
record Lot41Windows(
        MonsterInfoCoordinator monsterInfo,
        GuildEventCoordinator guildEvents,
        EnhancePreviewCoordinator enhancePreview,
        FriendRankingCoordinator friendRanking,
        ReminderTimerCoordinator reminders,
        ServerClockCoordinator serverClock,
        NotebookCoordinator notebook) {

    static Lot41Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot41Windows(
                new MonsterInfoCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new GuildEventCoordinator(sender, feeds.chat()),
                new EnhancePreviewCoordinator(sender, feeds.chat()),
                new FriendRankingCoordinator(sender, feeds.chat()),
                new ReminderTimerCoordinator(feeds.chat()),
                new ServerClockCoordinator(sender, feeds.chat(), System::currentTimeMillis),
                new NotebookCoordinator(feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(monsterInfo);
        panels.register(guildEvents);
        panels.register(enhancePreview);
        panels.register(friendRanking);
        panels.register(reminders);
        panels.register(serverClock);
        panels.register(notebook);
    }
}
