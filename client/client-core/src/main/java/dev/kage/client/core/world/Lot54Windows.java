package dev.kage.client.core.world;

/**
 * Luồng lô 54 (ADR-359…) — đọc hết thư / ai thích mình / lịch sử bị soi / chỉ bạn bè soi (ADR-359..362), số dòng và
 * màu bạn bè trong khung chat (ADR-363/364). Màn hình xám khi chết (ADR-365) trong {@code LowHealthWarning}, % EXP
 * (ADR-366) trong {@code HudProgress}. MỘT thành phần của {@link Lot53Windows}.
 */
record Lot54Windows(ProfileVisitsCoordinator profileVisits, ChatLinesCoordinator chatLines, Lot55Windows lot55) {

    static Lot54Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot54Windows(
                new ProfileVisitsCoordinator(sender, feeds.chat()),
                new ChatLinesCoordinator(feeds.chat()),
                Lot55Windows.create(sender, feeds)); // lô 55 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(profileVisits);
        panels.register(chatLines);
        lot55.registerInto(panels);
    }
}
