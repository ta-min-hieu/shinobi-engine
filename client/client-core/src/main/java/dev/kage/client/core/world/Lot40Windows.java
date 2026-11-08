package dev.kage.client.core.world;

/**
 * Luồng lô 40 (ADR-247…) — bình chọn trong bang (ADR-248), lịch sử nhặt đồ (ADR-253)… MỘT thành phần của {@link
 * Lot39Windows}.
 */
record Lot40Windows(GuildPollCoordinator guildPoll, LootHistoryCoordinator lootHistory) {

    static Lot40Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot40Windows(
                new GuildPollCoordinator(sender, feeds.chat()),
                new LootHistoryCoordinator(sender, feeds.chat(), feeds.itemCatalog()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildPoll);
        panels.register(lootHistory);
    }
}
