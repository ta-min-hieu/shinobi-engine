package dev.kage.client.core.world;

/**
 * Luồng lô 59 (ADR-399…) — tìm bang / xem lại chat bang / chỉ bạn bè nhắn riêng (ADR-399/401/402), giới hạn FPS / lặp
 * lệnh / giờ 12h (ADR-403/404/406), đồng hồ giao chiến (ADR-405). Báo đã đọc thư (ADR-400) là dòng hệ thống từ server.
 * MỘT thành phần của {@link Lot58Windows}.
 */
record Lot59Windows(
        GuildToolsCoordinator guildTools,
        ClientToolsCoordinator clientTools,
        CombatTimerCoordinator combatTimer,
        Lot60Windows lot60) {

    static Lot59Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot59Windows(
                new GuildToolsCoordinator(sender, feeds.chat()),
                new ClientToolsCoordinator(feeds.chat()),
                new CombatTimerCoordinator(feeds.chat(), System::currentTimeMillis),
                Lot60Windows.create(sender, feeds)); // lô 60 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(guildTools);
        panels.register(clientTools);
        panels.register(combatTimer);
        lot60.registerInto(panels);
    }
}
