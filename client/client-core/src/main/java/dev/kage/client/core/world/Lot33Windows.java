package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 33 — nhường trưởng nhóm (ADR-191), đặt tên linh thú (ADR-193), cừu nhân (ADR-194), tóm tắt khi chết
 * (ADR-195)… Là MỘT thành phần của {@link Lot32Windows} (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm
 * trần {@code MethodLength}).
 */
record Lot33Windows(
        PartyLeaderCoordinator partyLeader,
        PetNameCoordinator petName,
        EnemyListCoordinator enemies,
        DeathRecapCoordinator deathRecap,
        MarketWatchCoordinator marketWatch,
        GuildBankCoordinator guildBank) {

    @SuppressWarnings("unused") // skin/feeds dành cho các cửa sổ lô 33 tiếp theo
    static Lot33Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot33Windows(
                new PartyLeaderCoordinator(sender, feeds.chat()),
                new PetNameCoordinator(sender, feeds.chat()),
                new EnemyListCoordinator(sender, feeds.chat()),
                new DeathRecapCoordinator(feeds.chat()),
                new MarketWatchCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new GuildBankCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(partyLeader);
        panels.register(petName);
        panels.register(enemies);
        panels.register(deathRecap);
        panels.register(marketWatch);
        panels.register(guildBank);
    }
}
