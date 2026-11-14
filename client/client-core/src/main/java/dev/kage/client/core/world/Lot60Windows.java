package dev.kage.client.core.world;

/**
 * Luồng lô 60 (ADR-407…) — lịch sử chat thế giới / bạn chung / tuổi nhân vật (ADR-408/409/410), bốn dòng HUD sự kiện kế
 * tiếp / thư chưa đọc / DPS / linh thạch phiên (ADR-411…414). Cấp tối thiểu gửi thư (ADR-407) là luật server + câu
 * báo lỗi trong {@code MailController}. MỘT thành phần của {@link Lot59Windows}.
 */
record Lot60Windows(LookupToolsCoordinator lookups, HudStatsCoordinator hudStats, Lot61Windows lot61) {

    static Lot60Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot60Windows(
                new LookupToolsCoordinator(sender, feeds.chat()),
                new HudStatsCoordinator(sender, feeds.chat(), System::currentTimeMillis),
                Lot61Windows.create(sender, feeds)); // lô 61 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(lookups);
        panels.register(hudStats);
        lot61.registerInto(panels);
    }
}
