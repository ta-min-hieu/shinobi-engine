package dev.kage.client.core.world;

/**
 * Luồng lô 37 (ADR-223…) — thống kê cá nhân (ADR-223), lịch sự kiện (ADR-224), nhật ký chiến đấu (ADR-226)… Là MỘT
 * thành phần của {@link Lot36Windows} (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code
 * MethodLength}).
 */
record Lot37Windows(
        PersonalStatsCoordinator personalStats, EventCalendarCoordinator calendar, CombatLogCoordinator combatLog) {

    static Lot37Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot37Windows(
                new PersonalStatsCoordinator(sender, feeds.chat()),
                new EventCalendarCoordinator(sender, feeds.chat()),
                new CombatLogCoordinator(feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(personalStats);
        panels.register(calendar);
        panels.register(combatLog);
    }
}
