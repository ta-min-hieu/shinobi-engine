package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 32 — nhường chức bang chủ (ADR-183), phó bang chủ (ADR-184), bảng chiêu mộ bang (ADR-185), điểm danh bang
 * (ADR-186), khoe vật phẩm (ADR-188), lịch sử giao dịch (ADR-189), đổi phím tắt (ADR-190). Là MỘT thành phần của {@link
 * Lot31Windows} (không phải của {@link ExpansionWindows}: {@code ExpansionWindows.create} đã chạm trần {@code
 * MethodLength}).
 */
record Lot32Windows(
        GuildLeadershipCoordinator guildLeadership,
        GuildViceCoordinator guildVice,
        GuildRecruitmentCoordinator guildRecruitment,
        GuildCheckInCoordinator guildCheckIn,
        ItemLinkCoordinator itemLink,
        TradeHistoryCoordinator tradeHistory,
        KeyBindingsCoordinator keyBindings) {

    static Lot32Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot32Windows(
                new GuildLeadershipCoordinator(sender, feeds.chat()),
                new GuildViceCoordinator(sender, feeds.chat()),
                GuildRecruitmentCoordinator.create(skin, sender, feeds),
                new GuildCheckInCoordinator(sender, feeds.chat()),
                new ItemLinkCoordinator(sender, feeds),
                TradeHistoryCoordinator.create(skin, sender, feeds),
                keyBindings(feeds));
    }

    /** ADR-190 — lắp phím đã lưu trên máy rồi dựng lệnh {@code /phimtat}. */
    private static KeyBindingsCoordinator keyBindings(ReportWindows.Feeds feeds) {
        dev.kage.client.core.input.KeyBindings.install(dev.kage.client.core.input.KeyBindings.load(
                new dev.kage.client.core.input.GdxPreferencesKeyBindingsStore()));
        return new KeyBindingsCoordinator(feeds.chat());
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(guildLeadership);
        panels.register(guildVice);
        panels.register(guildRecruitment.panel(), guildRecruitment);
        panels.register(guildCheckIn);
        panels.register(itemLink);
        panels.register(tradeHistory.panel(), tradeHistory);
        panels.register(keyBindings);
    }
}
