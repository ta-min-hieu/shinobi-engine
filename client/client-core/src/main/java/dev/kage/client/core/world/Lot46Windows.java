package dev.kage.client.core.world;

/**
 * Luồng lô 46 (ADR-295…) — chuyển tiếp thư (ADR-295), boss đã hạ (ADR-296), thư hẹn giờ (ADR-297), thích hồ sơ
 * (ADR-298), chụp màn hình F12 (ADR-300), đồng hồ bấm giờ (ADR-301), máy tính nhanh (ADR-302). Khoảng cách tới mục tiêu
 * (ADR-299) nằm ở khung mục tiêu của HUD. MỘT thành phần của {@link Lot45Windows}.
 */
record Lot46Windows(
        MailForwardCoordinator forward,
        BossKillsCoordinator bossKills,
        ScheduledMailCoordinator scheduledMail,
        ProfileLikeCoordinator likes,
        ScreenshotCoordinator screenshot,
        StopwatchCoordinator stopwatch,
        CalculatorCoordinator calculator,
        Lot47Windows lot47) {

    static Lot46Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot46Windows(
                new MailForwardCoordinator(sender, feeds.chat()),
                new BossKillsCoordinator(sender, feeds.chat()),
                new ScheduledMailCoordinator(sender, feeds.chat()),
                new ProfileLikeCoordinator(sender, feeds.chat()),
                new ScreenshotCoordinator(feeds.chat()),
                new StopwatchCoordinator(feeds.chat(), System::currentTimeMillis),
                new CalculatorCoordinator(feeds.chat()),
                Lot47Windows.create(sender, feeds)); // lô 47 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(forward);
        panels.register(bossKills);
        panels.register(scheduledMail);
        panels.register(likes);
        panels.register(screenshot);
        panels.register(stopwatch);
        panels.register(calculator);
        lot47.registerInto(panels);
    }
}
