package dev.kage.client.core.world;

/**
 * Luồng lô 39 (ADR-239…) — buff bang (ADR-239), kỷ lục cá nhân (ADR-240), xem trước thưởng nhiệm vụ (ADR-242), cảnh
 * báo máu thấp (ADR-243), chế độ chụp ảnh (ADR-244), cảnh báo sắp đầy túi (ADR-246). Là MỘT thành phần của {@link
 * Lot38Windows}.
 */
record Lot39Windows(
        GuildBuffCoordinator guildBuff,
        PersonalRecordsCoordinator records,
        QuestRewardPreviewCoordinator questPreview,
        LowHealthWarning lowHealth,
        PhotoModeCoordinator photoMode,
        BagSpaceWarningCoordinator bagSpace,
        Lot40Windows lot40) {

    static Lot39Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot39Windows(
                new GuildBuffCoordinator(sender, feeds.chat()),
                new PersonalRecordsCoordinator(sender, feeds.chat()),
                new QuestRewardPreviewCoordinator(sender, feeds.chat()),
                new LowHealthWarning(feeds.chat()),
                new PhotoModeCoordinator(feeds.chat()),
                new BagSpaceWarningCoordinator(feeds.chat()),
                Lot40Windows.create(sender, feeds)); // lô 40 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildBuff);
        panels.register(records);
        panels.register(questPreview);
        panels.register(photoMode);
        panels.register(bagSpace);
        lot40.registerInto(panels);
    }
}
