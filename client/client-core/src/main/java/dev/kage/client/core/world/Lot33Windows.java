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
        GuildBankCoordinator guildBank,
        Lot34Windows lot34) {

    static Lot33Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot33Windows(
                new PartyLeaderCoordinator(sender, feeds.chat()),
                new PetNameCoordinator(sender, feeds.chat()),
                new EnemyListCoordinator(sender, feeds.chat()),
                new DeathRecapCoordinator(feeds.chat()),
                new MarketWatchCoordinator(sender, feeds.chat(), feeds.itemCatalog()),
                new GuildBankCoordinator(sender, feeds.chat()),
                Lot34Windows.create(skin, sender, feeds)); // lô 34 lồng ở đây
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(partyLeader);
        panels.register(petName);
        panels.register(enemies);
        panels.register(deathRecap);
        panels.register(marketWatch);
        panels.register(guildBank);
        lot34.registerInto(panels);
    }
}
