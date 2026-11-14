package dev.kage.client.core.world;

/**
 * Luồng lô 56 (ADR-375…) — thư cả nhóm + đỉnh online (ADR-375/377), chữ bay linh thạch (ADR-379), Tab hoàn thành
 * lệnh (ADR-380), nền chat + sao chép tên (ADR-381/382). Lời nhắn kết bạn (ADR-376) ở {@code /ketban} và cửa sổ Bạn
 * bè; thống kê PvP (ADR-378) trong {@code /thongke}. MỘT thành phần của {@link Lot55Windows}.
 */
record Lot56Windows(
        PartyMailCoordinator partyMail,
        GoldGainCoordinator goldGain,
        ChatCompletionCoordinator completion,
        ChatToolsCoordinator chatTools) {

    static Lot56Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot56Windows(
                new PartyMailCoordinator(sender, feeds.chat()),
                new GoldGainCoordinator(feeds.chat()),
                new ChatCompletionCoordinator(feeds.chat()),
                new ChatToolsCoordinator(feeds.chat()));
    }

    void registerInto(PanelHub panels) {
        panels.register(partyMail);
        panels.register(goldGain);
        panels.register(completion);
        panels.register(chatTools);
    }
}
