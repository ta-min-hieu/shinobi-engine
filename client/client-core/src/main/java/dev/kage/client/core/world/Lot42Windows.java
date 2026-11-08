package dev.kage.client.core.world;

/**
 * Luồng lô 42 (ADR-263…) — kiểm tra sẵn sàng (ADR-263), đếm ngược (ADR-264), xúc xắc (ADR-265), báo bang online
 * (ADR-266), danh sách lệnh (ADR-267), giờ trước tin chat (ADR-268), đo ping (ADR-269), toạ độ (ADR-270); chia vàng
 * nhóm (ADR-278, lô 43). MỘT thành phần của {@link Lot41Windows}.
 */
record Lot42Windows(
        CommandHelpCoordinator help,
        ChatTimestampCoordinator chatTimestamps,
        PingCoordinator ping,
        CoordinatesCoordinator coordinates,
        ReadyCheckCoordinator readyCheck,
        PartyCountdownCoordinator countdown,
        DiceRollCoordinator dice,
        GuildOnlineNoticeCoordinator guildOnline,
        PartyGoldSplitCoordinator goldSplit) {

    static Lot42Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot42Windows(
                new CommandHelpCoordinator(feeds.chat()),
                new ChatTimestampCoordinator(feeds.chat()),
                new PingCoordinator(sender, feeds.chat(), System::currentTimeMillis),
                new CoordinatesCoordinator(sender, feeds.chat()),
                new ReadyCheckCoordinator(sender, feeds.chat()),
                new PartyCountdownCoordinator(sender, feeds.chat()),
                new DiceRollCoordinator(sender, feeds.chat()),
                new GuildOnlineNoticeCoordinator(feeds.chat()),
                new PartyGoldSplitCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(help);
        panels.register(chatTimestamps);
        panels.register(ping);
        panels.register(coordinates);
        panels.register(readyCheck);
        panels.register(countdown);
        panels.register(dice);
        panels.register(guildOnline);
        panels.register(goldSplit);
    }
}
