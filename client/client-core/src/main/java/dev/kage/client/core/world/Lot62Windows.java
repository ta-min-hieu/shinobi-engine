package dev.kage.client.core.world;

/**
 * Luồng lô 62 (ADR-423…) — lịch sử tên bang / mời bạn vào nhóm / câu chào / báo cừu nhân (ADR-423…426), đếm tin mới khi
 * ẩn chat / kênh rút gọn / từ khoá theo dõi / chế độ streamer (ADR-427…430). MỘT thành phần của {@link Lot61Windows}.
 */
record Lot62Windows(SocialExtrasCoordinator social, ChatLensCoordinator lens, Lot63Windows lot63) {

    static Lot62Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot62Windows(
                new SocialExtrasCoordinator(sender, feeds.chat()),
                new ChatLensCoordinator(feeds.chat()),
                Lot63Windows.create(sender, feeds)); // lô 63 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(social);
        panels.register(lens);
        lot63.registerInto(panels);
    }
}
