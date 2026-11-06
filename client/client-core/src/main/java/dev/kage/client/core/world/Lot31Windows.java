package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 31 — lưu bộ trang bị (ADR-175), dùng nhiều vật phẩm (ADR-176), lịch sử phường thị (ADR-177), tự trả lời khi
 * vắng (ADR-178). Gom thành MỘT thành phần của {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code
 * MethodLength}.
 */
record Lot31Windows(
        EquipmentPresetCoordinator equipmentPresets,
        BulkUseCoordinator bulkUse,
        MarketHistoryCoordinator marketHistory,
        AwayCoordinator away,
        GuildNoticeCoordinator guildNotice,
        GuildLogCoordinator guildLog) {

    static Lot31Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot31Windows(
                new EquipmentPresetCoordinator(sender, feeds),
                new BulkUseCoordinator(sender, feeds.chat()),
                MarketHistoryCoordinator.create(skin, sender, feeds),
                new AwayCoordinator(sender, feeds.chat()),
                new GuildNoticeCoordinator(sender, feeds.chat()),
                GuildLogCoordinator.create(skin, sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(equipmentPresets);
        panels.register(bulkUse);
        panels.register(marketHistory.panel(), marketHistory);
        panels.register(away);
        panels.register(guildNotice);
        panels.register(guildLog.panel(), guildLog);
    }
}
