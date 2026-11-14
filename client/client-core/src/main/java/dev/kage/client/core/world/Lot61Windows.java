package dev.kage.client.core.world;

/**
 * Luồng lô 61 (ADR-415…) — bạn lâu vắng / tân thủ hôm nay (ADR-416/417), dọn khung chat: ẩn dòng hệ thống, gộp tin
 * trùng, bong bóng chỉ bạn bè, câu nói nhanh (ADR-419…422). Tóm tắt online (ADR-415) và nhắc hộp thư (ADR-418) là
 * dòng hệ thống từ server. MỘT thành phần của {@link Lot60Windows}.
 */
record Lot61Windows(FriendDigestCoordinator digest, ChatTidyCoordinator tidy, Lot62Windows lot62) {

    static Lot61Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot61Windows(
                new FriendDigestCoordinator(sender, feeds.chat()),
                new ChatTidyCoordinator(feeds.chat()),
                Lot62Windows.create(sender, feeds)); // lô 62 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(digest);
        panels.register(tidy);
        lot62.registerInto(panels);
    }
}
