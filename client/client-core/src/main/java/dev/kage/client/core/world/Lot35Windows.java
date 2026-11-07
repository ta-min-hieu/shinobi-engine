package dev.kage.client.core.world;

/**
 * Luồng lô 35 (ADR-207…) — góp vật phẩm cho bang (ADR-207), lịch sử bang chiến (ADR-208), gợi ý kết bạn (ADR-211), chặn
 * thư người lạ (ADR-212), đánh dấu mục tiêu nhóm (ADR-213)… Là MỘT thành phần của {@link Lot34Windows} (chuỗi lồng nhau
 * vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot35Windows(
        GuildItemDonationCoordinator guildItemDonation,
        GuildWarHistoryCoordinator warHistory,
        FriendSuggestionCoordinator friendSuggestions,
        StrangerMailCoordinator strangerMail,
        PartyMarkCoordinator partyMark) {

    static Lot35Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot35Windows(
                new GuildItemDonationCoordinator(sender, feeds.chat()),
                new GuildWarHistoryCoordinator(sender, feeds.chat()),
                new FriendSuggestionCoordinator(sender, feeds.chat()),
                new StrangerMailCoordinator(sender, feeds.chat()),
                new PartyMarkCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildItemDonation);
        panels.register(warHistory);
        panels.register(friendSuggestions);
        panels.register(strangerMail);
        panels.register(partyMark);
    }
}
