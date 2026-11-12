package dev.kage.client.core.world;

/**
 * Luồng lô 43 (ADR-271…) — tóm tắt phiên + EXP/giờ (ADR-271/272), biệt danh lệnh (ADR-273), giá trị túi (ADR-274), tìm
 * thư (ADR-275), bạn yêu thích (ADR-276). Thư nháp (ADR-277) nằm trong {@link MailCoordinator}; chia vàng (ADR-278) ở
 * {@link Lot42Windows}. MỘT thành phần của {@link Lot42Windows}.
 */
record Lot43Windows(
        SessionSummaryCoordinator session,
        CommandAliasCoordinator aliases,
        BagValueCoordinator bagValue,
        MailSearchCoordinator mailSearch,
        FavoriteFriendsCoordinator favorites,
        Lot44Windows lot44) {

    static Lot43Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot43Windows(
                new SessionSummaryCoordinator(feeds.chat(), System::currentTimeMillis),
                new CommandAliasCoordinator(feeds.chat()),
                new BagValueCoordinator(sender, feeds.chat()),
                new MailSearchCoordinator(sender, feeds.chat()),
                new FavoriteFriendsCoordinator(feeds.chat()),
                Lot44Windows.create(sender, feeds)); // lô 44 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(session);
        panels.register(aliases);
        panels.register(bagValue);
        panels.register(mailSearch);
        panels.register(favorites);
        lot44.registerInto(panels);
    }

    /** ADR-276 — khung bạn bè xếp bạn yêu thích lên đầu, vẽ lại mỗi khi danh sách ghim đổi. */
    void useFriends(FriendCoordinator friends) {
        friends.useFavorites(favorites::isFavorite);
        favorites.onChanged(() -> friends.useFavorites(favorites::isFavorite));
    }
}
