package dev.kage.client.core.world;

/**
 * Luồng lô 48 (ADR-311…) — báo bạn bè vào game kèm dòng chat (ADR-311), thư gửi mọi bạn bè (ADR-312), lời chào thành
 * viên mới (ADR-313), thư chưa đọc (ADR-314), bong bóng chat (ADR-315), ping trên HUD (ADR-316), lịch sử thông báo hệ
 * thống (ADR-317). HUD tối giản (ADR-318) là dòng cài đặt hiển thị. MỘT thành phần của {@link Lot47Windows}.
 */
record Lot48Windows(
        FriendPresenceCoordinator friendPresence,
        FriendsMailCoordinator friendsMail,
        GuildWelcomeCoordinator guildWelcome,
        MailReadCoordinator mailRead,
        ChatBubbleCoordinator bubbles,
        PingHudCoordinator pingHud,
        SystemNoticeHistoryCoordinator systemLog,
        Lot49Windows lot49) {

    static Lot48Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot48Windows(
                new FriendPresenceCoordinator(feeds.chat()),
                new FriendsMailCoordinator(sender, feeds.chat()),
                new GuildWelcomeCoordinator(sender, feeds.chat()),
                new MailReadCoordinator(sender),
                new ChatBubbleCoordinator(feeds.chat(), feeds.emoteDisplay()),
                new PingHudCoordinator(feeds.chat()),
                new SystemNoticeHistoryCoordinator(feeds.chat()),
                Lot49Windows.create(sender, feeds)); // lô 49 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(friendPresence);
        panels.register(friendsMail);
        panels.register(guildWelcome);
        panels.register(mailRead);
        panels.register(bubbles);
        panels.register(pingHud);
        panels.register(systemLog);
        lot49.registerInto(panels);
    }
}
