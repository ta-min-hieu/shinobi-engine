package dev.kage.client.core.world;

/**
 * Luồng lô 50 (ADR-327…) — chuỗi đăng nhập (ADR-327), người vừa chơi cùng (ADR-329), báo bị soi (ADR-330), chữ +EXP
 * (ADR-331), đồng hồ phiên (ADR-332), mục tiêu cấp (ADR-334). Cảnh báo mạng lạ (ADR-328) chỉ ở server; phím ẩn chat
 * (ADR-333) nằm trong {@code ChatPanel}. MỘT thành phần của {@link Lot49Windows}.
 */
record Lot50Windows(
        LoginStreakCoordinator loginStreak,
        RecentPlayersCoordinator recentPlayers,
        InspectNoticeCoordinator inspectNotice,
        ExpGainCoordinator expGain,
        SessionClockCoordinator sessionClock,
        LevelGoalCoordinator levelGoal,
        Lot51Windows lot51) {

    static Lot50Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot50Windows(
                new LoginStreakCoordinator(sender, feeds.chat()),
                new RecentPlayersCoordinator(sender, feeds.chat()),
                new InspectNoticeCoordinator(sender, feeds.chat()),
                new ExpGainCoordinator(feeds.chat()),
                new SessionClockCoordinator(feeds.chat(), System::currentTimeMillis),
                new LevelGoalCoordinator(feeds.chat()),
                Lot51Windows.create(sender, feeds)); // lô 51 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(loginStreak);
        panels.register(recentPlayers);
        panels.register(inspectNotice);
        panels.register(expGain);
        panels.register(sessionClock);
        panels.register(levelGoal);
        lot51.registerInto(panels);
    }
}
