package dev.kage.client.core.world;

/**
 * Luồng lô 35 (ADR-207…) — góp vật phẩm cho bang (ADR-207), lịch sử bang chiến (ADR-208)… Là MỘT thành phần của {@link
 * Lot34Windows} (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot35Windows(GuildItemDonationCoordinator guildItemDonation, GuildWarHistoryCoordinator warHistory) {

    static Lot35Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot35Windows(
                new GuildItemDonationCoordinator(sender, feeds.chat()),
                new GuildWarHistoryCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildItemDonation);
        panels.register(warHistory);
    }
}
