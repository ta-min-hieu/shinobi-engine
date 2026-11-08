package dev.kage.client.core.world;

/**
 * Luồng lô 37 (ADR-223…) — thống kê cá nhân (ADR-223), lịch sự kiện (ADR-224), nhật ký chiến đấu (ADR-226), gợi ý nhiệm
 * vụ (ADR-228), lọc kênh chat (ADR-229), khoe thành tựu (ADR-230)… Là MỘT thành phần của {@link Lot36Windows} (chuỗi
 * lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot37Windows(
        PersonalStatsCoordinator personalStats,
        EventCalendarCoordinator calendar,
        CombatLogCoordinator combatLog,
        NextQuestHintCoordinator nextQuest,
        ChatFilterCoordinator chatFilter,
        AchievementShareCoordinator achievementShare) {

    static Lot37Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot37Windows(
                new PersonalStatsCoordinator(sender, feeds.chat()),
                new EventCalendarCoordinator(sender, feeds.chat()),
                new CombatLogCoordinator(feeds.chat()),
                new NextQuestHintCoordinator(sender, feeds.chat()),
                new ChatFilterCoordinator(feeds.chat()),
                new AchievementShareCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(personalStats);
        panels.register(calendar);
        panels.register(combatLog);
        panels.register(nextQuest);
        panels.register(chatFilter);
        panels.register(achievementShare);
    }
}
