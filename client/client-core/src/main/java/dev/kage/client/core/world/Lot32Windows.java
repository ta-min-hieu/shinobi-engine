package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 32 — nhường chức bang chủ (ADR-183), phó bang chủ (ADR-184). Là MỘT thành phần của {@link Lot31Windows}
 * (không phải của {@link ExpansionWindows}: {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot32Windows(GuildLeadershipCoordinator guildLeadership, GuildViceCoordinator guildVice) {

    static Lot32Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot32Windows(
                new GuildLeadershipCoordinator(sender, feeds.chat()), new GuildViceCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildLeadership);
        panels.register(guildVice);
    }
}
