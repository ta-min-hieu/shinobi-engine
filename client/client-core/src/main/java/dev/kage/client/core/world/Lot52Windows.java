package dev.kage.client.core.world;

/**
 * Luồng lô 52 (ADR-343…) — tra tên cũ (ADR-343), ký hiệu bang (ADR-344), báo bị xoá bạn (ADR-345), lưu chat (ADR-347),
 * chống chữ hoa (ADR-348). Cảnh báo treo máy (ADR-346) chỉ ở server; xác nhận bán (ADR-349) trong {@code
 * InventoryCoordinator}; số chí mạng (ADR-350) trong {@code CombatFeedbackPresenter}. MỘT thành phần của {@link
 * Lot51Windows}.
 */
record Lot52Windows(
        NameHistoryCoordinator nameHistory,
        GuildTagCoordinator guildTag,
        FriendRemovedCoordinator friendRemoved,
        ChatLogExportCoordinator chatLog,
        CapsCalmCoordinator capsCalm) {

    static Lot52Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot52Windows(
                new NameHistoryCoordinator(sender, feeds.chat()),
                new GuildTagCoordinator(sender, feeds.chat()),
                new FriendRemovedCoordinator(sender, feeds.chat()),
                new ChatLogExportCoordinator(feeds.chat()),
                new CapsCalmCoordinator(feeds.chat()));
    }

    void registerInto(PanelHub panels) {
        panels.register(nameHistory);
        panels.register(guildTag);
        panels.register(friendRemoved);
        panels.register(chatLog);
        panels.register(capsCalm);
    }
}
