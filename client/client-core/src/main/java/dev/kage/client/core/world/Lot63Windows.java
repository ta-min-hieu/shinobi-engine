package dev.kage.client.core.world;

/**
 * Luồng lô 63 (ADR-431…) — chăm chỉ hôm nay / online 7 ngày / thân mật (ADR-431/432/434), báo kỹ năng sẵn sàng / màu
 * tên mình / nhắc việc trên HUD / ẩn dòng HUD phụ (ADR-435…438). Báo bang khi đồng môn đột phá (ADR-433) là dòng từ
 * server. MỘT thành phần của {@link Lot62Windows}.
 */
record Lot63Windows(RankingMoreCoordinator rankings, HudPolishCoordinator polish, Lot64Windows lot64) {

    static Lot63Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot63Windows(
                new RankingMoreCoordinator(sender, feeds.chat()),
                new HudPolishCoordinator(feeds.chat()),
                Lot64Windows.create(sender, feeds)); // lô 64 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(rankings);
        panels.register(polish);
        lot64.registerInto(panels);
    }
}
